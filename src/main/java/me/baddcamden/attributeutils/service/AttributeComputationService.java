package me.baddcamden.attributeutils.service;

import me.baddcamden.attributeutils.attributes.model.AttributeInstance;

public class AttributeComputationService {

    private static final int VANILLA_HUNGER_MAX = 20;
    private static final int VANILLA_OXYGEN_MAX = 20;

    public double fromHungerBar(int barValue, AttributeInstance attribute) {
        return fromVanillaBar(barValue, VANILLA_HUNGER_MAX, attribute.getActualMaxValue());
    }

    public int toHungerBar(AttributeInstance attribute) {
        return toVanillaBar(attribute.getActualValue(), attribute.getActualMaxValue(), VANILLA_HUNGER_MAX);
    }

    public double fromOxygenBar(int barValue, AttributeInstance attribute) {
        return fromVanillaBar(barValue, VANILLA_OXYGEN_MAX, attribute.getActualMaxValue());
    }

    public int toOxygenBar(AttributeInstance attribute) {
        return toVanillaBar(attribute.getActualValue(), attribute.getActualMaxValue(), VANILLA_OXYGEN_MAX);
    }

    private double fromVanillaBar(int barValue, int vanillaMax, double actualMax) {
        if (actualMax <= 0) {
            return 0;
        }
        double percentage = Math.max(0, Math.min(barValue, vanillaMax)) / (double) vanillaMax;
        return percentage * actualMax;
    }

    private int toVanillaBar(double actualValue, double actualMax, int vanillaMax) {
        if (actualMax <= 0) {
            return 0;
        }
        double percentage = Math.max(0, Math.min(actualValue, actualMax)) / actualMax;
        return (int) Math.round(percentage * vanillaMax);
    }
}
