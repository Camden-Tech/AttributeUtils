package me.baddcamden.attributeutils.listener;

import me.baddcamden.attributeutils.api.AttributeApi;
import me.baddcamden.attributeutils.handler.entity.EntityAttributeHandler;
import me.baddcamden.attributeutils.handler.item.ItemAttributeHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class AttributeListener implements Listener {

    private final AttributeApi attributeApi;
    private final ItemAttributeHandler itemAttributeHandler;
    private final EntityAttributeHandler entityAttributeHandler;

    public AttributeListener(AttributeApi attributeApi, ItemAttributeHandler itemAttributeHandler, EntityAttributeHandler entityAttributeHandler) {
        this.attributeApi = attributeApi;
        this.itemAttributeHandler = itemAttributeHandler;
        this.entityAttributeHandler = entityAttributeHandler;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        itemAttributeHandler.applyDefaults(event.getPlayer().getInventory());
        entityAttributeHandler.applyPlayerCaps(event.getPlayer());
        attributeApi.getRegisteredDefinitions().forEach(attribute ->
                event.getPlayer().sendMessage("Loaded attribute: " + attribute.key())
        );
    }
}
