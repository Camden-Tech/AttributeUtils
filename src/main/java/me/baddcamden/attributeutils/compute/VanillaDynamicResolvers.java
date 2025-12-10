package me.baddcamden.attributeutils.compute;

import me.baddcamden.attributeutils.api.VanillaAttributeSupplier;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility methods for resolving dynamic vanilla attribute baselines from live player state.
 * Each helper attempts to read the Bukkit attribute instance first, then falls back to
 * computing contributions from equipped items when the attribute is unavailable.
 */
public final class VanillaDynamicResolvers {

    private VanillaDynamicResolvers() {
    }

    /**
     * Creates a {@link VanillaAttributeSupplier} for a dynamic vanilla attribute.
     *
     * @param attributeId the normalized attribute identifier
     * @param attribute   the Bukkit attribute backing the identifier
     * @param defaultBase the default base value configured for the attribute
     * @return a supplier that computes the live vanilla value for the attribute, or {@code null}
     * when the attribute is not dynamic
     */
    public static VanillaAttributeSupplier create(String attributeId, Attribute attribute, double defaultBase) {
        switch (attributeId) {
            case "attack_damage":
            case "attack_knockback":
            case "attack_speed":
                return player -> computeAttributeValue(player, attribute, defaultBase);
            case "armor":
                return player -> computeArmorValue(player, attribute, defaultBase);
            case "armor_toughness":
                return player -> computeAttributeValue(player, attribute, defaultBase);
            case "knockback_resistance":
                return player -> computeAttributeValue(player, attribute, defaultBase);
            default:
                return null;
        }
    }

    /**
     * Computes the vanilla attribute value using the attribute instance when available, falling
     * back to equipment contributions when necessary.
     */
    public static double computeAttributeValue(Player player, Attribute attribute, double defaultBase) {
        if (player != null && attribute != null) {
            org.bukkit.attribute.AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                return instance.getValue();
            }
        }

        return computeEquipmentAttribute(player, attribute, defaultBase);
    }

    private static double computeArmorValue(Player player, Attribute attribute, double defaultBase) {
        // Armor is entirely derived from equipment; prefer the attribute instance when present so
        // temporary vanilla modifiers (like Resistance effects) are still reflected.
        if (player != null && attribute != null) {
            org.bukkit.attribute.AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                return instance.getValue();
            }
        }
        return computeEquipmentAttribute(player, attribute, defaultBase);
    }

    private static double computeEquipmentAttribute(Player player, Attribute attribute, double fallback) {
        if (player == null || attribute == null) {
            return fallback;
        }

        org.bukkit.inventory.EntityEquipment equipment = player.getEquipment();
        if (equipment == null) {
            return fallback;
        }

        Map<EquipmentSlot, ItemStack> slots = new HashMap<>();
        slots.put(EquipmentSlot.HAND, equipment.getItemInMainHand());
        slots.put(EquipmentSlot.OFF_HAND, equipment.getItemInOffHand());
        slots.put(EquipmentSlot.HEAD, equipment.getHelmet());
        slots.put(EquipmentSlot.CHEST, equipment.getChestplate());
        slots.put(EquipmentSlot.LEGS, equipment.getLeggings());
        slots.put(EquipmentSlot.FEET, equipment.getBoots());

        double additive = 0d;
        double multiplicative = 1d;

        for (Map.Entry<EquipmentSlot, ItemStack> entry : slots.entrySet()) {
            ItemStack item = entry.getValue();
            if (item == null) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                continue;
            }

            Iterable<AttributeModifier> modifiers = meta.getAttributeModifiers(attribute);
            if (modifiers == null) {
                continue;
            }

            for (AttributeModifier modifier : modifiers) {
                EquipmentSlot slot = modifier.getSlot();
                if (slot != null && slot != entry.getKey()) {
                    continue;
                }
                switch (modifier.getOperation()) {
                    case ADD_NUMBER -> additive += modifier.getAmount();
                    default -> multiplicative *= 1 + modifier.getAmount();
                }
            }
        }

        return (fallback + additive) * multiplicative;
    }
}
