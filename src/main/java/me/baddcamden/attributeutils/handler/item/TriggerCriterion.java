package me.baddcamden.attributeutils.handler.item;

import org.bukkit.entity.LivingEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Represents the contexts in which an item's attribute modifiers should apply. These map to the trigger
 * categories outlined in docs/OUTLINE.md and are evaluated against the player's inventory state when
 * refreshing attributes.
 */
public enum TriggerCriterion {

    INVENTORY("inventory", "in inventory"),
    HOTBAR("hotbar", "on hotbar"),
    HELD("held", "while held"),
    USE("use", "when used"),
    ATTACK("attack", "on attack"),
    FULL_SWING("full_swing", "on full swing"),
    CRIT("crit", "on crit"),
    EQUIPPED("equipped", "while equipped"),
    OFFHAND("offhand", "in off-hand");

    private final String key;
    private final String description;

    TriggerCriterion(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String key() {
        return key;
    }

    public String description() {
        return description;
    }

    public static TriggerCriterion defaultCriterion() {
        return INVENTORY;
    }

    public static Optional<TriggerCriterion> fromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(raw);
        return Arrays.stream(values())
                .filter(value -> value.key.equals(normalized))
                .findFirst();
    }

    public static List<String> keys() {
        return Arrays.stream(values())
                .map(TriggerCriterion::key)
                .toList();
    }

    public boolean isSatisfied(ItemSlotContext context, LivingEntity entity) {
        if (context == null) {
            return false;
        }

        return switch (this) {
            case INVENTORY -> true;
            case HOTBAR -> context.isHotbar();
            case HELD, USE, ATTACK, FULL_SWING, CRIT -> context.isHeld();
            case EQUIPPED -> context.bucket() == ItemSlotContext.Bucket.ARMOR;
            case OFFHAND -> context.bucket() == ItemSlotContext.Bucket.OFFHAND;
        };
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
    }

    public static List<String> completions(String prefix) {
        String normalized = normalize(prefix);
        return Arrays.stream(values())
                .map(TriggerCriterion::key)
                .filter(option -> option.startsWith(normalized))
                .collect(Collectors.toList());
    }

    /**
     * Describes where the item currently lives when scanning a player's inventory.
     */
    public record ItemSlotContext(Bucket bucket, int slot, int heldSlot) {

        public enum Bucket {
            INVENTORY,
            ARMOR,
            OFFHAND
        }

        public boolean isHotbar() {
            return bucket == Bucket.INVENTORY && slot >= 0 && slot < 9;
        }

        public boolean isHeld() {
            return bucket == Bucket.INVENTORY && slot == heldSlot;
        }
    }
}
