package me.baddcamden.attributeutils.handler.item;

import org.bukkit.entity.LivingEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Enumerates the contexts in which an item's attribute modifiers should apply.
 * <p>
 * Each value corresponds to a trigger category defined for attribute evaluation, and is checked against
 * the player's current inventory state when refreshing attributes.
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

    /**
     * Creates a new criterion.
     *
     * @param key         normalized string representation exposed to config/commands.
     * @param description short human-readable description used in help text.
     */
    TriggerCriterion(String key, String description) {
        this.key = key;
        this.description = description;
    }

    /**
     * Gets the configuration/command key for this criterion.
     *
     * @return normalized key such as {@code inventory} or {@code hotbar}.
     */
    public String key() {
        return key;
    }

    /**
     * Gets the human-readable description of this criterion.
     *
     * @return description string intended for tooltips and help output.
     */
    public String description() {
        return description;
    }

    /**
     * Provides the criterion used when no explicit value is supplied.
     *
     * @return {@link #INVENTORY} as the fallback criterion.
     */
    public static TriggerCriterion defaultCriterion() {
        return INVENTORY;
    }

    /**
     * Attempts to resolve a criterion from a raw string, applying normalization prior to lookup.
     *
     * @param raw source string from configuration or user input.
     * @return matching criterion, or {@link Optional#empty()} when the value is absent or unknown.
     */
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

    /**
     * Determines whether this criterion is satisfied for a given slot context.
     *
     * @param context slot information describing where the item currently resides.
     * @param entity  entity whose equipment/inventory is being evaluated.
     * @return {@code true} if the current context satisfies the criterion; otherwise {@code false}.
     */
    public boolean isSatisfied(ItemSlotContext context, LivingEntity entity) {
        if (context == null) {
            return false;
        }

        //VAGUE/IMPROVEMENT NEEDED entity parameter is unused; clarify whether future logic needs it or remove parameter.
        return switch (this) {
            case INVENTORY -> true;
            case HOTBAR -> context.isHotbar();
            case HELD, USE, ATTACK, FULL_SWING, CRIT -> context.isHeld();
            case EQUIPPED -> context.bucket() == ItemSlotContext.Bucket.ARMOR;
            case OFFHAND -> context.bucket() == ItemSlotContext.Bucket.OFFHAND;
        };
    }

    /**
     * Normalizes a raw string for comparison with criterion keys.
     *
     * @param raw untrusted user or configuration input.
     * @return lowercased key with whitespace and hyphens collapsed into underscores.
     */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
    }

    /**
     * Generates autocompletion options for a partially typed key.
     *
     * @param prefix user input to match.
     * @return list of criterion keys that start with the normalized prefix.
     */
    public static List<String> completions(String prefix) {
        String normalized = normalize(prefix);
        return Arrays.stream(values())
                .map(TriggerCriterion::key)
                .filter(option -> option.startsWith(normalized))
                .collect(Collectors.toList());
    }

    /**
     * Describes where the item currently lives when scanning a player's inventory.
     *
     * @param bucket   logical grouping containing the slot (e.g., inventory or armor).
     * @param slot     zero-based slot index within the bucket.
     * @param heldSlot zero-based active hotbar slot reported by the player; used to determine held items.
     */
    public record ItemSlotContext(Bucket bucket, int slot, int heldSlot) {

        /**
         * Logical groupings for where items can be found when scanning inventories.
         */
        public enum Bucket {
            INVENTORY,
            ARMOR,
            OFFHAND
        }

        /**
         * Indicates whether the slot resides within the hotbar region of the inventory.
         *
         * @return {@code true} if the slot is in the primary nine-slot hotbar region.
         */
        public boolean isHotbar() {
            return bucket == Bucket.INVENTORY && slot >= 0 && slot < 9;
        }

        /**
         * Indicates whether the slot corresponds to the currently held item.
         *
         * @return {@code true} when the bucket is {@link Bucket#INVENTORY} and the slot matches the player's held slot.
         */
        public boolean isHeld() {
            return bucket == Bucket.INVENTORY && slot == heldSlot;
        }
    }
}
