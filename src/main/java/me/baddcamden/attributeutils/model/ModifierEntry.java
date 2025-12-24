package me.baddcamden.attributeutils.model;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Describes a single modifier entry and the computation buckets it participates in. A modifier can
 * target the default layer, the current layer, or both. Additive modifiers without multiplier keys
 * are summed into the stage subtotal before multipliers are compounded, so they receive the full
 * stage-wide multiplier effect. When {@code useMultiplierKeys} is true, the additive amount is
 * multiplied only by the intersecting multipliers and then added after the unkeyed multiplier
 * product is applied. Multiplier modifiers contribute to the multiplicative product for a stage
 * regardless of scope.
 */
public record ModifierEntry(String key,
                            ModifierOperation operation,
                            double amount,
                            boolean temporary,
                            boolean appliesToDefault,
                            boolean appliesToCurrent,
                            boolean useMultiplierKeys,
                            Set<String> multiplierKeys,
                            Double durationSeconds) {

    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9_.-]+", Pattern.CASE_INSENSITIVE);

    public ModifierEntry(String key,
                         ModifierOperation operation,
                         double amount,
                         boolean temporary,
                         boolean appliesToDefault,
                         boolean appliesToCurrent,
                         boolean useMultiplierKeys,
                         Set<String> multiplierKeys) {
        this(key, operation, amount, temporary, appliesToDefault, appliesToCurrent, useMultiplierKeys, multiplierKeys, null);
    }

    public ModifierEntry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(operation, "operation");
        multiplierKeys = normalizeMultiplierKeys(multiplierKeys);
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("Modifier keys must match " + KEY_PATTERN.pattern());
        }
        if (!appliesToDefault && !appliesToCurrent) {
            throw new IllegalArgumentException("Modifier must apply to at least one stage");
        }
        if (durationSeconds != null) {
            if (durationSeconds <= 0) {
                throw new IllegalArgumentException("Duration must be positive when specified");
            }
            if (!temporary) {
                throw new IllegalArgumentException("Duration may only be set for temporary modifiers");
            }
        }
    }

    /**
     * Indicates whether the modifier should be cleared after a temporary effect window. Temporary
     * entries are the ones {@link AttributeInstance#purgeTemporaryModifiers()} will remove.
     */
    public boolean isTemporary() {
        return temporary;
    }

    /**
     * Convenience inverse of {@link #isTemporary()} used by callers that treat permanent modifiers
     * differently during scheduling.
     */
    public boolean isPermanent() {
        return !temporary;
    }

    /**
     * @return {@code true} when this modifier participates in the default computation layer.
     */
    public boolean isDefaultModifier() {
        return appliesToDefault;
    }

    /**
     * @return {@code true} when this modifier participates in the current computation layer.
     */
    public boolean appliesToCurrent() {
        return appliesToCurrent;
    }

    /**
     * Whether additive amounts should only be multiplied by matched multiplier buckets rather than
     * the full multiplier stack for the stage.
     */
    public boolean useMultiplierKeys() {
        return useMultiplierKeys;
    }

    /**
     * Returns the normalized set of multiplier keys that gate multiplier application when
     * {@link #useMultiplierKeys()} is true.
     */
    public Set<String> multiplierKeys() {
        return multiplierKeys;
    }

    /**
     * Exposes the optional duration as an {@link Optional}, preserving {@code null} to represent a
     * timeless modifier while still allowing callers to use fluent Optional APIs.
     */
    public Optional<Double> durationSecondsOptional() {
        return Optional.ofNullable(durationSeconds);
    }

    /**
     * Normalizes multiplier key input by lowercasing entries and discarding {@code null} values so
     * bucket matching remains case-insensitive and null-safe.
     */
    private Set<String> normalizeMultiplierKeys(Set<String> keys) {
        if (keys == null) {
            return Collections.emptySet();
        }
        return keys.stream()
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
    }
}
