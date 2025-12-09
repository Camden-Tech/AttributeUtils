package me.BaddCamden.AttributeUtils.config;

import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AttributeConfig {
    private final Map<Attribute, Double> defaults = new EnumMap<>(Attribute.class);
    private final Map<Attribute, Double> caps = new EnumMap<>(Attribute.class);
    private final Map<String, CustomAttributeDefinition> customAttributes = new HashMap<>();
    private final String playerStorageFolder;

    public AttributeConfig(FileConfiguration config) {
        this.playerStorageFolder = config.getString("player-storage-folder", "players");
        ConfigurationSection globalSection = config.getConfigurationSection("global-attributes");
        if (globalSection != null) {
            ConfigurationSection defaultSection = globalSection.getConfigurationSection("defaults");
            if (defaultSection != null) {
                for (String key : defaultSection.getKeys(false)) {
                    Attribute attribute = asAttribute(key);
                    if (attribute != null) {
                        defaults.put(attribute, defaultSection.getDouble(key));
                    }
                }
            }
            ConfigurationSection capsSection = globalSection.getConfigurationSection("caps");
            if (capsSection != null) {
                for (String key : capsSection.getKeys(false)) {
                    Attribute attribute = asAttribute(key);
                    if (attribute != null) {
                        caps.put(attribute, capsSection.getDouble(key));
                    }
                }
            }
        }

        ConfigurationSection customSection = config.getConfigurationSection("custom-attributes");
        if (customSection != null) {
            for (String key : customSection.getKeys(false)) {
                ConfigurationSection defSection = customSection.getConfigurationSection(key);
                if (defSection == null) {
                    continue;
                }
                double defaultValue = defSection.getDouble("default", 0.0);
                ConfigurationSection labelsSection = defSection.getConfigurationSection("actual-value-labels");
                Map<Double, String> labels = new HashMap<>();
                if (labelsSection != null) {
                    for (String valueKey : labelsSection.getKeys(false)) {
                        try {
                            double numericKey = Double.parseDouble(valueKey);
                            labels.put(numericKey, labelsSection.getString(valueKey, ""));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                customAttributes.put(key, new CustomAttributeDefinition(key, defaultValue, labels));
            }
        }
    }

    private Attribute asAttribute(String name) {
        try {
            return Attribute.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public Optional<Double> getDefault(Attribute attribute) {
        return Optional.ofNullable(defaults.get(attribute));
    }

    public Optional<Double> getCap(Attribute attribute) {
        return Optional.ofNullable(caps.get(attribute));
    }

    public Map<Attribute, Double> getDefaults() {
        return Collections.unmodifiableMap(defaults);
    }

    public Map<Attribute, Double> getCaps() {
        return Collections.unmodifiableMap(caps);
    }

    public Map<String, CustomAttributeDefinition> getCustomAttributes() {
        return Collections.unmodifiableMap(customAttributes);
    }

    public String getPlayerStorageFolder() {
        return playerStorageFolder;
    }
}
