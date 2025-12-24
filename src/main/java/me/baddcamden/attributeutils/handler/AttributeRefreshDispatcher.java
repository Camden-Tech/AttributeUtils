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
 *
 * <p>The dispatcher listens for attribute refresh callbacks and re-applies the vanilla attribute values for the
 * relevant entities. It relies on the provided {@link Plugin} to resolve entities and {@link EntityAttributeHandler}
 * to perform the actual attribute update.</p>
 */
public class AttributeRefreshDispatcher implements AttributeFacade.AttributeRefreshListener {

    private final Plugin plugin;
    private final EntityAttributeHandler entityAttributeHandler;

    /**
     * Creates a dispatcher that can resolve entities from the server and apply refreshed attributes.
     *
     * @param plugin the owning plugin used to access the server for entity lookups
     * @param entityAttributeHandler the handler responsible for applying vanilla attribute updates
     */
    public AttributeRefreshDispatcher(Plugin plugin, EntityAttributeHandler entityAttributeHandler) {
        this.plugin = plugin;
        this.entityAttributeHandler = entityAttributeHandler;
    }

    @Override
    public void refreshAttributeForPlayer(UUID playerId, String attributeId) {
        // VAGUE/IMPROVEMENT NEEDED Clarify whether this should target only player entities or any entity resolvable by UUID.
        if (playerId == null || attributeId == null) {
            return;
        }

        // Using the server lookup ensures we only attempt to refresh attributes for entities currently loaded in the world.
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

        // Iterate through every loaded world to refresh the attribute for all living entities currently active.
        for (World world : plugin.getServer().getWorlds()) {
            for (LivingEntity livingEntity : world.getLivingEntities()) {
                entityAttributeHandler.applyVanillaAttribute(livingEntity, attributeId);
            }
        }
    }
}
