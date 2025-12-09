package me.baddcamden.attributeutils.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

public class PlayerModifierCommand implements CommandExecutor {

    private final Plugin plugin;

    public PlayerModifierCommand(Plugin plugin) {
        this.plugin = plugin;
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
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <player> add <plugin.key> <amount> [durationSeconds]");
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
        if (args.length >= 5) {
            durationSeconds = CommandParsingUtils.parseNumeric(sender, args[4], "duration (seconds)");
            if (durationSeconds.isPresent() && durationSeconds.get() <= 0) {
                sender.sendMessage(ChatColor.RED + "Duration must be greater than zero when provided.");
                return true;
            }
        }

        if (key.isEmpty() || amount.isEmpty() || (args.length >= 5 && durationSeconds.isEmpty())) {
            return true;
        }

        String durationLabel = durationSeconds.map(value -> value + "s temporary").orElse("permanent");
        sender.sendMessage(ChatColor.GREEN + "Would apply a " + amount.get() + " modifier to " + key.get().asString()
                + " for player " + target.getName() + " (" + durationLabel + "). Computation and persistence pending.");
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

        sender.sendMessage(ChatColor.GREEN + "Would remove modifiers for " + key.get().asString() + " from player "
                + target.getName() + ". Storage hooks will be added later.");
        return true;
    }
}
