package me.baddcamden.attributeutils.attributes;

import me.baddcamden.attributeutils.AttributeUtilitiesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class AttributeManager {
    private final AttributeUtilitiesPlugin plugin;
    private final Map<String, VanillaAttributeDefinition> vanillaAttributes = new HashMap<>();
    private final Logger logger;
    private File globalFile;
    private YamlConfiguration globalConfig;

    public AttributeManager(AttributeUtilitiesPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void load() {
        this.globalFile = new File(plugin.getDataFolder(), "data/global.yml");
        this.globalConfig = YamlConfiguration.loadConfiguration(globalFile);

        FileConfiguration config = plugin.getConfig();
        loadVanillaAttributes(config);
        ensureGlobalEntries();
        saveGlobalData();
    }

    public void reload() {
        vanillaAttributes.clear();
        load();
    }

    public void applyDefaultsToOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyDefaultsToPlayer(player);
        }
    }

    public void applyDefaultsToPlayer(Player player) {
        for (VanillaAttributeDefinition definition : vanillaAttributes.values()) {
            applyDefinition(player, definition);
        }
    }

    public boolean updateBase(String attributeKey, double value) {
        VanillaAttributeDefinition definition = vanillaAttributes.get(attributeKey);
        if (definition == null) {
            return false;
        }

        String dataPath = definition.dataPath();
        globalConfig.set(dataPath + ".default-base", value);
        globalConfig.set(dataPath + ".current-base", value);
        globalConfig.set(dataPath + ".default-final-baseline", value);
        saveGlobalData();
        applyDefaultsToOnlinePlayers();
        return true;
    }

    public Set<String> getRegisteredAttributeKeys() {
        return Collections.unmodifiableSet(vanillaAttributes.keySet());
    }

    public void saveGlobalData() {
        try {
            globalConfig.save(globalFile);
        } catch (IOException e) {
            logger.warning("Failed to save global attribute data: " + e.getMessage());
        }
    }

    private void applyDefinition(Player player, VanillaAttributeDefinition definition) {
        double baseValue = globalConfig.getDouble(definition.dataPath() + ".default-base", definition.defaultBase());

        for (Attribute attribute : definition.attributes()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            instance.setBaseValue(baseValue);
        }
    }

    private void loadVanillaAttributes(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("vanilla-attribute-defaults");
        if (section == null) {
            logger.warning("No vanilla-attribute-defaults section found; skipping load.");
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection attributeSection = section.getConfigurationSection(key);
            if (attributeSection == null) {
                continue;
            }

            List<String> bukkitNames = attributeSection.getStringList("bukkit-attributes");
            if (bukkitNames == null || bukkitNames.isEmpty()) {
                continue;
            }

            List<Attribute> attributes = new ArrayList<>();
            for (String name : bukkitNames) {
                try {
                    attributes.add(Attribute.valueOf(name));
                } catch (IllegalArgumentException ex) {
                    logger.warning("Unknown Bukkit attribute '" + name + "' for " + key);
                }
            }

            if (attributes.isEmpty()) {
                continue;
            }

            double defaultBase = attributeSection.getDouble("default-base", 0);
            String normalizedKey = normalizeKey(key);

            vanillaAttributes.put(normalizedKey, new VanillaAttributeDefinition(normalizedKey, attributes, defaultBase));
        }
    }

    private void ensureGlobalEntries() {
        for (VanillaAttributeDefinition definition : vanillaAttributes.values()) {
            String path = definition.dataPath();
            double defaultBase = definition.defaultBase();
            if (!globalConfig.contains(path + ".default-base")) {
                globalConfig.set(path + ".default-base", defaultBase);
            }
            if (!globalConfig.contains(path + ".current-base")) {
                globalConfig.set(path + ".current-base", defaultBase);
            }
            if (!globalConfig.contains(path + ".default-final-baseline")) {
                globalConfig.set(path + ".default-final-baseline", defaultBase);
            }
            if (!globalConfig.contains(path + ".modifiers")) {
                globalConfig.createSection(path + ".modifiers");
            }
        }
    }

    private String normalizeKey(String key) {
        return key.replace("-", "_").toLowerCase(Locale.ROOT);
    }
}
