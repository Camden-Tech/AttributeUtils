package me.baddcamden.attributeutils.model;

/**
 * Snapshot of the values produced by {@code AttributeComputationEngine}. Each component captures
 * the running total after a discrete stage in the computation pipeline, starting from capped
 * baselines and ending with every modifier applied.
 *
 * @param rawDefault        capped default baseline before any modifiers are considered.
 * @param defaultPermanent  default baseline after permanent modifiers are applied in isolation.
 * @param defaultFinal      default baseline after all default modifiers (permanent and temporary).
 * @param rawCurrent        current baseline after synchronization with the default final value for
 *                          static attributes or after vanilla resolution for dynamic attributes.
 * @param currentPermanent  current baseline after only permanent current modifiers.
 * @param currentFinal      fully resolved current value after all current modifiers have been
 *                          applied.
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

    /**
     * Returns the current baseline that will later feed into permanent and temporary current
     * modifiers. This value is already clamped and, for static attributes, synchronized with the
     * default stage to avoid drift between the two tracks.
     */
    @Override
    public double rawCurrent() {
        return rawCurrent;
    }
}
