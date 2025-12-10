package me.baddcamden.attributeutils.command;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.Map;

public class CommandMessages {

    private final Plugin plugin;

    public CommandMessages(Plugin plugin) {
        this.plugin = plugin;
    }

    public String format(String path, String fallback) {
        return format(path, Map.of(), fallback);
    }

    public String format(String path, Map<String, String> placeholders, String fallback) {
        FileConfiguration config = plugin.getConfig();
        String message = config.getString(path, fallback);
        if (message == null || message.isBlank()) {
            message = fallback;
        }
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
