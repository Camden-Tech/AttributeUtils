package me.baddcamden.attributeutils.command;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.handler.item.ItemAttributeHandler;
import me.baddcamden.attributeutils.handler.item.TriggerCriterion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Interprets item-focused commands that write attribute baselines and modifiers onto generated items.
 *
 * <p>The command shape mirrors entity attribute commands: attribute definitions start after the material token,
 * and support {@code <plugin> <name> <value> [cap=<cap>] [criteria=<criteria> ...]} sequences. Values are written to
 * item metadata so the attributes travel with the item instead of the player. Optional {@code cap} tokens apply an
 * upper bound to that attribute definition only, while {@code criteria} tokens populate modifier buckets so item
 * modifiers activate alongside player/global modifiers during computation.</p>
 *
 * <p>Callers are expected to validate permissions and input structure before modifying or delivering items; this
 * executor provides the parsing, validation, and delivery for {@code /<label> <player> <material> ...} commands.</p>
 */
public class ItemAttributeCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final ItemAttributeHandler itemAttributeHandler;
    private final AttributeFacade attributeFacade;
    private final CommandMessages messages;
    private static final List<String> CRITERIA = TriggerCriterion.keys();

    /**
     * Creates a new handler for item attribute commands.
     *
     * @param plugin               owning plugin used for permissions, message formatting, and online player lookups.
     * @param itemAttributeHandler builder responsible for writing attribute data into item metadata.
     * @param attributeFacade      attribute registry used to validate requested attributes.
     */
    public ItemAttributeCommand(Plugin plugin, ItemAttributeHandler itemAttributeHandler, AttributeFacade attributeFacade) {
        this.plugin = plugin;
        this.itemAttributeHandler = itemAttributeHandler;
        this.attributeFacade = attributeFacade;
        this.messages = new CommandMessages(plugin);
    }

    /**
     * Generates an attribute-infused item for a player by parsing attribute definitions and criteria from the argument
     * list. The command validates permissions, player availability, target material support, and attribute existence
     * before delegating to {@link ItemAttributeHandler} for item creation and delivery.
     *
     * <p>The argument order is strictly {@code <player> <material> <plugin> <name> <value> [...definitions...]}. When
     * multiple attribute definitions are present, they repeat the {@code plugin/name/value} sequence with optional
     * {@code cap} or {@code criteria} tokens trailing each definition.</p>
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("attributeutils.command.items")) {
            sender.sendMessage(messages.format("messages.item-command.no-permission", Map.of(),
                    ChatColor.RED + "You do not have permission to generate attribute items."));
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(messages.format("messages.item-command.usage", Map.of("label", label),
                    ChatColor.YELLOW + "Usage: /" + label + " <player> <material> <plugin> <name> <value> [cap=<cap> criteria=<criteria> ...]"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(messages.format("messages.item-command.player-offline", Map.of("player", args[0]),
                    ChatColor.RED + "Player '" + args[0] + "' is not online."));
            return true;
        }

        Material material = resolveMaterial(args[1]);
        if (material == null) {
            sender.sendMessage(messages.format("messages.item-command.invalid-material", Map.of("material", args[1]),
                    ChatColor.RED + "Unknown material '" + args[1] + "'."));
            return true;
        }

        List<CommandParsingUtils.AttributeDefinition> definitions = CommandParsingUtils.parseAttributeDefinitions(
                sender,
                args,
                2,
                messages,
                key -> attributeFacade.getDefinition(key).isPresent(),
                raw -> TriggerCriterion.fromRaw(raw).isPresent(),
                CRITERIA);
        if (definitions.isEmpty()) {
            return true;
        }

        ItemAttributeHandler.ItemBuildResult buildResult;
        try {
            buildResult = itemAttributeHandler.buildAttributeItem(material, definitions);
        } catch (IllegalArgumentException ex) {
            if ("unsupported-material".equalsIgnoreCase(ex.getMessage())) {
                sender.sendMessage(messages.format("messages.item-command.unsupported-material", Map.of("material", material.name()),
                        ChatColor.RED + "That material cannot hold attribute data."));
                return true;
            }
            //VAGUE/IMPROVEMENT NEEDED use typed error codes instead of relying on the exception message text
            sender.sendMessage(messages.format("messages.item-command.unknown-attribute", Map.of("attribute", ex.getMessage()),
                    ChatColor.RED + "Unknown attribute: " + ex.getMessage()));
            return true;
        }

        ItemAttributeHandler.ItemDeliveryResult deliveryResult = itemAttributeHandler.deliverItem(target, buildResult.itemStack());
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", target.getName());
        placeholders.put("material", material.name());
        placeholders.put("attributes", buildResult.summary());
        String path = deliveryResult.dropped()
                ? "messages.item-command.item-dropped"
                : "messages.item-command.item-given";
        sender.sendMessage(messages.format(path, placeholders, ChatColor.GREEN + "Gave item."));
        return true;
    }

    /**
     * Attempts to resolve a material from user input, checking literal, upper-cased, and enum name matches in that
     * order to maximize compatibility with how senders typically specify items.
     */
    private Material resolveMaterial(String raw) {
        Material material = Material.matchMaterial(raw);
        if (material != null) {
            return material;
        }
        material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
        if (material != null) {
            return material;
        }
        try {
            return Material.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Supplies tab completion for item attribute arguments, rotating through player, material, plugin, name, value,
     * and optional cap/criteria tokens. When enough arguments are present, suggestions loop so multiple attribute
     * definitions can be entered sequentially.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(playerNames(), args[0]);
        }

        if (args.length == 2) {
            return filter(materials(), args[1]);
        }

        if (args.length >= 3) {
            int relativeIndex = args.length - 3;
            int cyclePosition = relativeIndex % 4;
            if (cyclePosition == 0) {
                return filter(attributePlugins(), args[args.length - 1]);
            }
            if (cyclePosition == 1) {
                return filter(attributeNames(args[args.length - 2]), args[args.length - 1]);
            }
            if (cyclePosition == 2) {
                List<String> options = new ArrayList<>();
                options.add("0");
                options.add("cap=0");
                options.add("cap=");
                options.add("criteria=");
                options.addAll(criteriaOptions());
                return filter(options, args[args.length - 1]);
            }

            List<String> options = new ArrayList<>(attributePlugins());
            options.add("cap=0");
            options.add("cap=");
            options.add("criteria=");
            options.addAll(criteriaOptions());
            return filter(options, args[args.length - 1]);
        }

        return List.of();
    }

    /**
     * Lists plugin namespaces for registered attributes, used as the first token of an attribute definition.
     */
    private List<String> attributePlugins() {
        return CommandParsingUtils.namespacedCompletionsFromIds(attributeFacade.getDefinitionIds(), plugin.getName()).stream()
                .map(value -> value.split("\\.", 2)[0])
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Lists attribute names for the provided plugin namespace so the second token of an attribute definition can be
     * suggested.
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
     * @return sorted online player names for the initial command argument.
     */
    private List<String> playerNames() {
        return plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .sorted()
                .toList();
    }

    /**
     * @return sorted list of material enum names to assist users choosing an item base.
     */
    private List<String> materials() {
        return Arrays.stream(Material.values())
                .map(Enum::name)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Filters the provided options by the given prefix using case-insensitive comparison.
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

    /**
     * Builds {@code criteria=} suggestions from the known {@link TriggerCriterion} keys.
     */
    private List<String> criteriaOptions() {
        return CRITERIA.stream()
                .map(option -> "criteria=" + option)
                .toList();
    }
}
