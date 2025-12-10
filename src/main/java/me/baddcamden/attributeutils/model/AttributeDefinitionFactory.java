package me.baddcamden.attributeutils.model;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AttributeDefinitionFactory {

    private AttributeDefinitionFactory() {
    }

    public static Map<String, AttributeDefinition> vanillaAttributes(FileConfiguration config) {
        Map<String, AttributeDefinition> definitions = new LinkedHashMap<>();

        definitions.put("max_health", cappedAttribute(
                "max_health",
                "Max Health",
                capConfig(config, "max_health", 100),
                true,
                defaultBase(config, "max_health", 20)
        ));
        definitions.put("attack_damage", cappedAttribute(
                "attack_damage",
                "Attack Damage",
                capConfig(config, "attack_damage", 100),
                true,
                defaultBase(config, "attack_damage", 1)
        ));
        definitions.put("attack_speed", cappedAttribute(
                "attack_speed",
                "Attack Speed",
                capConfig(config, "attack_speed", 40),
                true,
                defaultBase(config, "attack_speed", 4)
        ));
        definitions.put("movement_speed", cappedAttribute(
                "movement_speed",
                "Movement Speed",
                capConfig(config, "movement_speed", 1),
                true,
                defaultBase(config, "movement_speed", 0.1)
        ));
        definitions.put("armor", cappedAttribute(
                "armor",
                "Armor",
                capConfig(config, "armor", 40),
                true,
                defaultBase(config, "armor", 0)
        ));
        definitions.put("armor_toughness", cappedAttribute(
                "armor_toughness",
                "Armor Toughness",
                capConfig(config, "armor_toughness", 20),
                true,
                defaultBase(config, "armor_toughness", 0)
        ));
        definitions.put("luck", cappedAttribute(
                "luck",
                "Luck",
                capConfig(config, "luck", 1024),
                true,
                defaultBase(config, "luck", 0)
        ));
        definitions.put("knockback_resistance", cappedAttribute(
                "knockback_resistance",
                "Knockback Resistance",
                capConfig(config, "knockback_resistance", 1),
                true,
                defaultBase(config, "knockback_resistance", 0)
        ));
        definitions.put("max_hunger", cappedAttribute(
                "max_hunger",
                "Max Hunger",
                capConfig(config, "max_hunger", defaultBase(config, "max_hunger", 20)),
                true,
                defaultBase(config, "max_hunger", 20)
        ));
        definitions.put("max_oxygen", cappedAttribute(
                "max_oxygen",
                "Max Oxygen",
                capConfig(config, "max_oxygen", defaultBase(config, "max_oxygen", 20)),
                true,
                defaultBase(config, "max_oxygen", 20)
        ));
        definitions.put("block_range", cappedAttribute(
                "block_range",
                "Block Interaction Range",
                capConfig(config, "block_range", 128),
                false,
                defaultBase(config, "block_range", 5)
        ));
        definitions.put("interaction_range", cappedAttribute(
                "interaction_range",
                "Entity Interaction Range",
                capConfig(config, "interaction_range", 64),
                false,
                defaultBase(config, "interaction_range", 3)
        ));
        definitions.put("mining_efficiency", cappedAttribute(
                "mining_efficiency",
                "Mining Efficiency",
                capConfig(config, "mining_efficiency", 1024),
                false,
                defaultBase(config, "mining_efficiency", 1)
        ));
        definitions.put("gravity", cappedAttribute(
                "gravity",
                "Gravity",
                capConfig(config, "gravity", 10),
                true,
                defaultBase(config, "gravity", 1)
        ));
        definitions.put("scale", cappedAttribute(
                "scale",
                "Scale",
                capConfig(config, "scale", 10),
                true,
                defaultBase(config, "scale", 1)
        ));
        definitions.put("regeneration_rate", cappedAttribute(
                "regeneration_rate",
                "Regeneration Rate",
                capConfig(config, "regeneration_rate", 100),
                true,
                defaultBase(config, "regeneration_rate", 1)
        ));
        definitions.put("damage_reduction", cappedAttribute(
                "damage_reduction",
                "Damage Reduction",
                capConfig(config, "damage_reduction", 1),
                false,
                defaultBase(config, "damage_reduction", 0)
        ));
        return definitions;
    }

    public static void registerConfigCaps(AttributeServiceConsumer consumer, ConfigurationSection caps) {
        registerConfigCaps(consumer, caps, Set.of());
    }

    public static void registerConfigCaps(AttributeServiceConsumer consumer, ConfigurationSection caps, Set<String> skipKeys) {
        if (caps == null) {
            return;
        }

        for (String key : caps.getKeys(false)) {
            String normalizedKey = normalizeKey(key);
            if (skipKeys.contains(normalizedKey)) {
                continue;
            }
            ConfigurationSection capSection = caps.getConfigurationSection(key);
            double capValue = capSection == null ? caps.getDouble(key) : capSection.getDouble("max", caps.getDouble(key));
            consumer.accept(cappedAttribute(normalizedKey, humanize(normalizedKey), capValue));
        }
    }

    public static AttributeDefinition cappedAttribute(String id, String displayName, double capValue) {
        return cappedAttribute(id, displayName, capValue, false);
    }

    public static AttributeDefinition cappedAttribute(String id, String displayName, double capValue, boolean dynamic) {
        return cappedAttribute(id, displayName, capValue, dynamic, capValue);
    }

    public static AttributeDefinition cappedAttribute(String id, String displayName, double capValue, boolean dynamic, double defaultValue) {
        CapConfig capConfig = new CapConfig(0, capValue, Map.of());
        return cappedAttribute(id, displayName, capConfig, dynamic, defaultValue);
    }

    public static AttributeDefinition cappedAttribute(String id,
                                                      String displayName,
                                                      CapConfig capConfig,
                                                      boolean dynamic,
                                                      double defaultValue) {
        return new AttributeDefinition(
                id.toLowerCase(Locale.ROOT),
                displayName,
                dynamic,
                defaultValue,
                defaultValue,
                capConfig,
                MultiplierApplicability.allowAllMultipliers()
        );
    }

    public interface AttributeServiceConsumer {
        void accept(AttributeDefinition definition);
    }

    private static String humanize(String id) {
        String withSpaces = id.replace('_', ' ').trim();
        if (withSpaces.isEmpty()) {
            return id;
        }
        return Character.toUpperCase(withSpaces.charAt(0)) + withSpaces.substring(1);
    }

    private static double defaultBase(FileConfiguration config, String attributeId, double fallback) {
        return defaultValue(config, attributeId, "default-base", fallback);
    }

    private static double defaultValue(FileConfiguration config, String attributeId, String field, double fallback) {
        String configKey = configKey(attributeId);
        ConfigurationSection defaults = config.getConfigurationSection("vanilla-attribute-defaults");
        if (defaults != null) {
            ConfigurationSection entry = defaults.getConfigurationSection(configKey);
            if (entry != null) {
                return entry.getDouble(field, fallback);
            }
            if (defaults.isSet(configKey)) {
                return defaults.getDouble(configKey, fallback);
            }
        }

        if (config.isSet(configKey)) {
            return config.getDouble(configKey, fallback);
        }
        return fallback;
    }

    private static CapConfig capConfig(FileConfiguration config, String attributeId, double defaultMax) {
        double min = 0;
        double max = defaultMax;
        Map<String, Double> overrides = new LinkedHashMap<>();
        ConfigurationSection caps = config.getConfigurationSection("global-attribute-caps");
        String configKey = configKey(attributeId);
        if (caps != null) {
            ConfigurationSection capSection = caps.getConfigurationSection(configKey);
            if (capSection != null) {
                min = capSection.getDouble("min", min);
                max = capSection.getDouble("max", max);
                ConfigurationSection overrideSection = capSection.getConfigurationSection("overrides");
                if (overrideSection != null) {
                    for (String key : overrideSection.getKeys(false)) {
                        overrides.put(normalizeKey(key), overrideSection.getDouble(key));
                    }
                }
            } else if (caps.isSet(configKey)) {
                max = caps.getDouble(configKey, max);
            }
        }

        return new CapConfig(min, max, overrides);
    }

    private static String configKey(String attributeId) {
        return attributeId.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
