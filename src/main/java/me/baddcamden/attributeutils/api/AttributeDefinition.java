package me.baddcamden.attributeutils.api;

import java.util.Locale;
import java.util.Objects;

public record AttributeDefinition(String key, double baseValue, double maxValue) {

    public AttributeDefinition {
        Objects.requireNonNull(key, "Attribute key must not be null");
    }

    public String normalizedKey() {
        return key.toLowerCase(Locale.ROOT);
    }
}
