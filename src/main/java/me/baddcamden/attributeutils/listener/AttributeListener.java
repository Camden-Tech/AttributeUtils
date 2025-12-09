package me.baddcamden.attributeutils.listener;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.handler.entity.EntityAttributeHandler;
import me.baddcamden.attributeutils.handler.item.ItemAttributeHandler;
import me.baddcamden.attributeutils.persistence.AttributePersistence;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class AttributeListener implements Listener {

    private final AttributeFacade attributeFacade;
    private final AttributePersistence persistence;
    private final ItemAttributeHandler itemAttributeHandler;
    private final EntityAttributeHandler entityAttributeHandler;

    public AttributeListener(AttributeFacade attributeFacade,
                             AttributePersistence persistence,
                             ItemAttributeHandler itemAttributeHandler,
                             EntityAttributeHandler entityAttributeHandler) {
        this.attributeFacade = attributeFacade;
        this.persistence = persistence;
        this.itemAttributeHandler = itemAttributeHandler;
        this.entityAttributeHandler = entityAttributeHandler;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        persistence.loadPlayer(attributeFacade, event.getPlayer().getUniqueId());
        itemAttributeHandler.applyDefaults(event.getPlayer().getInventory());
        entityAttributeHandler.applyPlayerCaps(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        persistence.savePlayer(attributeFacade, event.getPlayer().getUniqueId());
        attributeFacade.purgeTemporary(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player player) {
            double cap = attributeFacade.compute("max_hunger", player).currentFinal();
            if (event.getFoodLevel() > cap) {
                event.setFoodLevel((int) cap);
            }
        }
    }

    @EventHandler
    public void onAirChange(EntityAirChangeEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player player) {
            double cap = attributeFacade.compute("max_oxygen", player).currentFinal();
            if (event.getAmount() > cap) {
                event.setAmount((int) cap);
            }
        }
    }
}
