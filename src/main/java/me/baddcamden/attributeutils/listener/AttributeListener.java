package me.baddcamden.attributeutils.listener;

import me.baddcamden.attributeutils.handler.entity.EntityAttributeHandler;
import me.baddcamden.attributeutils.handler.item.ItemAttributeHandler;
import me.baddcamden.attributeutils.service.AttributeService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class AttributeListener implements Listener {

    private final AttributeService attributeService;
    private final ItemAttributeHandler itemAttributeHandler;
    private final EntityAttributeHandler entityAttributeHandler;

    public AttributeListener(AttributeService attributeService, ItemAttributeHandler itemAttributeHandler, EntityAttributeHandler entityAttributeHandler) {
        this.attributeService = attributeService;
        this.itemAttributeHandler = itemAttributeHandler;
        this.entityAttributeHandler = entityAttributeHandler;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        itemAttributeHandler.applyDefaults(event.getPlayer().getInventory());
        entityAttributeHandler.applyPlayerCaps(event.getPlayer());
        attributeService.getAttributes().values().forEach(attribute ->
                event.getPlayer().sendMessage("Loaded attribute: " + attribute.getKey())
        );
    }
}
