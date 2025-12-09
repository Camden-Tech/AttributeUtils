package me.baddcamden.attributeutils.handler.entity;

import me.baddcamden.attributeutils.attributes.model.AttributeModel;
import me.baddcamden.attributeutils.service.AttributeService;
import org.bukkit.entity.Player;

import java.util.Optional;

public class EntityAttributeHandler {

    private final AttributeService attributeService;

    public EntityAttributeHandler(AttributeService attributeService) {
        this.attributeService = attributeService;
    }

    public void applyPlayerCaps(Player player) {
        setFoodLevel(player, attributeService.getAttribute("max_hunger"));
        setOxygen(player, attributeService.getAttribute("max_oxygen"));
    }

    private void setFoodLevel(Player player, Optional<AttributeModel> model) {
        model.ifPresent(attribute -> player.setFoodLevel((int) attribute.getValue()));
    }

    private void setOxygen(Player player, Optional<AttributeModel> model) {
        model.ifPresent(attribute -> player.setMaximumAir((int) attribute.getMaxValue()));
    }
}
