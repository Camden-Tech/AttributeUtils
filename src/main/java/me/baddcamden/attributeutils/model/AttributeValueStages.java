package me.baddcamden.attributeutils.model;

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
