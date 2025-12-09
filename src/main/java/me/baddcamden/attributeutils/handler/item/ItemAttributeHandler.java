package me.baddcamden.attributeutils.handler.item;

import me.baddcamden.attributeutils.api.AttributeFacade;
import org.bukkit.inventory.PlayerInventory;

public class ItemAttributeHandler {

    private final AttributeFacade attributeFacade;

    public ItemAttributeHandler(AttributeFacade attributeFacade) {
        this.attributeFacade = attributeFacade;
    }

    public void applyDefaults(PlayerInventory inventory) {
        // Future item hooks will populate player data. For now we simply ensure defaults are registered.
        attributeFacade.getDefinitions();
    }
}
