package me.baddcamden.attributeutils.attributes.model;

import java.util.Locale;
import java.util.Optional;

public enum AttributeTrigger {
    INVENTORY("inventory"),
    HELD("held"),
    USED("used"),
    SWUNG("swung"),
    CRIT("crit"),
    EQUIPPED("equipped"),
    OFF_HAND("off-hand"),
    HOTBAR("hotbar"),
    CUSTOM("custom");

    private final String key;

    AttributeTrigger(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static Optional<AttributeTrigger> fromKey(String rawKey) {
        if (rawKey == null || rawKey.isEmpty()) {
            return Optional.empty();
        }

        String normalized = rawKey.toLowerCase(Locale.ROOT).replace('_', '-');

        for (AttributeTrigger trigger : values()) {
            if (trigger.key.equals(normalized)) {
                return Optional.of(trigger);
            }
        }

        return Optional.empty();
    }
}
