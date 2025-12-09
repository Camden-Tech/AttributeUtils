package me.baddcamden.attributeutils.service;

import me.baddcamden.attributeutils.model.AttributeDefinition;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class AttributeService {

    private final Map<String, AttributeModel> attributes = new HashMap<>();
    private final Map<String, Map<String, AttributeModel>> entityDefaults = new HashMap<>();

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
