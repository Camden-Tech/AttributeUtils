package me.baddcamden.attributeutils.command;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Objects;

/**
 * Helper for rendering command feedback. Messages often include placeholders for stage terminology (default/current,
 * modifiers, caps) so command handlers can communicate how user input maps to the computation pipeline.
 */
public class CommandMessages {

    /**
     * Owning plugin used for configuration access.  This is expected to remain valid for the lifetime of the
     * {@link CommandMessages} instance.
     */
    private final Plugin plugin;

    /**
     * Create a new message formatter bound to the provided plugin configuration.
     *
     * @param plugin plugin that supplies configuration values for messages
     */
    public CommandMessages(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Render a message that does not require placeholder substitution. This overload ensures existing callers can
     * pass a raw configuration path and fallback while delegating to the placeholder-aware implementation.
     *
     * @param path      configuration path to look up
     * @param fallback  message to use when no configuration is available
     * @return formatted message with color codes resolved
     */
    public String format(String path, String fallback) {
        return format(path, Map.of(), fallback);
    }

    /**
     * Resolve a message from configuration, swap in any supplied placeholders, and translate color codes.
     *
     * @param path         configuration path to look up
     * @param placeholders replacements keyed by placeholder name without braces
     * @param fallback     message to use if the configuration is missing or blank
     * @return message ready for sending to a {@code CommandSender}
     */
    public String format(String path, Map<String, String> placeholders, String fallback) {
        FileConfiguration config = plugin.getConfig();
        String message = config.getString(path, fallback);
        if (message == null || message.isBlank()) {
            message = fallback;
        }
        //VAGUE/IMPROVEMENT NEEDED placeholder replacement does not validate missing placeholders or handle nested braces
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
