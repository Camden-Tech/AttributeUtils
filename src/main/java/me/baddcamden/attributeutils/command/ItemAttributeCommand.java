package me.baddcamden.attributeutils.command;

import me.baddcamden.attributeutils.handler.item.ItemAttributeHandler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ItemAttributeCommand implements CommandExecutor {

    private final Plugin plugin;
    private final ItemAttributeHandler itemAttributeHandler;

    public ItemAttributeCommand(Plugin plugin, ItemAttributeHandler itemAttributeHandler) {
        this.plugin = plugin;
        this.itemAttributeHandler = itemAttributeHandler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("attributeutils.command.items")) {
            sender.sendMessage(formatMessage("messages.item-command.no-permission", Map.of(),
                    ChatColor.RED + "You do not have permission to generate attribute items."));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(formatMessage("messages.item-command.usage", Map.of("label", label),
                    ChatColor.YELLOW + "Usage: /" + label + " <player> <material> <plugin.key> <value> [cap=<cap> ...]"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(formatMessage("messages.item-command.player-offline", Map.of("player", args[0]),
                    ChatColor.RED + "Player '" + args[0] + "' is not online."));
            return true;
        }

        Material material = resolveMaterial(args[1]);
        if (material == null) {
            sender.sendMessage(formatMessage("messages.item-command.invalid-material", Map.of("material", args[1]),
                    ChatColor.RED + "Unknown material '" + args[1] + "'."));
            return true;
        }

        List<CommandParsingUtils.AttributeDefinition> definitions = CommandParsingUtils.parseAttributeDefinitions(sender, args, 2);
        if (definitions.isEmpty()) {
            return true;
        }

        ItemAttributeHandler.ItemBuildResult buildResult;
        try {
            buildResult = itemAttributeHandler.buildAttributeItem(material, definitions);
        } catch (IllegalArgumentException ex) {
            if ("unsupported-material".equalsIgnoreCase(ex.getMessage())) {
                sender.sendMessage(formatMessage("messages.item-command.unsupported-material", Map.of("material", material.name()),
                        ChatColor.RED + "That material cannot hold attribute data."));
                return true;
            }
            sender.sendMessage(formatMessage("messages.item-command.unknown-attribute", Map.of("attribute", ex.getMessage()),
                    ChatColor.RED + "Unknown attribute: " + ex.getMessage()));
            return true;
        }

        ItemAttributeHandler.ItemDeliveryResult deliveryResult = itemAttributeHandler.deliverItem(target, buildResult.itemStack());
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", target.getName());
        placeholders.put("material", material.name());
        placeholders.put("attributes", buildResult.summary());
        String path = deliveryResult.dropped()
                ? "messages.item-command.item-dropped"
                : "messages.item-command.item-given";
        sender.sendMessage(formatMessage(path, placeholders, ChatColor.GREEN + "Gave item."));
        return true;
    }

    private Material resolveMaterial(String raw) {
        Material material = Material.matchMaterial(raw);
        if (material != null) {
            return material;
        }
        material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
        if (material != null) {
            return material;
        }
        try {
            return Material.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String formatMessage(String path, Map<String, String> placeholders, String fallback) {
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
