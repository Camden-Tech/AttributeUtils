package me.baddcamden.attributeutils.api;

import org.bukkit.entity.Player;

/**
 * Supplies a player-specific vanilla baseline used as the starting point for calculations. Implementations are
 * only consulted for <em>dynamic</em> attributes; static attributes rely solely on their configured baselines.
 * <p>
 * Implementations should source their values directly from Bukkit-provided state (for example, armor points,
 * movement speed, or hunger) <strong>before</strong> any plugin-defined modifiers are applied. The returned value
 * is injected as the current baseline and is subsequently adjusted by player/global base overrides and all
 * registered modifiers. Callers should avoid returning capped values because capping is handled later by the
 * computation engine.
 */
@FunctionalInterface
public interface VanillaAttributeSupplier {
    /**
     * Returns the live vanilla value for the provided player prior to any plugin adjustments. The player instance is
     * guaranteed to be non-null when invoked for dynamic attributes; static attributes skip the supplier entirely.
     *
     * @param player player whose current vanilla state should be converted into a baseline value.
     * @return raw vanilla value to use as the current baseline for computation.
     */
    double getVanillaValue(Player player);
}
