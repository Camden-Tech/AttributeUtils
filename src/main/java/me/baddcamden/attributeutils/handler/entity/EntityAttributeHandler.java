package me.baddcamden.attributeutils.handler.entity;

import me.baddcamden.attributeutils.attributes.model.AttributeInstance;
import me.baddcamden.attributeutils.service.AttributeComputationService;
import me.baddcamden.attributeutils.service.AttributeService;
import org.bukkit.entity.Player;

import java.util.Optional;

public class EntityAttributeHandler {

    private final AttributeService attributeService;
    private final AttributeComputationService computationService;

    public EntityAttributeHandler(AttributeService attributeService, AttributeComputationService computationService) {
        this.attributeService = attributeService;
        this.computationService = computationService;
    }

    public void applyPlayerCaps(Player player) {
        setFoodLevel(player, attributeService.getAttribute("max_hunger"));
        setOxygen(player, attributeService.getAttribute("max_oxygen"));
    }

    private void setFoodLevel(Player player, Optional<AttributeInstance> model) {
        model.ifPresent(attribute -> player.setFoodLevel(computationService.toHungerBar(attribute)));
    }

    private void setOxygen(Player player, Optional<AttributeInstance> model) {
        model.ifPresent(attribute -> {
            player.setMaximumAir((int) Math.round(attribute.getActualMaxValue()));
            player.setRemainingAir(computationService.toOxygenBar(attribute));
        });
    }
}
