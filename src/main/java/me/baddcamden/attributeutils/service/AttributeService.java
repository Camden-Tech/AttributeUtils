package me.baddcamden.attributeutils.service;

import me.baddcamden.attributeutils.model.AttributeDefinition;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class AttributeService {

    private final Map<String, AttributeDefinition> attributes = new HashMap<>();

    public void registerAttribute(AttributeDefinition attribute) {
        attributes.put(attribute.id().toLowerCase(Locale.ROOT), attribute);
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

    public void clearAttributes() {
        attributes.clear();
    }
}
