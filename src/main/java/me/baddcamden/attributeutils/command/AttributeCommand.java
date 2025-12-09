package me.baddcamden.attributeutils.command;

import me.baddcamden.attributeutils.service.AttributeService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class AttributeCommand implements CommandExecutor {

    private final AttributeService attributeService;
    private final Plugin plugin;

    public AttributeCommand(AttributeService attributeService, Plugin plugin) {
        this.attributeService = attributeService;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("attributeutils.reload")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to reload attributes.");
                return true;
            }

            plugin.reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "Attribute configuration reloaded.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.YELLOW + "Attributes are player-focused; use in-game for details.");
            return true;
        }

        sender.sendMessage(ChatColor.AQUA + "Registered attributes:");
        attributeService.getAttributes().values().forEach(attribute ->
                sender.sendMessage(ChatColor.GRAY + " - " + attribute.getKey() + ChatColor.WHITE + ": " + attribute.getValue())
        );
        return true;
    }
}
