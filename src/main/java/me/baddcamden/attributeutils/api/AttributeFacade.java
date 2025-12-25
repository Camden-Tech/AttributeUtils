package me.baddcamden.attributeutils.api;

import me.baddcamden.attributeutils.compute.AttributeComputationEngine;
import me.baddcamden.attributeutils.model.AttributeDefinition;
import me.baddcamden.attributeutils.model.AttributeInstance;
import me.baddcamden.attributeutils.model.AttributeValueStages;
import me.baddcamden.attributeutils.model.ModifierEntry;
import me.baddcamden.attributeutils.model.ModifierOperation;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Public façade that keeps attribute definitions, player instances, and vanilla providers in a single entrypoint
 * for the {@link AttributeComputationEngine}. All attribute calculations executed by the plugin should flow through
 * this class so that global values, per-player overrides, and computed caps stay consistent.
 * <p>
 * The façade differentiates three stages for each attribute:
 * <ul>
 *     <li><strong>Default baseline</strong>: populated from the definition and stored in the global {@link AttributeInstance}.
 *     These values apply to every player unless overridden.</li>
 *     <li><strong>Current baseline</strong>: player-specific overrides created via {@link #getOrCreatePlayerInstance(UUID, String)}
 *     which start from {@link me.baddcamden.attributeutils.model.AttributeDefinition#defaultCurrentValue()} and replace the
 *     default baseline for that player.</li>
 *     <li><strong>Modifiers</strong>: global and player modifier buckets, each of which includes temporary and permanent
 *     entries. These modifiers are aggregated by the computation engine before any cap overrides.</li>
 * </ul>
 * Cap overrides are set per-player via {@link me.baddcamden.attributeutils.model.AttributeInstance#setCapOverrideKey(String)}
 * and are honored by the computation engine when calculating the final stage values returned by
 * {@link me.baddcamden.attributeutils.model.AttributeValueStages}.
 */
public class AttributeFacade {

    /**
     * Expected format for modifier source keys, ensuring each entry is namespaced as
     * {@code plugin.key} to avoid collisions between plugins.
     */
    private static final Pattern SOURCE_KEY_PATTERN = Pattern.compile("[a-z0-9_-]+\\.[a-z0-9_.-]+", Pattern.CASE_INSENSITIVE);

    /** Owning plugin used solely for logging warnings about invalid calls. */
    private final Plugin plugin;
    /** Engine that combines baselines, modifiers, caps, and vanilla suppliers into staged values. */
    private final AttributeComputationEngine computationEngine;
    /** Registered attribute definitions keyed by normalized id. */
    private final Map<String, AttributeDefinition> definitions = new ConcurrentHashMap<>();
    /** Optional vanilla value suppliers keyed by normalized attribute id. */
    private final Map<String, VanillaAttributeSupplier> vanillaSuppliers = new ConcurrentHashMap<>();
    /** Global attribute instances that store shared baselines and modifier buckets. */
    private final Map<String, AttributeInstance> globalInstances = new ConcurrentHashMap<>();
    /** Per-player attribute instances keyed by player id then normalized attribute id. */
    private final Map<UUID, Map<String, AttributeInstance>> playerInstances = new ConcurrentHashMap<>();
    /** Listener that translates modifier removals into live refresh operations. */
    private AttributeRefreshListener attributeRefreshListener;

    /**
     * Creates a façade bound to the plugin instance and computation engine. The plugin is only used for logging
     * warnings when callers attempt to access unknown attributes.
     *
     * @param plugin             owning plugin used for logging warnings.
     * @param computationEngine  engine that aggregates baselines, modifiers, and caps into final values.
     */
    public AttributeFacade(Plugin plugin, AttributeComputationEngine computationEngine) {
        this.plugin = plugin;
        this.computationEngine = computationEngine;
    }

    /**
     * Registers a new attribute definition and ensures there is a global {@link AttributeInstance} ready to hold
     * baselines and modifiers for that attribute.
     *
     * @param definition attribute definition to add; ids are normalized before being stored.
     */
    public void registerDefinition(AttributeDefinition definition) {
        String normalizedId = normalize(definition.id());
        definitions.put(normalizedId, definition);
        globalInstances.putIfAbsent(normalizedId, new AttributeInstance(definition));
    }

    /**
     * Registers a supplier for vanilla baseline values that will be included when computing an attribute. The key
     * is normalized to align with registered definitions and should typically match the attribute id.
     *
     * @param key      attribute identifier, typically the same id used for the definition.
     * @param supplier provider that returns the vanilla baseline value for a player.
     */
    public void registerVanillaBaseline(String key, VanillaAttributeSupplier supplier) {
        vanillaSuppliers.put(normalize(key), supplier);
    }

    /**
     * Returns an immutable view of every definition currently registered with the façade.
     * Caller should treat the returned collection as read-only and instead use {@link #registerDefinition(AttributeDefinition)}
     * to add additional entries.
     */
    public Collection<AttributeDefinition> getDefinitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    /**
     * Attempts to retrieve a definition by id. The lookup is case-insensitive and returns an empty optional when
     * the provided id is null or unrecognized.
     *
     * @param id raw attribute identifier.
     * @return optional containing the matching definition when present.
     */
    public Optional<AttributeDefinition> getDefinition(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(normalize(id)));
    }

    /**
     * Provides a normalized view of all registered attribute identifiers so that command
     * completers can suggest every available key, including custom attributes loaded at
     * runtime.
     *
     * @return collection of normalized attribute ids
     */
    public Collection<String> getDefinitionIds() {
        return Collections.unmodifiableCollection(definitions.keySet());
    }

    /**
     * Computes the staged attribute values for the provided player using the player's UUID extracted from
     * the {@link Player} object. When {@code player} is {@code null}, the computation is performed without
     * player-specific overrides.
     *
     * @param id     attribute id to compute.
     * @param player player whose modifiers and overrides should be applied; may be null for global computations.
     * @return staged values containing baseline, modifiers, and capped totals.
     */
    public AttributeValueStages compute(String id, Player player) {
        UUID playerId = player == null ? null : player.getUniqueId();
        return compute(id, playerId, player);
    }

    /**
     * Computes the staged attribute values for the provided attribute id and player. When the id is unknown a zeroed
     * {@link AttributeValueStages} is returned and a warning is logged.
     *
     * @param id       attribute id to compute.
     * @param ownerId  player UUID used to look up player-specific instances; may be null for global-only computation.
     * @param player   player reference used by vanilla suppliers; may be null when ownerId is null.
     * @return staged values containing baseline, modifiers, and capped totals.
     */
    public AttributeValueStages compute(String id, UUID ownerId, Player player) {
        AttributeDefinition definition = definitions.get(normalize(id));
        if (definition == null) {
            plugin.getLogger().warning("Attempted to compute unknown attribute: " + id);
            return new AttributeValueStages(0, 0, 0, 0, 0, 0);
        }

        String normalizedId = normalize(definition.id());
        AttributeInstance global = globalInstances.get(normalizedId);
        AttributeInstance playerInstance = ownerId == null ? null : getOrCreatePlayerInstance(ownerId, definition);
        VanillaAttributeSupplier vanillaSupplier = vanillaSuppliers.get(normalizedId);
        return computationEngine.compute(definition, global, playerInstance, vanillaSupplier, player);
    }

    /**
     * Adds or updates a global modifier for the specified attribute. The modifier key is validated before being
     * stored and will throw when the attribute is unknown.
     *
     * @param attributeId attribute id whose global modifiers should be updated.
     * @param entry       modifier entry to attach to the global instance.
     */
    public void setGlobalModifier(String attributeId, ModifierEntry entry) {
        String normalizedId = normalize(attributeId);
        AttributeDefinition definition = definitions.get(normalizedId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown attribute: " + attributeId);
        }

        AttributeInstance instance = getOrCreateGlobalInstance(attributeId);
        ModifierEntry validated = validate(entry);
        String normalizedKey = validated.key().toLowerCase(Locale.ROOT);

        if (validated.operation() == ModifierOperation.MULTIPLY) {
            refreshAll(normalizedId);
        }

        if (instance.getModifiers().containsKey(normalizedKey)) {
            instance.removeModifier(normalizedKey);
            refreshAll(normalizedId);
        }

        instance.addModifier(validated);
        refreshAll(normalizedId);
    }

    /**
     * Adds or updates a player-specific modifier for the specified attribute. The modifier key is validated before
     * being stored and will throw when the attribute is unknown.
     *
     * @param playerId    player owning the modifier.
     * @param attributeId attribute id to associate with the modifier.
     * @param entry       modifier entry describing the adjustment.
     */
    public void setPlayerModifier(UUID playerId, String attributeId, ModifierEntry entry) {
        String normalizedId = normalize(attributeId);
        AttributeDefinition definition = definitions.get(normalizedId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown attribute: " + attributeId);
        }

        AttributeInstance instance = getOrCreatePlayerInstance(playerId, definition);
        ModifierEntry validated = validate(entry);
        String normalizedKey = validated.key().toLowerCase(Locale.ROOT);

        if (validated.operation() == ModifierOperation.MULTIPLY) {
            refreshPlayer(playerId, normalizedId);
        }

        if (instance.getModifiers().containsKey(normalizedKey)) {
            instance.removeModifier(normalizedKey);
            refreshPlayer(playerId, normalizedId);
        }

        instance.addModifier(validated);
        refreshPlayer(playerId, normalizedId);
    }

    /**
     * Assigns a cap override for the provided player and attribute. The cap value is clamped to the
     * attribute's global minimum to avoid invalid overrides and stored using the instance's
     * {@code capOverrideKey} so subsequent computations honor the persisted cap. No refresh is triggered here because
     * callers are expected to recompute manually after persisting overrides.
     */
    public void setPlayerCapOverride(UUID playerId, String attributeId, double capValue) {
        AttributeDefinition definition = definitions.get(normalize(attributeId));
        if (definition == null) {
            throw new IllegalArgumentException("Unknown attribute: " + attributeId);
        }

        AttributeInstance instance = getOrCreatePlayerInstance(playerId, definition);
        String overrideKey = instance.getCapOverrideKey();
        if (overrideKey == null || overrideKey.isBlank()) {
            overrideKey = playerId == null ? null : playerId.toString();
            instance.setCapOverrideKey(overrideKey);
        }

        if (overrideKey == null || overrideKey.isBlank()) {
            return;
        }

        double boundedCap = Math.max(definition.capConfig().globalMin(), capValue);
        definition.capConfig().overrideMaxValues().put(overrideKey.toLowerCase(Locale.ROOT), boundedCap);
    }

    /**
     * Removes a global modifier when present. Unknown attributes are ignored to allow callers to safely clean up.
     *
     * @param attributeId attribute id whose modifier should be removed.
     * @param key         modifier key to remove.
     */
    public void removeGlobalModifier(String attributeId, String key) {
        String normalizedId = normalize(attributeId);
        AttributeInstance instance = globalInstances.get(normalizedId);
        if (instance != null) {
            String normalizedKey = key == null ? null : key.toLowerCase(Locale.ROOT);
            boolean removed = normalizedKey != null && instance.getModifiers().containsKey(normalizedKey);
            instance.removeModifier(normalizedKey);
            if (removed) {
                refreshAll(normalizedId);
            }
        }
    }

    /**
     * Removes a player modifier when present. If the player or attribute is not tracked, the call is a no-op.
     *
     * @param playerId    player owning the modifier.
     * @param attributeId attribute id whose modifier should be removed.
     * @param key         modifier key to remove.
     */
    public void removePlayerModifier(UUID playerId, String attributeId, String key) {
        Map<String, AttributeInstance> store = playerInstances.get(playerId);
        if (store == null) {
            return;
        }
        String normalizedId = normalize(attributeId);
        AttributeInstance instance = store.get(normalizedId);
        if (instance != null) {
            String normalizedKey = key == null ? null : key.toLowerCase(Locale.ROOT);
            boolean removed = normalizedKey != null && instance.getModifiers().containsKey(normalizedKey);
            instance.removeModifier(normalizedKey);
            if (removed) {
                refreshPlayer(playerId, normalizedId);
            }
        }
    }

    /**
     * Provides an immutable view of the global attribute instances keyed by normalized id.
     *
     * @return unmodifiable map of global attribute instances.
     */
    public Map<String, AttributeInstance> getGlobalInstances() {
        return Collections.unmodifiableMap(globalInstances);
    }

    /**
     * Provides an immutable view of the player-specific attribute instances for the given player id. When the player
     * has no tracked instances an empty map is returned.
     *
     * @param playerId player whose instances should be fetched.
     * @return unmodifiable map keyed by normalized attribute id.
     */
    public Map<String, AttributeInstance> getPlayerInstances(UUID playerId) {
        return Collections.unmodifiableMap(playerInstances.getOrDefault(playerId, Map.of()));
    }

    /**
     * Requests a refresh of every registered attribute for a specific player, ensuring both static and dynamic
     * attributes are re-applied after equipment or vanilla attribute changes.
     *
     * @param playerId player whose attributes should be refreshed.
     */
    public void refreshAllAttributesForPlayer(UUID playerId) {
        AttributeRefreshListener listener = this.attributeRefreshListener;
        if (listener == null || playerId == null) {
            return;
        }

        definitions.keySet().forEach(attributeId -> listener.refreshAttributeForPlayer(playerId, attributeId));
    }

    /**
     * Requests a refresh for every registered attribute across all tracked entities.
     */
    public void refreshAllAttributes() {
        AttributeRefreshListener listener = this.attributeRefreshListener;
        if (listener == null) {
            return;
        }

        definitions.keySet().forEach(listener::refreshAttributeForAll);
    }

    /**
     * Retrieves the global instance for an attribute or creates one using the registered definition. Unknown
     * attributes trigger an {@link IllegalArgumentException}.
     *
     * @param attributeId attribute id to fetch.
     * @return the existing or newly created instance.
     */
    public AttributeInstance getOrCreateGlobalInstance(String attributeId) {
        String normalizedId = normalize(attributeId);
        AttributeDefinition definition = definitions.get(normalizedId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown attribute: " + attributeId);
        }
        return globalInstances.computeIfAbsent(normalizedId, key -> new AttributeInstance(definition));
    }

    /**
     * Retrieves the player-specific instance for an attribute or creates one seeded with the default base and current
     * values from the definition. Unknown attributes trigger an {@link IllegalArgumentException}.
     *
     * @param playerId    player whose instance should be fetched.
     * @param attributeId attribute id to fetch.
     * @return player-specific attribute instance.
     */
    public AttributeInstance getOrCreatePlayerInstance(UUID playerId, String attributeId) {
        AttributeDefinition definition = definitions.get(normalize(attributeId));
        if (definition == null) {
            throw new IllegalArgumentException("Unknown attribute: " + attributeId);
        }
        return getOrCreatePlayerInstance(playerId, definition);
    }

    /**
     * Shared implementation for creating player instances that seeds defaults and assigns the cap override key.
     */
    private AttributeInstance getOrCreatePlayerInstance(UUID playerId, AttributeDefinition definition) {
        Map<String, AttributeInstance> map = playerInstances.computeIfAbsent(playerId, ignored -> new HashMap<>());
        String normalizedId = normalize(definition.id());
        return map.computeIfAbsent(normalizedId, ignored -> {
            //VAGUE/IMPROVEMENT NEEDED Clarify whether the cap override key should differ from the player id or support multi-identity scenarios.
            AttributeInstance instance = new AttributeInstance(definition, definition.defaultBaseValue(), definition.defaultCurrentValue(), playerId.toString());
            instance.setCapOverrideKey(playerId.toString());
            return instance;
        });
    }

    /**
     * Removes all temporary modifiers for a player across every tracked attribute instance. Used when a player
     * disconnects to avoid session-scoped effects persisting between logins.
     *
     * @param playerId player whose temporary modifiers should be cleared.
     */
    public void purgeTemporary(UUID playerId) {
        Map<String, AttributeInstance> map = playerInstances.get(playerId);
        if (map != null) {
            map.values().forEach(AttributeInstance::purgeTemporaryModifiers);
        }
    }

    /**
     * Clears temporary modifiers from every global attribute instance. Intended for cleanup when refreshing global state.
     */
    public void purgeGlobalTemporary() {
        globalInstances.values().forEach(AttributeInstance::purgeTemporaryModifiers);
    }

    /**
     * Validates modifier keys to ensure they follow the expected plugin-scoped format and prevents collisions between
     * unrelated plugins when multiple systems contribute modifiers.
     */
    private ModifierEntry validate(ModifierEntry entry) {
        if (!SOURCE_KEY_PATTERN.matcher(entry.key()).matches()) {
            throw new IllegalArgumentException("Modifier keys must follow [plugin].[key] format");
        }
        return entry;
    }

    /**
     * Normalizes attribute identifiers to a lower-case format for consistent lookups. Callers must ensure {@code id}
     * is non-null before invoking this helper.
     */
    private String normalize(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    /**
     * Registers a listener that will be notified when modifiers are removed so live entities can refresh their values.
     * Only one listener is supported because dispatch logic is centralized in {@link me.baddcamden.attributeutils.handler.AttributeRefreshDispatcher}.
     *
     * @param listener refresh listener to notify.
     */
    public void setAttributeRefreshListener(AttributeRefreshListener listener) {
        this.attributeRefreshListener = listener;
    }

    /**
     * Notifies the refresh listener to update a single player's computed attribute. No-ops when no listener is registered
     * to avoid forcing callers to null-check.
     */
    private void refreshPlayer(UUID playerId, String attributeId) {
        AttributeRefreshListener listener = this.attributeRefreshListener;
        if (listener == null) {
            return;
        }
        listener.refreshAttributeForPlayer(playerId, attributeId);
    }

    /**
     * Notifies the refresh listener to update all players for a given attribute. This is primarily used when a global
     * modifier is removed and all live entities need to be updated.
     */
    private void refreshAll(String attributeId) {
        AttributeRefreshListener listener = this.attributeRefreshListener;
        if (listener == null) {
            return;
        }
        listener.refreshAttributeForAll(attributeId);
    }

    /**
     * Listener invoked when modifier removals occur so implementations can re-apply live entity attributes.
     */
    public interface AttributeRefreshListener {
        /**
         * Refreshes the computed attribute values for a single player. Implementations typically recalculate and
         * reapply values to the live entity represented by {@code playerId}.
         */
        void refreshAttributeForPlayer(UUID playerId, String attributeId);

        /**
         * Refreshes the computed attribute values for all entities that use the supplied attribute id.
         */
        void refreshAttributeForAll(String attributeId);
    }
}
