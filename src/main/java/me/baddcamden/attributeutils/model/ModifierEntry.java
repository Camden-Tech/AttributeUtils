package me.baddcamden.attributeutils.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record ModifierEntry(String key,
                            ModifierOperation operation,
                            double amount,
                            boolean temporary,
                            boolean appliesToDefault,
                            boolean appliesToCurrent) {

    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9_.-]+", Pattern.CASE_INSENSITIVE);

    public ModifierEntry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(operation, "operation");
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("Modifier keys must match " + KEY_PATTERN.pattern());
        }
        if (!appliesToDefault && !appliesToCurrent) {
            throw new IllegalArgumentException("Modifier must apply to at least one stage");
        }
    }

    public boolean isTemporary() {
        return temporary;
    }

    public boolean isPermanent() {
        return !temporary;
    }

    public boolean isDefaultModifier() {
        return appliesToDefault;
    }

    public boolean appliesToCurrent() {
        return appliesToCurrent;
    }
}
