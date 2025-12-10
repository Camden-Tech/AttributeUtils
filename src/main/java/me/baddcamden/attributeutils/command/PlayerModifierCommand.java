package me.baddcamden.attributeutils.command;

import me.baddcamden.attributeutils.api.AttributeFacade;
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
import java.util.Optional;
import java.util.Set;

public class PlayerModifierCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final AttributeFacade attributeFacade;
    private final CommandMessages messages;

    public PlayerModifierCommand(Plugin plugin, AttributeFacade attributeFacade) {
        this.plugin = plugin;
        this.attributeFacade = attributeFacade;
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
                    "§eUsage: /" + label + " <player> <add|remove> <plugin.key> [amount] [durationSeconds]"));
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
        if (args.length < 4) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.usage-add",
                    java.util.Map.of("label", label),
                    "§eUsage: /" + label + " <player> add <plugin.key> <amount> [durationSeconds] [multipliers=key1,key2]"));
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

        Optional<CommandParsingUtils.NamespacedAttributeKey> key = CommandParsingUtils.parseAttributeKey(sender, args[2], messages);
        Optional<Double> amount = CommandParsingUtils.parseNumeric(sender, args[3], "modifier amount", messages);
        Optional<Double> durationSeconds = Optional.empty();
        boolean useMultiplierKeys = false;
        Set<String> multiplierKeys = Collections.emptySet();

        for (int index = 4; index < args.length; index++) {
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

        if (key.isEmpty() || amount.isEmpty()) {
            return true;
        }

        if (amount.get() < 0) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.negative-amount",
                    java.util.Map.of("amount", args[3]),
                    "§cModifier amounts must be zero or positive."));
            return true;
        }

        if (attributeFacade.getDefinition(key.get().key()).isEmpty()) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.unknown-attribute",
                    java.util.Map.of("attribute", key.get().asString()),
                    "§cUnknown attribute '" + key.get().asString() + "'."));
            return true;
        }

        ModifierEntry entry = new ModifierEntry(key.get().asString(), ModifierOperation.ADD, amount.get(), durationSeconds.isPresent(), false, true, useMultiplierKeys, multiplierKeys);
        attributeFacade.setPlayerModifier(target.getUniqueId(), key.get().key(), entry);

        durationSeconds.ifPresent(seconds -> plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> attributeFacade.removePlayerModifier(target.getUniqueId(), key.get().key(), entry.key()),
                (long) (seconds * 20)));

        String durationLabel = durationSeconds.map(value -> value + "s temporary").orElse("permanent");
        sender.sendMessage(messages.format(
                "messages.modifier-command.added",
                java.util.Map.of(
                        "amount", String.valueOf(amount.get()),
                        "attribute", key.get().asString(),
                        "player", target.getName(),
                        "duration", durationLabel),
                ChatColor.GREEN + "Applied a " + amount.get() + " modifier to " + key.get().asString()
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

        if (args.length < 3) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.usage-remove",
                    java.util.Map.of("label", label),
                    "§eUsage: /" + label + " <player> remove <plugin.key>"));
            return true;
        }

        Optional<CommandParsingUtils.NamespacedAttributeKey> key = CommandParsingUtils.parseAttributeKey(sender, args[2], messages);
        if (key.isEmpty()) {
            return true;
        }

        if (attributeFacade.getDefinition(key.get().key()).isEmpty()) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.unknown-attribute",
                    java.util.Map.of("attribute", key.get().asString()),
                    "§cUnknown attribute '" + key.get().asString() + "'."));
            return true;
        }

        attributeFacade.removePlayerModifier(target.getUniqueId(), key.get().key(), key.get().asString());
        sender.sendMessage(messages.format(
                "messages.modifier-command.removed",
                java.util.Map.of("attribute", key.get().asString(), "player", target.getName()),
                ChatColor.GREEN + "Removed modifiers for " + key.get().asString() + " from player "
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
            return filter(attributeKeys(), args[2]);
        }

        if (args.length == 4 && args[1].equalsIgnoreCase("add")) {
            return Collections.singletonList("1");
        }

        if (args.length == 5 && args[1].equalsIgnoreCase("add")) {
            return Collections.singletonList("60");
        }

        if (args.length >= 5 && args[1].equalsIgnoreCase("add")) {
            return filter(Collections.singletonList("multipliers="), args[args.length - 1]);
        }

        return Collections.emptyList();
    }

    private List<String> attributeKeys() {
        return attributeFacade.getDefinitions().stream()
                .map(me.baddcamden.attributeutils.model.AttributeDefinition::id)
                .sorted(Comparator.naturalOrder())
                .toList();
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
