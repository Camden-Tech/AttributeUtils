package me.baddcamden.attributeutils.command;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.model.AttributeDefinition;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class GlobalAttributeCommand implements CommandExecutor, TabCompleter {

    private final AttributeFacade attributeFacade;
    private final CommandMessages messages;

    public GlobalAttributeCommand(AttributeFacade attributeFacade, CommandMessages messages) {
        this.attributeFacade = attributeFacade;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("attributeutils.command.globals")) {
            sender.sendMessage(messages.format(
                    "messages.global-command.no-permission",
                    "§cYou do not have permission to edit global attribute defaults or caps."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(messages.format(
                    "messages.global-command.usage",
                    Map.of("label", label),
                    "§eUsage: /" + label + " <plugin.key> <baseValue> [cap=<capValue>]"));
            return true;
        }

        Optional<CommandParsingUtils.NamespacedAttributeKey> key = CommandParsingUtils.parseAttributeKey(sender, args[0], messages);
        Optional<Double> baseValue = CommandParsingUtils.parseNumeric(sender, args[1], "base value", messages);
        Double capOverride = null;
        if (args.length >= 3) {
            Optional<Double> cap = CommandParsingUtils.parseCapOverride(sender, args[2], messages);
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
            sender.sendMessage(messages.format(
                    "messages.global-command.unknown-attribute",
                    Map.of("attribute", key.get().asString()),
                    "§cUnknown attribute: " + key.get().key()));
            return true;
        }

        if (baseValue.get() < definition.capConfig().globalMin()) {
            sender.sendMessage(messages.format(
                    "messages.global-command.negative-base",
                    Map.of("attribute", key.get().asString(), "minimum", String.valueOf(definition.capConfig().globalMin())),
                    "§cBase values must be at least " + definition.capConfig().globalMin() + "."));
            return true;
        }

        double base = definition.capConfig().clamp(baseValue.get(), null);
        attributeFacade.getOrCreateGlobalInstance(definition.id()).setBaseValue(base);
        if (capOverride != null) {
            // update override map by replacing entry
            definition.capConfig().overrideMaxValues().put(key.get().key(), capOverride);
        }

        sender.sendMessage(messages.format(
                "messages.global-command.updated",
                Map.of("attribute", key.get().asString(), "value", String.valueOf(base)),
                ChatColor.GREEN + "Global base for " + key.get().asString() + " set to " + base + "."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(attributeKeys(), args[0]);
        }

        if (args.length == 2) {
            return Collections.singletonList("0");
        }

        if (args.length == 3) {
            return filter(Collections.singletonList("cap="), args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> attributeKeys() {
        return attributeFacade.getDefinitions().stream()
                .map(AttributeDefinition::id)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
