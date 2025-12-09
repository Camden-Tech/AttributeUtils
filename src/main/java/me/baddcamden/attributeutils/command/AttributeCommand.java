package me.baddcamden.attributeutils.command;

import me.baddcamden.attributeutils.api.AttributeApi;
import me.baddcamden.attributeutils.api.AttributeComputation;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class AttributeCommand implements CommandExecutor {

    private final AttributeApi attributeApi;
    private final Plugin plugin;

    public AttributeCommand(AttributeApi attributeApi, Plugin plugin) {
        this.attributeApi = attributeApi;
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
        attributeApi.getRegisteredDefinitions().forEach(definition -> {
            if (sender instanceof Player player) {
                attributeApi.queryAttribute(definition.key(), player)
                        .ifPresent(computation -> sender.sendMessage(buildPlayerLine(computation)));
                return;
            }

            sender.sendMessage(ChatColor.GRAY + " - " + definition.key() + ChatColor.WHITE +
                    ": base=" + definition.baseValue());
        });
        return true;
    }

    private String buildPlayerLine(AttributeComputation computation) {
        return ChatColor.GRAY + " - " + computation.key() + ChatColor.WHITE +
                " vanilla=" + computation.vanillaBaseline() +
                " base=" + computation.baseValue() +
                " global=" + computation.globalModifierTotal() +
                " player=" + computation.playerModifierTotal() +
                " final=" + computation.finalValue();
    }
}
