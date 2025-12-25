package me.baddcamden.attributeutils;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.function.DoubleSupplier;

/**
 * Utility methods for extracting vanilla (non-AttributeUtils) values from Bukkit attribute instances.
 * Plugin-authored modifiers are filtered out by name prefix to avoid double-counting when the computation
 * pipeline applies its own transient modifiers.
 */
public final class VanillaAttributeResolver {

    /**
     * Prefix used by AttributeUtils-authored modifiers so they can be ignored when reproducing
     * vanilla calculations.
     */
    public static final String ATTRIBUTEUTILS_PREFIX = "attributeutils:";
    /**
     * Legacy prefix used by earlier integrations that failed to use the colon separator. Modifiers using this prefix
     * are removed during refresh so that only properly namespaced entries remain.
     */
    private static final String LEGACY_ATTRIBUTEUTILS_PREFIX = "attributeutils.";

    private VanillaAttributeResolver() {
    }

    /**
     * Resolves the vanilla value for the provided attribute instance by recomputing the base value plus
     * non-AttributeUtils modifiers. The calculation mirrors the order used by the game so that the
     * final number matches what vanilla would expose:
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

        scrubLegacyPluginModifiers(instance);

        double baseValue = instance.getBaseValue();
        double additive = 0.0d;
        double scalar = 0.0d;
        double multiplier = 1.0d;

        for (AttributeModifier modifier : instance.getModifiers()) {
            if (isPluginModifier(modifier)) {
                continue;
            }
            // Separating operations keeps parity with Bukkit's aggregation order.
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
     * when a live attribute instance exists and otherwise using the provided supplier for equipment-driven values
     * (for example, during login before attributes are hydrated).
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
                                                DoubleSupplier equipmentComputation) {
        if (player == null || attribute == null) {
            return fallback;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            return resolveVanillaValue(instance, fallback);
        }
        //VAGUE/IMPROVEMENT NEEDED clarify whether the equipment supplier should factor in plugin-created
        // modifiers or only items native to vanilla when no attribute instance exists.
        return equipmentComputation == null ? fallback : equipmentComputation.getAsDouble();
    }

    /**
     * Removes legacy AttributeUtils modifiers that do not use the colon prefix so that subsequent refreshes can
     * reapply correctly namespaced entries. This cleanup prevents double-counting during vanilla reconstruction and
     * keeps stored state aligned with {@link #ATTRIBUTEUTILS_PREFIX}.
     *
     * @param instance attribute instance to purge legacy AttributeUtils modifiers from
     */
    public static void scrubLegacyPluginModifiers(AttributeInstance instance) {
        if (instance == null) {
            return;
        }

        for (AttributeModifier modifier : new java.util.ArrayList<>(instance.getModifiers())) {
            String name = modifier.getName();
            if (name == null) {
                continue;
            }

            String normalized = name.toLowerCase(Locale.ROOT);
            if (normalized.startsWith(ATTRIBUTEUTILS_PREFIX)) {
                continue;
            }

            if (normalized.startsWith(LEGACY_ATTRIBUTEUTILS_PREFIX) || normalized.startsWith("attributeutils")) {
                instance.removeModifier(modifier);
            }
        }
    }

    /**
     * Detects whether the provided modifier originated from AttributeUtils by checking the name prefix.
     * This is used to prevent plugin-added modifiers from being included when recreating vanilla-only values.
     *
     * @param modifier modifier to inspect; null yields {@code false}.
     * @return {@code true} when the modifier name starts with the AttributeUtils prefix (case-insensitive).
     */
    static boolean isPluginModifier(AttributeModifier modifier) {
        if (modifier == null) {
            return false;
        }
        String name = modifier.getName();
        return name != null && name.toLowerCase(Locale.ROOT).startsWith(ATTRIBUTEUTILS_PREFIX);
    }
}
