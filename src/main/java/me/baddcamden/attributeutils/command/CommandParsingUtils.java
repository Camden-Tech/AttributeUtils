package me.baddcamden.attributeutils.command;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import me.baddcamden.attributeutils.model.ModifierOperation;

/**
 * Utility functions for turning user input into normalized attribute keys and numeric values.
 * Parsed keys are tied back to specific modifier buckets or baselines depending on the invoking command, while numeric
 * inputs may be clamped against cap configuration to ensure stage boundaries are preserved before computation.
 */
public final class CommandParsingUtils {

    private static final Pattern NAMESPACED_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_.-]+$");

    private CommandParsingUtils() {
    }

    public static Optional<NamespacedAttributeKey> parseAttributeKey(CommandSender sender, String raw, CommandMessages messages) {
        if (!NAMESPACED_KEY_PATTERN.matcher(raw).matches()) {
            sender.sendMessage(messages.format(
                    "messages.shared.attribute-key-format",
                    Map.of("value", raw),
                    "§cAttribute keys must follow the [plugin].[key] format (e.g. example.max_health)."));
            return Optional.empty();
        }

        String[] parts = raw.split("\\.", 2);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            sender.sendMessage(messages.format(
                    "messages.shared.attribute-key-segments",
                    Map.of("value", raw),
                    "§cAttribute keys must include both a plugin namespace and key segment."));
            return Optional.empty();
        }

        return Optional.of(new NamespacedAttributeKey(parts[0], parts[1]));
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
        if (startIndex >= args.length) {
            sender.sendMessage(messages.format(
                    "messages.shared.definition-missing",
                    "§cProvide attribute definitions as <plugin>.<key> <value> [cap=<cap>]."));
            return Collections.emptyList();
        }

        List<AttributeDefinition> definitions = new ArrayList<>();
        int index = startIndex;
        while (index < args.length) {
            if (index + 1 >= args.length) {
                sender.sendMessage(messages.format(
                        "messages.shared.definition-missing-value",
                        Map.of("key", args[index]),
                        "§cAttribute definitions must include a numeric value after the key."));
                return Collections.emptyList();
            }

            Optional<NamespacedAttributeKey> key = parseAttributeKey(sender, args[index], messages);
            Optional<Double> value = parseNumeric(sender, args[index + 1], "attribute value", messages);
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
                        Map.of("attribute", key.get().asString(), "value", args[index + 1]),
                        "§cAttribute values must be zero or positive."));
                return Collections.emptyList();
            }

            Double capOverride = null;
            if (index + 2 < args.length && args[index + 2].startsWith("cap=")) {
                Optional<Double> cap = parseCapOverride(sender, args[index + 2], messages);
                if (cap.isEmpty()) {
                    return Collections.emptyList();
                }
                capOverride = cap.get();
                index += 3;
            } else {
                index += 2;
            }

            definitions.add(new AttributeDefinition(key.get(), value.get(), capOverride));
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

    public static class AttributeDefinition {
        private final NamespacedAttributeKey key;
        private final double value;
        private final Double capOverride;

        public AttributeDefinition(NamespacedAttributeKey key, double value, Double capOverride) {
            this.key = key;
            this.value = value;
            this.capOverride = capOverride;
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
    }
}
