package me.baddcamden.attributeutils.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ItemAttributeCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("attributeutils.command.items")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to generate attribute items.");
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <player> <material> <plugin.key> <value> [cap=<cap> ...]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + args[0] + "' is not online.");
            return true;
        }

        Material material = Material.matchMaterial(args[1]);
        if (material == null) {
            sender.sendMessage(ChatColor.RED + "Unknown material '" + args[1] + "'.");
            return true;
        }

        List<CommandParsingUtils.AttributeDefinition> definitions = CommandParsingUtils.parseAttributeDefinitions(sender, args, 2);
        if (definitions.isEmpty()) {
            return true;
        }

        StringBuilder summary = new StringBuilder();
        definitions.forEach(definition -> {
            summary.append(definition.getKey().asString()).append("=").append(definition.getValue());
            definition.getCapOverride().ifPresent(cap -> summary.append(" (cap ").append(cap).append(")"));
            summary.append(", ");
        });
        if (summary.length() > 2) {
            summary.setLength(summary.length() - 2);
        }

        sender.sendMessage(ChatColor.GREEN + "Would give " + target.getName() + " a " + material.name()
                + " with attributes: " + summary + ". Item construction and NBT application pending.");
        return true;
    }
}
