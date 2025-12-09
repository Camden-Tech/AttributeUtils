package me.baddcamden.attributeutils.handler.entity;

import me.baddcamden.attributeutils.api.AttributeApi;
import me.baddcamden.attributeutils.api.AttributeComputation;
import org.bukkit.entity.Player;

import java.util.Optional;

public class EntityAttributeHandler {

    private final AttributeApi attributeApi;

    public EntityAttributeHandler(AttributeApi attributeApi) {
        this.attributeApi = attributeApi;
    }

    public void applyPlayerCaps(Player player) {
        setFoodLevel(player, attributeApi.queryAttribute("max_hunger", player));
        setOxygen(player, attributeApi.queryAttribute("max_oxygen", player));
    }

    private void setFoodLevel(Player player, Optional<AttributeComputation> computation) {
        computation.ifPresent(attribute -> player.setFoodLevel((int) attribute.finalValue()));
    }

    private void setOxygen(Player player, Optional<AttributeComputation> computation) {
        computation.ifPresent(attribute -> player.setMaximumAir((int) attribute.finalValue()));
    }
}
