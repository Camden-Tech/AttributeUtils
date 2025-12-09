package me.baddcamden.attributeutils.attributes.model;

public class AttributeInstance {

    private final AttributeModel model;
    private double actualValue;
    private double actualMaxValue;

    public AttributeInstance(AttributeModel model) {
        this.model = model;
        this.actualValue = model.getValue();
        this.actualMaxValue = model.getMaxValue();
    }

    public String getKey() {
        return model.getKey();
    }

    public AttributeModel getModel() {
        return model;
    }

    public double getValue() {
        return model.getValue();
    }

    public void setValue(double value) {
        model.setValue(value);
    }

    public double getMaxValue() {
        return model.getMaxValue();
    }

    public void setMaxValue(double maxValue) {
        model.setMaxValue(maxValue);
        setActualMaxValue(maxValue);
    }

    public double getActualValue() {
        return actualValue;
    }

    public void setActualValue(double actualValue) {
        this.actualValue = Math.min(actualValue, actualMaxValue);
    }

    public double getActualMaxValue() {
        return actualMaxValue;
    }

    public void setActualMaxValue(double actualMaxValue) {
        this.actualMaxValue = actualMaxValue;
        setActualValue(actualValue);
    }

    public boolean tracksActualValue() {
        return model.tracksActualValue();
    }

    public String getActualValueFieldKey() {
        return model.getActualValueFieldKey();
    }
}
