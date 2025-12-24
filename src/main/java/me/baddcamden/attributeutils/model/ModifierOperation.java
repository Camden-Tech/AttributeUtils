package me.baddcamden.attributeutils.model;

/**
 * Supported modifier operation types used during staged attribute calculations. The operation
 * determines whether a modifier contributes to the additive subtotal or the multiplier product when
 * {@link me.baddcamden.attributeutils.compute.AttributeComputationEngine} evaluates a stage.
 */
public enum ModifierOperation {
    /**
     * Adds the modifier amount into the additive subtotal for a stage. Unscoped additives are
     * summed before the multiplier stack is applied, while scoped additives (see
     * {@link ModifierEntry#useMultiplierKeys()}) are multiplied only by the matching multipliers and
     * then added after the stage-wide multiplier product is applied.
     */
    ADD,

    /**
     * Multiplies into the stage multiplier product. Multipliers are compounded together and affect
     * unscoped additive subtotals, and optionally scoped additive entries when their keys intersect.
     */
    MULTIPLY
}
