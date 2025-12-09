package me.baddcamden.attributeutils.command;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.model.AttributeDefinition;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Optional;

public class GlobalAttributeCommand implements CommandExecutor {

    private final AttributeFacade attributeFacade;

    public GlobalAttributeCommand(AttributeFacade attributeFacade) {
        this.attributeFacade = attributeFacade;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("attributeutils.command.globals")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to edit global attribute defaults or caps.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <plugin.key> <baseValue> [cap=<capValue>]");
            return true;
        }

        Optional<CommandParsingUtils.NamespacedAttributeKey> key = CommandParsingUtils.parseAttributeKey(sender, args[0]);
        Optional<Double> baseValue = CommandParsingUtils.parseNumeric(sender, args[1], "base value");
        Double capOverride = null;
        if (args.length >= 3) {
            Optional<Double> cap = CommandParsingUtils.parseCapOverride(sender, args[2]);
            if (cap.isEmpty()) {
                return true;
            }
            capOverride = cap.get();
        }

        if (key.isEmpty() || baseValue.isEmpty()) {
            return true;
        }

        AttributeDefinition definition = attributeFacade.getDefinition(key.get().key()).orElse(null);
        if (definition == null) {
            sender.sendMessage(ChatColor.RED + "Unknown attribute: " + key.get().key());
            return true;
        }

        double base = definition.capConfig().clamp(baseValue.get(), null);
        attributeFacade.getOrCreateGlobalInstance(definition.id()).setBaseValue(base);
        if (capOverride != null) {
            // update override map by replacing entry
            definition.capConfig().overrideMaxValues().put(key.get().key(), capOverride);
        }

        sender.sendMessage(ChatColor.GREEN + "Global base for " + key.get().asString() + " set to " + base + ".");
        return true;
    }
}
