package me.baddcamden.attributeutils.model;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controls which multiplier modifiers can participate in a computation. This allows attribute
 * definitions to opt-in to specific multiplier buckets or opt-out of unwanted ones while still
 * keeping additive modifiers intact.
 */
public record MultiplierApplicability(boolean applyAll, Set<String> allowedKeys, Set<String> ignoredKeys) {

    public MultiplierApplicability {
        allowedKeys = normalize(allowedKeys);
        ignoredKeys = normalize(ignoredKeys);
    }

    /**
     * @return a configuration that permits all multiplier modifiers except those explicitly
     * ignored.
     */
    public static MultiplierApplicability allowAllMultipliers() {
        return new MultiplierApplicability(true, Collections.emptySet(), Collections.emptySet());
    }

    /**
     * Builds a configuration that only allows multiplier modifiers whose keys appear in the given
     * set.
     */
    public static MultiplierApplicability optIn(Set<String> allowedKeys) {
        return new MultiplierApplicability(false, allowedKeys, Collections.emptySet());
    }

    /**
     * Builds a configuration that allows all multipliers except the keys listed in {@code ignoredKeys}.
     */
    public static MultiplierApplicability optOut(Set<String> ignoredKeys) {
        return new MultiplierApplicability(true, Collections.emptySet(), ignoredKeys);
    }

    /**
     * Returns whether the provided modifier key is eligible for multiplier participation based on
     * the allow/deny configuration. Keys are normalized to lowercase to ensure stable matching
     * against stored sets.
     */
    public boolean canApply(String modifierKey) {
        String normalizedKey = modifierKey.toLowerCase();
        if (ignoredKeys.contains(normalizedKey)) {
            return false;
        }
        return applyAll || allowedKeys.contains(normalizedKey);
    }

    /**
     * Lowercases and deduplicates key sets while ignoring {@code null} entries to keep comparisons
     * predictable regardless of caller formatting.
     */
    private Set<String> normalize(Set<String> keys) {
        if (keys == null) {
            return Collections.emptySet();
        }

        return keys.stream()
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
    }
}
