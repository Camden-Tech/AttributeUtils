package me.baddcamden.attributeutils.persistence;

import java.util.UUID;

/**
 * Contract for components that can supply and hydrate persisted {@link ResourceMeterState} data
 * for players. Implementations are responsible for applying any necessary clamping when
 * reconstructing meters from persisted values.
 */
public interface ResourceMeterStore {

    /**
     * Restores persisted meter states for the given player. Implementations may choose to ignore
     * {@code null} states to leave existing in-memory values intact.
     */
    void hydrateMeters(UUID playerId, ResourceMeterState hunger, ResourceMeterState oxygen);

    /**
     * @return the current hunger meter state for the given player, or {@code null} if none is tracked.
     */
    ResourceMeterState getHungerMeter(UUID playerId);

    /**
     * @return the current oxygen meter state for the given player, or {@code null} if none is tracked.
     */
    ResourceMeterState getOxygenMeter(UUID playerId);
}
