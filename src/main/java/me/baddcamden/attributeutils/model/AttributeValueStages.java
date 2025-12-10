package me.baddcamden.attributeutils.model;

/**
 * Snapshot of the values produced by {@code AttributeComputationEngine}. Each pair describes the
 * state of an attribute after a stage in the pipeline:
 * <ul>
 *     <li>{@code rawDefault}: default baseline after caps, before modifiers.</li>
 *     <li>{@code defaultPermanent}: default baseline after permanent modifiers.</li>
 *     <li>{@code defaultFinal}: default baseline after temporary modifiers.</li>
 *     <li>{@code rawCurrent}: current baseline aligned with the default final value.</li>
 *     <li>{@code currentPermanent}: current value after permanent current modifiers.</li>
 *     <li>{@code currentFinal}: final value after all current modifiers.</li>
 * </ul>
 */
public record AttributeValueStages(double rawDefault,
                                   double defaultPermanent,
                                   double defaultFinal,
                                   double rawCurrent,
                                   double currentPermanent,
                                   double currentFinal) {

    /**
     * Explicit accessors keep the record compatible with callers that expect concrete getters,
     * ensuring method resolution succeeds even if record component inference changes.
     */
    @Override
    public double rawDefault() {
        return rawDefault;
    }

    @Override
    public double rawCurrent() {
        return rawCurrent;
    }
}
