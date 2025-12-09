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
        definitions.put(definition.id().toLowerCase(Locale.ROOT), definition);
        globalInstances.putIfAbsent(definition.id().toLowerCase(Locale.ROOT), new AttributeInstance(definition));
    }

    public void registerVanillaBaseline(String key, VanillaAttributeSupplier supplier) {
        vanillaSuppliers.put(key.toLowerCase(Locale.ROOT), supplier);
    }

    public Collection<AttributeDefinition> getDefinitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public Optional<AttributeDefinition> getDefinition(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id.toLowerCase(Locale.ROOT)));
    }

    public AttributeValueStages compute(String id, Player player) {
        AttributeDefinition definition = definitions.get(id.toLowerCase(Locale.ROOT));
        if (definition == null) {
            plugin.getLogger().warning("Attempted to compute unknown attribute: " + id);
            return new AttributeValueStages(0, 0, 0, 0, 0, 0);
        }

        AttributeInstance global = globalInstances.get(definition.id());
        AttributeInstance playerInstance = player == null ? null : getOrCreatePlayerInstance(player.getUniqueId(), definition);
        VanillaAttributeSupplier vanillaSupplier = vanillaSuppliers.get(definition.id());
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
        AttributeInstance instance = globalInstances.get(attributeId.toLowerCase(Locale.ROOT));
        if (instance != null) {
            instance.removeModifier(key);
        }
    }

    public void removePlayerModifier(UUID playerId, String attributeId, String key) {
        Map<String, AttributeInstance> store = playerInstances.get(playerId);
        if (store == null) {
            return;
        }
        AttributeInstance instance = store.get(attributeId.toLowerCase(Locale.ROOT));
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
        AttributeDefinition definition = definitions.get(attributeId.toLowerCase(Locale.ROOT));
        if (definition == null) {
            throw new IllegalArgumentException("Unknown attribute: " + attributeId);
        }
        return globalInstances.computeIfAbsent(definition.id(), key -> new AttributeInstance(definition));
    }

    public AttributeInstance getOrCreatePlayerInstance(UUID playerId, String attributeId) {
        AttributeDefinition definition = definitions.get(attributeId.toLowerCase(Locale.ROOT));
        if (definition == null) {
            throw new IllegalArgumentException("Unknown attribute: " + attributeId);
        }
        return getOrCreatePlayerInstance(playerId, definition);
    }

    private AttributeInstance getOrCreatePlayerInstance(UUID playerId, AttributeDefinition definition) {
        Map<String, AttributeInstance> map = playerInstances.computeIfAbsent(playerId, ignored -> new HashMap<>());
        return map.computeIfAbsent(definition.id(), ignored -> {
            AttributeInstance instance = new AttributeInstance(definition, definition.defaultCurrentValue(), playerId.toString());
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
}
