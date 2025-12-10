package me.baddcamden.attributeutils.model;

import java.util.Objects;

/**
 * Immutable description of an attribute, including baseline defaults, cap behaviour and how
 * multiplier buckets should be filtered.
 *
 * <p>{@code defaultBaseValue} represents the configured baseline that is applied to both default
 * and current layers when an {@link AttributeInstance} is created. {@code defaultCurrentValue}
 * acts as the vanilla starting point for the "current" baseline before any player-specific
 * deltas are applied. Caps and multiplier applicability are shared by both layers.</p>
 */
public record AttributeDefinition(
        String id,
        String displayName,
        boolean dynamic,
        double defaultBaseValue,
        double defaultCurrentValue,
        CapConfig capConfig,
        MultiplierApplicability multiplierApplicability
) {

    public AttributeDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(capConfig, "capConfig");
        Objects.requireNonNull(multiplierApplicability, "multiplierApplicability");
    }

    /**
     * Creates an instance seeded with the default and current baselines and without a cap override
     * key. Callers can adjust the baselines or apply modifiers before passing the instance to the
     * computation engine.
     */
    public AttributeInstance newInstance() {
        return new AttributeInstance(this, defaultBaseValue, defaultCurrentValue, null);
    }
}
