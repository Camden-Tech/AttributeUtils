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
        double maxHealth = 20;
        double attackDamage = 1;
        double attackSpeed = 4;
        double movementSpeed = 0.1;
        double armor = 0;
        double armorToughness = 0;
        double luck = 0;
        double knockbackResistance = 0;
        double maxHunger = config.getDouble("max-hunger", 20);
        double maxOxygen = config.getDouble("max-oxygen", 20);
        double blockReach = 5;
        double interactionRange = 3;
        double miningEfficiency = 1;
        double gravity = 1;
        double scale = 1;
        double regenerationRate = 1;
        double damageReduction = 0;

        Map<String, AttributeDefinition> definitions = new LinkedHashMap<>();
        definitions.put("max_health", cappedAttribute("max_health", "Max Health", 100, true, maxHealth));
        definitions.put("attack_damage", cappedAttribute("attack_damage", "Attack Damage", 100, true, attackDamage));
        definitions.put("attack_speed", cappedAttribute("attack_speed", "Attack Speed", 40, true, attackSpeed));
        definitions.put("movement_speed", cappedAttribute("movement_speed", "Movement Speed", 1, true, movementSpeed));
        definitions.put("armor", cappedAttribute("armor", "Armor", 40, true, armor));
        definitions.put("armor_toughness", cappedAttribute("armor_toughness", "Armor Toughness", 20, true, armorToughness));
        definitions.put("luck", cappedAttribute("luck", "Luck", 1024, true, luck));
        definitions.put("knockback_resistance", cappedAttribute("knockback_resistance", "Knockback Resistance", 1, true, knockbackResistance));
        definitions.put("max_hunger", cappedAttribute("max_hunger", "Max Hunger", maxHunger, true));
        definitions.put("max_oxygen", cappedAttribute("max_oxygen", "Max Oxygen", maxOxygen, true));
        definitions.put("block_range", cappedAttribute("block_range", "Block Range", 128, false, blockReach));
        definitions.put("interaction_range", cappedAttribute("interaction_range", "Interaction Range", 64, false, interactionRange));
        definitions.put("mining_efficiency", cappedAttribute("mining_efficiency", "Mining Efficiency", 1024, false, miningEfficiency));
        definitions.put("gravity", cappedAttribute("gravity", "Gravity", 10, true, gravity));
        definitions.put("scale", cappedAttribute("scale", "Scale", 10, true, scale));
        definitions.put("regeneration_rate", cappedAttribute("regeneration_rate", "Regeneration Rate", 100, true, regenerationRate));
        definitions.put("damage_reduction", cappedAttribute("damage_reduction", "Damage Reduction", 1, false, damageReduction));
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
            if (skipKeys.contains(key.toLowerCase(Locale.ROOT))) {
                continue;
            }
            double capValue = caps.getDouble(key);
            consumer.accept(cappedAttribute(key, humanize(key), capValue));
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
}
