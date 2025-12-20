package me.baddcamden.attributeutils.handler.entity;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.command.CommandParsingUtils;
import me.baddcamden.attributeutils.model.AttributeDefinition;
import me.baddcamden.attributeutils.model.AttributeValueStages;
import me.baddcamden.attributeutils.persistence.ResourceMeterState;
import me.baddcamden.attributeutils.persistence.ResourceMeterStore;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.Attributable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Integrates entity interactions with the attribute computation pipeline. Responsibilities include applying computed
 * caps (current stage) to live player entities and translating command-provided baseline/cap values into persistent
 * data for spawned entities so that subsequent computations start from the correct stage values.
 */
public class EntityAttributeHandler implements ResourceMeterStore {

    /**
     * The number of hunger bars shown in the vanilla UI.
     */
    private static final int HUNGER_BARS = 20;
    /**
     * Number of air ticks that make up a single vanilla air bubble.
     */
    private static final int AIR_PER_BUBBLE = 30;
    /**
     * Vanilla Minecraft renders ten air bubbles by default.
     */
    private static final double VANILLA_MAX_BUBBLES = 10.0d;
    /**
     * Divisor to translate attribute speed stages into Bukkit fly speed (clamped to [-1, 1]).
     */
    private static final double FLY_SPEED_SCALE = 4.0d;
    /**
     * Default maximum number of bubbles shown in the vanilla UI.
     */
    private static final double DEFAULT_MAX_BUBBLES = VANILLA_MAX_BUBBLES;
    /**
     * Minimum vanilla hunger level required before regeneration begins.
     */
    private static final int MINIMUM_FOOD_LEVEL_FOR_REGENERATION = 18;
    /**
     * Number of ticks between vanilla regeneration pulses.
     */
    private static final int VANILLA_REGEN_INTERVAL_TICKS = 80;
    /**
     * Saturation multiplier used by vanilla to increase regen while saturated.
     */
    private static final double VANILLA_SATURATED_REGEN_MULTIPLIER = 8.0d;
    /**
     * Exhaustion increase per heart regenerated in vanilla.
     */
    private static final double VANILLA_EXHAUSTION_PER_HEALTH = 6.0d;
    /**
     * Smallest heal amount considered meaningful; avoids tiny floating-point churn.
     */
    private static final double MINIMUM_HEALING_AMOUNT = 0.00001d;
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
     * Tracked hunger meters keyed by player id.
     */
    private final Map<UUID, ResourceMeter> hungerMeters = new HashMap<>();
    /**
     * Tracked oxygen meters keyed by player id.
     */
    private final Map<UUID, ResourceMeter> oxygenMeters = new HashMap<>();
    /**
     * Accumulated fractional regeneration amounts keyed by entity id.
     */
    private final Map<UUID, Double> regenerationRemainders = new HashMap<>();
    /**
     * Living entities that should receive regeneration ticks.
     */
    private final Set<UUID> regenerationTargets = new HashSet<>();
    private Method transientModifierMethod;
    private boolean transientMethodResolved;
    private BukkitTask ticker;

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
     * Syncs player-facing values (hunger/oxygen), applies speed modifiers, and registers regeneration tracking.
     */
    public void applyPlayerCaps(Player player) {
        if (player == null) {
            return;
        }
        syncHunger(player);
        syncOxygen(player);
        applyFlySpeed(player);
        applySwimSpeed(player);
        registerRegenerationTarget(player);
    }

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

    public void applyPersistentAttributes(Entity entity) {
        if (entity == null) {
            return;
        }

        if (entity instanceof LivingEntity living) {
            registerRegenerationTarget(living);
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
                applyVanillaAttribute(entity, attr.id(), clampedValue);
                return;
            }

            applyVanillaAttribute(entity, attr.id(), clampedValue);
            if (entity instanceof Player player) {
                attributeFacade.getOrCreatePlayerInstance(player.getUniqueId(), attr.id())
                        .setCurrentBaseValue(clampedValue);
            }
        });
    }

    /**
     * Applies a raw value to a vanilla attribute, clamping health safely when necessary.
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

    private NamespacedKey valueKey(String attributeId) {
        return new NamespacedKey(plugin, ATTRIBUTE_KEY_PREFIX + sanitize(attributeId));
    }

    private NamespacedKey capKey(String attributeId) {
        return new NamespacedKey(plugin, ATTRIBUTE_KEY_PREFIX + sanitize(attributeId) + ATTRIBUTE_CAP_SUFFIX);
    }

    private String sanitize(String attributeId) {
        return attributeId.toLowerCase(Locale.ROOT).replace('.', '_');
    }

    private boolean isBlank(String attributeId) {
        return attributeId == null || attributeId.isBlank();
    }

    private Attribute resolveVanillaTarget(String attributeId) {
        if (isBlank(attributeId)) {
            return null;
        }
        Attribute target = vanillaAttributeTargets.get(attributeId.toLowerCase(Locale.ROOT));
        return target != null ? target : resolveAttribute(attributeId);
    }

    private boolean isPluginAttributeKey(NamespacedKey key) {
        return key != null && key.getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT))
                && key.getKey().startsWith(ATTRIBUTE_KEY_PREFIX);
    }

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

    private Optional<AttributeDefinition> findAttributeDefinition(String attributeId) {
        if (attributeId == null || attributeId.isBlank()) {
            return Optional.empty();
        }
        return attributeFacade.getDefinition(attributeId)
                .or(() -> attributeFacade.getDefinition(attributeId.replace('_', '.')));
    }

    private record AttributeKeyDetails(String attributeId, boolean isCap) {
    }

    /**
     * Encapsulates the newly spawned entity plus a summary of applied attributes for display.
     */
    public record SpawnedEntityResult(Entity entity, String summary) {
    }

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

        double computed = attributeFacade.compute(attributeId, entity.getUniqueId(), null).currentFinal();
        applyComputedModifier(entity, target, attributeId, computed);
    }

    public void applyVanillaAttribute(Player player, String attributeId) {
        if (player == null || isBlank(attributeId)) {
            return;
        }
        Attribute target = resolveVanillaTarget(attributeId);
        if (target == null && isHunger(attributeId)) {
            syncHunger(player);
            return;
        }
        if (target == null && isOxygen(attributeId)) {
            syncOxygen(player);
            return;
        }
        if (target == null) {
            return;
        }

        double computed = attributeFacade.compute(attributeId, player).currentFinal();
        applyComputedModifier(player, target, attributeId, computed);
    }

    private Attribute resolveAttribute(String attributeId) {
        String normalized = attributeId.toUpperCase(Locale.ROOT);
        try {
            return Attribute.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void applyComputedModifier(Attributable attributable, Attribute target, String attributeId, double computed) {
        if (attributable == null || target == null) {
            return;
        }
        AttributeInstance instance = attributable.getAttribute(target);
        if (instance == null) {
            return;
        }

        UUID modifierId = attributeModifierId(attributeId);
        removeModifierById(instance, modifierId);

        double baseline = instance.getValue();
        double delta = computed - baseline;
        if (Math.abs(delta) < ATTRIBUTE_DELTA_EPSILON) {
            return;
        }

        AttributeModifier modifier = new AttributeModifier(
                modifierId,
                ATTRIBUTE_MODIFIER_PREFIX + attributeId,
                delta,
                AttributeModifier.Operation.ADD_NUMBER
        );
        addModifier(instance, modifier);
    }

    private UUID attributeModifierId(String attributeId) {
        return java.util.UUID.nameUUIDFromBytes((ATTRIBUTE_MODIFIER_PREFIX + attributeId).getBytes(StandardCharsets.UTF_8));
    }

    private void removeModifierById(AttributeInstance instance, UUID modifierId) {
        instance.getModifiers().stream()
                .filter(modifier -> modifier.getUniqueId().equals(modifierId))
                .findFirst()
                .ifPresent(instance::removeModifier);
    }

    private boolean isHunger(String attributeId) {
        return "max_hunger".equalsIgnoreCase(attributeId);
    }

    private boolean isOxygen(String attributeId) {
        return "max_oxygen".equalsIgnoreCase(attributeId) || "oxygen_bonus".equalsIgnoreCase(attributeId);
    }

    /**
     * Adjusts hunger meter based on the requested change, returning the display hunger level that should be applied.
     */
    public int handleFoodLevelChange(Player player, int requestedFoodLevel) {
        if (player == null) {
            return requestedFoodLevel;
        }
        ResourceMeter meter = resolveHungerMeter(player, computeHungerCap(player));
        double delta = requestedFoodLevel - player.getFoodLevel();
        meter.applyDelta(delta);
        return meter.asDisplay(HUNGER_BARS);
    }

    /**
     * Adjusts oxygen meter when the vanilla engine attempts to change remaining air.
     */
    public int handleAirChange(Player player, int requestedAirAmount) {
        if (player == null) {
            return requestedAirAmount;
        }
        double oxygenCap = computeOxygenCap(player);
        ResourceMeter meter = resolveOxygenMeter(player, oxygenCap);
        boolean submerged = player.getEyeLocation().getBlock().isLiquid();
        if (!submerged) {
            meter.restoreToMax();
        } else {
            double tickDelta = requestedAirAmount - player.getRemainingAir();
            double bubbleDelta = bubbleDeltaFromVanillaTicks(meter, tickDelta);
            meter.applyDelta(bubbleDelta);
        }
        int adjustedAirTicks = toVanillaAirTicks(meter);
        player.setRemainingAir(adjustedAirTicks);
        return adjustedAirTicks;
    }

    public void clearPlayerData(UUID playerId) {
        hungerMeters.remove(playerId);
        oxygenMeters.remove(playerId);
        regenerationRemainders.remove(playerId);
        regenerationTargets.remove(playerId);
    }

    /**
     * Restores cached meters from persistence for a player.
     */
    @Override
    public void hydrateMeters(UUID playerId, ResourceMeterState hunger, ResourceMeterState oxygen) {
        if (playerId == null) {
            return;
        }
        if (hunger != null) {
            hungerMeters.put(playerId, ResourceMeter.fromState(hunger));
        }
        if (oxygen != null) {
            oxygenMeters.put(playerId, ResourceMeter.fromState(oxygen));
        }
    }

    /**
     * Provides the current hunger meter for persistence.
     */
    @Override
    public ResourceMeterState getHungerMeter(UUID playerId) {
        ResourceMeter meter = hungerMeters.get(playerId);
        return meter == null ? null : new ResourceMeterState(meter.getCurrent(), meter.getMax());
    }

    /**
     * Provides the current oxygen meter for persistence.
     */
    @Override
    public ResourceMeterState getOxygenMeter(UUID playerId) {
        ResourceMeter meter = oxygenMeters.get(playerId);
        return meter == null ? null : new ResourceMeterState(meter.getCurrent(), meter.getMax());
    }

    /**
     * Determines whether vanilla regeneration should be replaced by the custom pipeline.
     */
    public boolean shouldCancelVanillaRegeneration(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return false;
        }

        EntityRegainHealthEvent.RegainReason reason = event.getRegainReason();
        if (reason != EntityRegainHealthEvent.RegainReason.SATIATED
                && reason != EntityRegainHealthEvent.RegainReason.REGEN) {
            return false;
        }

        if (attributeFacade.getDefinition("regeneration_rate").isEmpty()) {
            return false;
        }

        double regenRate = computeRegenerationRate(living);
        return regenRate >= 0 && (living instanceof Player || regenerationTargets.contains(living.getUniqueId()));
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

    private void syncHunger(Player player) {
        ResourceMeter meter = resolveHungerMeter(player, computeHungerCap(player));
        player.setFoodLevel(meter.asDisplay(HUNGER_BARS));
    }

    private void syncOxygen(Player player) {
        double oxygenCap = computeOxygenCap(player);
        ResourceMeter meter = resolveOxygenMeter(player, oxygenCap);
        player.setRemainingAir(toVanillaAirTicks(meter));
    }

    /**
     * Computes the maximum hunger cap, clamped to non-negative values.
     */
    private double computeHungerCap(Player player) {
        return Math.max(attributeFacade.compute("max_hunger", player).currentFinal(), 0);
    }

    /**
     * Computes the total oxygen cap (base + bonus) expressed as bubbles.
     */
    private double computeOxygenCap(Player player) {
        AttributeValueStages oxygen = attributeFacade.compute("max_oxygen", player);
        AttributeValueStages oxygenBonus = attributeFacade.compute("oxygen_bonus", player);
        double airCap = Math.max(oxygen.currentFinal() + oxygenBonus.currentFinal(), 0);
        double bubbleCap = airCap > 0 ? airCap / AIR_PER_BUBBLE : DEFAULT_MAX_BUBBLES;
        return bubbleCap;
    }

    private int toVanillaAirTicks(ResourceMeter meter) {
        if (meter == null || meter.getMax() <= 0) {
            return 0;
        }
        double fraction = meter.getCurrent() / meter.getMax();
        double vanillaAir = fraction * VANILLA_MAX_BUBBLES * AIR_PER_BUBBLE;
        return (int) Math.round(vanillaAir);
    }

    private double bubbleDeltaFromVanillaTicks(ResourceMeter meter, double tickDelta) {
        if (meter == null || meter.getMax() <= 0) {
            return 0;
        }
        double ticksPerBubble = (VANILLA_MAX_BUBBLES * AIR_PER_BUBBLE) / meter.getMax();
        if (ticksPerBubble == 0) {
            return 0;
        }
        return tickDelta / ticksPerBubble;
    }

    /**
     * Resolves or creates a hunger meter for the player, adjusting to the provided cap.
     */
    private ResourceMeter resolveHungerMeter(Player player, double cap) {
        return resolveMeter(player.getUniqueId(), hungerMeters, cap,
                () -> ResourceMeter.fromDisplay(player.getFoodLevel(), HUNGER_BARS, cap));
    }

    /**
     * Resolves or creates an oxygen meter for the player, adjusting to the provided cap.
     */
    private ResourceMeter resolveOxygenMeter(Player player, double cap) {
        return resolveMeter(player.getUniqueId(), oxygenMeters, cap,
                () -> new ResourceMeter(cap, cap));
    }

    /**
     * Common helper to fetch or create a resource meter keyed by player id.
     */
    private ResourceMeter resolveMeter(UUID playerId,
                                       Map<UUID, ResourceMeter> meters,
                                       double cap,
                                       Supplier<ResourceMeter> creator) {
        ResourceMeter meter = meters.computeIfAbsent(playerId, ignored -> creator.get());
        meter.updateMax(cap);
        return meter;
    }

    private void registerRegenerationTarget(LivingEntity entity) {
        regenerationTargets.add(entity.getUniqueId());
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
     * Repeatedly applies speed, oxygen/hunger updates, and regeneration to tracked entities.
     */
    private void tickPlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            applyFlySpeed(player);
            applySwimSpeed(player);
            tickRegeneration(player, true);
        }

        for (UUID targetId : List.copyOf(regenerationTargets)) {
            Entity entity = plugin.getServer().getEntity(targetId);
            if (!(entity instanceof LivingEntity living) || !entity.isValid()) {
                regenerationTargets.remove(targetId);
                continue;
            }
            if (entity instanceof Player) {
                continue;
            }
            tickRegeneration(living, false);
        }
    }

    /**
     * Applies computed flying speed to the player, clamping to Bukkit's expected range.
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

    /**
     * Applies custom regeneration logic to a living entity. For players, hunger gating is optionally respected.
     */
    private void tickRegeneration(LivingEntity entity, boolean respectHunger) {
        double regenRate = computeRegenerationRate(entity);
        double maxHealth = resolveMaxHealth(entity);
        if (!canRegenerate(entity, regenRate, maxHealth)) {
            regenerationRemainders.remove(entity.getUniqueId());
            return;
        }

        if (respectHunger && entity instanceof Player player && player.getFoodLevel() < MINIMUM_FOOD_LEVEL_FOR_REGENERATION) {
            return;
        }

        double accumulated = accumulateHealing(entity, regenRate);
        double missing = maxHealth - entity.getHealth();
        double requestedHeal = Math.min(accumulated, missing);
        if (!isMeaningfulHealing(requestedHeal)) {
            regenerationRemainders.put(entity.getUniqueId(), accumulated);
            return;
        }

        EntityRegainHealthEvent event = new EntityRegainHealthEvent(entity, requestedHeal, EntityRegainHealthEvent.RegainReason.CUSTOM);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            regenerationRemainders.put(entity.getUniqueId(), accumulated);
            return;
        }

        double actualHeal = Math.min(Math.min(event.getAmount(), accumulated), missing);
        if (!isMeaningfulHealing(actualHeal)) {
            regenerationRemainders.put(entity.getUniqueId(), accumulated);
            return;
        }

        applyRegeneration(entity, maxHealth, accumulated, actualHeal);
    }

    /**
     * Represents a resource with a current and maximum value (e.g., hunger or oxygen) that can be translated to and
     * from display scales used by vanilla UI elements.
     */
    static final class ResourceMeter {
        private double current;
        private double max;

        private ResourceMeter(double current, double max) {
            this.max = Math.max(max, 0);
            this.current = clamp(current, this.max);
        }

        /**
         * Creates a meter from a displayed value (e.g., food bars) scaled to a custom cap.
         */
        static ResourceMeter fromDisplay(int displayValue, int displayMax, double max) {
            double clampedDisplay = clamp(displayValue, displayMax);
            double fraction = displayMax > 0 ? clampedDisplay / (double) displayMax : 0;
            return new ResourceMeter(fraction * Math.max(max, 0), Math.max(max, 0));
        }

        /**
         * Restores a meter from persisted state.
         */
        static ResourceMeter fromState(ResourceMeterState state) {
            double max = state == null ? 0 : state.max();
            double current = state == null ? 0 : state.current();
            return new ResourceMeter(current, max);
        }

        void updateMax(double newMax) {
            double cappedMax = Math.max(newMax, 0);
            double fraction = max > 0 ? current / max : 0;
            max = cappedMax;
            current = clamp(fraction * max, max);
        }

        /**
         * Applies a raw delta against the current value within the meter's cap.
         */
        void applyDelta(double delta) {
            current = clamp(current + delta, max);
        }

        void restoreToMax() {
            current = max;
        }

        /**
         * Applies a delta expressed in display units (bars/bubbles) rather than raw resource units.
         */
        void applyDisplayDelta(double displayDelta, double displayScale) {
            applyDelta(displayDelta * displayScale);
        }

        /**
         * Translates the current resource into a UI-friendly count (e.g., food bars).
         */
        int asDisplay(int displayMax) {
            if (max <= 0) {
                return 0;
            }
            double fraction = current / max;
            return (int) Math.ceil(Math.min(fraction, 1.0) * displayMax);
        }

        double getCurrent() {
            return current;
        }

        double getMax() {
            return max;
        }

        private static double clamp(double value, double max) {
            return Math.max(0, Math.min(value, max));
        }
    }

    private double computeRegenerationRate(LivingEntity entity) {
        return entity instanceof Player player
                ? attributeFacade.compute("regeneration_rate", player).currentFinal()
                : attributeFacade.compute("regeneration_rate", entity.getUniqueId(), null).currentFinal();
    }

    /**
     * Resolves the max health attribute using Bukkit's API, favoring attribute instances when present.
     */
    private double resolveMaxHealth(LivingEntity entity) {
        org.bukkit.attribute.AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
        return maxHealthAttr != null ? maxHealthAttr.getValue() : entity.getMaxHealth();
    }

    /**
     * Basic validation to ensure regeneration is meaningful and entity is still alive.
     */
    private boolean canRegenerate(LivingEntity entity, double regenRate, double maxHealth) {
        return regenRate > 0 && maxHealth > MINIMUM_MAX_HEALTH && !entity.isDead() && entity.getHealth() < maxHealth;
    }

    /**
     * Accumulates fractional healing ticks, honoring vanilla saturation multiplier when applicable.
     */
    private double accumulateHealing(LivingEntity entity, double regenRate) {
        double saturationMultiplier = computeSaturationMultiplier(entity);
        double perTick = (regenRate * saturationMultiplier) / (double) VANILLA_REGEN_INTERVAL_TICKS;
        return regenerationRemainders.getOrDefault(entity.getUniqueId(), 0.0d) + perTick;
    }

    private boolean isMeaningfulHealing(double amount) {
        return amount > MINIMUM_HEALING_AMOUNT;
    }

    /**
     * Applies calculated regeneration, adds exhaustion for players, spawns visuals, and stores remainders.
     */
    private void applyRegeneration(LivingEntity entity, double maxHealth, double accumulated, double actualHeal) {
        entity.setHealth(Math.min(maxHealth, entity.getHealth() + actualHeal));
        if (entity instanceof Player player) {
            float exhaustionIncrease = (float) (VANILLA_EXHAUSTION_PER_HEALTH * actualHeal);
            player.setExhaustion(player.getExhaustion() + exhaustionIncrease);
        }
        showHeartAnimation(entity);
        regenerationRemainders.put(entity.getUniqueId(), accumulated - actualHeal);
    }

    private double computeSaturationMultiplier(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return 1.0d;
        }
        return player.getSaturation() > 0 ? VANILLA_SATURATED_REGEN_MULTIPLIER : 1.0d;
    }

    /**
     * Adds a heart particle to indicate regeneration.
     */
    private void showHeartAnimation(LivingEntity entity) {
        Location location = entity.getLocation().add(0, entity.getHeight() * 0.6d, 0);
        entity.getWorld().spawnParticle(
                Particle.HEART,
                location,
                1,
                0.25d,
                0.25d,
                0.25d,
                0.0d
        );
    }
}
