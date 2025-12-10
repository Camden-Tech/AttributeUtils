package me.baddcamden.attributeutils.command;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.model.ModifierEntry;
import me.baddcamden.attributeutils.model.ModifierOperation;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class PlayerModifierCommand implements CommandExecutor {

    private final Plugin plugin;
    private final AttributeFacade attributeFacade;

    public PlayerModifierCommand(Plugin plugin, AttributeFacade attributeFacade) {
        this.plugin = plugin;
        this.attributeFacade = attributeFacade;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("attributeutils.command.modifiers")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to manage player attribute modifiers.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <player> <add|remove> <plugin.key> [amount] [durationSeconds]");
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

        sender.sendMessage(ChatColor.RED + "Unknown action '" + action + "'. Use add or remove.");
        return true;
    }

    private boolean handleAdd(CommandSender sender, String label, String targetName, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <player> add <plugin.key> <amount> [durationSeconds] [multipliers=key1,key2]");
            return true;
        }

        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + targetName + "' is not online.");
            return true;
        }

        Optional<CommandParsingUtils.NamespacedAttributeKey> key = CommandParsingUtils.parseAttributeKey(sender, args[2]);
        Optional<Double> amount = CommandParsingUtils.parseNumeric(sender, args[3], "modifier amount");
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
                sender.sendMessage(ChatColor.RED + "Unexpected argument '" + arg + "'.");
                return true;
            }

            durationSeconds = CommandParsingUtils.parseNumeric(sender, arg, "duration (seconds)");
            if (durationSeconds.isEmpty()) {
                return true;
            }
            if (durationSeconds.get() <= 0) {
                sender.sendMessage(ChatColor.RED + "Duration must be greater than zero when provided.");
                return true;
            }
        }

        if (key.isEmpty() || amount.isEmpty()) {
            return true;
        }

        ModifierEntry entry = new ModifierEntry(key.get().asString(), ModifierOperation.ADD, amount.get(), durationSeconds.isPresent(), false, true, useMultiplierKeys, multiplierKeys);
        attributeFacade.setPlayerModifier(target.getUniqueId(), key.get().key(), entry);

        durationSeconds.ifPresent(seconds -> plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> attributeFacade.removePlayerModifier(target.getUniqueId(), key.get().key(), entry.key()),
                (long) (seconds * 20)));

        String durationLabel = durationSeconds.map(value -> value + "s temporary").orElse("permanent");
        sender.sendMessage(ChatColor.GREEN + "Applied a " + amount.get() + " modifier to " + key.get().asString()
                + " for player " + target.getName() + " (" + durationLabel + ").");
        return true;
    }

    private boolean handleRemove(CommandSender sender, String label, String targetName, String[] args) {
        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + targetName + "' is not online.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <player> remove <plugin.key>");
            return true;
        }

        Optional<CommandParsingUtils.NamespacedAttributeKey> key = CommandParsingUtils.parseAttributeKey(sender, args[2]);
        if (key.isEmpty()) {
            return true;
        }

        attributeFacade.removePlayerModifier(target.getUniqueId(), key.get().key(), key.get().asString());
        sender.sendMessage(ChatColor.GREEN + "Removed modifiers for " + key.get().asString() + " from player "
                + target.getName() + ".");
        return true;
    }
}
