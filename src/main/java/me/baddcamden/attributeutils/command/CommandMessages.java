package me.baddcamden.attributeutils.command;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.Map;

/**
 * Helper for rendering command feedback. Messages often include placeholders for stage terminology (default/current,
 * modifiers, caps) so command handlers can communicate how user input maps to the computation pipeline.
 */
public class CommandMessages {

    private final Plugin plugin;

    /**
     * Creates a formatter backed by the plugin configuration so commands can render user-facing feedback strings.
     *
     * @param plugin plugin whose config contains localized message templates.
     */
    public CommandMessages(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Formats a message path using the configuration values and a fallback when the path is missing or blank.
     *
     * @param path     configuration path to read.
     * @param fallback default message to return when the path is unavailable.
     * @return formatted and colorized message.
     */
    public String format(String path, String fallback) {
        return format(path, Map.of(), fallback);
    }

    /**
     * Formats a message path, replacing placeholder tokens before colorizing the result. If the configured value is
     * missing or empty, the provided fallback is used instead.
     *
     * @param path          configuration path to read.
     * @param placeholders  placeholder values to inject into the message using {key} syntax.
     * @param fallback      default message to return when the path is unavailable.
     * @return formatted and colorized message.
     */
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
