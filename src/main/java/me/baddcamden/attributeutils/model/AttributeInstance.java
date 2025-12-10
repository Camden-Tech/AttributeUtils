package me.baddcamden.attributeutils.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class AttributeInstance {

    private final AttributeDefinition definition;
    private double defaultBaseValue;
    private double currentBaseValue;
    private final Map<String, ModifierEntry> modifiers = new LinkedHashMap<>();
    private final Map<String, ModifierEntry> defaultPermanentAdditives = new LinkedHashMap<>();
    private final Map<String, ModifierEntry> defaultTemporaryAdditives = new LinkedHashMap<>();
    private final Map<String, ModifierEntry> defaultPermanentMultipliers = new LinkedHashMap<>();
    private final Map<String, ModifierEntry> defaultTemporaryMultipliers = new LinkedHashMap<>();
    private final Map<String, ModifierEntry> currentPermanentAdditives = new LinkedHashMap<>();
    private final Map<String, ModifierEntry> currentTemporaryAdditives = new LinkedHashMap<>();
    private final Map<String, ModifierEntry> currentPermanentMultipliers = new LinkedHashMap<>();
    private final Map<String, ModifierEntry> currentTemporaryMultipliers = new LinkedHashMap<>();
    private String capOverrideKey;

    public AttributeInstance(AttributeDefinition definition) {
        this(definition, definition.defaultBaseValue(), definition.defaultCurrentValue(), null);
    }

    public AttributeInstance(AttributeDefinition definition, double defaultBaseValue, double currentBaseValue, String capOverrideKey) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.defaultBaseValue = defaultBaseValue;
        this.currentBaseValue = currentBaseValue;
        this.capOverrideKey = capOverrideKey;
    }

    public AttributeDefinition getDefinition() {
        return definition;
    }

    public double getDefaultBaseValue() {
        return defaultBaseValue;
    }

    public void setDefaultBaseValue(double defaultBaseValue) {
        this.defaultBaseValue = defaultBaseValue;
    }

    public double getBaseValue() {
        return defaultBaseValue;
    }

    public void setBaseValue(double baseValue) {
        this.defaultBaseValue = baseValue;
        this.currentBaseValue = baseValue;
    }

    public double getCurrentBaseValue() {
        return currentBaseValue;
    }

    public void setCurrentBaseValue(double currentBaseValue) {
        this.currentBaseValue = currentBaseValue;
    }

    public Map<String, ModifierEntry> getModifiers() {
        return Map.copyOf(modifiers);
    }

    public Map<String, ModifierEntry> getDefaultPermanentAdditives() {
        return Map.copyOf(defaultPermanentAdditives);
    }

    public Map<String, ModifierEntry> getDefaultTemporaryAdditives() {
        return Map.copyOf(defaultTemporaryAdditives);
    }

    public Map<String, ModifierEntry> getDefaultPermanentMultipliers() {
        return Map.copyOf(defaultPermanentMultipliers);
    }

    public Map<String, ModifierEntry> getDefaultTemporaryMultipliers() {
        return Map.copyOf(defaultTemporaryMultipliers);
    }

    public Map<String, ModifierEntry> getCurrentPermanentAdditives() {
        return Map.copyOf(currentPermanentAdditives);
    }

    public Map<String, ModifierEntry> getCurrentTemporaryAdditives() {
        return Map.copyOf(currentTemporaryAdditives);
    }

    public Map<String, ModifierEntry> getCurrentPermanentMultipliers() {
        return Map.copyOf(currentPermanentMultipliers);
    }

    public Map<String, ModifierEntry> getCurrentTemporaryMultipliers() {
        return Map.copyOf(currentTemporaryMultipliers);
    }

    public void addModifier(ModifierEntry modifier) {
        Objects.requireNonNull(modifier, "modifier");
        String key = modifier.key().toLowerCase();
        removeModifier(key);
        modifiers.put(key, modifier);
        addToBucket(key, modifier, true);
        addToBucket(key, modifier, false);
    }

    public void removeModifier(String key) {
        if (key == null) {
            return;
        }
        String normalized = key.toLowerCase();
        modifiers.remove(normalized);
        removeFromBuckets(normalized);
    }

    public void purgeTemporaryModifiers() {
        modifiers.entrySet().removeIf(entry -> {
            if (entry.getValue().isTemporary()) {
                removeFromBuckets(entry.getKey());
                return true;
            }
            return false;
        });
        defaultTemporaryAdditives.clear();
        defaultTemporaryMultipliers.clear();
        currentTemporaryAdditives.clear();
        currentTemporaryMultipliers.clear();
    }

    public String getCapOverrideKey() {
        return capOverrideKey;
    }

    public void setCapOverrideKey(String capOverrideKey) {
        this.capOverrideKey = capOverrideKey;
    }

    private void addToBucket(String key, ModifierEntry modifier, boolean defaultLayer) {
        if (defaultLayer && !modifier.appliesToDefault()) {
            return;
        }
        if (!defaultLayer && !modifier.appliesToCurrent()) {
            return;
        }

        Map<String, ModifierEntry> target = resolveBucket(modifier, defaultLayer);
        target.put(key, modifier);
    }

    private Map<String, ModifierEntry> resolveBucket(ModifierEntry modifier, boolean defaultLayer) {
        boolean temporary = modifier.isTemporary();
        if (modifier.operation() == ModifierOperation.ADD) {
            if (defaultLayer) {
                return temporary ? defaultTemporaryAdditives : defaultPermanentAdditives;
            }
            return temporary ? currentTemporaryAdditives : currentPermanentAdditives;
        }

        if (defaultLayer) {
            return temporary ? defaultTemporaryMultipliers : defaultPermanentMultipliers;
        }
        return temporary ? currentTemporaryMultipliers : currentPermanentMultipliers;
    }

    private void removeFromBuckets(String key) {
        defaultPermanentAdditives.remove(key);
        defaultTemporaryAdditives.remove(key);
        defaultPermanentMultipliers.remove(key);
        defaultTemporaryMultipliers.remove(key);
        currentPermanentAdditives.remove(key);
        currentTemporaryAdditives.remove(key);
        currentPermanentMultipliers.remove(key);
        currentTemporaryMultipliers.remove(key);
    }
}
