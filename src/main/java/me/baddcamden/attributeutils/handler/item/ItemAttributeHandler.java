package me.baddcamden.attributeutils.handler.item;

import me.baddcamden.attributeutils.service.AttributeService;
import org.bukkit.inventory.PlayerInventory;

public class ItemAttributeHandler {

    private final AttributeService attributeService;

    public ItemAttributeHandler(AttributeService attributeService) {
        this.attributeService = attributeService;
    }

    public void applyDefaults(PlayerInventory inventory) {
        attributeService.getAttributes().forEach((key, attribute) -> {
            // Placeholder for applying item-based attributes to the player's inventory.
        });
    }
}
