package me.baddcamden.attributeutils.api;

public record AttributeComputation(
        String key,
        double vanillaBaseline,
        double baseValue,
        double globalModifierTotal,
        double playerModifierTotal,
        double finalValue,
        double cap
) {

    public double combinedModifiers() {
        return globalModifierTotal + playerModifierTotal;
    }
}
