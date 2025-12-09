package me.baddcamden.attributeutils.api;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface VanillaAttributeSupplier {
    double getVanillaValue(Player player);
}
