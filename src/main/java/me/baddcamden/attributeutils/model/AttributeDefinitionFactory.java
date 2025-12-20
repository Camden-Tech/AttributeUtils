package me.baddcamden.attributeutils.model;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.function.Consumer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Utility methods for building {@link AttributeDefinition} instances from configuration. The
 * helpers mirror the engine's concepts: each definition has a default baseline, optional
 * dynamic current baseline, and cap configuration that can be overridden via keyed entries.
 */
public final class AttributeDefinitionFactory {

    private AttributeDefinitionFactory() {
    }

    public static Map<String, AttributeDefinition> vanillaAttributes(FileConfiguration config) {
        Map<String, AttributeDefinition> definitions = new LinkedHashMap<>();

        definitions.put("max_health", cappedAttribute(
                "max_health",
                "Max Health",
                capConfigWithMin(config, "max_health", 100, 0.0001d),
                false,
                defaultBase(config, "max_health", 20)
        ));
        definitions.put("follow_range", cappedAttribute(
                "follow_range",
                "Follow Range",
                capConfig(config, "follow_range", 256),
                false,
                defaultBase(config, "follow_range", 32)
        ));
        definitions.put("attack_damage", cappedAttribute(
                "attack_damage",
                "Attack Damage",
                capConfig(config, "attack_damage", 100),
                true,
                defaultBase(config, "attack_damage", 1)
        ));
        definitions.put("attack_knockback", cappedAttribute(
                "attack_knockback",
                "Attack Knockback",
                capConfig(config, "attack_knockback", 10),
                true,
                defaultBase(config, "attack_knockback", 0)
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
                false,
                defaultBase(config, "movement_speed", 0.1)
        ));
        definitions.put("flying_speed", cappedAttribute(
                "flying_speed",
                "Flying Speed",
                capConfig(config, "flying_speed", 1),
                false,
                defaultBase(config, "flying_speed", 0.4)
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
                false,
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
                false,
                defaultBase(config, "max_hunger", 20)
        ));
        definitions.put("oxygen_bonus", cappedAttribute(
                "oxygen_bonus",
                "Oxygen Bonus",
                capConfig(config, "oxygen_bonus", 600),
                false,
                defaultBase(config, "oxygen_bonus", 0)
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
        definitions.put("block_break_speed", cappedAttribute(
                "block_break_speed",
                "Block Break Speed",
                capConfig(config, "block_break_speed", 1024),
                false,
                defaultBase(config, "block_break_speed", 1)
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
                false,
                defaultBase(config, "gravity", 1)
        ));
        definitions.put("scale", cappedAttribute(
                "scale",
                "Scale",
                capConfigWithMin(config, "scale", 10, 0.0001d),
                false,
                defaultBase(config, "scale", 1)
        ));
        definitions.put("step_height", cappedAttribute(
                "step_height",
                "Step Height",
                capConfig(config, "step_height", 5),
                false,
                defaultBase(config, "step_height", 0.6)
        ));
        definitions.put("safe_fall_distance", cappedAttribute(
                "safe_fall_distance",
                "Safe Fall Distance",
                capConfig(config, "safe_fall_distance", 256),
                false,
                defaultBase(config, "safe_fall_distance", 3)
        ));
        definitions.put("fall_damage_multiplier", cappedAttribute(
                "fall_damage_multiplier",
                "Fall Damage Multiplier",
                capConfig(config, "fall_damage_multiplier", 10),
                false,
                defaultBase(config, "fall_damage_multiplier", 1)
        ));
        definitions.put("jump_strength", cappedAttribute(
                "jump_strength",
                "Jump Strength",
                capConfig(config, "jump_strength", 5),
                false,
                defaultBase(config, "jump_strength", 1)
        ));
        definitions.put("sneaking_speed", cappedAttribute(
                "sneaking_speed",
                "Sneaking Speed",
                capConfig(config, "sneaking_speed", 4),
                false,
                defaultBase(config, "sneaking_speed", 1)
        ));
        definitions.put("sprint_speed", cappedAttribute(
                "sprint_speed",
                "Sprint Speed",
                capConfig(config, "sprint_speed", 4),
                false,
                defaultBase(config, "sprint_speed", 1)
        ));
        definitions.put("swim_speed", cappedAttribute(
                "swim_speed",
                "Swim Speed",
                capConfig(config, "swim_speed", 4),
                false,
                defaultBase(config, "swim_speed", 1)
        ));
        definitions.put("regeneration_rate", cappedAttribute(
                "regeneration_rate",
                "Regeneration Rate",
                capConfig(config, "regeneration_rate", 100),
                false,
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

    /**
     * Registers capped attributes found in configuration with the provided consumer. Each entry is
     * converted into a {@link CapConfig} that respects per-key overrides so callers can map
     * override keys (such as player identifiers) to distinct maxima.
     */
    public static void registerConfigCaps(Consumer<AttributeDefinition> consumer, ConfigurationSection caps) {
        registerConfigCaps(consumer, caps, Set.of());
    }

    public static void registerConfigCaps(Consumer<AttributeDefinition> consumer, ConfigurationSection caps, Set<String> skipKeys) {
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

    private static CapConfig capConfigWithMin(FileConfiguration config, String attributeId, double defaultMax, double minimum) {
        CapConfig base = capConfig(config, attributeId, defaultMax);
        double enforcedMin = Math.max(base.globalMin(), minimum);
        return new CapConfig(enforcedMin, base.globalMax(), base.overrideMaxValues());
    }

    private static String configKey(String attributeId) {
        return attributeId.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
