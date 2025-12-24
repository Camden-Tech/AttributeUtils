package me.baddcamden.attributeutils.model;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Describes a single modifier entry and the computation buckets it participates in. A modifier can
 * target the default layer, the current layer, or both. Additive modifiers are folded into the base
 * subtotal before multiplier buckets are compounded, while multiplier modifiers contribute to the
 * multiplicative product for a stage. When {@code useMultiplierKeys} is true the additive amount is
 * multiplied only by the multipliers whose keys intersect with {@link #multiplierKeys()}.
 *
 * @param key               unique identifier for this modifier, used to replace existing entries
 *                          in the same bucket and to gate applicability checks
 * @param operation         whether the modifier amount is additive or multiplicative in
 *                          {@link me.baddcamden.attributeutils.compute.AttributeComputationEngine}
 * @param amount            magnitude applied during computation; sign controls buff/debuff behavior
 * @param temporary         whether the modifier is transient and should be purged when temporary
 *                          state is cleared
 * @param appliesToDefault  marks participation in the default attribute layer
 * @param appliesToCurrent  marks participation in the current attribute layer
 * @param useMultiplierKeys whether additive modifiers should be paired only with specific
 *                          multiplier keys
 * @param multiplierKeys    allowed multiplier identifiers when {@code useMultiplierKeys} is true
 * @param durationSeconds   optional lifetime for temporary modifiers; {@code null} for timeless
 *                          entries
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

    /** Pattern restricting modifier keys to a readable, namespaced format. */
    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9_.-]+", Pattern.CASE_INSENSITIVE);

    /**
     * Convenience constructor for timeless modifiers that defaults {@link #durationSeconds()} to
     * {@code null}.
     *
     * @param key                unique identifier for the modifier; must match {@link #KEY_PATTERN}
     * @param operation          whether this modifier is additive or multiplicative
     * @param amount             signed magnitude used during computation
     * @param temporary          whether the modifier is scoped to a session and eligible for purge
     * @param appliesToDefault   participation flag for the default computation layer
     * @param appliesToCurrent   participation flag for the current computation layer
     * @param useMultiplierKeys  toggles keyed multiplier application for additive entries
     * @param multiplierKeys     multiplier identifiers used when {@code useMultiplierKeys} is true
     */
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

    /**
     * Canonical constructor that validates inputs and normalizes multiplier keys. Validation
     * enforces key formatting, presence of at least one computation layer, and alignment between
     * temporary flags and duration semantics.
     */
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
     * entries are the ones {@link AttributeInstance#purgeTemporaryModifiers()} will remove and the
     * only entries permitted to declare {@link #durationSeconds()}.
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
     * @return {@code true} when this modifier participates in the default computation layer, which
     * is typically used for base-value calculations shared across players or sessions.
     */
    public boolean isDefaultModifier() {
        return appliesToDefault;
    }

    /**
     * @return {@code true} when this modifier participates in the current computation layer, which
     * is commonly used for player-specific adjustments.
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
     * {@link #useMultiplierKeys()} is true. Keys are lowercased and deduplicated to keep matching
     * consistent across sources.
     */
    public Set<String> multiplierKeys() {
        return multiplierKeys;
    }

    /**
     * Exposes the optional duration as an {@link Optional}, preserving {@code null} to represent a
     * timeless modifier while still allowing callers to use fluent Optional APIs. Serialization and
     * persistence layers can rely on this to determine whether timers should be scheduled.
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
