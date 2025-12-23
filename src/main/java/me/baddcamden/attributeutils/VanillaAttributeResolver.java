package me.baddcamden.attributeutils;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Utility methods for extracting vanilla (non-AttributeUtils) values from Bukkit attribute instances.
 * Plugin-authored modifiers are filtered out by name prefix to avoid double-counting when the computation
 * pipeline applies its own transient modifiers.
 */
public final class VanillaAttributeResolver {

    private static final String ATTRIBUTEUTILS_PREFIX = "attributeutils:";

    private VanillaAttributeResolver() {
    }

    /**
     * Resolves the vanilla value for the provided attribute instance by recomputing the base value plus
     * non-AttributeUtils modifiers. The calculation mirrors the order used by the game:
     * <ul>
     *     <li>Base value plus {@link AttributeModifier.Operation#ADD_NUMBER}.</li>
     *     <li>Base-scaled {@link AttributeModifier.Operation#ADD_SCALAR} contributions.</li>
     *     <li>{@link AttributeModifier.Operation#MULTIPLY_SCALAR_1} applied multiplicatively.</li>
     * </ul>
     *
     * @param instance attribute instance to inspect; when null the fallback is returned.
     * @param fallback value to return when no instance is available.
     * @return vanilla attribute value without AttributeUtils modifiers.
     */
    public static double resolveVanillaValue(AttributeInstance instance, double fallback) {
        if (instance == null) {
            return fallback;
        }

        double baseValue = instance.getBaseValue();
        double additive = 0.0d;
        double scalar = 0.0d;
        double multiplier = 1.0d;

        for (AttributeModifier modifier : instance.getModifiers()) {
            if (isPluginModifier(modifier)) {
                continue;
            }
            switch (modifier.getOperation()) {
                case ADD_NUMBER -> additive += modifier.getAmount();
                case ADD_SCALAR -> scalar += modifier.getAmount();
                case MULTIPLY_SCALAR_1 -> multiplier *= 1 + modifier.getAmount();
            }
        }

        return (baseValue + additive + (baseValue * scalar)) * multiplier;
    }

    /**
     * Resolves the vanilla value for a player attribute, delegating to {@link #resolveVanillaValue(AttributeInstance, double)}
     * and falling back to the provided supplier when the attribute is not present.
     *
     * @param player               player that owns the attribute.
     * @param attribute            Bukkit attribute to resolve.
     * @param fallback             value returned when the player or attribute is not available.
     * @param equipmentComputation computation used when the player lacks a live attribute instance.
     * @return vanilla attribute value without AttributeUtils modifiers.
     */
    public static double resolvePlayerAttribute(Player player,
                                                Attribute attribute,
                                                double fallback,
                                                java.util.function.DoubleSupplier equipmentComputation) {
        if (player == null || attribute == null) {
            return fallback;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            return resolveVanillaValue(instance, fallback);
        }
        return equipmentComputation == null ? fallback : equipmentComputation.getAsDouble();
    }

    static boolean isPluginModifier(AttributeModifier modifier) {
        if (modifier == null) {
            return false;
        }
        String name = modifier.getName();
        return name != null && name.toLowerCase(Locale.ROOT).startsWith(ATTRIBUTEUTILS_PREFIX);
    }
}
