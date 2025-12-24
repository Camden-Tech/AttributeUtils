package me.baddcamden.attributeutils.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Cap configuration for an attribute.
 * <p>
 * Caps are applied after each computation stage to enforce a min/max range. The
 * {@code overrideMaxValues} map allows specific contexts (e.g., player IDs) to supply alternative
 * maxima while the global minimum always applies. Keys are normalized to lower case to keep
 * lookups consistent regardless of caller casing.
 */
public record CapConfig(double globalMin, double globalMax, Map<String, Double> overrideMaxValues) {

    public CapConfig {
        if (globalMax < globalMin) {
            throw new IllegalArgumentException("Global max must be greater than or equal to global min");
        }

        Map<String, Double> normalizedOverrides = new LinkedHashMap<>();
        if (overrideMaxValues != null) {
            for (Map.Entry<String, Double> entry : overrideMaxValues.entrySet()) {
                String key = entry.getKey();
                if (key == null || key.isBlank()) {
                    //VAGUE/IMPROVEMENT NEEDED Clarify whether blank override keys should be ignored or treated as a request for the global max.
                    continue;
                }
                normalizedOverrides.put(key.toLowerCase(Locale.ROOT), entry.getValue());
            }
        }

        overrideMaxValues = Collections.unmodifiableMap(normalizedOverrides);
    }

    /**
     * Resolves the maximum bound to use for a given override key. Blank keys fall back to the
     * global maximum, and unknown keys default to the configured global cap.
     */
    public double resolveMax(String overrideKey) {
        if (overrideKey == null || overrideKey.isBlank()) {
            return globalMax;
        }
        return overrideMaxValues.getOrDefault(overrideKey.toLowerCase(Locale.ROOT), globalMax);
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
     * contexts (such as specific players) to enforce bespoke caps. The clamp operation is inclusive
     * of both bounds.
     */
    public double clamp(double value, String overrideKey) {
        double max = resolveMax(overrideKey);
        double clamped = Math.min(value, max);
        return Math.max(globalMin, clamped);
    }
}
