package me.baddcamden.attributeutils.attributes.model;

public class AttributeModel {

    private final String key;
    private double value;
    private double maxValue;

    public AttributeModel(String key, double value, double maxValue) {
        this.key = key;
        this.value = value;
        this.maxValue = maxValue;
    }

    public String getKey() {
        return key;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = Math.min(value, maxValue);
    }

    public double getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(double maxValue) {
        this.maxValue = maxValue;
        setValue(value);
    }
}
