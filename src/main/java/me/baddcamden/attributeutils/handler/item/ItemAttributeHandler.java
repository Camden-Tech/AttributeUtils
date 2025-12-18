package me.baddcamden.attributeutils.handler.item;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.command.CommandParsingUtils;
import me.baddcamden.attributeutils.handler.entity.EntityAttributeHandler;
import me.baddcamden.attributeutils.model.AttributeDefinition;
import me.baddcamden.attributeutils.model.ModifierEntry;
import me.baddcamden.attributeutils.model.ModifierOperation;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Bridges item metadata with the attribute pipeline. Default baselines are stamped onto items when players join,
 * ensuring that the computation engine can combine item, player, and global modifier buckets during the current
 * stage calculation. Cap overrides stored on items are also respected downstream.
 */
public class ItemAttributeHandler {

    private final AttributeFacade attributeFacade;
    private final EntityAttributeHandler entityAttributeHandler;
    private final Plugin plugin;

    public ItemAttributeHandler(AttributeFacade attributeFacade,
                               Plugin plugin,
                               EntityAttributeHandler entityAttributeHandler) {
        this.attributeFacade = attributeFacade;
        this.plugin = plugin;
        this.entityAttributeHandler = entityAttributeHandler;
    }

    /**
     * Ensures attribute defaults are primed for the provided inventory. Currently used to warm caches on join.
     *
     * @param inventory player inventory
     */
    public void applyDefaults(PlayerInventory inventory) {
        attributeFacade.getDefinitions();
    }

    /**
     * Builds an {@link ItemStack} with the provided material and attribute definitions applied as persistent data and lore.
     *
     * @param material    the material to construct
     * @param definitions parsed attribute definitions from the command
     * @return build result containing the item stack and a human-readable attribute summary
     * @throws IllegalArgumentException if a material cannot hold metadata or an attribute is unknown
     */
    public ItemBuildResult buildAttributeItem(Material material, List<CommandParsingUtils.AttributeDefinition> definitions) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            throw new IllegalArgumentException("unsupported-material");
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        List<String> lore = new ArrayList<>();
        List<String> summary = new ArrayList<>();
        for (CommandParsingUtils.AttributeDefinition definition : definitions) {
            AttributeDefinition attributeDefinition = attributeFacade.getDefinition(definition.getKey().key())
                    .orElseThrow(() -> new IllegalArgumentException(definition.getKey().asString()));

            double clampedValue = attributeDefinition.capConfig().clamp(definition.getValue());
            Optional<Double> capOverride = definition.getCapOverride()
                    .map(value -> attributeDefinition.capConfig().clamp(value));

            container.set(valueKey(attributeDefinition.id()), PersistentDataType.DOUBLE, clampedValue);
            capOverride.ifPresent(cap -> container.set(capKey(attributeDefinition.id()), PersistentDataType.DOUBLE, cap));

            String loreLine = ChatColor.GRAY + attributeDefinition.displayName() + ChatColor.WHITE + ": " + clampedValue;
            if (capOverride.isPresent()) {
                double cap = capOverride.get();
                loreLine += ChatColor.GRAY + " (cap " + cap + ")";
            }
            lore.add(loreLine);

            String summaryLine = attributeDefinition.id() + "=" + clampedValue;
            if (capOverride.isPresent()) {
                double cap = capOverride.get();
                summaryLine += " (cap " + cap + ")";
            }
            summary.add(summaryLine);
        }

        meta.setLore(lore);
        itemStack.setItemMeta(meta);
        return new ItemBuildResult(itemStack, String.join(", ", summary));
    }

    /**
     * Attempts to give an attributed item to the target player, dropping overflow at their feet if their inventory is full.
     *
     * @param target    player who should receive the item
     * @param itemStack constructed attribute item
     * @return delivery result indicating whether overflow occurred
     */
    public ItemDeliveryResult deliverItem(Player target, ItemStack itemStack) {
        PlayerInventory inventory = target.getInventory();
        Map<Integer, ItemStack> overflow = inventory.addItem(itemStack);
        boolean dropped = false;
        if (!overflow.isEmpty()) {
            dropped = true;
            overflow.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        }
        return new ItemDeliveryResult(dropped);
    }

    /**
     * Reads all item-based attribute metadata from a player's inventory and converts it to temporary
     * modifier entries on the player's {@link me.baddcamden.attributeutils.model.AttributeInstance}
     * records. The temporary bucket is purged up-front to ensure modifiers reflect only the current
     * equipment state.
     *
     * @param player player whose inventory should be scanned
     */
    public void applyPersistentAttributes(Player player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        attributeFacade.purgeTemporary(playerId);

        Set<String> touchedAttributes = new HashSet<>();
        Map<String, Integer> attributeCounts = new HashMap<>();
        scanItems(player.getInventory().getContents(), "inventory", player, attributeCounts, touchedAttributes);
        scanItems(player.getInventory().getArmorContents(), "armor", player, attributeCounts, touchedAttributes);
        scanItems(new ItemStack[]{player.getInventory().getItemInOffHand()}, "offhand", player, attributeCounts, touchedAttributes);

        for (String attributeId : touchedAttributes) {
            entityAttributeHandler.applyVanillaAttribute(player, attributeId);
        }
    }

    private void scanItems(ItemStack[] items,
                           String bucketLabel,
                           Player player,
                           Map<String, Integer> attributeCounts,
                           Set<String> touchedAttributes) {
        if (items == null) {
            return;
        }

        for (int slot = 0; slot < items.length; slot++) {
            ItemStack item = items[slot];
            if (item == null) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                continue;
            }

            PersistentDataContainer container = meta.getPersistentDataContainer();
            for (NamespacedKey key : container.getKeys()) {
                String keyName = key.getKey();
                if (!key.getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                if (!keyName.startsWith("attr_") || keyName.endsWith("_cap")) {
                    continue;
                }

                String attributeId = keyName.substring("attr_".length());
                String resolvedId = resolveAttributeId(attributeId);
                if (resolvedId == null) {
                    continue;
                }

                Double value = container.get(key, PersistentDataType.DOUBLE);
                if (value == null) {
                    continue;
                }

                NamespacedKey capKey = new NamespacedKey(plugin, keyName + "_cap");
                Double capOverride = container.get(capKey, PersistentDataType.DOUBLE);
                double effective = capOverride == null ? value : Math.min(value, capOverride);

                applyModifier(player, resolvedId, effective, bucketLabel, slot, attributeCounts, touchedAttributes);
            }
        }
    }

    private void applyModifier(Player player,
                               String attributeId,
                               double value,
                               String bucketLabel,
                               int slot,
                               Map<String, Integer> attributeCounts,
                               Set<String> touchedAttributes) {
        Optional<AttributeDefinition> definition = attributeFacade.getDefinition(attributeId);
        if (definition.isEmpty()) {
            return;
        }

        touchedAttributes.add(definition.get().id());
        int ordinal = attributeCounts.merge(attributeId, 1, Integer::sum);
        String source = "attributeutils." + bucketLabel + "." + slot + "." + ordinal + "." + attributeId;
        double clamped = definition.get().capConfig().clamp(value, player.getUniqueId().toString());
        ModifierEntry entry = new ModifierEntry(source,
                ModifierOperation.ADD,
                clamped,
                true,
                false,
                true,
                false,
                Set.of());
        attributeFacade.setPlayerModifier(player.getUniqueId(), definition.get().id(), entry);
    }

    private String resolveAttributeId(String sanitizedId) {
        if (sanitizedId == null || sanitizedId.isBlank()) {
            return null;
        }

        String normalized = sanitizedId.toLowerCase(Locale.ROOT);
        if (attributeFacade.getDefinition(normalized).isPresent()) {
            return normalized;
        }

        String dotted = normalized.replace('_', '.');
        return attributeFacade.getDefinition(dotted).map(AttributeDefinition::id).orElse(null);
    }

    private NamespacedKey valueKey(String attributeId) {
        return new NamespacedKey(plugin, "attr_" + sanitize(attributeId));
    }

    private NamespacedKey capKey(String attributeId) {
        return new NamespacedKey(plugin, "attr_" + sanitize(attributeId) + "_cap");
    }

    private String sanitize(String attributeId) {
        return attributeId.toLowerCase(Locale.ROOT).replace('.', '_');
    }

    public record ItemBuildResult(ItemStack itemStack, String summary) {
    }

    public record ItemDeliveryResult(boolean dropped) {
    }
}
