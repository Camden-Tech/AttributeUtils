package me.baddcamden.attributeutils.service;

import me.baddcamden.attributeutils.attributes.model.AttributeInstance;
import me.baddcamden.attributeutils.attributes.model.AttributeModel;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AttributeService {

    private final Map<String, AttributeInstance> attributes = new HashMap<>();

    public void registerAttribute(AttributeModel attribute) {
        attributes.put(attribute.getKey().toLowerCase(), new AttributeInstance(attribute));
    }

    public Optional<AttributeInstance> getAttribute(String key) {
        return Optional.ofNullable(attributes.get(key.toLowerCase()));
    }

    public Map<String, AttributeInstance> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public void clearAttributes() {
        attributes.clear();
    }
}
