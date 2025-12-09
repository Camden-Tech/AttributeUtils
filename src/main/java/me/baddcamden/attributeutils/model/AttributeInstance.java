package me.baddcamden.attributeutils.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class AttributeInstance {

    private final AttributeDefinition definition;
    private double baseValue;
    private final Map<String, ModifierEntry> modifiers = new LinkedHashMap<>();
    private String capOverrideKey;

    public AttributeInstance(AttributeDefinition definition) {
        this(definition, definition.defaultBaseValue(), null);
    }

    public AttributeInstance(AttributeDefinition definition, double baseValue, String capOverrideKey) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.baseValue = baseValue;
        this.capOverrideKey = capOverrideKey;
    }

    public AttributeDefinition getDefinition() {
        return definition;
    }

    public double getBaseValue() {
        return baseValue;
    }

    public void setBaseValue(double baseValue) {
        this.baseValue = baseValue;
    }

    public Map<String, ModifierEntry> getModifiers() {
        return Map.copyOf(modifiers);
    }

    public void addModifier(ModifierEntry modifier) {
        Objects.requireNonNull(modifier, "modifier");
        modifiers.put(modifier.key().toLowerCase(), modifier);
    }

    public void removeModifier(String key) {
        if (key == null) {
            return;
        }
        modifiers.remove(key.toLowerCase());
    }

    public void purgeTemporaryModifiers() {
        modifiers.values().removeIf(ModifierEntry::isTemporary);
    }

    public String getCapOverrideKey() {
        return capOverrideKey;
    }

    public void setCapOverrideKey(String capOverrideKey) {
        this.capOverrideKey = capOverrideKey;
    }
}
