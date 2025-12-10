package me.baddcamden.attributeutils.command;

import me.baddcamden.attributeutils.handler.entity.EntityAttributeHandler;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class EntityAttributeCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final EntityAttributeHandler entityAttributeHandler;
    private final Set<EntityType> disallowedEntityTypes;
    private final me.baddcamden.attributeutils.api.AttributeFacade attributeFacade;
    private final CommandMessages messages;

    public EntityAttributeCommand(Plugin plugin, EntityAttributeHandler entityAttributeHandler, me.baddcamden.attributeutils.api.AttributeFacade attributeFacade) {
        this.plugin = plugin;
        this.entityAttributeHandler = entityAttributeHandler;
        this.attributeFacade = attributeFacade;
        this.messages = new CommandMessages(plugin);
        this.disallowedEntityTypes = loadDisallowedTypes();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("attributeutils.command.entities")) {
            sender.sendMessage(messages.format("messages.entity-command.no-permission", Map.of(),
                    ChatColor.RED + "You do not have permission to spawn attributed entities."));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("messages.entity-command.invalid-sender", Map.of(),
                    ChatColor.RED + "Only players can spawn attributed entities."));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(messages.format("messages.entity-command.usage", Map.of("label", label),
                    ChatColor.YELLOW + "Usage: /" + label + " <entityType> <plugin.key> <value> [cap=<cap> ...]"));
            return true;
        }

        EntityType entityType = resolveEntityType(args[0]);
        if (entityType == null) {
            sender.sendMessage(messages.format("messages.entity-command.invalid-entity", Map.of("entity", args[0]),
                    ChatColor.RED + "Unknown entity type '" + args[0] + "'."));
            return true;
        }

        if (disallowedEntityTypes.contains(entityType) || !entityType.isSpawnable()) {
            sender.sendMessage(messages.format("messages.entity-command.disallowed-entity", Map.of("entity", entityType.name()),
                    ChatColor.RED + "That entity type cannot be spawned with attributes."));
            return true;
        }

        if (player.getWorld() == null) {
            sender.sendMessage(messages.format("messages.entity-command.missing-world", Map.of(),
                    ChatColor.RED + "Could not resolve your world to spawn the entity."));
            return true;
        }

        List<CommandParsingUtils.AttributeDefinition> definitions = CommandParsingUtils.parseAttributeDefinitions(
                sender,
                args,
                1,
                messages,
                key -> attributeFacade.getDefinition(key).isPresent());
        if (definitions.isEmpty()) {
            return true;
        }

        EntityAttributeHandler.SpawnedEntityResult result;
        try {
            result = entityAttributeHandler.spawnAttributedEntity(player.getLocation(), entityType, definitions);
        } catch (IllegalArgumentException ex) {
            if ("invalid-location".equalsIgnoreCase(ex.getMessage())) {
                sender.sendMessage(messages.format("messages.entity-command.missing-world", Map.of(),
                        ChatColor.RED + "Could not resolve your world to spawn the entity."));
                return true;
            }
            sender.sendMessage(messages.format("messages.entity-command.unknown-attribute", Map.of("attribute", ex.getMessage()),
                    ChatColor.RED + "Unknown attribute '" + ex.getMessage() + "'."));
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to spawn attributed entity: " + ex.getMessage());
            sender.sendMessage(messages.format("messages.entity-command.spawn-failed", Map.of(),
                    ChatColor.RED + "An error occurred while spawning the entity."));
            return true;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("entity", entityType.name());
        placeholders.put("attributes", result.summary());
        sender.sendMessage(messages.format("messages.entity-command.spawned", placeholders,
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
            return Set.of();
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(entityNames(), args[0]);
        }

        if (args.length >= 2) {
            int relativeIndex = args.length - 2;
            if (relativeIndex % 3 == 0 || args[args.length - 1].startsWith("cap=")) {
                return filter(attributeKeys(), args[args.length - 1]);
            }
            if (relativeIndex % 3 == 2) {
                List<String> options = new ArrayList<>(attributeKeys());
                options.add("cap=");
                return filter(options, args[args.length - 1]);
            }
        }

        return List.of();
    }

    private List<String> attributeKeys() {
        return attributeFacade.getDefinitions().stream()
                .map(me.baddcamden.attributeutils.model.AttributeDefinition::id)
                .sorted()
                .toList();
    }

    private List<String> entityNames() {
        return java.util.Arrays.stream(EntityType.values())
                .filter(EntityType::isSpawnable)
                .map(EntityType::name)
                .sorted()
                .collect(Collectors.toList());
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
