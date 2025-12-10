package me.baddcamden.attributeutils.command;

import me.baddcamden.attributeutils.handler.entity.EntityAttributeHandler;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class EntityAttributeCommand implements CommandExecutor {

    private final Plugin plugin;
    private final EntityAttributeHandler entityAttributeHandler;
    private final Set<EntityType> disallowedEntityTypes;

    public EntityAttributeCommand(Plugin plugin, EntityAttributeHandler entityAttributeHandler) {
        this.plugin = plugin;
        this.entityAttributeHandler = entityAttributeHandler;
        this.disallowedEntityTypes = loadDisallowedTypes();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("attributeutils.command.entities")) {
            sender.sendMessage(formatMessage("messages.entity-command.no-permission", Map.of(),
                    ChatColor.RED + "You do not have permission to spawn attributed entities."));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(formatMessage("messages.entity-command.invalid-sender", Map.of(),
                    ChatColor.RED + "Only players can spawn attributed entities."));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(formatMessage("messages.entity-command.usage", Map.of("label", label),
                    ChatColor.YELLOW + "Usage: /" + label + " <entityType> <plugin.key> <value> [cap=<cap> ...]"));
            return true;
        }

        EntityType entityType = resolveEntityType(args[0]);
        if (entityType == null) {
            sender.sendMessage(formatMessage("messages.entity-command.invalid-entity", Map.of("entity", args[0]),
                    ChatColor.RED + "Unknown entity type '" + args[0] + "'."));
            return true;
        }

        if (disallowedEntityTypes.contains(entityType) || !entityType.isSpawnable()) {
            sender.sendMessage(formatMessage("messages.entity-command.disallowed-entity", Map.of("entity", entityType.name()),
                    ChatColor.RED + "That entity type cannot be spawned with attributes."));
            return true;
        }

        if (player.getWorld() == null) {
            sender.sendMessage(formatMessage("messages.entity-command.missing-world", Map.of(),
                    ChatColor.RED + "Could not resolve your world to spawn the entity."));
            return true;
        }

        List<CommandParsingUtils.AttributeDefinition> definitions = CommandParsingUtils.parseAttributeDefinitions(sender, args,
1);
        if (definitions.isEmpty()) {
            return true;
        }

        EntityAttributeHandler.SpawnedEntityResult result;
        try {
            result = entityAttributeHandler.spawnAttributedEntity(player.getLocation(), entityType, definitions);
        } catch (IllegalArgumentException ex) {
            if ("invalid-location".equalsIgnoreCase(ex.getMessage())) {
                sender.sendMessage(formatMessage("messages.entity-command.missing-world", Map.of(),
                        ChatColor.RED + "Could not resolve your world to spawn the entity."));
                return true;
            }
            sender.sendMessage(formatMessage("messages.entity-command.unknown-attribute", Map.of("attribute", ex.getMessage()),
                    ChatColor.RED + "Unknown attribute '" + ex.getMessage() + "'."));
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to spawn attributed entity: " + ex.getMessage());
            sender.sendMessage(formatMessage("messages.entity-command.spawn-failed", Map.of(),
                    ChatColor.RED + "An error occurred while spawning the entity."));
            return true;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("entity", entityType.name());
        placeholders.put("attributes", result.summary());
        sender.sendMessage(formatMessage("messages.entity-command.spawned", placeholders,
                ChatColor.GREEN + "Spawned " + entityType.name() + " with attributes: " + result.summary()));
        return true;
    }

    private EntityType resolveEntityType(String raw) {
        EntityType entityType = EntityType.fromName(raw);
        if (entityType != null) {
            return entityType;
        }
        try {
            return EntityType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Set<EntityType> loadDisallowedTypes() {
        List<String> configured = plugin.getConfig().getStringList("entity-command.disallowed-entities");
        if (configured.isEmpty()) {
            return Collections.emptySet();
        }

        Set<EntityType> result = new HashSet<>();
        for (String raw : configured) {
            EntityType type = resolveEntityType(raw);
            if (type != null) {
                result.add(type);
            }
        }
        return result;
    }

    private String formatMessage(String path, Map<String, String> placeholders, String fallback) {
        FileConfiguration config = plugin.getConfig();
        String message = config.getString(path, fallback);
        if (message == null || message.isBlank()) {
            message = fallback;
        }
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
