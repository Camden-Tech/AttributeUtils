package me.baddcamden.attributeutils.commands;

import me.baddcamden.attributeutils.AttributeUtilitiesPlugin;
import me.baddcamden.attributeutils.attributes.AttributeManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public class GlobalCommand implements CommandExecutor {
    private final AttributeUtilitiesPlugin plugin;
    private final AttributeManager attributeManager;
    private final FileConfiguration config;

    public GlobalCommand(AttributeUtilitiesPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.attributeManager = plugin.getAttributeManager();
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("attributeutils.command.globals")) {
            sender.sendMessage(color(config.getString("messages.global-command.no-permission")));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(color(formatLabel(config.getString("messages.global-command.usage"), label)));
            return true;
        }

        String action = args[0].toLowerCase();
        if (!action.equals("base") && !action.equals("default")) {
            sender.sendMessage(color(formatLabel(config.getString("messages.global-command.unknown-action"), label)));
            return true;
        }

        String attributeName = args[2].replace("-", "_").toLowerCase();
        double value;
        try {
            value = Double.parseDouble(args[3]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(color(config.getString("messages.shared.invalid-numeric")
                    .replace("{label}", "value")
                    .replace("{value}", args[3])));
            return true;
        }

        if (value < 0) {
            sender.sendMessage(color(config.getString("messages.shared.negative-value")));
            return true;
        }

        boolean updated = attributeManager.updateBase(attributeName, value);
        if (!updated) {
            sender.sendMessage(color(config.getString("messages.global-command.unknown-attribute")
                    .replace("{attribute}", attributeName)));
            return true;
        }

        sender.sendMessage(color(config.getString("messages.global-command.updated")
                .replace("{attribute}", attributeName)
                .replace("{value}", String.valueOf(value))));
        return true;
    }

    private String color(String message) {
        if (message == null) {
            return ChatColor.RED + "An unknown error occurred.";
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private String formatLabel(String message, String label) {
        if (message == null) {
            return null;
        }
        return message.replace("{label}", label);
    }
}
