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

/**
 * Manages {@code /globalattribute} style commands that mutate global attribute state.
 * <p>
 * Player input maps directly to different baselines and caps:
 * <ul>
 *     <li>{@code default/current/base} update the corresponding baseline fields on the global instance before any
 *     modifiers are considered.</li>
 *     <li>{@code cap} updates the cap used by the computation engine when combining global and player modifiers.</li>
 * </ul>
 * Caps are enforced immediately using {@link me.baddcamden.attributeutils.model.CapConfig#clamp(double, java.util.UUID)}
 * so that subsequent computations never exceed allowed values.
 */
public class GlobalAttributeCommand implements CommandExecutor, TabCompleter {

    private final AttributeFacade attributeFacade;
    private final CommandMessages messages;
    private final String defaultNamespace;

    public GlobalAttributeCommand(AttributeFacade attributeFacade, CommandMessages messages, String defaultNamespace) {
        this.attributeFacade = attributeFacade;
        this.messages = messages;
        this.defaultNamespace = defaultNamespace == null ? "" : defaultNamespace.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("attributeutils.command.globals")) {
            sender.sendMessage(messages.format(
                    "messages.global-command.no-permission",
                    "§cYou do not have permission to edit global attribute defaults or caps."));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(messages.format(
                    "messages.global-command.usage",
                    Map.of("label", label),
                    "§eUsage: /" + label + " <default|current|base|cap> ..."));
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "default":
            case "current":
            case "base":
                return handleValueUpdate(sender, label, action, args);
            case "cap":
                return handleCapUpdate(sender, label, args);
            default:
                sender.sendMessage(messages.format(
                        "messages.global-command.unknown-action",
                        Map.of("action", args[0], "label", label),
                        ChatColor.YELLOW + "Usage: /" + label + " <default|current|base|cap> ..."));
                return true;
        }
    }

    private boolean handleValueUpdate(CommandSender sender, String label, String action, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(messages.format(
                    "messages.global-command.usage-value",
                    Map.of("label", label, "action", action),
                    "§eUsage: /" + label + " " + action + " <plugin> <name> <value>"));
            return true;
        }

        Optional<CommandParsingUtils.NamespacedAttributeKey> key = CommandParsingUtils.parseAttributeKey(sender, args[1], args[2], messages);
        Optional<Double> value = CommandParsingUtils.parseNumeric(sender, args[3], "base value", messages);
        if (key.isEmpty() || value.isEmpty()) {
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

        if (value.get() < definition.capConfig().globalMin()) {
            sender.sendMessage(messages.format(
                    "messages.global-command.negative-base",
                    Map.of("attribute", key.get().asString(), "minimum", String.valueOf(definition.capConfig().globalMin())),
                    "§cBase values must be at least " + definition.capConfig().globalMin() + "."));
            return true;
        }

        double clamped = definition.capConfig().clamp(value.get(), null);
        switch (action) {
            case "default":
                attributeFacade.getOrCreateGlobalInstance(definition.id()).setDefaultBaseValue(clamped);
                break;
            case "current":
                attributeFacade.getOrCreateGlobalInstance(definition.id()).setCurrentBaseValue(clamped);
                break;
            default:
                attributeFacade.getOrCreateGlobalInstance(definition.id()).setBaseValue(clamped);
                break;
        }

        sender.sendMessage(messages.format(
                "messages.global-command.updated",
                Map.of("attribute", key.get().asString(), "value", String.valueOf(clamped)),
                ChatColor.GREEN + "Global base for " + key.get().asString() + " set to " + clamped + "."));
        return true;
    }

    private boolean handleCapUpdate(CommandSender sender, String label, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(messages.format(
                    "messages.global-command.usage-cap",
                    Map.of("label", label),
                    "§eUsage: /" + label + " cap <plugin> <name> <capValue>"));
            return true;
        }

        Optional<CommandParsingUtils.NamespacedAttributeKey> key = CommandParsingUtils.parseAttributeKey(sender, args[1], args[2], messages);
        Optional<Double> capValue = CommandParsingUtils.parseNumeric(sender, args[3], "cap value", messages);
        if (key.isEmpty() || capValue.isEmpty()) {
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

        if (capValue.get() < definition.capConfig().globalMin()) {
            sender.sendMessage(messages.format(
                    "messages.global-command.cap-below-min",
                    Map.of("attribute", key.get().asString(), "minimum", String.valueOf(definition.capConfig().globalMin())),
                    "§cCaps cannot be below the global minimum."));
            return true;
        }

        definition.capConfig().overrideMaxValues().put(key.get().key(), capValue.get());
        sender.sendMessage(messages.format(
                "messages.global-command.cap-updated",
                Map.of("attribute", key.get().asString(), "value", String.valueOf(capValue.get())),
                ChatColor.GREEN + "Cap override for " + key.get().asString() + " set to " + capValue.get() + "."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> actions = List.of("default", "current", "base", "cap");
        if (args.length == 1) {
            return filter(actions, args[0]);
        }

        if (args.length == 2) {
            if (actions.contains(args[0].toLowerCase(Locale.ROOT))) {
                return filter(attributePlugins(), args[1]);
            }
            return filter(actions, args[1]);
        }

        if (args.length == 3) {
            return filter(attributeNames(args[1]), args[2]);
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("cap")) {
                return Collections.singletonList("1");
            }
            return Collections.singletonList("0");
        }

        return Collections.emptyList();
    }

    private List<String> attributePlugins() {
        return CommandParsingUtils.namespacedCompletionsFromIds(attributeFacade.getDefinitionIds(), pluginName()).stream()
                .map(value -> value.split("\\.", 2)[0])
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> attributeNames(String plugin) {
        String normalized = plugin == null ? "" : plugin.toLowerCase(Locale.ROOT);
        return CommandParsingUtils.namespacedCompletionsFromIds(attributeFacade.getDefinitionIds(), pluginName()).stream()
                .filter(value -> value.startsWith(normalized + "."))
                .map(value -> value.split("\\.", 2)[1])
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private String pluginName() {
        return defaultNamespace;
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
