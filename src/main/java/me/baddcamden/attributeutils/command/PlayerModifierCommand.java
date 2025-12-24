package me.baddcamden.attributeutils.command;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.handler.entity.EntityAttributeHandler;
import me.baddcamden.attributeutils.model.ModifierEntry;
import me.baddcamden.attributeutils.model.ModifierOperation;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Applies or removes modifiers from player buckets. User supplied values are written into the player's
 * {@link me.baddcamden.attributeutils.model.AttributeInstance} modifier collection, preserving whether the modifier is
 * temporary so the cleanup stage can purge it later. Caps are not changed here; they are respected when the
 * computation engine aggregates stages.
 */
public class PlayerModifierCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final AttributeFacade attributeFacade;
    private final EntityAttributeHandler entityAttributeHandler;
    private final CommandMessages messages;

    /**
     * Creates a new player modifier command handler.
     *
     * @param plugin                 owning plugin for scheduling and configuration.
     * @param attributeFacade        facade used to read and mutate player attribute state.
     * @param entityAttributeHandler utility for updating vanilla attributes after modifier changes.
     */
    public PlayerModifierCommand(Plugin plugin,
                                 AttributeFacade attributeFacade,
                                 EntityAttributeHandler entityAttributeHandler) {
        this.plugin = plugin;
        this.attributeFacade = attributeFacade;
        this.entityAttributeHandler = entityAttributeHandler;
        this.messages = new CommandMessages(plugin);
    }

    /**
     * Delegates to add or remove handlers depending on the user-supplied action after verifying permissions and basic
     * argument presence. Unknown actions return usage information without mutating state.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("attributeutils.command.modifiers")) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.no-permission",
                    "§cYou do not have permission to manage player attribute modifiers."));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.usage",
                    java.util.Map.of("label", label),
                    "§eUsage: /" + label + " <player> <add|remove> ..."));
            return true;
        }

        String targetName = args[0];
        String action = args[1].toLowerCase();

        if (action.equals("add")) {
            return handleAdd(sender, label, targetName, args);
        }

        if (action.equals("remove")) {
            return handleRemove(sender, label, targetName, args);
        }

        sender.sendMessage(messages.format(
                "messages.modifier-command.unknown-action",
                java.util.Map.of("action", action),
                "§cUnknown action '" + action + "'. Use add or remove."));
        return true;
    }

    /**
     * Parses modifier additions for a target player, validating attribute existence, optional duration and scope
     * tokens, and multiplier lists. Successful calls write the modifier, apply vanilla attributes, and schedule
     * cleanup tasks for temporary entries.
     */
    private boolean handleAdd(CommandSender sender, String label, String targetName, String[] args) {
        if (args.length < 7) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.usage-add",
                    java.util.Map.of("label", label),
                    "§eUsage: /" + label + " <player> add <plugin> <name> <modifierKey> <add|multiply> <amount> [temporary|permanent] [duration=<seconds>] [scope=default|current|both] [multipliers=key1,key2]"));
            return true;
        }

        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.player-offline",
                    java.util.Map.of("player", targetName),
                    "§cPlayer '" + targetName + "' is not online."));
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
                String[] parts = arg.substring("multipliers=".length()).split(",");
                Set<String> parsedKeys = new HashSet<>();
                Arrays.stream(parts)
                        .map(String::trim)
                        .filter(part -> !part.isEmpty())
                        .forEach(part -> parsedKeys.add(part.toLowerCase()));
                useMultiplierKeys = true;
                multiplierKeys = parsedKeys.isEmpty() ? Collections.emptySet() : parsedKeys;
                //VAGUE/IMPROVEMENT NEEDED Validate multiplier keys against known definitions to fail fast on typos.
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
                            "messages.modifier-command.unexpected-argument",
                            java.util.Map.of("argument", arg),
                            "§cUnexpected argument '" + arg + "'."));
                    return true;
                }

                durationSeconds = CommandParsingUtils.parseNumeric(sender, arg.substring("duration=".length()), "duration (seconds)", messages);
                if (durationSeconds.isEmpty()) {
                    return true;
                }
                if (durationSeconds.get() <= 0) {
                    sender.sendMessage(messages.format(
                            "messages.modifier-command.positive-duration",
                            "§cDuration must be greater than zero when provided."));
                    return true;
                }
                continue;
            }

            if (durationSeconds.isPresent()) {
                sender.sendMessage(messages.format(
                        "messages.modifier-command.unexpected-argument",
                        java.util.Map.of("argument", arg),
                        "§cUnexpected argument '" + arg + "'."));
                return true;
            }

            durationSeconds = CommandParsingUtils.parseNumeric(sender, arg, "duration (seconds)", messages);
            if (durationSeconds.isEmpty()) {
                return true;
            }
            if (durationSeconds.get() <= 0) {
                sender.sendMessage(messages.format(
                        "messages.modifier-command.positive-duration",
                        "§cDuration must be greater than zero when provided."));
                return true;
            }
        }

        if (attributeKey.isEmpty() || modifierKey.isEmpty() || amount.isEmpty() || operation.isEmpty()) {
            return true;
        }

        if (attributeFacade.getDefinition(attributeKey.get().key()).isEmpty()) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.unknown-attribute",
                    java.util.Map.of("attribute", attributeKey.get().asString()),
                    "§cUnknown attribute '" + attributeKey.get().asString() + "'."));
            return true;
        }

        if (durationSeconds.isPresent() && Boolean.FALSE.equals(temporaryExplicit)) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.permanent-duration",
                    "§cPermanent modifiers cannot include a duration."));
            return true;
        }

        boolean temporary = durationSeconds.isPresent() || Boolean.TRUE.equals(temporaryExplicit);

        ModifierEntry entry = new ModifierEntry(modifierKey.get().asString(), operation.get(), amount.get(), temporary, scope.appliesToDefault(), scope.appliesToCurrent(), useMultiplierKeys, multiplierKeys, durationSeconds.orElse(null));
        attributeFacade.setPlayerModifier(target.getUniqueId(), attributeKey.get().key(), entry);
        entityAttributeHandler.applyVanillaAttribute(target, attributeKey.get().key());

        // The scheduler expects ticks (20 per second), so multiply seconds to align with Minecraft timing.
        durationSeconds.ifPresent(seconds -> plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> {
                    attributeFacade.removePlayerModifier(target.getUniqueId(), attributeKey.get().key(), entry.key());
                    if (target.isOnline()) {
                        entityAttributeHandler.applyVanillaAttribute(target, attributeKey.get().key());
                    }
                },
                (long) (seconds * 20)));

        String durationLabel = temporary
                ? durationSeconds.map(value -> value + "s temporary").orElse("temporary")
                : "permanent";
        sender.sendMessage(messages.format(
                "messages.modifier-command.added",
                java.util.Map.of(
                        "amount", String.valueOf(amount.get()),
                        "attribute", attributeKey.get().asString(),
                        "player", target.getName(),
                        "duration", durationLabel,
                        "modifier", modifierKey.get().asString(),
                        "operation", operation.get().name()),
                ChatColor.GREEN + "Applied a " + amount.get() + " " + operation.get().name().toLowerCase(Locale.ROOT)
                        + " modifier (" + modifierKey.get().asString() + ") to " + attributeKey.get().asString()
                        + " for player " + target.getName() + " (" + durationLabel + ")."));
        return true;
    }

    /**
     * Removes a modifier entry for a specific attribute on the provided player, applying the vanilla attribute update
     * after mutation and returning detailed error messages for missing players or attributes.
     */
    private boolean handleRemove(CommandSender sender, String label, String targetName, String[] args) {
        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.player-offline",
                    java.util.Map.of("player", targetName),
                    "§cPlayer '" + targetName + "' is not online."));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.usage-remove",
                    java.util.Map.of("label", label),
                    "§eUsage: /" + label + " <player> remove <attributeKey> <modifierKey>"));
            return true;
        }

        Optional<CommandParsingUtils.NamespacedAttributeKey> attributeKey = CommandParsingUtils.parseAttributeKey(sender, args[2], messages);
        Optional<CommandParsingUtils.NamespacedAttributeKey> modifierKey = CommandParsingUtils.parseAttributeKey(sender, args[3], messages);
        if (attributeKey.isEmpty() || modifierKey.isEmpty()) {
            return true;
        }

        if (attributeFacade.getDefinition(attributeKey.get().key()).isEmpty()) {
            sender.sendMessage(messages.format(
                    "messages.modifier-command.unknown-attribute",
                    java.util.Map.of("attribute", attributeKey.get().asString()),
                    "§cUnknown attribute '" + attributeKey.get().asString() + "'."));
            return true;
        }

        attributeFacade.removePlayerModifier(target.getUniqueId(), attributeKey.get().key(), modifierKey.get().asString());
        entityAttributeHandler.applyVanillaAttribute(target, attributeKey.get().key());
        sender.sendMessage(messages.format(
                "messages.modifier-command.removed",
                java.util.Map.of("attribute", attributeKey.get().asString(), "player", target.getName(), "modifier", modifierKey.get().asString()),
                ChatColor.GREEN + "Removed modifier " + modifierKey.get().asString() + " for " + attributeKey.get().asString() + " from player "
                        + target.getName() + "."));
        return true;
    }

    /**
     * Supplies tab completion for the add/remove flows, dynamically suggesting player names, action keywords,
     * attribute ids, modifier keys, numeric defaults, and optional flags based on the current argument position.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(playerNames(), args[0]);
        }

        if (args.length == 2) {
            return filter(List.of("add", "remove"), args[1]);
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("add")) {
            return filter(attributePlugins(), args[2]);
        }

        if (args.length == 4 && args[1].equalsIgnoreCase("add")) {
            return filter(attributeNames(args[2]), args[3]);
        }

        if (args.length == 5 && args[1].equalsIgnoreCase("add")) {
            return filter(List.of(defaultModifierKey(args[3])), args[4]);
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
            return filter(activeAttributeKeys(args[0]), args[2]);
        }

        if (args.length == 4 && args[1].equalsIgnoreCase("remove")) {
            return filter(activeModifierKeys(args[0], args[2]), args[3]);
        }

        if (args.length == 6 && args[1].equalsIgnoreCase("add")) {
            return filter(List.of("add", "multiply"), args[5]);
        }

        if (args.length == 7 && args[1].equalsIgnoreCase("add")) {
            return Collections.singletonList("1");
        }

        if (args.length >= 8 && args[1].equalsIgnoreCase("add")) {
            List<String> options = new ArrayList<>();
            boolean hasTemporary = false;
            boolean hasPermanent = false;
            boolean hasDuration = false;
            boolean hasScope = false;
            boolean hasMultipliers = false;

            for (int index = 7; index < args.length - 1; index++) {
                String option = args[index];
                if (option.equalsIgnoreCase("temporary")) {
                    hasTemporary = true;
                    continue;
                }
                if (option.equalsIgnoreCase("permanent")) {
                    hasPermanent = true;
                    continue;
                }
                if (option.startsWith("scope=")) {
                    hasScope = true;
                    continue;
                }
                if (option.startsWith("multipliers=")) {
                    hasMultipliers = true;
                    continue;
                }
                if (option.startsWith("duration=")) {
                    hasDuration = true;
                    continue;
                }
                if (isNumeric(option)) {
                    hasDuration = true;
                }
            }

            if (!hasTemporary && !hasPermanent) {
                options.add("temporary");
                options.add("permanent");
            }
            if (!hasDuration) {
                options.add("duration=60");
            }
            if (!hasScope) {
                options.addAll(scopeOptions());
            }
            if (!hasMultipliers) {
                options.add("multipliers=");
            }

            return filter(options, args[args.length - 1]);
        }

        return Collections.emptyList();
    }

    /**
     * @return scope option tokens used after the primary add parameters.
     */
    private List<String> scopeOptions() {
        return List.of("scope=default", "scope=current", "scope=both");
    }

    /**
     * Lists plugin namespaces pulled from registered attributes for tab completion.
     */
    private List<String> attributePlugins() {
        return CommandParsingUtils.namespacedCompletionsFromIds(attributeFacade.getDefinitionIds(), plugin.getName()).stream()
                .map(value -> value.split("\\.", 2)[0])
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Lists attribute names for a given plugin namespace so the attribute id can be completed.
     */
    private List<String> attributeNames(String pluginName) {
        String normalized = pluginName == null ? "" : pluginName.toLowerCase(Locale.ROOT);
        return CommandParsingUtils.namespacedCompletionsFromIds(attributeFacade.getDefinitionIds(), plugin.getName()).stream()
                .filter(value -> value.startsWith(normalized + "."))
                .map(value -> value.split("\\.", 2)[1])
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /**
     * Collects attribute ids for the specified player that currently have modifiers. Used to scope remove suggestions.
     */
    private List<String> activeAttributeKeys(String playerName) {
        Player player = plugin.getServer().getPlayerExact(playerName);
        if (player == null) {
            return List.of();
        }

        Set<String> attributeIds = new HashSet<>();
        attributeFacade.getPlayerInstances(player.getUniqueId()).forEach((id, instance) -> {
            if (!instance.getModifiers().isEmpty()) {
                attributeIds.add(instance.getDefinition().id());
            }
        });

        return CommandParsingUtils.namespacedCompletionsFromIds(attributeIds, plugin.getName());
    }

    /**
     * Retrieves modifier keys associated with the provided attribute for the given player.
     */
    private List<String> activeModifierKeys(String playerName, String attributeId) {
        Player player = plugin.getServer().getPlayerExact(playerName);
        if (player == null || attributeId == null || attributeId.isBlank()) {
            return List.of();
        }

        String normalizedId = attributeId.toLowerCase(Locale.ROOT);
        if (normalizedId.contains(".")) {
            normalizedId = normalizedId.split("\\.", 2)[1];
        }
        final String lookupId = normalizedId;
        //VAGUE/IMPROVEMENT NEEDED Namespaces are discarded here; if multiple plugins share the same local id the match may be ambiguous.
        return attributeFacade.getPlayerInstances(player.getUniqueId()).entrySet().stream()
                .filter(entry -> entry.getKey().equals(lookupId))
                .map(entry -> entry.getValue().getModifiers().keySet())
                .findFirst()
                .map(keys -> keys.stream().sorted().toList())
                .orElse(List.of());
    }

    /**
     * @return sorted online player names for quick targeting.
     */
    private List<String> playerNames() {
        return plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .sorted()
                .toList();
    }

    /**
     * Builds a fallback modifier key using the plugin namespace and normalized attribute name for add tab completion.
     */
    private String defaultModifierKey(String attributeName) {
        String normalizedAttribute = normalizeAttributeName(attributeName);
        String pluginName = plugin.getName() == null ? "" : plugin.getName().toLowerCase(Locale.ROOT);
        return pluginName.isBlank() ? normalizedAttribute : pluginName + "." + normalizedAttribute;
    }

    /**
     * Normalizes attribute names to lower-case, replaces dashes, and strips namespaces when present.
     */
    private String normalizeAttributeName(String attributeName) {
        if (attributeName == null) {
            return "";
        }
        String normalized = attributeName.toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.contains(".")) {
            return normalized.split("\\.", 2)[1];
        }
        return normalized;
    }

    /**
     * Filters the provided options by the supplied prefix using case-insensitive comparison.
     */
    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(java.util.Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(java.util.Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }

    /**
     * Determines whether the provided value can be parsed as a double. Used to detect implicit duration arguments.
     */
    private boolean isNumeric(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
