package me.baddcamden.attributeutils.command;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.model.AttributeValueStages;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AttributeCommand implements CommandExecutor, TabCompleter {

    private final AttributeFacade attributeFacade;
    private final Plugin plugin;
    private final CommandMessages messages;

    public AttributeCommand(AttributeFacade attributeFacade, Plugin plugin) {
        this.attributeFacade = attributeFacade;
        this.plugin = plugin;
        this.messages = new CommandMessages(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("attributeutils.reload")) {
                sender.sendMessage(messages.format(
                        "messages.attribute-command.reload-no-permission",
                        "§cYou do not have permission to reload attributes."));
                return true;
            }

            plugin.reloadConfig();
            sender.sendMessage(messages.format(
                    "messages.attribute-command.reload-success",
                    "§aAttribute configuration reloaded."));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(
                    "messages.attribute-command.invalid-sender",
                    "§eAttributes are player-focused; use in-game for details."));
            return true;
        }

        sender.sendMessage(messages.format(
                "messages.attribute-command.header",
                "§bRegistered attributes:"));
        attributeFacade.getDefinitions().forEach(definition -> {
            AttributeValueStages stages = attributeFacade.compute(definition.id(), (Player) sender);
            sender.sendMessage(buildPlayerLine(definition.displayName(), stages));
        });
        return true;
    }

    private String buildPlayerLine(String displayName, AttributeValueStages stages) {
        return ChatColor.GRAY + " - " + displayName + ChatColor.WHITE +
                " rawDefault=" + stages.rawDefault() +
                " default=" + stages.defaultFinal() +
                " rawCurrent=" + stages.rawCurrent() +
                " final=" + stages.currentFinal();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1
                && sender.hasPermission("attributeutils.reload")
                && "reload".toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return Collections.singletonList("reload");
        }
        return Collections.emptyList();
    }
}
