package me.baddcamden.attributeutils.attributes.model;

import me.baddcamden.attributeutils.api.AttributeDefinition;
import me.baddcamden.attributeutils.service.AttributeService;

import java.util.Locale;
import java.util.Optional;

public class AttributeInstance {

    private final String key;
    private final double value;
    private final Double maxValue;
    private final AttributeInstanceType type;
    private final AttributeTrigger trigger;

    private AttributeInstance(String key, double value, Double maxValue, AttributeInstanceType type, AttributeTrigger trigger) {
        this.key = key.toLowerCase(Locale.ROOT);
        this.value = value;
        this.maxValue = maxValue;
        this.type = type;
        this.trigger = trigger;
    }

    public static AttributeInstance definition(String key, double value, Double maxValue, AttributeTrigger trigger) {
        return new AttributeInstance(key, value, maxValue, AttributeInstanceType.DEFINITION, trigger);
    }

    public static AttributeInstance modifier(String key, double value, AttributeTrigger trigger) {
        return new AttributeInstance(key, value, null, AttributeInstanceType.MODIFIER, trigger);
    }

    public String getKey() {
        return key;
    }

    public double getValue() {
        return value;
    }

    public Double getMaxValue() {
        return maxValue;
    }

    public AttributeInstanceType getType() {
        return type;
    }

    public AttributeTrigger getTrigger() {
        return trigger;
    }

    public void apply(AttributeService attributeService) {
        Optional<AttributeDefinition> existing = attributeService.getAttribute(key);

        if (type == AttributeInstanceType.DEFINITION) {
            double resolvedMax = maxValue != null ? maxValue : value;
            attributeService.registerAttribute(new AttributeDefinition(key, value, resolvedMax));
            return;
        }

        if (existing.isPresent()) {
            AttributeDefinition model = existing.get();
            attributeService.registerAttribute(new AttributeDefinition(model.key(), model.baseValue() + value, model.maxValue()));
        } else {
            attributeService.registerAttribute(new AttributeDefinition(key, value, Double.MAX_VALUE));
        }
    }
}
