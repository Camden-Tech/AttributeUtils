package me.baddcamden.attributeutils.api;

import java.util.Locale;
import java.util.Objects;

/**
 * Definition used by {@link AttributeApi} to describe an attribute's boundaries and default baselines.
 * <p>
 * {@code baseValue} represents the plugin-configured default baseline used when no vanilla supplier is registered,
 * while {@code maxValue} acts as a hard cap applied after all modifiers have been summed. Keys are normalized to
 * lowercase for consistent storage.
 */
public record AttributeDefinition(String key, double baseValue, double maxValue) {

    public AttributeDefinition {
        Objects.requireNonNull(key, "Attribute key must not be null");
    }

    public String normalizedKey() {
        return key.toLowerCase(Locale.ROOT);
    }
}
