package me.baddcamden.attributeutils.service;

import me.baddcamden.attributeutils.attributes.model.AttributeModel;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AttributeService {

    private final Map<String, AttributeModel> attributes = new HashMap<>();
    private final Map<String, Map<String, AttributeModel>> entityDefaults = new HashMap<>();

    public void registerAttribute(AttributeModel attribute) {
        attributes.put(attribute.getKey().toLowerCase(), attribute);
    }

    public Optional<AttributeModel> getAttribute(String key) {
        return Optional.ofNullable(attributes.get(key.toLowerCase()));
    }

    public Map<String, AttributeModel> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public Map<String, AttributeModel> getEntityDefaults(String customEntityId) {
        return Collections.unmodifiableMap(entityDefaults.getOrDefault(customEntityId.toLowerCase(), Collections.emptyMap()));
    }

    public void registerEntityDefaults(String customEntityId, Map<String, AttributeModel> defaults) {
        entityDefaults.put(customEntityId.toLowerCase(), new HashMap<>(defaults));
    }

    public void clearAttributes() {
        attributes.clear();
        entityDefaults.clear();
    }
}
