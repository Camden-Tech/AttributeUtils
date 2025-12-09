package me.baddcamden.attributeutils.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;

import java.util.List;

public class EntityAttributeCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("attributeutils.command.entities")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to spawn attributed entities.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <entityType> <plugin.key> <value> [cap=<cap> ...]");
            return true;
        }

        EntityType entityType = EntityType.fromName(args[0]);
        if (entityType == null) {
            sender.sendMessage(ChatColor.RED + "Unknown entity type '" + args[0] + "'.");
            return true;
        }

        List<CommandParsingUtils.AttributeDefinition> definitions = CommandParsingUtils.parseAttributeDefinitions(sender, args, 1);
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

        sender.sendMessage(ChatColor.GREEN + "Would spawn a(n) " + entityType.name() + " with attributes: " + summary
                + ". Entity creation will be wired once attribute computation is ready.");
        return true;
    }
}
