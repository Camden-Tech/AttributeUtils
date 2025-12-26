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

/**
 * Handles entity-scoped attribute commands that spawn attributed entities with validated attribute definitions.
 * <p>
 * The command parses sender input into {@link me.baddcamden.attributeutils.model.AttributeInstance} data, clamps cap
 * values via {@link me.baddcamden.attributeutils.model.CapConfig#clamp(double, java.util.UUID)}, and ensures
 * disallowed entities are not created.
 */
public class EntityAttributeCommand implements CommandExecutor, TabCompleter {

    /** Owning plugin used for permissions, config, and logger access. */
    private final Plugin plugin;
    /** Handler responsible for spawning and mutating entities with attribute data attached. */
    private final EntityAttributeHandler entityAttributeHandler;
    /** Cached list of entity types that should not be spawned via this command. */
    private final Set<EntityType> disallowedEntityTypes;
    /** Attribute registry used to validate requested attribute keys. */
    private final me.baddcamden.attributeutils.api.AttributeFacade attributeFacade;
    /** Message formatter for localization-aware feedback. */
    private final CommandMessages messages;

    /**
     * Creates a new entity attribute command handler.
     *
     * @param plugin                  owning plugin used for configuration and permissions.
     * @param entityAttributeHandler  handler for spawning and mutating attributed entities.
     * @param attributeFacade         registry for attribute definitions used during validation.
     */
    public EntityAttributeCommand(Plugin plugin, EntityAttributeHandler entityAttributeHandler, me.baddcamden.attributeutils.api.AttributeFacade attributeFacade) {
        this.plugin = plugin;
        this.entityAttributeHandler = entityAttributeHandler;
        this.attributeFacade = attributeFacade;
        this.messages = new CommandMessages(plugin);
        this.disallowedEntityTypes = loadDisallowedTypes();
    }

    /**
     * Parses the entity attribute command to spawn an attributed mob near the player. The method validates permissions,
     * sender type, entity availability, disallowed lists, and attribute definitions before delegating to
     * {@link EntityAttributeHandler#spawnAttributedEntity}.
     *
     * <p>Execution short-circuits with user-facing messages whenever an invalid state is detected rather than throwing
     * errors to the console, so players receive immediate feedback.</p>
     */
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

        if (args.length < 4) {
            sender.sendMessage(messages.format("messages.entity-command.usage", Map.of("label", label),
                    ChatColor.YELLOW + "Usage: /" + label + " <entityType> <plugin> <name> <value> [cap=<cap> operation=<add|multiply> ...]"));
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

    /**
     * Attempts to resolve an {@link EntityType} using Bukkit's lookup facilities and enum names, allowing flexible
     * casing from command senders.
     *
     * @param raw user-provided entity name.
     * @return matched {@link EntityType} or {@code null} if not found.
     */
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

    /**
     * Reads the disallowed entity list from configuration and resolves each entry to an {@link EntityType}.
     *
     * @return cached set of types that should not be available to this command.
     */
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

    /**
     * Provides tab completion suggestions for entity attribute commands, iterating through entity type, plugin,
     * attribute name, value, and optional cap tokens to mirror the parsing order.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(entityNames(), args[0]);
        }

        if (args.length >= 2) {
            int index = 1;
            while (index < args.length) {
                //VAGUE/IMPROVEMENT NEEDED This loop assumes arguments are chunked strictly as <plugin> <name> <value> [cap=X] repeating; it is unclear how partial trailing arguments should be handled or surfaced to callers.
                int remaining = args.length - index;

                if (remaining == 1) {
                    return filter(attributePlugins(), args[index]);
                }

                if (remaining == 2) {
                    return filter(attributeNames(args[index]), args[index + 1]);
                }

                if (remaining == 3) {
                    List<String> options = List.of("0", "cap=0", "cap=");
                    return filter(options, args[index + 2]);
                }

                boolean hasCapToken = args[index + 3].startsWith("cap=");
                if (hasCapToken) {
                    if (remaining == 4) {
                        return filter(List.of("cap=", "cap=0"), args[index + 3]);
                    }
                    index += 4;
                } else {
                    index += 3;
                }
            }
        }

        return attributePlugins();
    }

    /**
     * Lists plugin namespaces drawn from registered attribute ids for tab completion.
     *
     * @return sorted, distinct plugin identifiers derived from attribute keys.
     */
    private List<String> attributePlugins() {
        return CommandParsingUtils.namespacedCompletionsFromIds(attributeFacade.getDefinitionIds(), plugin.getName()).stream()
                .map(value -> value.split("\\.", 2)[0])
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Lists attribute names for a given plugin namespace to help complete the {@code <name>} token.
     *
     * @param pluginName namespace provided in the command (may be partial or null while typing).
     * @return sorted attribute names scoped to the provided namespace.
     */
    private List<String> attributeNames(String pluginName) {
        String normalized = pluginName == null ? "" : pluginName.toLowerCase(Locale.ROOT);
        return CommandParsingUtils.namespacedCompletionsFromIds(attributeFacade.getDefinitionIds(), plugin.getName()).stream()
                .filter(value -> value.startsWith(normalized + "."))
                .map(value -> value.split("\\.", 2)[1])
                .sorted()
                .toList();
    }

    /**
     * @return sorted, spawnable entity type names for the first command argument.
     */
    private List<String> entityNames() {
        return java.util.Arrays.stream(EntityType.values())
                .filter(EntityType::isSpawnable)
                .map(EntityType::name)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Filters options by prefix using case-insensitive comparison.
     *
     * @param options available suggestions for the current argument.
     * @param prefix  player-provided text to match.
     * @return options that begin with the prefix, preserving the original ordering.
     */
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
