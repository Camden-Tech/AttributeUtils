package me.baddcamden.attributeutils.handler.entity;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.model.AttributeValueStages;
import org.bukkit.entity.Player;

public class EntityAttributeHandler {

    private final AttributeFacade attributeFacade;

    public EntityAttributeHandler(AttributeFacade attributeFacade) {
        this.attributeFacade = attributeFacade;
    }

    public void applyPlayerCaps(Player player) {
        AttributeValueStages hunger = attributeFacade.compute("max_hunger", player);
        player.setFoodLevel((int) Math.round(hunger.currentFinal()));

        AttributeValueStages oxygen = attributeFacade.compute("max_oxygen", player);
        player.setMaximumAir((int) Math.round(oxygen.currentFinal()));
    }
}
