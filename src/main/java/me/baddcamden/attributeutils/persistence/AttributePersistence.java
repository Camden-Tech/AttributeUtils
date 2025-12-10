package me.baddcamden.attributeutils.persistence;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.model.AttributeDefinition;
import me.baddcamden.attributeutils.model.AttributeInstance;
import me.baddcamden.attributeutils.model.ModifierEntry;
import me.baddcamden.attributeutils.model.ModifierOperation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles serialization of {@link AttributeInstance} state to YAML. Baselines for both default and
 * current layers are stored alongside the last computed default final baseline so static
 * attributes can keep their current deltas in sync. Modifier entries are written with their
 * bucket flags so they can be restored into the correct stage buckets during reload. Caps are
 * respected when loading to prevent persisted values from exceeding configured limits or override
 * maxima.
 */
public class AttributePersistence {

    private final Path dataFolder;

    public AttributePersistence(Path dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void loadGlobals(AttributeFacade facade) {
        Path file = dataFolder.resolve("global.yml");
        if (Files.notExists(file)) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        loadInstances(facade, config.getConfigurationSection("attributes"), null);
    }

    public void saveGlobals(AttributeFacade facade) {
        FileConfiguration config = new YamlConfiguration();
        ConfigurationSection attributes = config.createSection("attributes");
        writeInstances(attributes, facade.getGlobalInstances());
        save(config, dataFolder.resolve("global.yml"));
    }

    public void loadPlayer(AttributeFacade facade, UUID playerId) {
        Path file = dataFolder.resolve("players").resolve(playerId.toString() + ".yml");
        if (Files.notExists(file)) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        loadInstances(facade, config.getConfigurationSection("attributes"), playerId);
    }

    public void savePlayer(AttributeFacade facade, UUID playerId) {
        FileConfiguration config = new YamlConfiguration();
        ConfigurationSection attributes = config.createSection("attributes");
        writeInstances(attributes, facade.getPlayerInstances(playerId));
        Path folder = dataFolder.resolve("players");
        try {
            Files.createDirectories(folder);
        } catch (IOException ignored) {
        }
        save(config, folder.resolve(playerId.toString() + ".yml"));
    }

    /**
     * Loads instances into the facade, clamping baselines to cap values for the associated override
     * key (player ID) and reconstructing modifier buckets with their configured stage flags.
     */
    private void loadInstances(AttributeFacade facade, ConfigurationSection section, UUID playerId) {
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            facade.getDefinition(key).ifPresentOrElse(definition -> {
                double defaultBase = section.getDouble(key + ".default-base",
                        section.getDouble(key + ".base", definition.defaultBaseValue()));
                double currentBase = section.getDouble(key + ".current-base",
                        section.getDouble(key + ".base", definition.defaultCurrentValue()));
                double defaultFinalBaseline = section.getDouble(key + ".default-final-baseline", definition.defaultCurrentValue());
                AttributeInstance instance = playerId == null
                        ? facade.getOrCreateGlobalInstance(definition.id())
                        : facade.getOrCreatePlayerInstance(playerId, definition.id());
                String capKey = playerId == null ? null : playerId.toString();
                instance.setDefaultBaseValue(definition.capConfig().clamp(defaultBase, capKey));
                instance.setCurrentBaseValue(definition.capConfig().clamp(currentBase, capKey));
                instance.setDefaultFinalBaseline(definition.capConfig().clamp(defaultFinalBaseline, capKey));
                ConfigurationSection modifiers = section.getConfigurationSection(key + ".modifiers");
                if (modifiers != null) {
                    for (String modKey : modifiers.getKeys(false)) {
                        ModifierOperation operation = ModifierOperation.valueOf(modifiers.getString(modKey + ".operation", "ADD").toUpperCase(Locale.ROOT));
                        double amount = modifiers.getDouble(modKey + ".amount", 0);
                        boolean temporary = modifiers.getBoolean(modKey + ".temporary", false);
                        boolean appliesToDefault = modifiers.getBoolean(modKey + ".default", false);
                        boolean appliesToCurrent = modifiers.getBoolean(modKey + ".current", !appliesToDefault);
                        boolean useMultiplierKeys = modifiers.getBoolean(modKey + ".use-multiplier-keys", false);
                        List<String> keyList = modifiers.getStringList(modKey + ".multiplier-keys");
                        Set<String> normalizedKeys = new HashSet<>();
                        keyList.stream().filter(entry -> entry != null && !entry.isBlank()).forEach(entry -> normalizedKeys.add(entry.toLowerCase(Locale.ROOT)));
                        instance.addModifier(new ModifierEntry(modKey, operation, amount, temporary, appliesToDefault, appliesToCurrent, useMultiplierKeys, normalizedKeys));
                    }
                }
            }, () -> facade.compute(key, null));
        }
    }

    /**
     * Writes the full state of each instance: default/current baselines, the last default final
     * baseline (used for resynchronizing static attributes), and all modifiers with their bucket
     * metadata so they can be restored to the same computation stage.
     */
    private void writeInstances(ConfigurationSection section, Map<String, AttributeInstance> instances) {
        for (Map.Entry<String, AttributeInstance> entry : instances.entrySet()) {
            String attributeId = entry.getKey();
            AttributeInstance instance = entry.getValue();
            section.set(attributeId + ".default-base", instance.getDefaultBaseValue());
            section.set(attributeId + ".current-base", instance.getCurrentBaseValue());
            section.set(attributeId + ".base", instance.getDefaultBaseValue());
            section.set(attributeId + ".default-final-baseline", instance.getDefaultFinalBaseline());
            ConfigurationSection modifiers = section.createSection(attributeId + ".modifiers");
            instance.getModifiers().forEach((key, modifier) -> {
                modifiers.set(key + ".operation", modifier.operation().name());
                modifiers.set(key + ".amount", modifier.amount());
                modifiers.set(key + ".temporary", modifier.isTemporary());
                modifiers.set(key + ".default", modifier.isDefaultModifier());
                modifiers.set(key + ".current", modifier.appliesToCurrent());
                modifiers.set(key + ".use-multiplier-keys", modifier.useMultiplierKeys());
                modifiers.set(key + ".multiplier-keys", List.copyOf(modifier.multiplierKeys()));
            });
        }
    }

    private void save(FileConfiguration config, Path target) {
        try {
            Files.createDirectories(target.getParent());
            config.save(target.toFile());
        } catch (IOException ignored) {
        }
    }
}
