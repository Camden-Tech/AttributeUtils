package me.baddcamden.attributeutils.commands;

import me.baddcamden.attributeutils.AttributeUtilitiesPlugin;
import me.baddcamden.attributeutils.attributes.AttributeManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.stream.Collectors;

public class AttributesCommand implements CommandExecutor {
    private final AttributeUtilitiesPlugin plugin;
    private final AttributeManager attributeManager;
    private final FileConfiguration config;

    public AttributesCommand(AttributeUtilitiesPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.attributeManager = plugin.getAttributeManager();
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("attributeutils.reload")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to reload attributes.");
                return true;
            }

            plugin.reloadPluginConfig();
            sender.sendMessage(color(config.getString("messages.attribute-command.reload-success", "&aAttribute configuration reloaded.")));
            return true;
        }

        String header = color(config.getString("messages.attribute-command.header", "&bRegistered attributes:"));
        sender.sendMessage(header);
        String list = attributeManager.getRegisteredAttributeKeys()
                .stream()
                .sorted()
                .collect(Collectors.joining(", "));
        sender.sendMessage(ChatColor.GRAY + list);
        return true;
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}
