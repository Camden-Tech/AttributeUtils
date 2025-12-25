package me.baddcamden.attributeutils.handler.entity;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.command.CommandParsingUtils;
import me.baddcamden.attributeutils.model.AttributeDefinition;
import me.baddcamden.attributeutils.model.AttributeValueStages;
import me.baddcamden.attributeutils.VanillaAttributeResolver;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.Attributable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Integrates entity interactions with the attribute computation pipeline. Responsibilities include applying computed
 * caps (current stage) to live player entities and translating command-provided baseline/cap values into persistent
 * data for spawned entities so that subsequent computations start from the correct stage values.
 */
public class EntityAttributeHandler {
    /**
     * Divisor to translate attribute speed stages into Bukkit fly speed (clamped to [-1, 1]).
     */
    private static final double FLY_SPEED_SCALE = 4.0d;
    /**
     * Tolerance for comparing floating point deltas when applying modifiers.
     */
    private static final double ATTRIBUTE_DELTA_EPSILON = 0.0000001d;
    /**
     * Minimum health applied when MAX_HEALTH is adjusted to avoid invalid values.
     */
    private static final double MINIMUM_MAX_HEALTH = 0.0001d;
    /**
     * Prefix used when constructing modifier ids and names.
     */
    private static final String ATTRIBUTE_MODIFIER_PREFIX = "attributeutils:";
    /**
     * Persistent data key prefix/suffix used for attribute storage.
     */
    private static final String ATTRIBUTE_KEY_PREFIX = "attr_";
    private static final String ATTRIBUTE_CAP_SUFFIX = "_cap";
    /**
     * Deterministic modifier id for swim speed adjustments.
     */
    private static final java.util.UUID SWIM_SPEED_MODIFIER_ID = java.util.UUID.nameUUIDFromBytes(
            (ATTRIBUTE_MODIFIER_PREFIX + "swim_speed").getBytes(StandardCharsets.UTF_8));
    /**
     * Entry point into the attribute computation pipeline.
     */
    private final AttributeFacade attributeFacade;
    /**
     * Owning plugin, used for scheduling and persistent data scoping.
     */
    private final Plugin plugin;
    /**
     * Pre-resolved mapping of custom attribute ids to Bukkit attributes.
     */
    private final Map<String, Attribute> vanillaAttributeTargets;
    /**
     * Reflection handle for the transient modifier API; resolved lazily to remain compatible with older Bukkit versions.
     */
    private Method transientModifierMethod;
    /**
     * Indicates whether the transient modifier lookup was attempted to avoid repeated reflection work.
     */
    private boolean transientMethodResolved;
    /**
     * Periodic task that re-applies movement-related attributes to online players.
     */
    private BukkitTask ticker;

    /**
     * Creates a handler that synchronizes computed attribute values with Bukkit entities and begins the periodic
     * player tick used to refresh movement-related attributes.
     */
    public EntityAttributeHandler(AttributeFacade attributeFacade,
                                  Plugin plugin,
                                  Map<String, Attribute> vanillaAttributeTargets) {
        this.attributeFacade = attributeFacade;
        this.plugin = plugin;
        this.vanillaAttributeTargets = vanillaAttributeTargets;
        resolveTransientModifierMethod();
        startTicker();
    }

    /**
     * Syncs player movement-related attributes by recalculating flight and swim speeds from the attribute pipeline.
     *
     * @param player online player whose live attribute caps should be refreshed
     */
    public void applyPlayerCaps(Player player) {
        if (player == null) {
            return;
        }
        applyFlySpeed(player);
        applySwimSpeed(player);
    }

    /**
     * Spawns a Bukkit entity at the provided location and persists baseline attribute and cap data for each definition.
     * Matching vanilla attributes are updated immediately so the spawned entity reflects the requested values.
     *
     * @param location    position at which the entity should be created (must have a world)
     * @param entityType  Bukkit entity type to create
     * @param definitions parsed attribute inputs specifying base values and optional caps
     * @return container summarizing the spawned entity and a user-facing string of applied attributes
     */
    public SpawnedEntityResult spawnAttributedEntity(Location location,
                                                    EntityType entityType,
                                                    List<CommandParsingUtils.AttributeDefinition> definitions) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("invalid-location");
        }
        if (definitions == null || definitions.isEmpty()) {
            throw new IllegalArgumentException("attribute-definitions-required");
        }

        Entity entity = location.getWorld().spawnEntity(location, entityType);

        PersistentDataContainer container = entity.getPersistentDataContainer();
        List<String> summary = new ArrayList<>();
        for (CommandParsingUtils.AttributeDefinition definition : definitions) {
            AttributeDefinition attributeDefinition = attributeFacade.getDefinition(definition.getKey().key())
                    .orElseThrow(() -> new IllegalArgumentException(definition.getKey().asString()));

            double clampedValue = attributeDefinition.capConfig().clamp(definition.getValue());
            Optional<Double> capOverride = definition.getCapOverride()
                    .map(value -> attributeDefinition.capConfig().clamp(value));

            container.set(valueKey(attributeDefinition.id()), PersistentDataType.DOUBLE, clampedValue);
            capOverride.ifPresent(cap -> container.set(capKey(attributeDefinition.id()), PersistentDataType.DOUBLE, cap));

            applyVanillaAttribute(entity, attributeDefinition.id(), clampedValue);

            String summaryLine = attributeDefinition.id() + "=" + clampedValue;
            if (capOverride.isPresent()) {
                double cap = capOverride.get();
                summaryLine += " (cap " + cap + ")";
            }
            summary.add(summaryLine);
        }

        return new SpawnedEntityResult(entity, String.join(", ", summary));
    }

    /**
     * Rehydrates persisted attribute values and cap overrides from an entity's data container, applying clamped values
     * to vanilla attributes and the attribute pipeline when appropriate.
     *
     * @param entity entity whose stored attribute baseline and cap entries should be applied
     */
    public void applyPersistentAttributes(Entity entity) {
        if (entity == null) {
            return;
        }

        PersistentDataContainer container = entity.getPersistentDataContainer();
        container.getKeys().forEach(key -> {
            if (!isPluginAttributeKey(key)) {
                return;
            }

            AttributeKeyDetails keyDetails = parseAttributeKey(key.getKey());
            if (keyDetails == null) {
                return;
            }

            Optional<AttributeDefinition> definition = findAttributeDefinition(keyDetails.attributeId());
            if (definition.isEmpty()) {
                return;
            }

            Double value = container.get(key, PersistentDataType.DOUBLE);
            if (value == null) {
                return;
            }

            AttributeDefinition attr = definition.get();
            double clampedValue = attr.capConfig().clamp(value);
            if (keyDetails.isCap()) {
                attributeFacade.setPlayerCapOverride(entity.getUniqueId(), attr.id(), clampedValue);
                return;
            }

            applyVanillaAttribute(entity, attr.id(), clampedValue);
            attributeFacade.getOrCreatePlayerInstance(entity.getUniqueId(), attr.id())
                    .setCurrentBaseValue(clampedValue);
        });
    }

    /**
     * Applies a raw value to a vanilla attribute, clamping health safely when necessary.
     *
     * @param entity      target entity owning the vanilla attribute
     * @param attributeId identifier for the attribute to update
     * @param value       baseline value to store on the attribute instance
     */
    private void applyVanillaAttribute(Entity entity, String attributeId, double value) {
        if (!(entity instanceof Attributable attributable) || isBlank(attributeId)) {
            return;
        }

        Attribute target = resolveVanillaTarget(attributeId);
        if (target == null) {
            return;
        }

        AttributeInstance instance = attributable.getAttribute(target);
        if (instance == null) {
            return;
        }

        instance.setBaseValue(value);
        if (entity instanceof LivingEntity living && target == Attribute.MAX_HEALTH) {
            double safeHealth = Math.max(MINIMUM_MAX_HEALTH, value);
            living.setHealth(safeHealth);
        }
    }

    /**
     * Builds a persistent data key for an attribute value entry.
     *
     * @param attributeId raw attribute identifier
     * @return namespaced key scoped to this plugin used to store the baseline value
     */
    private NamespacedKey valueKey(String attributeId) {
        return new NamespacedKey(plugin, ATTRIBUTE_KEY_PREFIX + sanitize(attributeId));
    }

    /**
     * Builds a persistent data key for an attribute cap override entry.
     *
     * @param attributeId raw attribute identifier
     * @return namespaced key scoped to this plugin used to store a cap override
     */
    private NamespacedKey capKey(String attributeId) {
        return new NamespacedKey(plugin, ATTRIBUTE_KEY_PREFIX + sanitize(attributeId) + ATTRIBUTE_CAP_SUFFIX);
    }

    /**
     * Normalizes attribute identifiers for safe storage in {@link NamespacedKey} values.
     *
     * @param attributeId attribute identifier that may contain dots or uppercase letters
     * @return lowercase identifier with dots replaced by underscores
     */
    private String sanitize(String attributeId) {
        return attributeId.toLowerCase(Locale.ROOT).replace('.', '_');
    }

    /**
     * Returns whether the provided attribute id is either null or composed only of whitespace.
     *
     * @param attributeId attribute identifier to validate
     * @return true when the identifier is null or blank
     */
    private boolean isBlank(String attributeId) {
        return attributeId == null || attributeId.isBlank();
    }

    /**
     * Resolves a configured mapping for the attribute or falls back to Bukkit's vanilla attribute registry.
     *
     * @param attributeId attribute identifier that may correspond to a vanilla attribute
     * @return Bukkit attribute mapped from configuration or directly from the enum; {@code null} when unknown
     */
    private Attribute resolveVanillaTarget(String attributeId) {
        if (isBlank(attributeId)) {
            return null;
        }
        Attribute target = vanillaAttributeTargets.get(attributeId.toLowerCase(Locale.ROOT));
        return target != null ? target : resolveAttribute(attributeId);
    }

    /**
     * Checks whether a persistent data key belongs to this plugin and follows the attribute key pattern.
     *
     * @param key namespaced key stored in an entity's persistent data container
     * @return true if the key is scoped to this plugin and starts with the attribute prefix
     */
    private boolean isPluginAttributeKey(NamespacedKey key) {
        return key != null && key.getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT))
                && key.getKey().startsWith(ATTRIBUTE_KEY_PREFIX);
    }

    /**
     * Extracts the attribute id and whether the key represents a cap override from a stored data key.
     *
     * @param key raw key string fetched from a namespaced key
     * @return parsed attribute details or {@code null} when the key does not match the expected pattern
     */
    private AttributeKeyDetails parseAttributeKey(String key) {
        if (key == null || !key.startsWith(ATTRIBUTE_KEY_PREFIX)) {
            return null;
        }
        boolean isCap = key.endsWith(ATTRIBUTE_CAP_SUFFIX);
        int start = ATTRIBUTE_KEY_PREFIX.length();
        int end = isCap ? key.length() - ATTRIBUTE_CAP_SUFFIX.length() : key.length();
        if (end <= start) {
            return null;
        }
        String attributeId = key.substring(start, end);
        return new AttributeKeyDetails(attributeId, isCap);
    }

    /**
     * Looks up an attribute definition using the provided id, accepting either dotted or underscored formats.
     *
     * @param attributeId attribute identifier in dotted or underscored form
     * @return optional definition when found in the facade
     */
    private Optional<AttributeDefinition> findAttributeDefinition(String attributeId) {
        if (attributeId == null || attributeId.isBlank()) {
            return Optional.empty();
        }
        return attributeFacade.getDefinition(attributeId)
                .or(() -> attributeFacade.getDefinition(attributeId.replace('_', '.')));
    }

    /**
     * Details extracted from a persistent data key including the normalized attribute id and whether it represents a cap.
     *
     * @param attributeId sanitized attribute identifier stored in the persistent data key
     * @param isCap       flag indicating whether the key refers to a cap override entry
     */
    private record AttributeKeyDetails(String attributeId, boolean isCap) {
    }

    /**
     * Encapsulates the newly spawned entity plus a summary of applied attributes for display.
     *
     * @param entity  the Bukkit entity created by {@link #spawnAttributedEntity(Location, EntityType, List)}
     * @param summary comma-delimited description of baseline values and cap overrides applied to the entity
     */
    public record SpawnedEntityResult(Entity entity, String summary) {
    }

    /**
     * Computes and applies a single attribute to non-player and player entities, routing players to the dedicated
     * overload to leverage player-specific computation helpers.
     *
     * @param entity      living entity whose computed attribute should be applied
     * @param attributeId identifier of the attribute to compute
     */
    public void applyVanillaAttribute(LivingEntity entity, String attributeId) {
        if (entity == null || isBlank(attributeId)) {
            return;
        }

        if (entity instanceof Player player) {
            applyVanillaAttribute(player, attributeId);
            return;
        }

        Attribute target = resolveVanillaTarget(attributeId);
        if (target == null) {
            return;
        }

        AttributeValueStages computed = attributeFacade.compute(attributeId, entity.getUniqueId(), null);
        applyComputedModifier(entity, target, attributeId, computed);
    }

    /**
     * Computes and applies a single attribute to a player, adjusting the relevant vanilla attribute with a transient
     * modifier based on the computed delta.
     *
     * @param player      target player whose computed attribute should be applied
     * @param attributeId identifier of the attribute to compute
     */
    public void applyVanillaAttribute(Player player, String attributeId) {
        if (player == null || isBlank(attributeId)) {
            return;
        }
        Attribute target = resolveVanillaTarget(attributeId);
        if (target == null) {
            return;
        }

        AttributeValueStages computed = attributeFacade.compute(attributeId, player);
        applyComputedModifier(player, target, attributeId, computed);
    }

    /**
     * Attempts to resolve a Bukkit {@link Attribute} enum constant from the provided id.
     *
     * @param attributeId identifier that may match a vanilla attribute enum constant
     * @return resolved attribute or {@code null} when no match exists
     */
    private Attribute resolveAttribute(String attributeId) {
        String normalized = attributeId.toUpperCase(Locale.ROOT);
        try {
            return Attribute.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Applies a computed delta to the target attribute using a deterministic modifier id, skipping zero-delta
     * updates to avoid unnecessary churn.
     *
     * @param attributable entity or player with the target attribute instance
     * @param target       vanilla attribute resolved for the provided id
     * @param attributeId  identifier used to generate a deterministic modifier id
     * @param computed     fully computed attribute values from the pipeline
     */
    private void applyComputedModifier(Attributable attributable, Attribute target, String attributeId, AttributeValueStages computed) {
        if (attributable == null || target == null || computed == null) {
            return;
        }
        AttributeInstance instance = attributable.getAttribute(target);
        if (instance == null) {
            return;
        }

        UUID modifierId = attributeModifierId(attributeId);
        boolean hadModifier = hasModifierById(instance, modifierId);
        removeModifierById(instance, modifierId);
        if (hasModifierById(instance, modifierId)) {
            // Avoid stacking if removal failed for any reason.
            return;
        }

        double vanillaValue = VanillaAttributeResolver.resolveVanillaValue(instance, instance.getBaseValue());

        double defaultAdditive = computed.defaultPermanent() - computed.rawDefault();
        double defaultMultiplier = resolveMultiplier(computed.defaultPermanent(), computed.defaultFinal());
        double currentAdditive = computed.currentPermanent() - computed.rawCurrent();
        double currentMultiplier = resolveMultiplier(computed.currentPermanent(), computed.currentFinal());

        double rebuilt = vanillaValue;
        rebuilt += defaultAdditive;
        rebuilt *= defaultMultiplier;
        rebuilt += currentAdditive;
        rebuilt *= currentMultiplier;

        double delta = rebuilt - vanillaValue;
        if (Math.abs(delta) < ATTRIBUTE_DELTA_EPSILON) {
            return;
        }

        AttributeModifier modifier = new AttributeModifier(
                modifierId,
                ATTRIBUTE_MODIFIER_PREFIX + attributeId,
                delta,
                AttributeModifier.Operation.ADD_NUMBER
        );
        if (hadModifier || !hasModifierById(instance, modifierId)) {
            addModifier(instance, modifier);
        }
    }

    private boolean hasModifierById(AttributeInstance instance, UUID modifierId) {
        return instance.getModifiers().stream()
                .anyMatch(modifier -> modifier.getUniqueId().equals(modifierId));
    }

    private double resolveMultiplier(double valueBefore, double valueAfter) {
        if (Math.abs(valueBefore) < ATTRIBUTE_DELTA_EPSILON) {
            return 1.0d;
        }
        return valueAfter / valueBefore;
    }

    /**
     * Generates a deterministic modifier id based on the attribute id to simplify later removal/replacement.
     *
     * @param attributeId identifier used to seed the UUID calculation
     * @return deterministic UUID scoped to the attribute id
     */
    private UUID attributeModifierId(String attributeId) {
        return java.util.UUID.nameUUIDFromBytes((ATTRIBUTE_MODIFIER_PREFIX + attributeId).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Removes a modifier from the instance that matches the provided unique id, if present.
     *
     * @param instance   attribute instance to prune modifiers from
     * @param modifierId deterministic identifier generated for the modifier
     */
    private void removeModifierById(AttributeInstance instance, UUID modifierId) {
        instance.getModifiers().stream()
                .filter(modifier -> modifier.getUniqueId().equals(modifierId))
                .findFirst()
                .ifPresent(instance::removeModifier);
    }

    /**
     * Determines whether the running Bukkit version supports transient attribute modifiers.
     */
    private void resolveTransientModifierMethod() {
        try {
            transientModifierMethod = org.bukkit.attribute.AttributeInstance.class
                    .getMethod("addTransientModifier", org.bukkit.attribute.AttributeModifier.class);
        } catch (ReflectiveOperationException ignored) {
            transientModifierMethod = null;
        } finally {
            transientMethodResolved = true;
        }
    }

    /**
     * Applies an attribute modifier, preferring Bukkit's transient API when available.
     *
     * @param instance attribute instance to mutate
     * @param modifier modifier representing the computed delta
     */
    private void addModifier(org.bukkit.attribute.AttributeInstance instance,
                             org.bukkit.attribute.AttributeModifier modifier) {
        if (!transientMethodResolved) {
            resolveTransientModifierMethod();
        }

        if (transientModifierMethod != null) {
            try {
                transientModifierMethod.invoke(instance, modifier);
                return;
            } catch (ReflectiveOperationException ignored) {
                transientModifierMethod = null;
            }
        }

        instance.addModifier(modifier);
    }

    /**
     * Starts (or restarts) the repeating tick that keeps player attributes in sync.
     */
    private void startTicker() {
        if (ticker != null) {
            ticker.cancel();
        }
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickPlayers, 1L, 1L);
    }

    /**
     * Repeatedly applies speed updates to tracked players.
     */
    private void tickPlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            applyFlySpeed(player);
            applySwimSpeed(player);
        }
    }

    /**
     * Applies computed flying speed to the player, clamping to Bukkit's expected range.
     *
     * @param player player whose flying speed should mirror the computed value
     */
    private void applyFlySpeed(Player player) {
        AttributeValueStages stages = attributeFacade.compute("flying_speed", player);
        float clamped = (float) Math.max(-1.0d, Math.min(1.0d, stages.currentFinal() / FLY_SPEED_SCALE));
        if (Math.abs(player.getFlySpeed() - clamped) > ATTRIBUTE_DELTA_EPSILON) {
            player.setFlySpeed(clamped);
        }
    }

    /**
     * Applies swim speed modifier while the player is swimming or submerged.
     *
     * @param player player whose swim speed attribute should be refreshed
     */
    private void applySwimSpeed(Player player) {
        org.bukkit.attribute.AttributeInstance instance = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (instance == null) {
            return;
        }

        instance.getModifiers().stream()
                .filter(modifier -> modifier.getUniqueId().equals(SWIM_SPEED_MODIFIER_ID))
                .findFirst()
                .ifPresent(instance::removeModifier);

        boolean swimming = player.isSwimming() || player.getEyeLocation().getBlock().isLiquid();
        if (!swimming) {
            return;
        }

        double swimSpeed = attributeFacade.compute("swim_speed", player).currentFinal();
        if (swimSpeed <= 0) {
            return;
        }

        double multiplier = swimSpeed - 1.0d;
        if (Math.abs(multiplier) < ATTRIBUTE_DELTA_EPSILON) {
            return;
        }

        AttributeModifier modifier = new AttributeModifier(
                SWIM_SPEED_MODIFIER_ID,
                ATTRIBUTE_MODIFIER_PREFIX + "swim_speed",
                multiplier,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1
        );
        addModifier(instance, modifier);
    }

}
