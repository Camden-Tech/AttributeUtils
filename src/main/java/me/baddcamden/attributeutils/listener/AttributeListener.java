package me.baddcamden.attributeutils.listener;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.handler.entity.EntityAttributeHandler;
import me.baddcamden.attributeutils.handler.item.ItemAttributeHandler;
import me.baddcamden.attributeutils.persistence.AttributePersistence;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Listens for player lifecycle and attribute-related events to keep persisted data in sync and enforce
 * calculated attribute limits. The listener ensures player data is loaded and saved during join/quit,
 * applies default item attributes to new inventories, and clamps hunger and oxygen changes to their
 * computed caps so downstream handlers do not observe impossible values.
 */
public class AttributeListener implements Listener {

    private static final long CAP_CACHE_TTL_NANOS = 250_000_000L;

    private final AttributeFacade attributeFacade;
    private final AttributePersistence persistence;
    private final ItemAttributeHandler itemAttributeHandler;
    private final EntityAttributeHandler entityAttributeHandler;
    private final Executor syncExecutor;
    private final Map<UUID, TimedValue> hungerCapCache = new HashMap<>();
    private final Map<UUID, TimedValue> oxygenCapCache = new HashMap<>();

    /**
     * Creates a new listener bound to the application's attribute components.
     *
     * @param attributeFacade facade for computing final attribute values for players; must be non-null.
     * @param persistence persistence service for loading and saving player attribute data.
     * @param itemAttributeHandler handler that applies default attribute data to player inventories.
     * @param entityAttributeHandler handler responsible for applying attribute caps to entities.
     */
    public AttributeListener(Plugin plugin,
                             AttributeFacade attributeFacade,
                             AttributePersistence persistence,
                             ItemAttributeHandler itemAttributeHandler,
                             EntityAttributeHandler entityAttributeHandler) {
        this.attributeFacade = attributeFacade;
        this.persistence = persistence;
        this.itemAttributeHandler = itemAttributeHandler;
        this.entityAttributeHandler = entityAttributeHandler;
        this.syncExecutor = command -> plugin.getServer().getScheduler().runTask(plugin, command);
    }

    /**
     * Loads the player's persisted attributes, applies default item attributes to new inventory slots,
     * and reapplies entity caps on join. The event's player is expected to be non-null and represent
     * the joining user; no value is returned because event handlers communicate via side effects on the
     * Bukkit event system. Caps are re-applied to ensure the newly loaded attributes immediately affect
     * the player's state.
     *
     * @param event player join event containing the joining player.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        persistence.loadPlayerAsync(attributeFacade, event.getPlayer().getUniqueId())
                .thenRunAsync(() -> {
                    itemAttributeHandler.applyDefaults(event.getPlayer().getInventory());
                    entityAttributeHandler.applyPlayerCaps(event.getPlayer());
                    clearCapCache(event.getPlayer().getUniqueId());
                }, syncExecutor);
    }

    /**
     * Persists the player's attribute data and clears temporary state on quit. The event always
     * provides the quitting player; no return value is produced because persistence occurs as a
     * side effect. Temporary attributes are purged so that any session-scoped effects do not bleed
     * into subsequent sessions.
     *
     * @param event player quit event containing the quitting player.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        persistence.savePlayerAsync(attributeFacade, event.getPlayer().getUniqueId())
                .whenComplete((ignored, error) -> syncExecutor.execute(() -> {
                    attributeFacade.purgeTemporary(event.getPlayer().getUniqueId());
                    clearCapCache(event.getPlayer().getUniqueId());
                }));
    }

    /**
     * Clamps hunger changes to the computed maximum hunger cap for the player. The hunger cap is
     * derived through {@link AttributeFacade#compute(String, org.bukkit.entity.Player)} using the
     * {@code "max_hunger"} attribute and represents the upper bound on the player's food level.
     *
     * <p>When the incoming {@link FoodLevelChangeEvent#getFoodLevel()} exceeds the cap, the new
     * value is truncated to the integer portion of the cap to prevent the Bukkit API from
     * applying a value above the allowed maximum.</p>
     *
     * @param event food level change event containing the updated hunger value for the player.
     */
    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player player) {
            double cap = resolveCachedCap(player, hungerCapCache,
                    () -> attributeFacade.compute("max_hunger", player).currentFinal());
            // Cap the food level here so later listeners and the server never observe values above the
            // computed maximum hunger for the player.
            if (event.getFoodLevel() > cap) {
                event.setFoodLevel((int) cap);
            }
        }
    }

    /**
     * Clamps oxygen changes to the computed maximum oxygen cap for the player. The cap uses the
     * {@code "max_oxygen"} attribute to represent the highest allowable air amount. When the event's
     * {@link EntityAirChangeEvent#getAmount()} exceeds that cap, the requested amount is truncated to
     * the cap's integer portion to avoid the server applying invalid air values.
     *
     * @param event entity air change event containing the updated oxygen value for the player.
     */
    @EventHandler
    public void onAirChange(EntityAirChangeEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player player) {
            int maxAir = player.getMaximumAir();
            if (event.getAmount() <= maxAir) {
                return;
            }

            double cap = resolveCachedCap(player, oxygenCapCache, () -> attributeFacade.compute("max_oxygen", player).currentFinal()
                    + attributeFacade.compute("oxygen_bonus", player).currentFinal());
            // Enforce the oxygen cap within the handler to keep the player's air value consistent with
            // calculated attribute limits before the server processes the change.
            if (event.getAmount() > cap) {
                event.setAmount((int) cap);
            }
        }
    }

    private void clearCapCache(UUID playerId) {
        hungerCapCache.remove(playerId);
        oxygenCapCache.remove(playerId);
    }

    private double resolveCachedCap(org.bukkit.entity.Player player,
                                    Map<UUID, TimedValue> cache,
                                    Supplier<Double> loader) {
        long now = System.nanoTime();
        TimedValue cached = cache.get(player.getUniqueId());
        if (cached != null && cached.expiresAtNanos > now) {
            return cached.value;
        }

        double value = loader.get();
        cache.put(player.getUniqueId(), new TimedValue(value, now + CAP_CACHE_TTL_NANOS));
        return value;
    }

    private record TimedValue(double value, long expiresAtNanos) {
    }
}
