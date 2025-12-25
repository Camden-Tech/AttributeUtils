package me.baddcamden.attributeutils.handler;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.handler.entity.EntityAttributeHandler;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;

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
    private final Map<UUID, Set<String>> pendingPlayerAttributes = new HashMap<>();
    private final Set<String> pendingGlobalAttributes = new HashSet<>();
    private boolean flushScheduled;
    private final boolean debugLogging;

    /**
     * Creates a dispatcher that can resolve entities from the server and apply refreshed attributes.
     *
     * @param plugin the owning plugin used to access the server for entity lookups
     * @param entityAttributeHandler the handler responsible for applying vanilla attribute updates
     */
    public AttributeRefreshDispatcher(Plugin plugin, EntityAttributeHandler entityAttributeHandler, boolean debugLogging) {
        this.plugin = plugin;
        this.entityAttributeHandler = entityAttributeHandler;
        this.debugLogging = debugLogging;
    }

    @Override
    public void refreshAttributeForPlayer(UUID playerId, String attributeId) {
        // VAGUE/IMPROVEMENT NEEDED Clarify whether this should target only player entities or any entity resolvable by UUID.
        String normalizedId = normalizeAttributeId(attributeId);
        if (playerId == null || normalizedId == null) {
            return;
        }

        pendingPlayerAttributes.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(normalizedId);
        if (debugLogging) {
            plugin.getLogger().info("[refresh-debug] queued player attribute refresh: " + normalizedId
                    + " for " + playerId + idNote(attributeId, normalizedId));
        }
        scheduleFlush();
    }

    @Override
    public void refreshAttributeForAll(String attributeId) {
        String normalizedId = normalizeAttributeId(attributeId);
        if (normalizedId == null) {
            return;
        }

        pendingGlobalAttributes.add(normalizedId);
        if (debugLogging) {
            plugin.getLogger().info("[refresh-debug] queued global attribute refresh: " + normalizedId
                    + idNote(attributeId, normalizedId));
        }
        scheduleFlush();
    }

    private void scheduleFlush() {
        if (flushScheduled) {
            return;
        }
        flushScheduled = true;
        plugin.getServer().getScheduler().runTask(plugin, this::flushPending);
    }

    private void flushPending() {
        flushScheduled = false;
        Map<UUID, Set<String>> playerSnapshot = new HashMap<>(pendingPlayerAttributes);
        Set<String> globalSnapshot = new HashSet<>(pendingGlobalAttributes);
        pendingPlayerAttributes.clear();
        pendingGlobalAttributes.clear();

        if (debugLogging) {
            plugin.getLogger().info("[refresh-debug] flushing queued refreshes. players=" + playerSnapshot.size()
                    + " globals=" + globalSnapshot.size());
        }

        for (Map.Entry<UUID, Set<String>> entry : playerSnapshot.entrySet()) {
            Entity entity = plugin.getServer().getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity livingEntity)) {
                if (debugLogging) {
                    plugin.getLogger().info("[refresh-debug] skipped missing/invalid entity for " + entry.getKey());
                }
                continue;
            }
            for (String attributeId : entry.getValue()) {
                if (debugLogging) {
                    plugin.getLogger().info("[refresh-debug] applying player refresh " + attributeId + " to "
                            + livingEntity.getName() + " (" + livingEntity.getUniqueId() + ")");
                }
                entityAttributeHandler.applyVanillaAttribute(livingEntity, attributeId);
            }
        }

        for (String attributeId : globalSnapshot) {
            for (World world : plugin.getServer().getWorlds()) {
                for (LivingEntity livingEntity : world.getLivingEntities()) {
                    if (debugLogging) {
                        plugin.getLogger().info("[refresh-debug] applying global refresh " + attributeId + " to "
                                + livingEntity.getType() + " (" + livingEntity.getUniqueId() + ")");
                    }
                    entityAttributeHandler.applyVanillaAttribute(livingEntity, attributeId);
                }
            }
        }
    }

    private String normalizeAttributeId(String attributeId) {
        return attributeId == null ? null : attributeId.toLowerCase(Locale.ROOT);
    }

    private String idNote(String rawId, String normalizedId) {
        if (rawId == null || rawId.equals(normalizedId)) {
            return "";
        }
        return " (normalized from " + rawId + ")";
    }
}
