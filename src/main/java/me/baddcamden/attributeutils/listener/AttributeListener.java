package me.baddcamden.attributeutils.listener;

import me.baddcamden.attributeutils.attributes.model.AttributeInstance;
import me.baddcamden.attributeutils.handler.entity.EntityAttributeHandler;
import me.baddcamden.attributeutils.handler.item.ItemAttributeHandler;
import me.baddcamden.attributeutils.service.AttributeComputationService;
import me.baddcamden.attributeutils.service.AttributeService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class AttributeListener implements Listener {

    private final AttributeService attributeService;
    private final ItemAttributeHandler itemAttributeHandler;
    private final EntityAttributeHandler entityAttributeHandler;
    private final AttributeComputationService computationService;

    public AttributeListener(AttributeService attributeService, ItemAttributeHandler itemAttributeHandler, EntityAttributeHandler entityAttributeHandler, AttributeComputationService computationService) {
        this.attributeService = attributeService;
        this.itemAttributeHandler = itemAttributeHandler;
        this.entityAttributeHandler = entityAttributeHandler;
        this.computationService = computationService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        itemAttributeHandler.applyDefaults(event.getPlayer().getInventory());
        entityAttributeHandler.applyPlayerCaps(event.getPlayer());
        attributeService.getAttributes().values().forEach(attribute ->
                event.getPlayer().sendMessage("Loaded attribute: " + attribute.getKey())
        );
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        attributeService.getAttribute("max_hunger").ifPresent(attribute -> updateHunger(attribute, event.getFoodLevel()));
    }

    @EventHandler
    public void onEntityAirChange(EntityAirChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        attributeService.getAttribute("max_oxygen").ifPresent(attribute -> updateOxygen(attribute, event.getAmount()));
    }

    private void updateHunger(AttributeInstance attribute, int barValue) {
        double actualValue = computationService.fromHungerBar(barValue, attribute);
        attribute.setActualValue(actualValue);
        attribute.setValue(actualValue);
    }

    private void updateOxygen(AttributeInstance attribute, int barValue) {
        double actualValue = computationService.fromOxygenBar(barValue, attribute);
        attribute.setActualValue(actualValue);
        attribute.setValue(actualValue);
    }
}
