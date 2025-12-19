package me.baddcamden.attributeutils.api;

import me.baddcamden.attributeutils.compute.AttributeComputationEngine;
import me.baddcamden.attributeutils.model.AttributeDefinition;
import me.baddcamden.attributeutils.model.AttributeInstance;
import me.baddcamden.attributeutils.model.AttributeValueStages;
import me.baddcamden.attributeutils.model.ModifierEntry;
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
 * Public façade for the richer computation pipeline backed by {@link me.baddcamden.attributeutils.compute.AttributeComputationEngine}.
 * <p>
 * The façade differentiates three stages for each attribute:
 * <ul>
 *     <li><strong>Default baseline</strong>: populated from the definition and stored in the global {@link AttributeInstance}.</li>
 *     <li><strong>Current baseline</strong>: player-specific overrides created via {@link #getOrCreatePlayerInstance(UUID, String)}
 *     which start from {@link me.baddcamden.attributeutils.model.AttributeDefinition#defaultCurrentValue()}.</li>
 *     <li><strong>Modifiers</strong>: global and player modifier buckets, each of which includes temporary and permanent
 *     entries. These modifiers are aggregated by the computation engine before any cap overrides.</li>
 * </ul>
 * Cap overrides are set per-player via {@link me.baddcamden.attributeutils.model.AttributeInstance#setCapOverrideKey(String)}
 * and are honored by the computation engine when calculating the final stage values returned by
 * {@link me.baddcamden.attributeutils.model.AttributeValueStages}.
 */
public class AttributeFacade {

    private static final Pattern SOURCE_KEY_PATTERN = Pattern.compile("[a-z0-9_-]+\\.[a-z0-9_.-]+", Pattern.CASE_INSENSITIVE);

    private final Plugin plugin;
    private final AttributeComputationEngine computationEngine;
    private final Map<String, AttributeDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, VanillaAttributeSupplier> vanillaSuppliers = new ConcurrentHashMap<>();
    private final Map<String, AttributeInstance> globalInstances = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, AttributeInstance>> playerInstances = new ConcurrentHashMap<>();

    public AttributeFacade(Plugin plugin, AttributeComputationEngine computationEngine) {
        this.plugin = plugin;
        this.computationEngine = computationEngine;
    }

    public void registerDefinition(AttributeDefinition definition) {
        String normalizedId = normalize(definition.id());
        definitions.put(normalizedId, definition);
        globalInstances.putIfAbsent(normalizedId, new AttributeInstance(definition));
    }

    public void registerVanillaBaseline(String key, VanillaAttributeSupplier supplier) {
        vanillaSuppliers.put(normalize(key), supplier);
    }

    public Collection<AttributeDefinition> getDefinitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }

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

    public AttributeValueStages compute(String id, Player player) {
        UUID playerId = player == null ? null : player.getUniqueId();
        return compute(id, playerId, player);
    }

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

    public void setGlobalModifier(String attributeId, ModifierEntry entry) {
        AttributeInstance instance = getOrCreateGlobalInstance(attributeId);
        instance.addModifier(validate(entry));
    }

    public void setPlayerModifier(UUID playerId, String attributeId, ModifierEntry entry) {
        AttributeInstance instance = getOrCreatePlayerInstance(playerId, attributeId);
        instance.addModifier(validate(entry));
    }

    public void removeGlobalModifier(String attributeId, String key) {
        AttributeInstance instance = globalInstances.get(normalize(attributeId));
        if (instance != null) {
            instance.removeModifier(key);
        }
    }

    public void removePlayerModifier(UUID playerId, String attributeId, String key) {
        Map<String, AttributeInstance> store = playerInstances.get(playerId);
        if (store == null) {
            return;
        }
        AttributeInstance instance = store.get(normalize(attributeId));
        if (instance != null) {
            instance.removeModifier(key);
        }
    }

    public Map<String, AttributeInstance> getGlobalInstances() {
        return Collections.unmodifiableMap(globalInstances);
    }

    public Map<String, AttributeInstance> getPlayerInstances(UUID playerId) {
        return Collections.unmodifiableMap(playerInstances.getOrDefault(playerId, Map.of()));
    }

    public AttributeInstance getOrCreateGlobalInstance(String attributeId) {
        String normalizedId = normalize(attributeId);
        AttributeDefinition definition = definitions.get(normalizedId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown attribute: " + attributeId);
        }
        return globalInstances.computeIfAbsent(normalizedId, key -> new AttributeInstance(definition));
    }

    public AttributeInstance getOrCreatePlayerInstance(UUID playerId, String attributeId) {
        AttributeDefinition definition = definitions.get(normalize(attributeId));
        if (definition == null) {
            throw new IllegalArgumentException("Unknown attribute: " + attributeId);
        }
        return getOrCreatePlayerInstance(playerId, definition);
    }

    private AttributeInstance getOrCreatePlayerInstance(UUID playerId, AttributeDefinition definition) {
        Map<String, AttributeInstance> map = playerInstances.computeIfAbsent(playerId, ignored -> new HashMap<>());
        String normalizedId = normalize(definition.id());
        return map.computeIfAbsent(normalizedId, ignored -> {
            AttributeInstance instance = new AttributeInstance(definition, definition.defaultBaseValue(), definition.defaultCurrentValue(), playerId.toString());
            instance.setCapOverrideKey(playerId.toString());
            return instance;
        });
    }

    public void purgeTemporary(UUID playerId) {
        Map<String, AttributeInstance> map = playerInstances.get(playerId);
        if (map != null) {
            map.values().forEach(AttributeInstance::purgeTemporaryModifiers);
        }
    }

    public void purgeGlobalTemporary() {
        globalInstances.values().forEach(AttributeInstance::purgeTemporaryModifiers);
    }

    private ModifierEntry validate(ModifierEntry entry) {
        if (!SOURCE_KEY_PATTERN.matcher(entry.key()).matches()) {
            throw new IllegalArgumentException("Modifier keys must follow [plugin].[key] format");
        }
        return entry;
    }

    private String normalize(String id) {
        return id.toLowerCase(Locale.ROOT);
    }
}
