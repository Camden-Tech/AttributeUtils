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
 * inputs may be clamped against cap configuration to ensure stage boundaries are preserved before computation.
 */
public final class CommandParsingUtils {

    private static final Pattern PLUGIN_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$");

    private CommandParsingUtils() {
    }

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

    public static Optional<Double> parseNumeric(CommandSender sender, String raw, String label, CommandMessages messages) {
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

    public static List<AttributeDefinition> parseAttributeDefinitions(
            CommandSender sender,
            String[] args,
            int startIndex,
            CommandMessages messages,
            Predicate<String> attributeExists) {
        return parseAttributeDefinitions(sender, args, startIndex, messages, attributeExists, null, null);
    }

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
                    "§cProvide attribute definitions as <plugin>.<key> <value> [cap=<cap> criteria=<criteria>]."));
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

    public enum Scope {
        DEFAULT,
        CURRENT,
        BOTH;

        public boolean appliesToDefault() {
            return this == DEFAULT || this == BOTH;
        }

        public boolean appliesToCurrent() {
            return this == CURRENT || this == BOTH;
        }
    }

    public record NamespacedAttributeKey(String plugin, String key) {

        public String key() {
            return key;
        }

        public String asString() {
            return plugin + "." + key;
        }
    }

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
        private final NamespacedAttributeKey key;
        private final double value;
        private final Double capOverride;
        private final String criterion;

        public AttributeDefinition(NamespacedAttributeKey key, double value, Double capOverride, String criterion) {
            this.key = key;
            this.value = value;
            this.capOverride = capOverride;
            this.criterion = criterion;
        }

        public NamespacedAttributeKey getKey() {
            return key;
        }

        public double getValue() {
            return value;
        }

        public Optional<Double> getCapOverride() {
            return Optional.ofNullable(capOverride);
        }

        public Optional<String> getCriterion() {
            return Optional.ofNullable(criterion);
        }
    }
}
