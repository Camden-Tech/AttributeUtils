package me.baddcamden.attributeutils.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class CommandParsingUtils {

    private static final Pattern NAMESPACED_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_.-]+$");

    private CommandParsingUtils() {
    }

    public static Optional<NamespacedAttributeKey> parseAttributeKey(CommandSender sender, String raw) {
        if (!NAMESPACED_KEY_PATTERN.matcher(raw).matches()) {
            sender.sendMessage(ChatColor.RED + "Attribute keys must follow the [plugin].[key] format (e.g. example.max_health).");
            return Optional.empty();
        }

        String[] parts = raw.split("\\.", 2);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            sender.sendMessage(ChatColor.RED + "Attribute keys must include both a plugin namespace and key segment.");
            return Optional.empty();
        }

        return Optional.of(new NamespacedAttributeKey(parts[0], parts[1]));
    }

    public static Optional<Double> parseNumeric(CommandSender sender, String raw, String label) {
        try {
            return Optional.of(Double.parseDouble(raw));
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Invalid " + label + ": '" + raw + "'. Provide a numeric value.");
            return Optional.empty();
        }
    }

    public static Optional<Double> parseCapOverride(CommandSender sender, String raw) {
        if (!raw.startsWith("cap=")) {
            sender.sendMessage(ChatColor.RED + "Cap overrides must be provided as cap=<value>.");
            return Optional.empty();
        }

        String numericPortion = raw.substring("cap=".length());
        Optional<Double> capValue = parseNumeric(sender, numericPortion, "cap override");
        capValue.ifPresent(value -> {
            if (value < 0) {
                sender.sendMessage(ChatColor.RED + "Cap overrides must be zero or positive.");
            }
        });
        if (capValue.isPresent() && capValue.get() < 0) {
            return Optional.empty();
        }
        return capValue;
    }

    public static List<AttributeDefinition> parseAttributeDefinitions(CommandSender sender, String[] args, int startIndex) {
        if (startIndex >= args.length) {
            sender.sendMessage(ChatColor.RED + "Provide attribute definitions as <plugin>.<key> <value> [cap=<cap>].");
            return Collections.emptyList();
        }

        List<AttributeDefinition> definitions = new ArrayList<>();
        int index = startIndex;
        while (index < args.length) {
            if (index + 1 >= args.length) {
                sender.sendMessage(ChatColor.RED + "Attribute definitions must include a numeric value after the key.");
                return Collections.emptyList();
            }

            Optional<NamespacedAttributeKey> key = parseAttributeKey(sender, args[index]);
            Optional<Double> value = parseNumeric(sender, args[index + 1], "attribute value");
            if (key.isEmpty() || value.isEmpty()) {
                return Collections.emptyList();
            }

            Double capOverride = null;
            if (index + 2 < args.length && args[index + 2].startsWith("cap=")) {
                Optional<Double> cap = parseCapOverride(sender, args[index + 2]);
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

    public static class NamespacedAttributeKey {
        private final String plugin;
        private final String key;

        public NamespacedAttributeKey(String plugin, String key) {
            this.plugin = plugin;
            this.key = key;
        }

        public String getPlugin() {
            return plugin;
        }

        public String getKey() {
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
