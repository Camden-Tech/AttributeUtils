package me.baddcamden.attributeutils.command;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import me.baddcamden.attributeutils.model.ModifierOperation;

/**
 * Utility functions for turning user input into normalized attribute keys and numeric values.
 * Parsed keys are tied back to specific modifier buckets or baselines depending on the invoking command, while numeric
 * inputs are validated before being applied so invalid parameters can be reported directly to the command sender.
 */
public final class CommandParsingUtils {

    /**
     * Validation pattern for plugin segments, restricting to alphanumeric characters, underscores, and hyphens.
     */
    private static final Pattern PLUGIN_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    /**
     * Validation pattern for key segments, restricting to alphanumeric characters, underscores, dashes, and dots.
     */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$");

    /**
     * Hidden constructor because this class only exposes static helpers.
     */
    private CommandParsingUtils() {
    }

    /**
     * Parses a plugin and key segment into a normalized namespaced key while sending validation errors back to the
     * command sender. Plugin segments are lower-cased, and key segments are lower-cased with dashes converted to
     * underscores for consistency with stored attribute ids.
     *
     * @param sender         command sender to notify of validation issues.
     * @param pluginSegment  raw plugin segment provided by the user.
     * @param keySegment     raw key segment provided by the user.
     * @param messages       message formatter for error responses.
     * @return optional containing the normalized key when valid.
     */
    public static Optional<NamespacedAttributeKey> parseAttributeKey(CommandSender sender,
                                                                    String pluginSegment,
                                                                    String keySegment,
                                                                    CommandMessages messages) {
        if (pluginSegment == null || pluginSegment.isBlank() || keySegment == null || keySegment.isBlank()) {
            sender.sendMessage(messages.format(
                    "messages.shared.attribute-key-segments",
                    Map.of("plugin", pluginSegment == null ? "" : pluginSegment, "name", keySegment == null ? "" : keySegment),
                    "§cAttribute keys must include both a plugin and name segment."));
            return Optional.empty();
        }

        if (!PLUGIN_PATTERN.matcher(pluginSegment).matches() || !KEY_PATTERN.matcher(keySegment).matches()) {
            sender.sendMessage(messages.format(
                    "messages.shared.attribute-key-format",
                    Map.of("plugin", pluginSegment, "name", keySegment),
                    "§cAttribute keys must use alphanumeric plugin and name segments (e.g. example max_health)."));
            return Optional.empty();
        }

        String normalizedPlugin = pluginSegment.toLowerCase(Locale.ROOT);
        String normalizedKey = keySegment.toLowerCase(Locale.ROOT).replace('-', '_');
        return Optional.of(new NamespacedAttributeKey(normalizedPlugin, normalizedKey));
    }

    /**
     * Parses a single dot-delimited attribute key (plugin.key) and validates its format. Errors are reported to the
     * sender before returning an empty optional.
     *
     * @param sender   command sender to notify.
     * @param raw      raw key string provided by the user.
     * @param messages message formatter for error responses.
     * @return optional namespaced key when valid.
     */
    public static Optional<NamespacedAttributeKey> parseAttributeKey(CommandSender sender, String raw, CommandMessages messages) {
        if (raw == null || raw.isBlank()) {
            sender.sendMessage(messages.format(
                    "messages.shared.attribute-key-segments",
                    Map.of("plugin", "", "name", ""),
                    "§cAttribute keys must include both a plugin and name segment."));
            return Optional.empty();
        }

        String[] parts = raw.split("\\.", 2);
        if (parts.length < 2) {
            sender.sendMessage(messages.format(
                    "messages.shared.attribute-key-segments",
                    Map.of("plugin", raw, "name", ""),
                    "§cAttribute keys must include both a plugin and name segment."));
            return Optional.empty();
        }

        return parseAttributeKey(sender, parts[0], parts[1], messages);
    }

    /**
     * Attempts to parse a numeric argument and reports errors to the sender if parsing fails.
     *
     * @param sender   command sender to notify on failure.
     * @param raw      raw numeric string.
     * @param label    user-friendly label for the numeric value.
     * @param messages message formatter for error responses.
     * @return optional double containing the parsed value.
     */
    public static Optional<Double> parseNumeric(CommandSender sender, String raw, String label, CommandMessages messages) {
        if (raw == null) {
            sender.sendMessage(messages.format(
                    "messages.shared.invalid-numeric",
                    Map.of("label", label, "value", ""),
                    "§cInvalid " + label + ": ''. Provide a numeric value."));
            return Optional.empty();
        }

        try {
            return Optional.of(Double.parseDouble(raw));
        } catch (NumberFormatException ex) {
            sender.sendMessage(messages.format(
                    "messages.shared.invalid-numeric",
                    Map.of("label", label, "value", raw),
                    "§cInvalid " + label + ": '" + raw + "'. Provide a numeric value."));
            return Optional.empty();
        }
    }

    /**
     * Validates and parses a cap override argument in the form {@code cap=<value>}. Ensures the cap is non-negative
     * and informs the sender when the input is malformed.
     *
     * @param sender   command sender to notify on validation failures.
     * @param raw      raw cap override string.
     * @param messages message formatter for error responses.
     * @return optional containing the parsed cap when valid.
     */
    public static Optional<Double> parseCapOverride(CommandSender sender, String raw, CommandMessages messages) {
        if (!raw.startsWith("cap=")) {
            sender.sendMessage(messages.format(
                    "messages.shared.cap-format",
                    "§cCap overrides must be provided as cap=<value>."));
            return Optional.empty();
        }

        String numericPortion = raw.substring("cap=".length());
        Optional<Double> capValue = parseNumeric(sender, numericPortion, "cap override", messages);
        capValue.ifPresent(value -> {
            if (value < 0) {
                sender.sendMessage(messages.format(
                        "messages.shared.cap-nonnegative",
                        Map.of("value", numericPortion),
                        "§cCap overrides must be zero or positive."));
            }
        });
        if (capValue.isPresent() && capValue.get() < 0) {
            return Optional.empty();
        }
        return capValue;
    }

    /**
     * Parses a modifier operation from user input, accepting add or multiply variants. Invalid values trigger an
     * error message to the sender.
     *
     * @param sender   command sender to notify.
     * @param raw      raw operation string.
     * @param messages message formatter for error responses.
     * @return optional containing the parsed operation.
     */
    public static Optional<ModifierOperation> parseOperation(CommandSender sender, String raw, CommandMessages messages) {
        try {
            return Optional.of(ModifierOperation.valueOf(raw.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(messages.format(
                    "messages.shared.operation-invalid",
                    Map.of("value", raw),
                    "§cOperation must be ADD or MULTIPLY, not '" + raw + "'."));
            return Optional.empty();
        }
    }

    /**
     * Parses a scope token describing which baselines a command should target. Accepted values are default, current,
     * or both; invalid entries are reported to the sender.
     *
     * @param sender   command sender to notify.
     * @param raw      raw scope string.
     * @param messages message formatter for error responses.
     * @return optional containing the parsed scope.
     */
    public static Optional<Scope> parseScope(CommandSender sender, String raw, CommandMessages messages) {
        String lower = raw.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "default":
                return Optional.of(Scope.DEFAULT);
            case "current":
                return Optional.of(Scope.CURRENT);
            case "both":
                return Optional.of(Scope.BOTH);
            default:
                sender.sendMessage(messages.format(
                        "messages.shared.scope-invalid",
                        Map.of("value", raw),
                        "§cScope must be default, current, or both."));
                return Optional.empty();
        }
    }

    /**
     * Parses attribute definitions from an argument array starting at the given index. Each definition consumes a
     * plugin segment, key segment, and numeric value, followed by optional cap and criteria tokens. Validation errors
     * are sent to the sender and result in an empty list.
     *
     * @param sender          command sender to notify.
     * @param args            full argument array.
     * @param startIndex      index where attribute parsing should begin.
     * @param messages        message formatter for error responses.
     * @param attributeExists predicate to verify whether an attribute id is registered; may be null to skip checks.
     * @return list of parsed attribute definitions, or empty if validation fails.
     */
    public static List<AttributeDefinition> parseAttributeDefinitions(
            CommandSender sender,
            String[] args,
            int startIndex,
            CommandMessages messages,
            Predicate<String> attributeExists) {
        return parseAttributeDefinitions(sender, args, startIndex, messages, attributeExists, null, null);
    }

    /**
     * Parses attribute definitions with optional validation for allowed criteria values. Each definition consumes a
     * plugin segment, key segment, and numeric value, followed by optional {@code cap=} and {@code criteria=} tokens.
     * Validation errors are sent to the sender and result in an empty list.
     *
     * @param sender             command sender to notify.
     * @param args               full argument array.
     * @param startIndex         index where attribute parsing should begin.
     * @param messages           message formatter for error responses.
     * @param attributeExists    predicate to verify whether an attribute id is registered; may be null to skip checks.
     * @param criterionValidator predicate to validate criteria tokens; may be null to allow all criteria.
     * @param allowedCriteria    collection of allowed criteria names to echo back in error messages.
     * @return list of parsed attribute definitions, or empty if validation fails.
     */
    public static List<AttributeDefinition> parseAttributeDefinitions(
            CommandSender sender,
            String[] args,
            int startIndex,
            CommandMessages messages,
            Predicate<String> attributeExists,
            Predicate<String> criterionValidator,
            Collection<String> allowedCriteria) {
        if (startIndex >= args.length) {
            sender.sendMessage(messages.format(
                    "messages.shared.definition-missing",
                    "§cProvide attribute definitions as <plugin> <key> <value> [cap=<cap> criteria=<criteria>]."));
            return Collections.emptyList();
        }

        List<AttributeDefinition> definitions = new ArrayList<>();
        int index = startIndex;
        while (index < args.length) {
            if (index + 2 >= args.length) {
                sender.sendMessage(messages.format(
                        "messages.shared.definition-missing-value",
                        Map.of("plugin", args[index], "name", index + 1 < args.length ? args[index + 1] : ""),
                        "§cAttribute definitions must include a numeric value after the key."));
                return Collections.emptyList();
            }

            Optional<NamespacedAttributeKey> key = parseAttributeKey(sender, args[index], args[index + 1], messages);
            Optional<Double> value = parseNumeric(sender, args[index + 2], "attribute value", messages);
            if (key.isEmpty() || value.isEmpty()) {
                return Collections.emptyList();
            }

            if (attributeExists != null && !attributeExists.test(key.get().key().toLowerCase(Locale.ROOT))) {
                //VAGUE/IMPROVEMENT NEEDED plugin segment is ignored during existence checks, which may allow
                // collisions between different plugins.
                sender.sendMessage(messages.format(
                        "messages.shared.unknown-attribute",
                        Map.of("attribute", key.get().asString()),
                        "§cUnknown attribute '" + key.get().asString() + "'."));
                return Collections.emptyList();
            }

            if (value.get() < 0) {
                sender.sendMessage(messages.format(
                        "messages.shared.negative-value",
                        Map.of("attribute", key.get().asString(), "value", args[index + 2]),
                        "§cAttribute values must be zero or positive."));
                return Collections.emptyList();
            }

            Double capOverride = null;
            String criterion = null;
            index += 3;

            while (index < args.length && (args[index].startsWith("cap=") || args[index].startsWith("criteria="))) {
                String token = args[index];
                if (token.startsWith("cap=")) {
                    Optional<Double> cap = parseCapOverride(sender, token, messages);
                    if (cap.isEmpty()) {
                        return Collections.emptyList();
                    }
                    capOverride = cap.get();
                } else if (token.startsWith("criteria=")) {
                    String rawCriterion = token.substring("criteria=".length());
                    if (rawCriterion.isBlank()) {
                        sender.sendMessage(messages.format(
                                "messages.shared.criteria-missing",
                                Map.of(),
                                "§cCriteria must specify a trigger such as held, inventory, or equipped."));
                        return Collections.emptyList();
                    }
                    if (criterionValidator != null && !criterionValidator.test(rawCriterion)) {
                        String allowed = allowedCriteria == null ? "" : String.join(", ", allowedCriteria);
                        sender.sendMessage(messages.format(
                                "messages.shared.criteria-invalid",
                                Map.of("criteria", rawCriterion, "allowed", allowed),
                                "§cInvalid criteria '" + rawCriterion + "'." + (allowed.isEmpty() ? "" : " Allowed: " + allowed)));
                        return Collections.emptyList();
                    }
                    criterion = rawCriterion;
                }

                index++;
            }

            definitions.add(new AttributeDefinition(key.get(), value.get(), capOverride, criterion));
        }

        return definitions;
    }

    /**
     * Indicates which baselines a command should target.
     */
    public enum Scope {
        /** Default baseline only. */
        DEFAULT,
        /** Active baseline only. */
        CURRENT,
        /** Both default and active baselines. */
        BOTH;

        /**
         * @return true when the scope includes the default baseline.
         */
        public boolean appliesToDefault() {
            return this == DEFAULT || this == BOTH;
        }

        /**
         * @return true when the scope includes the current baseline.
         */
        public boolean appliesToCurrent() {
            return this == CURRENT || this == BOTH;
        }
    }

    /**
     * Represents a normalized plugin-qualified attribute identifier.
     *
     * @param plugin normalized plugin id segment.
     * @param key    normalized attribute key segment.
     */
    public record NamespacedAttributeKey(String plugin, String key) {

        /**
         * @return normalized plugin segment.
         */
        public String plugin() {
            return plugin;
        }

        /**
         * @return normalized key segment.
         */
        public String key() {
            return key;
        }

        /**
         * @return dot-delimited plugin and key combination.
         */
        public String asString() {
            return plugin + "." + key;
        }
    }

    /**
     * Builds a sorted list of namespaced attribute ids for tab completion from a collection of definitions. Normalizes
     * ids to lower-case and applies the default plugin to single-segment ids.
     *
     * @param definitions   attribute definitions to inspect.
     * @param defaultPlugin plugin id to prepend to single-segment ids.
     * @return sorted list of unique namespaced ids.
     */
    public static List<String> namespacedCompletions(Collection<me.baddcamden.attributeutils.model.AttributeDefinition> definitions,
                                                     String defaultPlugin) {
        if (definitions == null) {
            return List.of();
        }

        List<String> normalizedIds = new ArrayList<>();
        for (me.baddcamden.attributeutils.model.AttributeDefinition definition : definitions) {
            if (definition == null || definition.id() == null || definition.id().isBlank()) {
                continue;
            }
            normalizedIds.add(definition.id().toLowerCase(Locale.ROOT));
        }

        return namespacedCompletionsFromIds(normalizedIds, defaultPlugin);
    }

    /**
     * Builds a sorted list of namespaced attribute ids for tab completion from a collection of raw id strings.
     * Strings already containing a plugin segment are preserved; otherwise, the provided default plugin is prefixed
     * when available.
     *
     * @param ids            attribute ids to normalize.
     * @param defaultPlugin  plugin id to prepend to single-segment ids.
     * @return sorted list of unique namespaced ids.
     */
    public static List<String> namespacedCompletionsFromIds(Collection<String> ids, String defaultPlugin) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        String fallbackPlugin = defaultPlugin == null ? "" : defaultPlugin.toLowerCase(Locale.ROOT);
        Set<String> unique = new HashSet<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }

            String normalizedId = id.toLowerCase(Locale.ROOT);
            if (normalizedId.contains(".")) {
                unique.add(normalizedId);
            } else if (!fallbackPlugin.isBlank()) {
                unique.add(fallbackPlugin + "." + normalizedId);
            }
        }

        List<String> result = new ArrayList<>(unique);
        Collections.sort(result);
        return result;
    }

    public static class AttributeDefinition {
        /**
         * Namespaced key associated with this definition.
         */
        private final NamespacedAttributeKey key;
        /**
         * Numeric value representing the attribute strength or modifier amount.
         */
        private final double value;
        /**
         * Optional cap value overriding the attribute's configured cap.
         */
        private final Double capOverride;
        /**
         * Optional trigger that determines when the attribute applies.
         */
        private final String criterion;

        /**
         * Represents a parsed attribute definition from command input, including the target key, value, and optional
         * cap override and trigger criterion.
         *
         * @param key         namespaced key for the attribute.
         * @param value       numeric value to apply.
         * @param capOverride optional cap override value.
         * @param criterion   optional trigger criterion name.
         */
        public AttributeDefinition(NamespacedAttributeKey key, double value, Double capOverride, String criterion) {
            this.key = key;
            this.value = value;
            this.capOverride = capOverride;
            this.criterion = criterion;
        }

        /**
         * @return namespaced key for the attribute.
         */
        public NamespacedAttributeKey getKey() {
            return key;
        }

        /**
         * @return numeric value associated with the attribute.
         */
        public double getValue() {
            return value;
        }

        /**
         * @return optional cap override value.
         */
        public Optional<Double> getCapOverride() {
            return Optional.ofNullable(capOverride);
        }

        /**
         * @return optional trigger criterion name.
         */
        public Optional<String> getCriterion() {
            return Optional.ofNullable(criterion);
        }
    }
}
