package me.baddcamden.attributeutils.model;

public record AttributeValueStages(double defaultBase,
                                   double defaultPermanent,
                                   double defaultFinal,
                                   double currentBase,
                                   double currentPermanent,
                                   double currentFinal) {
}
