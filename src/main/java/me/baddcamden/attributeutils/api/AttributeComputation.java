package me.baddcamden.attributeutils.api;

/**
 * Immutable summary of a single attribute calculation performed by {@link AttributeApi}.
 * <p>
 * Values are separated into stages so callers can display or audit how an attribute was produced:
 * <ul>
 *     <li>{@code vanillaBaseline}: player-aware supplier result before any configured base value is applied.</li>
 *     <li>{@code baseValue}: the definition's configured default baseline.</li>
 *     <li>{@code globalModifierTotal} / {@code playerModifierTotal}: sums of the respective modifier buckets; these
 *     totals already include both temporary and permanent entries.</li>
 *     <li>{@code finalValue}: {@code baseValue + modifiers} after clamping to {@code cap}.</li>
 * </ul>
 */
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
