package me.baddcamden.attributeutils.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class AttributeInstance {

    private final AttributeDefinition definition;
    private double baseValue;
    private double currentValue;
    private final Map<String, ModifierEntry> modifiers = new LinkedHashMap<>();

    public AttributeInstance(AttributeDefinition definition) {
        this(definition, definition.defaultBaseValue(), definition.defaultCurrentValue());
    }

    public AttributeInstance(AttributeDefinition definition, double baseValue, double currentValue) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.baseValue = baseValue;
        this.currentValue = definition.capConfig().clamp(currentValue);
    }

    public AttributeDefinition getDefinition() {
        return definition;
    }

    public double getBaseValue() {
        return baseValue;
    }

    public void setBaseValue(double baseValue) {
        this.baseValue = baseValue;
        recalculate();
    }

    public double getCurrentValue() {
        return currentValue;
    }

    public Map<String, ModifierEntry> getModifiers() {
        return Map.copyOf(modifiers);
    }

    public void addModifier(ModifierEntry modifier) {
        Objects.requireNonNull(modifier, "modifier");
        modifiers.put(modifier.key().toLowerCase(), modifier);
        recalculate();
    }

    public void removeModifier(String key) {
        if (key == null) {
            return;
        }
        modifiers.remove(key.toLowerCase());
        recalculate();
    }

    public void purgeTemporaryModifiers() {
        modifiers.values().removeIf(ModifierEntry::isTemporary);
        recalculate();
    }

    private void recalculate() {
        double value = baseValue;
        double multiplier = 1.0d;
        for (ModifierEntry modifier : modifiers.values()) {
            if (modifier.operation() == ModifierOperation.ADD) {
                value += modifier.amount();
                continue;
            }

            if (definition.multiplierApplicability().canApply(modifier.key())) {
                multiplier *= modifier.amount();
            }
        }

        currentValue = definition.capConfig().clamp(value * multiplier);
    }
}
