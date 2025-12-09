package me.baddcamden.attributeutils.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record ModifierEntry(String key, ModifierOperation operation, double amount, boolean permanent) {

    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9_.-]+", Pattern.CASE_INSENSITIVE);

    public ModifierEntry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(operation, "operation");
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("Modifier keys must match " + KEY_PATTERN.pattern());
        }
    }

    public boolean isTemporary() {
        return !permanent;
    }
}
