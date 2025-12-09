package me.BaddCamden.AttributeUtils.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Represents a custom attribute that is stored alongside vanilla attributes but not tied to a Bukkit Attribute enum.
 */
public class CustomAttributeDefinition {
    private final String key;
    private final double defaultValue;
    private final NavigableMap<Double, String> labelsByValue;

    public CustomAttributeDefinition(String key, double defaultValue, Map<Double, String> labelsByValue) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.labelsByValue = new TreeMap<>(labelsByValue);
    }

    public String getKey() {
        return key;
    }

    public double getDefaultValue() {
        return defaultValue;
    }

    public Map<Double, String> getLabelsByValue() {
        return new LinkedHashMap<>(labelsByValue);
    }

    public String labelFor(double value) {
        Map.Entry<Double, String> entry = labelsByValue.floorEntry(value);
        if (entry == null) {
            return labelsByValue.isEmpty() ? "" : labelsByValue.firstEntry().getValue();
        }
        return entry.getValue();
    }
}
