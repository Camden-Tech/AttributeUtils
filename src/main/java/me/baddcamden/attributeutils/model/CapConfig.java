package me.baddcamden.attributeutils.model;

import java.util.Collections;
import java.util.Map;

/**
 * Cap configuration for an attribute. Caps are applied after each computation stage to enforce a
 * min/max range. The {@code overrideMaxValues} map allows specific contexts (e.g., player IDs) to
 * supply alternative maxima while the global minimum always applies.
 */
public record CapConfig(double globalMin, double globalMax, Map<String, Double> overrideMaxValues) {

    public CapConfig {
        if (globalMax < globalMin) {
            throw new IllegalArgumentException("Global max must be greater than or equal to global min");
        }
        overrideMaxValues = overrideMaxValues == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(overrideMaxValues);
    }

    public double resolveMax(String overrideKey) {
        if (overrideKey == null || overrideKey.isBlank()) {
            return globalMax;
        }
        return overrideMaxValues.getOrDefault(overrideKey.toLowerCase(), globalMax);
    }

    public double clamp(double value) {
        return clamp(value, null);
    }

    public double clamp(double value, String overrideKey) {
        double max = resolveMax(overrideKey);
        double clamped = Math.min(value, max);
        return Math.max(globalMin, clamped);
    }
}
