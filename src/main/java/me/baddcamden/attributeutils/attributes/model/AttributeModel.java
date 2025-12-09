package me.baddcamden.attributeutils.attributes.model;

public class AttributeModel {

    private final String key;
    private double value;
    private double maxValue;
    private final boolean trackActualValue;

    public AttributeModel(String key, double value, double maxValue) {
        this(key, value, maxValue, false);
    }

    public AttributeModel(String key, double value, double maxValue, boolean trackActualValue) {
        this.key = key;
        this.value = value;
        this.maxValue = maxValue;
        this.trackActualValue = trackActualValue;
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

    public boolean tracksActualValue() {
        return trackActualValue;
    }

    public String getActualValueFieldKey() {
        return key + "_actual";
    }
}
