package me.baddcamden.attributeutils.model;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class AttributeDefinitionFactory {

    private AttributeDefinitionFactory() {
    }

    public static Map<String, AttributeDefinition> vanillaAttributes(FileConfiguration config) {
        double maxHunger = config.getDouble("max-hunger", 20);
        double maxOxygen = config.getDouble("max-oxygen", 20);

        Map<String, AttributeDefinition> definitions = new LinkedHashMap<>();
        definitions.put("max_hunger", cappedAttribute("max_hunger", "Max Hunger", maxHunger));
        definitions.put("max_oxygen", cappedAttribute("max_oxygen", "Max Oxygen", maxOxygen));
        return definitions;
    }

    public static void registerConfigCaps(AttributeServiceConsumer consumer, ConfigurationSection caps) {
        if (caps == null) {
            return;
        }

        for (String key : caps.getKeys(false)) {
            double capValue = caps.getDouble(key);
            consumer.accept(cappedAttribute(key, humanize(key), capValue));
        }
    }

    public static AttributeDefinition cappedAttribute(String id, String displayName, double capValue) {
        CapConfig capConfig = new CapConfig(0, capValue, Map.of());
        return new AttributeDefinition(
                id.toLowerCase(Locale.ROOT),
                displayName,
                false,
                capValue,
                capValue,
                capConfig,
                MultiplierApplicability.applyAll()
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
