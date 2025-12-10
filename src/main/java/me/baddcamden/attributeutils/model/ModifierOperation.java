package me.baddcamden.attributeutils.model;

/**
 * Supported modifier operation types. Additive entries are summed (after optional multiplier
 * stacking) and applied after the base and multiplier product, while multiplicative entries are
 * multiplied together to produce the stage multiplier.
 */
public enum ModifierOperation {
    ADD,
    MULTIPLY
}
