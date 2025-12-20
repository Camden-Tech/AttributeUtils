package me.baddcamden.attributeutils.persistence;

/**
 * Represents the persisted state of a resource meter (such as hunger).
 * Both the current value and the configured maximum are stored so the meter can
 * be reconstructed relative to the cap that was in effect when it was saved.
 */
public record ResourceMeterState(double current, double max) {
}
