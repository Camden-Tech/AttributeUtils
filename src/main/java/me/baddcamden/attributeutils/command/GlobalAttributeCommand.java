package me.baddcamden.attributeutils.command;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.model.AttributeDefinition;
import me.baddcamden.attributeutils.model.AttributeInstance;
import me.baddcamden.attributeutils.model.ModifierEntry;
import me.baddcamden.attributeutils.model.ModifierOperation;
import me.baddcamden.attributeutils.persistence.AttributePersistence;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Manages {@code /globalattribute} style commands that mutate global attribute state.
 * <p>
 * Player input maps directly to different baselines and caps:
 * <ul>
 *     <li>{@code default} updates the baseline field on the global instance before any modifiers are considered.</li>
 *     <li>{@code cap} updates the cap used by the computation engine when combining global and player modifiers.</li>
 *     <li>{@code modifier} lets operators push additives/multipliers into the global buckets, mirroring the player
 *     modifier command so permanent and temporary global effects can be represented.</li>
 * </ul>
 * Caps are enforced immediately using {@link me.baddcamden.attributeutils.model.CapConfig#clamp(double, java.util.UUID)}
 * so that subsequent computations never exceed allowed values.
 */
public class GlobalAttributeCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final AttributeFacade attributeFacade;
    private final AttributePersistence persistence;
    private final CommandMessages messages;
    private final String defaultNamespace;

    public GlobalAttributeCommand(Plugin plugin,
                                  AttributeFacade attributeFacade,
                                  AttributePersistence persistence,
                                  CommandMessages messages,
                                  String defaultNamespace) {
        this.plugin = plugin;
        this.attributeFacade = attributeFacade;
        this.persistence = persistence;
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
                    "§eUsage: /" + label + " <default|cap|modifier> ..."));
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "default":
                return handleValueUpdate(sender, label, action, args);
            case "cap":
                return handleCapUpdate(sender, label, args);
            case "modifier":
                return handleModifierUpdate(sender, label, args);
            default:
                sender.sendMessage(messages.format(
                        "messages.global-command.unknown-action",
                        Map.of("action", args[0], "label", label),
                        ChatColor.YELLOW + "Usage: /" + label + " <default|cap|modifier> ..."));
                return true;
        }
    }

    private boolean handleValueUpdate(CommandSender sender, String label, String action, String[] args) {
        if (!"default".equals(action)) {
            sender.sendMessage(messages.format(
                    "messages.global-command.unknown-action",
                    Map.of("action", action, "label", label),
                    ChatColor.YELLOW + "Usage: /" + label + " <default|cap|modifier> ..."));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(messages.format(
                    "messages.global-command.usage-value",
                    Map.of("label", label, "action", action, "layer", "default value"),
                    "§eUsage: /" + label + " " + action + " <plugin> <name> <value> (sets global default value)"));
            return true;
        }

        Optional<CommandParsingUtils.NamespacedAttributeKey> key = CommandParsingUtils.parseAttributeKey(sender, args[1], args[2], messages);
        Optional<Double> value = CommandParsingUtils.parseNumeric(sender, args[3], "default value", messages);
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
        attributeFacade.getOrCreateGlobalInstance(definition.id()).setDefaultBaseValue(clamped);

        persistence.saveGlobalsAsync(attributeFacade);
        sender.sendMessage(messages.format(
                "messages.global-command.updated",
                Map.of("attribute", key.get().asString(), "value", String.valueOf(clamped), "layer", "default value"),
                ChatColor.GREEN + "Global default value for " + key.get().asString() + " set to " + clamped + "."));
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

        String overrideKey = key.get().key().toLowerCase(Locale.ROOT);
        definition.capConfig().overrideMaxValues().put(overrideKey, capValue.get());
        persistCapOverride(definition, overrideKey, capValue.get());
        persistence.saveGlobalsAsync(attributeFacade);
        sender.sendMessage(messages.format(
                "messages.global-command.cap-updated",
                Map.of("attribute", key.get().asString(), "value", String.valueOf(capValue.get())),
                ChatColor.GREEN + "Cap override for " + key.get().asString() + " set to " + capValue.get() + "."));
        return true;
    }

    private boolean handleModifierUpdate(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messages.format(
                    "messages.global-command.modifier.usage",
                    Map.of("label", label),
                    "§eUsage: /" + label + " modifier <add|remove> ..."));
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "add":
                return handleModifierAdd(sender, label, args);
            case "remove":
                return handleModifierRemove(sender, label, args);
            default:
                sender.sendMessage(messages.format(
                        "messages.global-command.modifier.unknown-action",
                        Map.of("action", action),
                        ChatColor.YELLOW + "Usage: /" + label + " modifier <add|remove> ..."));
                return true;
        }
    }

    private boolean handleModifierAdd(CommandSender sender, String label, String[] args) {
        if (args.length < 8) {
            sender.sendMessage(messages.format(
                    "messages.global-command.modifier.usage-add",
                    Map.of("label", label),
                    "§eUsage: /" + label + " modifier add <plugin> <name> <modifierKey> <add|multiply> <amount> " +
                            "[temporary|permanent] [durationSeconds] [scope=default|current|both] [multipliers=key1,key2]"));
            return true;
        }

        Optional<CommandParsingUtils.NamespacedAttributeKey> attributeKey = CommandParsingUtils.parseAttributeKey(sender, args[2], args[3], messages);
        Optional<CommandParsingUtils.NamespacedAttributeKey> modifierKey = CommandParsingUtils.parseAttributeKey(sender, args[4], messages);
        Optional<ModifierOperation> operation = CommandParsingUtils.parseOperation(sender, args[5], messages);
        Optional<Double> amount = CommandParsingUtils.parseNumeric(sender, args[6], "modifier amount", messages);
        Optional<Double> durationSeconds = Optional.empty();
        Boolean temporaryExplicit = null;
        CommandParsingUtils.Scope scope = CommandParsingUtils.Scope.BOTH;
        boolean useMultiplierKeys = false;
        Set<String> multiplierKeys = Collections.emptySet();

        for (int index = 7; index < args.length; index++) {
            String arg = args[index];
            if (arg.equalsIgnoreCase("temporary")) {
                temporaryExplicit = true;
                continue;
            }

            if (arg.equalsIgnoreCase("permanent")) {
                temporaryExplicit = false;
                continue;
            }

            if (arg.startsWith("multipliers=")) {
                Set<String> parsedKeys = new HashSet<>();
                String[] parts = arg.substring("multipliers=".length()).split(",");
                for (String part : parts) {
                    if (!part.isBlank()) {
                        parsedKeys.add(part.toLowerCase(Locale.ROOT));
                    }
                }
                useMultiplierKeys = true;
                multiplierKeys = parsedKeys.isEmpty() ? Collections.emptySet() : parsedKeys;
                continue;
            }

            if (arg.startsWith("scope=")) {
                Optional<CommandParsingUtils.Scope> parsedScope = CommandParsingUtils.parseScope(sender, arg.substring("scope=".length()), messages);
                if (parsedScope.isEmpty()) {
                    return true;
                }
                scope = parsedScope.get();
                continue;
            }

            if (arg.startsWith("duration=")) {
                if (durationSeconds.isPresent()) {
                    sender.sendMessage(messages.format(
                            "messages.global-command.modifier.unexpected-argument",
                            Map.of("argument", arg),
                            "§cUnexpected argument '" + arg + "'."));
                    return true;
                }

                durationSeconds = CommandParsingUtils.parseNumeric(sender, arg.substring("duration=".length()), "duration (seconds)", messages);
                if (durationSeconds.isEmpty()) {
                    return true;
                }
                if (durationSeconds.get() <= 0) {
                    sender.sendMessage(messages.format(
                            "messages.global-command.modifier.positive-duration",
                            "§cDuration must be greater than zero when provided."));
                    return true;
                }
                continue;
            }

            if (durationSeconds.isPresent()) {
                sender.sendMessage(messages.format(
                        "messages.global-command.modifier.unexpected-argument",
                        Map.of("argument", arg),
                        "§cUnexpected argument '" + arg + "'."));
                return true;
            }

            durationSeconds = CommandParsingUtils.parseNumeric(sender, arg, "duration (seconds)", messages);
            if (durationSeconds.isEmpty()) {
                return true;
            }
            if (durationSeconds.get() <= 0) {
                sender.sendMessage(messages.format(
                        "messages.global-command.modifier.positive-duration",
                        "§cDuration must be greater than zero when provided."));
                return true;
            }
        }

        if (attributeKey.isEmpty() || modifierKey.isEmpty() || amount.isEmpty() || operation.isEmpty()) {
            return true;
        }

        if (amount.get() < 0) {
            sender.sendMessage(messages.format(
                    "messages.global-command.modifier.negative-amount",
                    Map.of("amount", args[6]),
                    "§cModifier amounts must be zero or positive."));
            return true;
        }

        if (attributeFacade.getDefinition(attributeKey.get().key()).isEmpty()) {
            sender.sendMessage(messages.format(
                    "messages.global-command.modifier.unknown-attribute",
                    Map.of("attribute", attributeKey.get().asString()),
                    "§cUnknown attribute '" + attributeKey.get().asString() + "'."));
            return true;
        }

        if (durationSeconds.isPresent() && Boolean.FALSE.equals(temporaryExplicit)) {
            sender.sendMessage(messages.format(
                    "messages.global-command.modifier.permanent-duration",
                    "§cPermanent modifiers cannot include a duration."));
            return true;
        }

        boolean temporary = durationSeconds.isPresent() || Boolean.TRUE.equals(temporaryExplicit);

        ModifierEntry entry = new ModifierEntry(modifierKey.get().asString(), operation.get(), amount.get(), temporary,
                scope.appliesToDefault(), scope.appliesToCurrent(), useMultiplierKeys, multiplierKeys, durationSeconds.orElse(null));
        attributeFacade.setGlobalModifier(attributeKey.get().key(), entry);

        durationSeconds.ifPresent(seconds -> plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> attributeFacade.removeGlobalModifier(attributeKey.get().key(), entry.key()),
                (long) (seconds * 20)));

        String durationLabel = temporary
                ? durationSeconds.map(value -> value + "s temporary").orElse("temporary")
                : "permanent";
        sender.sendMessage(messages.format(
                "messages.global-command.modifier.added",
                Map.of(
                        "amount", String.valueOf(amount.get()),
                        "attribute", attributeKey.get().asString(),
                        "duration", durationLabel,
                        "modifier", modifierKey.get().asString(),
                        "operation", operation.get().name()),
                ChatColor.GREEN + "Applied a " + amount.get() + " " + operation.get().name().toLowerCase(Locale.ROOT)
                        + " modifier (" + modifierKey.get().asString() + ") to " + attributeKey.get().asString()
                        + " (" + durationLabel + ")."));
        return true;
    }

    private boolean handleModifierRemove(CommandSender sender, String label, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(messages.format(
                    "messages.global-command.modifier.usage-remove",
                    Map.of("label", label),
                    "§eUsage: /" + label + " modifier remove <plugin> <name> <modifierKey>"));
            return true;
        }

        Optional<CommandParsingUtils.NamespacedAttributeKey> attributeKey = CommandParsingUtils.parseAttributeKey(sender, args[2], args[3], messages);
        Optional<CommandParsingUtils.NamespacedAttributeKey> modifierKey = CommandParsingUtils.parseAttributeKey(sender, args[4], messages);

        if (attributeKey.isEmpty() || modifierKey.isEmpty()) {
            return true;
        }

        if (attributeFacade.getDefinition(attributeKey.get().key()).isEmpty()) {
            sender.sendMessage(messages.format(
                    "messages.global-command.modifier.unknown-attribute",
                    Map.of("attribute", attributeKey.get().asString()),
                    "§cUnknown attribute '" + attributeKey.get().asString() + "'."));
            return true;
        }

        AttributeInstance instance = findGlobalInstance(attributeKey.get().plugin(), attributeKey.get().key());
        String normalizedModifierKey = modifierKey.get().asString().toLowerCase(Locale.ROOT);
        if (instance == null || !instance.getModifiers().containsKey(normalizedModifierKey)) {
            sender.sendMessage(messages.format(
                    "messages.global-command.modifier.missing-modifier",
                    Map.of("attribute", attributeKey.get().asString(), "modifier", modifierKey.get().asString()),
                    ChatColor.RED + "No modifier " + modifierKey.get().asString() + " set for " + attributeKey.get().asString() + "."));
            return true;
        }

        attributeFacade.removeGlobalModifier(attributeKey.get().key(), normalizedModifierKey);
        sender.sendMessage(messages.format(
                "messages.global-command.modifier.removed",
                Map.of("attribute", attributeKey.get().asString(), "modifier", modifierKey.get().asString()),
                ChatColor.GREEN + "Removed modifier " + modifierKey.get().asString() + " for " + attributeKey.get().asString() + "."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> actions = List.of("default", "cap", "modifier");
        if (args.length == 1) {
            return filter(actions, args[0]);
        }

        if (args.length == 2) {
            if (actions.contains(args[0].toLowerCase(Locale.ROOT))) {
                if (args[0].equalsIgnoreCase("modifier")) {
                    return filter(List.of("add", "remove"), args[1]);
                }
                return filter(attributePlugins(), args[1]);
            }
            return filter(actions, args[1]);
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("modifier")) {
                if (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("add")) {
                    return filter(attributePlugins(), args[2]);
                }
                return Collections.emptyList();
            }
            return filter(attributeNames(args[1]), args[2]);
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("modifier")) {
                return filter(attributeNames(args[2]), args[3]);
            }
            return Collections.singletonList("0");
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("modifier")) {
            if (args[1].equalsIgnoreCase("remove")) {
                return filter(globalModifierKeys(args[2], args[3]), args[4]);
            }
            return filter(List.of("plugin.key"), args[4]);
        }

        if (args.length == 6 && args[0].equalsIgnoreCase("modifier") && args[1].equalsIgnoreCase("add")) {
            return filter(List.of("add", "multiply"), args[5]);
        }

        if (args.length == 7 && args[0].equalsIgnoreCase("modifier") && args[1].equalsIgnoreCase("add")) {
            return Collections.singletonList("0");
        }

        if (args.length >= 8 && args[0].equalsIgnoreCase("modifier") && args[1].equalsIgnoreCase("add")) {
            List<String> options = new ArrayList<>();
            options.add("temporary");
            options.add("permanent");
            options.add("duration=");
            options.add("scope=");
            options.add("multipliers=");
            return filter(options, args[args.length - 1]);
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

    private AttributeInstance findGlobalInstance(String pluginSegment, String attributeName) {
        if ((pluginSegment == null || pluginSegment.isBlank()) && (attributeName == null || attributeName.isBlank())) {
            return null;
        }

        String normalizedPlugin = pluginSegment == null ? "" : pluginSegment.toLowerCase(Locale.ROOT);
        String normalizedName = attributeName == null ? "" : attributeName.toLowerCase(Locale.ROOT).replace('-', '_');
        String namespacedId = normalizedPlugin.isBlank() ? normalizedName : normalizedPlugin + "." + normalizedName;

        AttributeInstance instance = attributeFacade.getGlobalInstances().get(namespacedId);
        if (instance != null) {
            return instance;
        }

        return attributeFacade.getGlobalInstances().get(normalizedName);
    }

    private List<String> globalModifierKeys(String pluginSegment, String attributeName) {
        AttributeInstance instance = findGlobalInstance(pluginSegment, attributeName);
        if (instance == null) {
            return List.of();
        }

        return instance.getModifiers().keySet().stream()
                .sorted()
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

    private void persistCapOverride(AttributeDefinition definition, String overrideKey, double capValue) {
        if (definition == null) {
            return;
        }
        ConfigurationSection caps = plugin.getConfig().getConfigurationSection("global-attribute-caps");
        if (caps == null) {
            caps = plugin.getConfig().createSection("global-attribute-caps");
        }

        String configKey = definition.id().toLowerCase(Locale.ROOT).replace('_', '-');
        ConfigurationSection capSection = caps.getConfigurationSection(configKey);
        if (capSection == null) {
            double min = definition.capConfig().globalMin();
            double max = definition.capConfig().globalMax();
            caps.set(configKey, null);
            capSection = caps.createSection(configKey);
            capSection.set("min", min);
            capSection.set("max", max);
        }

        ConfigurationSection overrides = capSection.getConfigurationSection("overrides");
        if (overrides == null) {
            overrides = capSection.createSection("overrides");
        }
        overrides.set(overrideKey, capValue);
        plugin.saveConfig();
    }
}
