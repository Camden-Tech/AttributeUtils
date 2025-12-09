package me.baddcamden.attributeutils.model;

public record AttributeValueStages(double rawDefault,
                                   double defaultOnly,
                                   double defaultFinal,
                                   double rawCurrent,
                                   double currentPermanent,
                                   double currentFinal) {
}
