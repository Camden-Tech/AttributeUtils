package me.baddcamden.attributeutils.model;

public record AttributeValueStages(double rawDefault,
                                   double defaultPermanent,
                                   double defaultFinal,
                                   double rawCurrent,
                                   double currentPermanent,
                                   double currentFinal) {
}
