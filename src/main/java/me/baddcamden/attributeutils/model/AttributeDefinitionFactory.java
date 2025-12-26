package me.baddcamden.attributeutils.model;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.function.Consumer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import me.baddcamden.attributeutils.model.ModifierOperation;

/**
 * Utility methods for building {@link AttributeDefinition} instances from configuration.
 * The helpers mirror the engine's concepts: each definition has a default baseline,
 * optional dynamic current baseline, and cap configuration that can be overridden via
 * keyed entries. The factory keeps all helpers stateless and focused on configuration
 * parsing to simplify consumption elsewhere in the plugin.
 */
public final class AttributeDefinitionFactory {

    private AttributeDefinitionFactory() {
    }

    /**
     * Builds a set of vanilla attribute definitions using configured defaults and caps.
     *
     * @param config configuration to read values from
     * @return ordered map of attribute id to definition to preserve declaration order
     */
    public static Map<String, AttributeDefinition> vanillaAttributes(FileConfiguration config) {
        Map<String, AttributeDefinition> definitions = new LinkedHashMap<>();

        definitions.put("max_health", cappedAttribute(
                "max_health",
                "Max Health",
                capConfigWithMin(config, "max_health", 100, 0.0001d),
                dynamic(config, "max_health", false),
                defaultBase(config, "max_health", 20),
                defaultOperation(config, "max_health", ModifierOperation.ADD)
        ));
        definitions.put("follow_range", cappedAttribute(
                "follow_range",
                "Follow Range",
                capConfig(config, "follow_range", 256),
                dynamic(config, "follow_range", false),
                defaultBase(config, "follow_range", 32),
                defaultOperation(config, "follow_range", ModifierOperation.ADD)
        ));
        definitions.put("attack_damage", cappedAttribute(
                "attack_damage",
                "Attack Damage",
                capConfig(config, "attack_damage", 100),
                dynamic(config, "attack_damage", true),
                defaultBase(config, "attack_damage", 1),
                defaultOperation(config, "attack_damage", ModifierOperation.ADD)
        ));
        definitions.put("attack_knockback", cappedAttribute(
                "attack_knockback",
                "Attack Knockback",
                capConfig(config, "attack_knockback", 10),
                dynamic(config, "attack_knockback", true),
                defaultBase(config, "attack_knockback", 0),
                defaultOperation(config, "attack_knockback", ModifierOperation.ADD)
        ));
        definitions.put("attack_speed", cappedAttribute(
                "attack_speed",
                "Attack Speed",
                capConfig(config, "attack_speed", 40),
                dynamic(config, "attack_speed", true),
                defaultBase(config, "attack_speed", 4),
                defaultOperation(config, "attack_speed", ModifierOperation.ADD)
        ));
        definitions.put("movement_speed", cappedAttribute(
                "movement_speed",
                "Movement Speed",
                capConfig(config, "movement_speed", 1),
                dynamic(config, "movement_speed", false),
                defaultBase(config, "movement_speed", 0.1),
                defaultOperation(config, "movement_speed", ModifierOperation.ADD)
        ));
        definitions.put("flying_speed", cappedAttribute(
                "flying_speed",
                "Flying Speed",
                capConfig(config, "flying_speed", 1),
                dynamic(config, "flying_speed", false),
                defaultBase(config, "flying_speed", 0.4),
                defaultOperation(config, "flying_speed", ModifierOperation.ADD)
        ));
        definitions.put("armor", cappedAttribute(
                "armor",
                "Armor",
                capConfig(config, "armor", 40),
                dynamic(config, "armor", true),
                defaultBase(config, "armor", 0),
                defaultOperation(config, "armor", ModifierOperation.ADD)
        ));
        definitions.put("armor_toughness", cappedAttribute(
                "armor_toughness",
                "Armor Toughness",
                capConfig(config, "armor_toughness", 20),
                dynamic(config, "armor_toughness", true),
                defaultBase(config, "armor_toughness", 0),
                defaultOperation(config, "armor_toughness", ModifierOperation.ADD)
        ));
        definitions.put("luck", cappedAttribute(
                "luck",
                "Luck",
                capConfig(config, "luck", 1024),
                dynamic(config, "luck", false),
                defaultBase(config, "luck", 0),
                defaultOperation(config, "luck", ModifierOperation.ADD)
        ));
        definitions.put("knockback_resistance", cappedAttribute(
                "knockback_resistance",
                "Knockback Resistance",
                capConfig(config, "knockback_resistance", 1),
                dynamic(config, "knockback_resistance", true),
                defaultBase(config, "knockback_resistance", 0),
                defaultOperation(config, "knockback_resistance", ModifierOperation.ADD)
        ));
        definitions.put("block_range", cappedAttribute(
                "block_range",
                "Block Interaction Range",
                capConfig(config, "block_range", 128),
                dynamic(config, "block_range", false),
                defaultBase(config, "block_range", 5),
                defaultOperation(config, "block_range", ModifierOperation.ADD)
        ));
        definitions.put("interaction_range", cappedAttribute(
                "interaction_range",
                "Entity Interaction Range",
                capConfig(config, "interaction_range", 64),
                dynamic(config, "interaction_range", true),
                defaultBase(config, "interaction_range", 3),
                defaultOperation(config, "interaction_range", ModifierOperation.ADD)
        ));
        definitions.put("block_break_speed", cappedAttribute(
                "block_break_speed",
                "Block Break Speed",
                capConfig(config, "block_break_speed", 1024),
                dynamic(config, "block_break_speed", false),
                defaultBase(config, "block_break_speed", 1),
                defaultOperation(config, "block_break_speed", ModifierOperation.ADD)
        ));
        definitions.put("mining_efficiency", cappedAttribute(
                "mining_efficiency",
                "Mining Efficiency",
                capConfig(config, "mining_efficiency", 1024),
                dynamic(config, "mining_efficiency", false),
                defaultBase(config, "mining_efficiency", 1),
                defaultOperation(config, "mining_efficiency", ModifierOperation.ADD)
        ));
        definitions.put("gravity", cappedAttribute(
                "gravity",
                "Gravity",
                capConfig(config, "gravity", 10),
                dynamic(config, "gravity", false),
                defaultBase(config, "gravity", 1),
                defaultOperation(config, "gravity", ModifierOperation.ADD)
        ));
        definitions.put("scale", cappedAttribute(
                "scale",
                "Scale",
                capConfigWithMin(config, "scale", 10, 0.0001d),
                dynamic(config, "scale", false),
                defaultBase(config, "scale", 1),
                defaultOperation(config, "scale", ModifierOperation.ADD)
        ));
        definitions.put("step_height", cappedAttribute(
                "step_height",
                "Step Height",
                capConfig(config, "step_height", 5),
                dynamic(config, "step_height", false),
                defaultBase(config, "step_height", 0.6),
                defaultOperation(config, "step_height", ModifierOperation.ADD)
        ));
        definitions.put("safe_fall_distance", cappedAttribute(
                "safe_fall_distance",
                "Safe Fall Distance",
                capConfig(config, "safe_fall_distance", 256),
                dynamic(config, "safe_fall_distance", false),
                defaultBase(config, "safe_fall_distance", 3),
                defaultOperation(config, "safe_fall_distance", ModifierOperation.ADD)
        ));
        definitions.put("fall_damage_multiplier", cappedAttribute(
                "fall_damage_multiplier",
                "Fall Damage Multiplier",
                capConfig(config, "fall_damage_multiplier", 10),
                dynamic(config, "fall_damage_multiplier", false),
                defaultBase(config, "fall_damage_multiplier", 1),
                defaultOperation(config, "fall_damage_multiplier", ModifierOperation.ADD)
        ));
        definitions.put("jump_strength", cappedAttribute(
                "jump_strength",
                "Jump Strength",
                capConfig(config, "jump_strength", 5),
                dynamic(config, "jump_strength", false),
                defaultBase(config, "jump_strength", 1),
                defaultOperation(config, "jump_strength", ModifierOperation.ADD)
        ));
        definitions.put("sneaking_speed", cappedAttribute(
                "sneaking_speed",
                "Sneaking Speed",
                capConfig(config, "sneaking_speed", 4),
                dynamic(config, "sneaking_speed", false),
                defaultBase(config, "sneaking_speed", 1),
                defaultOperation(config, "sneaking_speed", ModifierOperation.ADD)
        ));
        definitions.put("sprint_speed", cappedAttribute(
                "sprint_speed",
                "Sprint Speed",
                capConfig(config, "sprint_speed", 4),
                dynamic(config, "sprint_speed", false),
                defaultBase(config, "sprint_speed", 1),
                defaultOperation(config, "sprint_speed", ModifierOperation.ADD)
        ));
        definitions.put("swim_speed", cappedAttribute(
                "swim_speed",
                "Swim Speed",
                capConfig(config, "swim_speed", 4),
                dynamic(config, "swim_speed", false),
                defaultBase(config, "swim_speed", 1),
                defaultOperation(config, "swim_speed", ModifierOperation.ADD)
        ));
        definitions.put("damage_reduction", cappedAttribute(
                "damage_reduction",
                "Damage Reduction",
                capConfig(config, "damage_reduction", 1),
                true,
                defaultBase(config, "damage_reduction", 0),
                defaultOperation(config, "damage_reduction", ModifierOperation.ADD)
        ));
        return definitions;
    }

    /**
     * Registers capped attributes found in configuration with the provided consumer. Each entry is
     * converted into a {@link CapConfig} that respects per-key overrides so callers can map
     * override keys (such as player identifiers) to distinct maxima.
     *
     * @param consumer destination for generated definitions
     * @param caps configuration section containing caps by key
     */
    public static void registerConfigCaps(Consumer<AttributeDefinition> consumer, ConfigurationSection caps) {
        registerConfigCaps(consumer, caps, Set.of());
    }

    /**
     * Registers capped attributes found in configuration while skipping known keys.
     *
     * @param consumer destination for generated definitions
     * @param caps configuration section containing caps by key
     * @param skipKeys normalized keys to ignore when creating definitions
     */
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
            // VAGUE/IMPROVEMENT NEEDED deciding precedence when both section and scalar exist is implicit; clarify desired priority
            consumer.accept(cappedAttribute(normalizedKey, humanize(normalizedKey), capValue));
        }
    }

    public static AttributeDefinition cappedAttribute(String id, String displayName, double capValue) {
        return cappedAttribute(id, displayName, capValue, false, capValue, ModifierOperation.ADD);
    }

    /**
     * Creates an attribute definition with a static cap and default current/base values equal to
     * the cap.
     */
    public static AttributeDefinition cappedAttribute(String id, String displayName, double capValue, boolean dynamic) {
        return cappedAttribute(id, displayName, capValue, dynamic, capValue, ModifierOperation.ADD);
    }

    /**
     * Creates an attribute definition with a static cap and explicit default values.
     */
    public static AttributeDefinition cappedAttribute(String id, String displayName, double capValue, boolean dynamic, double defaultValue) {
        return cappedAttribute(id, displayName, capValue, dynamic, defaultValue, ModifierOperation.ADD);
    }

    public static AttributeDefinition cappedAttribute(String id,
                                                      String displayName,
                                                      double capValue,
                                                      boolean dynamic,
                                                      double defaultValue,
                                                      ModifierOperation defaultOperation) {
        CapConfig capConfig = new CapConfig(0, capValue, Map.of());
        return cappedAttribute(id, displayName, capConfig, dynamic, defaultValue, defaultOperation);
    }

    /**
     * Creates an attribute definition with the provided cap configuration and default values.
     */
    public static AttributeDefinition cappedAttribute(String id,
                                                      String displayName,
                                                      CapConfig capConfig,
                                                      boolean dynamic,
                                                      double defaultValue,
                                                      ModifierOperation defaultOperation) {
        return new AttributeDefinition(
                id.toLowerCase(Locale.ROOT),
                displayName,
                dynamic,
                defaultValue,
                defaultValue,
                capConfig,
                MultiplierApplicability.allowAllMultipliers(),
                defaultOperation
        );
    }

    /**
     * Converts an identifier into a user-facing label with spaces and capitalized first letter.
     */
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

    private static boolean dynamic(FileConfiguration config, String attributeId, boolean fallback) {
        String configKey = configKey(attributeId);
        ConfigurationSection defaults = config.getConfigurationSection("vanilla-attribute-defaults");
        if (defaults != null) {
            ConfigurationSection entry = defaults.getConfigurationSection(configKey);
            if (entry != null) {
                return entry.getBoolean("dynamic", fallback);
            }
            if (defaults.isSet(configKey)) {
                return defaults.getBoolean(configKey, fallback);
            }
        }

        if (config.isSet(configKey)) {
            return config.getBoolean(configKey, fallback);
        }

        return fallback;
    }

    /**
     * Resolves a default value for a configured attribute by searching nested defaults and
     * falling back to the attribute root or provided fallback.
     */
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

    private static ModifierOperation defaultOperation(FileConfiguration config, String attributeId, ModifierOperation fallback) {
        String configKey = configKey(attributeId);
        ConfigurationSection defaults = config.getConfigurationSection("vanilla-attribute-defaults");
        if (defaults != null) {
            ConfigurationSection entry = defaults.getConfigurationSection(configKey);
            if (entry != null) {
                return parseOperation(entry.getString("operation"), fallback);
            }
            if (defaults.isSet(configKey)) {
                return parseOperation(defaults.getString(configKey), fallback);
            }
        }

        if (config.isSet(configKey)) {
            return parseOperation(config.getString(configKey), fallback);
        }
        return fallback;
    }

    /**
     * Builds a {@link CapConfig} from the {@code global-attribute-caps} configuration section,
     * preserving override entries. Overrides use normalized keys so different dash/underscore
     * styles map to the same identifier.
     */
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

    /**
     * Ensures the generated cap configuration respects a minimum allowed value in addition to
     * any configured minimum.
     */
    private static CapConfig capConfigWithMin(FileConfiguration config, String attributeId, double defaultMax, double minimum) {
        CapConfig base = capConfig(config, attributeId, defaultMax);
        double enforcedMin = Math.max(base.globalMin(), minimum);
        return new CapConfig(enforcedMin, base.globalMax(), base.overrideMaxValues());
    }

    private static ModifierOperation parseOperation(String raw, ModifierOperation fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return ModifierOperation.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static String configKey(String attributeId) {
        return attributeId.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
