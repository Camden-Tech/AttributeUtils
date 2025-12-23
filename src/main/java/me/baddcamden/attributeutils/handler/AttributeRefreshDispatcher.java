package me.baddcamden.attributeutils.handler;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.handler.entity.EntityAttributeHandler;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Bridges {@link AttributeFacade} refresh events to live Bukkit entities so computed values are re-applied immediately.
 */
public class AttributeRefreshDispatcher implements AttributeFacade.AttributeRefreshListener {

    private final Plugin plugin;
    private final EntityAttributeHandler entityAttributeHandler;

    public AttributeRefreshDispatcher(Plugin plugin, EntityAttributeHandler entityAttributeHandler) {
        this.plugin = plugin;
        this.entityAttributeHandler = entityAttributeHandler;
    }

    @Override
    public void refreshAttributeForPlayer(UUID playerId, String attributeId) {
        if (playerId == null || attributeId == null) {
            return;
        }

        Entity entity = plugin.getServer().getEntity(playerId);
        if (entity instanceof LivingEntity livingEntity) {
            entityAttributeHandler.applyVanillaAttribute(livingEntity, attributeId);
        }
    }

    @Override
    public void refreshAttributeForAll(String attributeId) {
        if (attributeId == null) {
            return;
        }

        for (World world : plugin.getServer().getWorlds()) {
            for (LivingEntity livingEntity : world.getLivingEntities()) {
                entityAttributeHandler.applyVanillaAttribute(livingEntity, attributeId);
            }
        }
    }
}
