package me.baddcamden.attributeutils.model;

import java.util.Objects;

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

    public AttributeInstance newInstance() {
        return new AttributeInstance(this, defaultBaseValue, defaultCurrentValue, null);
    }
}
