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

    /**
     * Resolves the maximum bound to use for a given override key. Blank keys fall back to the
     * global maximum, and unknown keys default to the configured global cap.
     */
    public double resolveMax(String overrideKey) {
        if (overrideKey == null || overrideKey.isBlank()) {
            return globalMax;
        }
        return overrideMaxValues.getOrDefault(overrideKey.toLowerCase(), globalMax);
    }

    /**
     * Clamps the provided value between the global minimum and maximum with no override key.
     * Equivalent to calling {@link #clamp(double, String)} with {@code null}.
     */
    public double clamp(double value) {
        return clamp(value, null);
    }

    /**
     * Clamps the provided value between the global minimum and the resolved maximum for the given
     * override key. Override keys map to entries in {@link #overrideMaxValues} and allow different
     * contexts (such as specific players) to enforce bespoke caps.
     */
    public double clamp(double value, String overrideKey) {
        double max = resolveMax(overrideKey);
        double clamped = Math.min(value, max);
        return Math.max(globalMin, clamped);
    }
}
