package me.baddcamden.attributeutils.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Optional;

public class GlobalAttributeCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("attributeutils.command.globals")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to edit global attribute defaults or caps.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <plugin.key> <baseValue> [cap=<capValue>]");
            return true;
        }

        Optional<CommandParsingUtils.NamespacedAttributeKey> key = CommandParsingUtils.parseAttributeKey(sender, args[0]);
        Optional<Double> baseValue = CommandParsingUtils.parseNumeric(sender, args[1], "base value");
        Double capOverride = null;
        if (args.length >= 3) {
            Optional<Double> cap = CommandParsingUtils.parseCapOverride(sender, args[2]);
            if (cap.isEmpty()) {
                return true;
            }
            capOverride = cap.get();
        }

        if (key.isEmpty() || baseValue.isEmpty()) {
            return true;
        }

        double base = baseValue.get();
        double cap = capOverride == null ? base : capOverride;
        if (cap < base) {
            sender.sendMessage(ChatColor.RED + "Cap overrides must be greater than or equal to the base value.");
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + "Global defaults for " + key.get().asString() + " would be set to " + base
                + " with a cap of " + cap + " once persistence is available.");
        return true;
    }
}
