package me.baddcamden.attributeutils.listener;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.handler.entity.EntityAttributeHandler;
import me.baddcamden.attributeutils.handler.item.ItemAttributeHandler;
import me.baddcamden.attributeutils.persistence.AttributePersistence;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.Executor;

/**
 * Listens for player lifecycle and attribute-related events to keep persisted data in sync and enforce
 * calculated attribute limits. The listener ensures player data is loaded and saved during join/quit,
 * reapplies caps after inventory changes, and refreshes persistent item attributes when entities spawn
 * or chunks load.
 */
public class AttributeListener implements Listener {

    /** Facade used for deriving attribute values during persistence operations. */
    private final AttributeFacade attributeFacade;
    /** Service that loads and saves persisted attribute data for players. */
    private final AttributePersistence persistence;
    /** Handles applying and clearing item-based attribute modifiers. */
    private final ItemAttributeHandler itemAttributeHandler;
    /** Handles applying attribute caps and other entity-level constraints. */
    private final EntityAttributeHandler entityAttributeHandler;
    /** Executes follow-up tasks synchronously on the main server thread. */
    private final Executor syncExecutor;

    /**
     * Creates a new listener bound to the application's attribute components.
     *
     * @param plugin owning plugin used to schedule synchronous tasks.
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
     * Loads persisted player attributes asynchronously, then reapplies attributes and caps on the main thread
     * once loading completes. This ensures players immediately benefit from stored modifiers after joining.
     *
     * @param event player join event containing the joining player.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        persistence.loadPlayerAsync(attributeFacade, event.getPlayer().getUniqueId())
                .thenRunAsync(() -> {
                    itemAttributeHandler.applyPersistentAttributes(event.getPlayer());
                    entityAttributeHandler.applyPlayerCaps(event.getPlayer());
                }, syncExecutor);
    }

    /**
     * Saves player attributes asynchronously when the player quits and then clears transient state on the main thread.
     * Temporary modifiers and cached caps are purged to avoid leaking session-specific data.
     *
     * @param event player quit event containing the quitting player.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        persistence.savePlayerAsync(attributeFacade, event.getPlayer().getUniqueId())
                .whenComplete((ignored, error) -> syncExecutor.execute(() -> {
                    attributeFacade.purgeTemporary(event.getPlayer().getUniqueId());
                    itemAttributeHandler.clearAppliedModifiers(event.getPlayer().getUniqueId());
                }));
    }

    /**
     * Reapplies persistent item attributes and caps when players change their held item slot.
     *
     * @param event event representing the change of held item.
     */
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        syncExecutor.execute(() -> refreshPlayer(event.getPlayer()));
    }

    /**
     * Reapplies persistent attributes and caps whenever a player clicks within their inventory, accounting for both
     * additions and removals that could affect attribute sources.
     *
     * @param event inventory click event fired by Bukkit.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            syncExecutor.execute(() -> refreshPlayer(player));
        }
    }

    /**
     * Reapplies persistent attributes and caps after swapping items between hands, ensuring both main-hand and off-hand
     * equipment changes are reflected.
     *
     * @param event hand swap event.
     */
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        syncExecutor.execute(() -> refreshPlayer(event.getPlayer()));
    }

    /**
     * Applies persistent attribute defaults to new entities and re-applies item attributes for living entities so they
     * start with the correct modifiers.
     * //VAGUE/IMPROVEMENT NEEDED Clarify whether caps should also be enforced here or only during player refresh.
     *
     * @param event entity spawn event.
     */
    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        entityAttributeHandler.applyPersistentAttributes(event.getEntity());
        if (event.getEntity() instanceof LivingEntity living) {
            itemAttributeHandler.applyPersistentAttributes(living);
        }
    }

    /**
     * Reapplies persistent attributes to all entities in a chunk when it loads, ensuring data remains consistent after
     * chunks are rehydrated from disk.
     * //VAGUE/IMPROVEMENT NEEDED Confirm whether item-level attributes should be refreshed for non-player living entities.
     *
     * @param event chunk load event.
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            entityAttributeHandler.applyPersistentAttributes(entity);
            if (entity instanceof LivingEntity living) {
                itemAttributeHandler.applyPersistentAttributes(living);
            }
        }
    }

    /**
     * Helper that reapplies persistent attributes and caps for a player in response to inventory changes. Intended to
     * be invoked on the main thread.
     *
     * @param player player whose attributes should be refreshed.
     */
    private void refreshPlayer(Player player) {
        itemAttributeHandler.applyPersistentAttributes(player);
        entityAttributeHandler.applyPlayerCaps(player);
        attributeFacade.refreshAllAttributesForPlayer(player.getUniqueId());
    }
}
