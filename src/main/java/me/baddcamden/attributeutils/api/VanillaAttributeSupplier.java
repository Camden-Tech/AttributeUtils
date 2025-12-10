package me.baddcamden.attributeutils.api;

import org.bukkit.entity.Player;

/**
 * Supplies a player-specific vanilla baseline used as the starting point for calculations. Implementations should
 * return the player's current in-game value before any plugin-defined modifiers are applied.
 */
@FunctionalInterface
public interface VanillaAttributeSupplier {
    double getVanillaValue(Player player);
}
