package me.baddcamden.attributeutils.service;

import me.baddcamden.attributeutils.api.AttributeDefinition;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class AttributeService {

    private final Map<String, AttributeDefinition> attributes = new HashMap<>();
    private final Map<String, Map<String, AttributeDefinition>> entityDefaults = new HashMap<>();

    public void registerAttribute(AttributeDefinition attribute) {
        attributes.put(attribute.normalizedKey(), attribute);
    }

    public Optional<AttributeDefinition> getAttribute(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(attributes.get(key.toLowerCase(Locale.ROOT)));
    }

    public Map<String, AttributeDefinition> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public Map<String, AttributeDefinition> getEntityDefaults(String customEntityId) {
        return Collections.unmodifiableMap(entityDefaults.getOrDefault(customEntityId.toLowerCase(Locale.ROOT), Collections.emptyMap()));
    }

    public void registerEntityDefaults(String customEntityId, Map<String, AttributeDefinition> defaults) {
        Map<String, AttributeDefinition> normalizedDefaults = new HashMap<>();
        defaults.forEach((key, value) -> normalizedDefaults.put(key.toLowerCase(Locale.ROOT), value));

        entityDefaults.put(customEntityId.toLowerCase(Locale.ROOT), normalizedDefaults);
    }

    public void clearAttributes() {
        attributes.clear();
        entityDefaults.clear();
    }
}
