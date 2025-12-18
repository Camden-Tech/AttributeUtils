package me.baddcamden.attributeutils.service;

import me.baddcamden.attributeutils.model.AttributeDefinition;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Registry for public attribute definitions. The service normalizes keys so downstream callers can
 * reliably look up attributes when constructing {@code AttributeInstance} baselines or applying
 * modifier buckets. Entity defaults allow plugins to supply a pre-baked set of definitions for a
 * custom entity type; these defaults can then be merged with global definitions during computation
 * or persistence workflows.
 */
public class AttributeService {

    private final Map<String, AttributeDefinition> attributes = new HashMap<>();
    private final Map<String, Map<String, AttributeDefinition>> entityDefaults = new HashMap<>();

    /**
     * Registers a definition into the service using its normalized key so lookups remain case and
     * delimiter insensitive.
     */
    public void registerAttribute(AttributeDefinition attribute) {
        attributes.put(attribute.id().toLowerCase(Locale.ROOT), attribute);
    }

    /**
     * Retrieves an attribute definition by key. Returns empty for null keys to match external
     * callers that may probe before creating new baselines.
     */
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

    /**
     * Registers a per-entity default set, normalizing keys so consumers can merge these definitions
     * with global attributes without worrying about casing. Downstream systems decide whether
     * entity defaults replace or extend global baselines when computing attribute stages.
     */
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
