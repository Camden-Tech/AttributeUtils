package me.baddcamden.attributeutils.command;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.handler.entity.EntityAttributeHandler;
import me.baddcamden.attributeutils.model.ModifierEntry;
import me.baddcamden.attributeutils.model.ModifierOperation;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Applies or removes modifiers from player buckets. User supplied values are written into the player's
 * {@link me.baddcamden.attributeutils.model.AttributeInstance} modifier collection, preserving whether the modifier is
 * temporary so the cleanup stage can purge it later. Caps are not changed here; they are respected when the
 * computation engine aggregates stages.
 */
public class PlayerModifierCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final AttributeFacade attributeFacade;
    private final EntityAttributeHandler entityAttributeHandler;
    private final CommandMessages messages;

    public PlayerModifierCommand(Plugin plugin,
                                 AttributeFacade attributeFacade,
                                 EntityAttributeHandler entityAttributeHandler) {
        this.plugin = plugin;
        this.attributeFacade = attributeFacade;
        this.entityAttributeHandler = entityAttributeHandler;
        this.messages = new CommandMessages(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("attributeutils.command.modifiers")) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.no-permission",
                    "§cYou do not have permission to manage player attribute modifiers."));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.usage",
                    java.util.Map.of("label", label),
                    "§eUsage: /" + label + " <player> <add|remove> ..."));
            return true;
        }

        String targetName = args[0];
        String action = args[1].toLowerCase();

        if (action.equals("add")) {
            return handleAdd(sender, label, targetName, args);
        }

        if (action.equals("remove")) {
            return handleRemove(sender, label, targetName, args);
        }

        sender.sendMessage(messages.format(
                "messages.modifier-command.unknown-action",
                java.util.Map.of("action", action),
                "§cUnknown action '" + action + "'. Use add or remove."));
        return true;
    }

    private boolean handleAdd(CommandSender sender, String label, String targetName, String[] args) {
        if (args.length < 7) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.usage-add",
                    java.util.Map.of("label", label),
                    "§eUsage: /" + label + " <player> add <plugin> <name> <modifierKey> <add|multiply> <amount> [durationSeconds] [scope=default|current|both] [multipliers=key1,key2]"));
            return true;
        }

        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.player-offline",
                    java.util.Map.of("player", targetName),
                    "§cPlayer '" + targetName + "' is not online."));
            return true;
        }

        Optional<CommandParsingUtils.NamespacedAttributeKey> attributeKey = CommandParsingUtils.parseAttributeKey(sender, args[2], args[3], messages);
        Optional<CommandParsingUtils.NamespacedAttributeKey> modifierKey = CommandParsingUtils.parseAttributeKey(sender, args[4], messages);
        Optional<ModifierOperation> operation = CommandParsingUtils.parseOperation(sender, args[5], messages);
        Optional<Double> amount = CommandParsingUtils.parseNumeric(sender, args[6], "modifier amount", messages);
        Optional<Double> durationSeconds = Optional.empty();
        CommandParsingUtils.Scope scope = CommandParsingUtils.Scope.BOTH;
        boolean useMultiplierKeys = false;
        Set<String> multiplierKeys = Collections.emptySet();

        for (int index = 7; index < args.length; index++) {
            String arg = args[index];
            if (arg.startsWith("multipliers=")) {
                String[] parts = arg.substring("multipliers=".length()).split(",");
                Set<String> parsedKeys = new HashSet<>();
                Arrays.stream(parts)
                        .map(String::trim)
                        .filter(part -> !part.isEmpty())
                        .forEach(part -> parsedKeys.add(part.toLowerCase()));
                useMultiplierKeys = true;
                multiplierKeys = parsedKeys.isEmpty() ? Collections.emptySet() : parsedKeys;
                continue;
            }

            if (arg.startsWith("scope=")) {
                Optional<CommandParsingUtils.Scope> parsedScope = CommandParsingUtils.parseScope(sender, arg.substring("scope=".length()), messages);
                if (parsedScope.isEmpty()) {
                    return true;
                }
                scope = parsedScope.get();
                continue;
            }

            if (durationSeconds.isPresent()) {
                sender.sendMessage(messages.format(
                        "messages.modifier-command.unexpected-argument",
                        java.util.Map.of("argument", arg),
                        "§cUnexpected argument '" + arg + "'."));
                return true;
            }

            durationSeconds = CommandParsingUtils.parseNumeric(sender, arg, "duration (seconds)", messages);
            if (durationSeconds.isEmpty()) {
                return true;
            }
            if (durationSeconds.get() <= 0) {
                sender.sendMessage(messages.format(
                        "messages.modifier-command.positive-duration",
                        "§cDuration must be greater than zero when provided."));
                return true;
            }
        }

        if (attributeKey.isEmpty() || modifierKey.isEmpty() || amount.isEmpty() || operation.isEmpty()) {
            return true;
        }

        if (amount.get() < 0) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.negative-amount",
                    java.util.Map.of("amount", args[6]),
                    "§cModifier amounts must be zero or positive."));
            return true;
        }

        if (attributeFacade.getDefinition(attributeKey.get().key()).isEmpty()) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.unknown-attribute",
                    java.util.Map.of("attribute", attributeKey.get().asString()),
                    "§cUnknown attribute '" + attributeKey.get().asString() + "'."));
            return true;
        }

        ModifierEntry entry = new ModifierEntry(modifierKey.get().asString(), operation.get(), amount.get(), durationSeconds.isPresent(), scope.appliesToDefault(), scope.appliesToCurrent(), useMultiplierKeys, multiplierKeys);
        attributeFacade.setPlayerModifier(target.getUniqueId(), attributeKey.get().key(), entry);
        entityAttributeHandler.applyVanillaAttribute(target, attributeKey.get().key());

        durationSeconds.ifPresent(seconds -> plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> {
                    attributeFacade.removePlayerModifier(target.getUniqueId(), attributeKey.get().key(), entry.key());
                    if (target.isOnline()) {
                        entityAttributeHandler.applyVanillaAttribute(target, attributeKey.get().key());
                    }
                },
                (long) (seconds * 20)));

        String durationLabel = durationSeconds.map(value -> value + "s temporary").orElse("permanent");
        sender.sendMessage(messages.format(
                "messages.modifier-command.added",
                java.util.Map.of(
                        "amount", String.valueOf(amount.get()),
                        "attribute", attributeKey.get().asString(),
                        "player", target.getName(),
                        "duration", durationLabel,
                        "modifier", modifierKey.get().asString(),
                        "operation", operation.get().name()),
                ChatColor.GREEN + "Applied a " + amount.get() + " " + operation.get().name().toLowerCase(Locale.ROOT)
                        + " modifier (" + modifierKey.get().asString() + ") to " + attributeKey.get().asString()
                        + " for player " + target.getName() + " (" + durationLabel + ")."));
        return true;
    }

    private boolean handleRemove(CommandSender sender, String label, String targetName, String[] args) {
        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.player-offline",
                    java.util.Map.of("player", targetName),
                    "§cPlayer '" + targetName + "' is not online."));
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.usage-remove",
                    java.util.Map.of("label", label),
                    "§eUsage: /" + label + " <player> remove <plugin> <name> <modifierKey>"));
            return true;
        }

        Optional<CommandParsingUtils.NamespacedAttributeKey> attributeKey = CommandParsingUtils.parseAttributeKey(sender, args[2], args[3], messages);
        Optional<CommandParsingUtils.NamespacedAttributeKey> modifierKey = CommandParsingUtils.parseAttributeKey(sender, args[4], messages);
        if (attributeKey.isEmpty() || modifierKey.isEmpty()) {
            return true;
        }

        if (attributeFacade.getDefinition(attributeKey.get().key()).isEmpty()) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.unknown-attribute",
                    java.util.Map.of("attribute", attributeKey.get().asString()),
                    "§cUnknown attribute '" + attributeKey.get().asString() + "'."));
            return true;
        }

        attributeFacade.removePlayerModifier(target.getUniqueId(), attributeKey.get().key(), modifierKey.get().asString());
        entityAttributeHandler.applyVanillaAttribute(target, attributeKey.get().key());
        sender.sendMessage(messages.format(
                "messages.modifier-command.removed",
                java.util.Map.of("attribute", attributeKey.get().asString(), "player", target.getName(), "modifier", modifierKey.get().asString()),
                ChatColor.GREEN + "Removed modifier " + modifierKey.get().asString() + " for " + attributeKey.get().asString() + " from player "
                        + target.getName() + "."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(playerNames(), args[0]);
        }

        if (args.length == 2) {
            return filter(List.of("add", "remove"), args[1]);
        }

        if (args.length == 3) {
            return filter(attributePlugins(), args[2]);
        }

        if (args.length == 4) {
            return filter(attributeNames(args[2]), args[3]);
        }

        if (args.length == 5 && args[1].equalsIgnoreCase("add")) {
            return filter(namespacedAttributeKeys(), args[4]);
        }

        if (args.length == 5 && args[1].equalsIgnoreCase("remove")) {
            return filter(namespacedAttributeKeys(), args[4]);
        }

        if (args.length == 6 && args[1].equalsIgnoreCase("add")) {
            return filter(List.of("add", "multiply"), args[5]);
        }

        if (args.length == 7 && args[1].equalsIgnoreCase("add")) {
            return Collections.singletonList("1");
        }

        if (args.length == 8 && args[1].equalsIgnoreCase("add")) {
            List<String> options = new ArrayList<>();
            options.add("60");
            options.addAll(scopeOptions());
            options.add("multipliers=");
            return filter(options, args[7]);
        }

        if (args.length >= 8 && args[1].equalsIgnoreCase("add")) {
            return filter(scopeAndMultiplierOptions(), args[args.length - 1]);
        }

        return Collections.emptyList();
    }

    private List<String> scopeOptions() {
        return List.of("scope=default", "scope=current", "scope=both");
    }

    private List<String> scopeAndMultiplierOptions() {
        List<String> options = new ArrayList<>(scopeOptions());
        options.add("multipliers=");
        return options;
    }

    private List<String> attributePlugins() {
        return CommandParsingUtils.namespacedCompletions(attributeFacade.getDefinitions(), plugin.getName()).stream()
                .map(value -> value.split("\\.", 2)[0])
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> attributeNames(String pluginName) {
        String normalized = pluginName == null ? "" : pluginName.toLowerCase(Locale.ROOT);
        return CommandParsingUtils.namespacedCompletions(attributeFacade.getDefinitions(), plugin.getName()).stream()
                .filter(value -> value.startsWith(normalized + "."))
                .map(value -> value.split("\\.", 2)[1])
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private List<String> namespacedAttributeKeys() {
        return CommandParsingUtils.namespacedCompletions(attributeFacade.getDefinitions(), plugin.getName());
    }

    private List<String> playerNames() {
        return plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .sorted()
                .toList();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(java.util.Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(java.util.Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
