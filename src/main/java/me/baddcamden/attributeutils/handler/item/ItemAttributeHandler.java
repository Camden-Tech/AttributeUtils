package me.baddcamden.attributeutils.handler.item;

import me.baddcamden.attributeutils.api.AttributeApi;
import org.bukkit.inventory.PlayerInventory;

public class ItemAttributeHandler {

    private final AttributeApi attributeApi;

    public ItemAttributeHandler(AttributeApi attributeApi) {
        this.attributeApi = attributeApi;
    }

    public void applyDefaults(PlayerInventory inventory) {
        attributeApi.getRegisteredDefinitions().forEach(attribute -> {
            // Placeholder for applying item-based attributes to the player's inventory.
        });
    }
}
