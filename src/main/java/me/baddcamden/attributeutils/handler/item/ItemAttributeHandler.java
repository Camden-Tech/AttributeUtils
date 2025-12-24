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
import com.google.common.collect.Multimap;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
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
    private final Map<UUID, Map<String, String>> appliedItemModifierKeys = new HashMap<>();

    public ItemAttributeHandler(AttributeFacade attributeFacade,
                               Plugin plugin,
                               EntityAttributeHandler entityAttributeHandler) {
        this.attributeFacade = attributeFacade;
        this.plugin = plugin;
        this.entityAttributeHandler = entityAttributeHandler;
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
        Multimap<Attribute, AttributeModifier> defaultAttributeModifiers = itemStack.getAttributeModifiers();
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            throw new IllegalArgumentException("unsupported-material");
        }

        if (defaultAttributeModifiers != null && (meta.getAttributeModifiers() == null || meta.getAttributeModifiers().isEmpty())) {
            meta.setAttributeModifiers(defaultAttributeModifiers);
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
            TriggerCriterion criterion = definition.getCriterion()
                    .flatMap(TriggerCriterion::fromRaw)
                    .orElse(TriggerCriterion.defaultCriterion());

            container.set(valueKey(attributeDefinition.id()), PersistentDataType.DOUBLE, clampedValue);
            capOverride.ifPresent(cap -> container.set(capKey(attributeDefinition.id()), PersistentDataType.DOUBLE, cap));
            container.set(criterionKey(attributeDefinition.id()), PersistentDataType.STRING, criterion.key());

            String loreLine = ChatColor.GRAY + attributeDefinition.displayName() + ChatColor.WHITE + ": " + clampedValue;
            if (capOverride.isPresent()) {
                double cap = capOverride.get();
                loreLine += ChatColor.GRAY + " (cap " + cap + ")";
            }
            loreLine += ChatColor.DARK_GRAY + " [" + criterion.description() + "]";
            lore.add(loreLine);

            String summaryLine = attributeDefinition.id() + "=" + clampedValue;
            if (capOverride.isPresent()) {
                double cap = capOverride.get();
                summaryLine += " (cap " + cap + ")";
            }
            summaryLine += " [" + criterion.key() + "]";
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
        applyPersistentAttributes((LivingEntity) player);
    }

    /**
     * Applies item-based modifiers for any living entity, enabling non-player entities with equipment
     * to benefit from attribute metadata on their gear.
     *
     * @param entity entity whose equipment should be scanned
     */
    public void applyPersistentAttributes(LivingEntity entity) {
        if (entity == null) {
            return;
        }

        UUID ownerId = entity.getUniqueId();
        Map<String, String> previousKeys = appliedItemModifierKeys.getOrDefault(ownerId, Map.of());
        Set<String> activeKeys = new HashSet<>();
        Map<String, String> currentKeyAttributes = new HashMap<>();
        Set<String> touchedAttributes = new HashSet<>();
        int heldSlot = entity instanceof Player player ? player.getInventory().getHeldItemSlot() : 0;

        if (entity instanceof Player player) {
            scanItems(player.getInventory().getContents(), TriggerCriterion.ItemSlotContext.Bucket.INVENTORY, player, heldSlot, activeKeys, currentKeyAttributes, touchedAttributes);
            scanItems(player.getInventory().getArmorContents(), TriggerCriterion.ItemSlotContext.Bucket.ARMOR, player, heldSlot, activeKeys, currentKeyAttributes, touchedAttributes);
            scanItems(new ItemStack[]{player.getInventory().getItemInOffHand()}, TriggerCriterion.ItemSlotContext.Bucket.OFFHAND, player, heldSlot, activeKeys, currentKeyAttributes, touchedAttributes);
        } else {
            EntityEquipment equipment = entity.getEquipment();
            if (equipment != null) {
                scanItems(new ItemStack[]{equipment.getItemInMainHand()}, TriggerCriterion.ItemSlotContext.Bucket.INVENTORY, entity, heldSlot, activeKeys, currentKeyAttributes, touchedAttributes);
                scanItems(equipment.getArmorContents(), TriggerCriterion.ItemSlotContext.Bucket.ARMOR, entity, heldSlot, activeKeys, currentKeyAttributes, touchedAttributes);
                scanItems(new ItemStack[]{equipment.getItemInOffHand()}, TriggerCriterion.ItemSlotContext.Bucket.OFFHAND, entity, heldSlot, activeKeys, currentKeyAttributes, touchedAttributes);
            }
        }

        for (Map.Entry<String, String> entry : previousKeys.entrySet()) {
            if (!activeKeys.contains(entry.getKey())) {
                attributeFacade.removePlayerModifier(ownerId, entry.getValue(), entry.getKey());
                touchedAttributes.add(entry.getValue());
            }
        }

        appliedItemModifierKeys.put(ownerId, currentKeyAttributes);

        applyVanillaAttributes(entity, touchedAttributes);
    }

    private void scanItems(ItemStack[] items,
                           TriggerCriterion.ItemSlotContext.Bucket bucket,
                           LivingEntity entity,
                           int heldSlot,
                           Set<String> activeKeys,
                           Map<String, String> currentKeyAttributes,
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
                if (!keyName.startsWith("attr_") || keyName.endsWith("_cap") || keyName.endsWith("_criteria")) {
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
                TriggerCriterion criterion = resolveCriterion(container, resolvedId);

                TriggerCriterion.ItemSlotContext context = new TriggerCriterion.ItemSlotContext(bucket, slot, heldSlot);
                if (!criterion.isSatisfied(context, entity)) {
                    continue;
                }

                applyModifier(entity, resolvedId, effective, criterion, context, activeKeys, currentKeyAttributes, touchedAttributes);
            }
        }
    }

    private void applyModifier(LivingEntity entity,
                               String attributeId,
                               double value,
                               TriggerCriterion criterion,
                               TriggerCriterion.ItemSlotContext context,
                               Set<String> activeKeys,
                               Map<String, String> currentKeyAttributes,
                               Set<String> touchedAttributes) {
        Optional<AttributeDefinition> definition = attributeFacade.getDefinition(attributeId);
        if (definition.isEmpty()) {
            return;
        }

        touchedAttributes.add(definition.get().id());
        String source = buildModifierKey(context, criterion, attributeId);
        double clamped = definition.get().capConfig().clamp(value, entity.getUniqueId().toString());
        ModifierEntry entry = new ModifierEntry(source,
                ModifierOperation.ADD,
                clamped,
                true,
                false,
                true,
                false,
                Set.of());
        attributeFacade.setPlayerModifier(entity.getUniqueId(), definition.get().id(), entry);
        activeKeys.add(source);
        currentKeyAttributes.put(source, definition.get().id());
    }

    private String buildModifierKey(TriggerCriterion.ItemSlotContext context, TriggerCriterion criterion, String attributeId) {
        String bucketLabel = context.bucket().name().toLowerCase(Locale.ROOT);
        return "attributeutils." + bucketLabel + "." + context.slot() + "." + criterion.key() + "." + attributeId;
    }

    private TriggerCriterion resolveCriterion(PersistentDataContainer container, String attributeId) {
        NamespacedKey criteriaKey = criterionKey(attributeId);
        String stored = container.get(criteriaKey, PersistentDataType.STRING);
        return TriggerCriterion.fromRaw(stored).orElse(TriggerCriterion.defaultCriterion());
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

    private NamespacedKey criterionKey(String attributeId) {
        return new NamespacedKey(plugin, "attr_" + sanitize(attributeId) + "_criteria");
    }

    private String sanitize(String attributeId) {
        return attributeId.toLowerCase(Locale.ROOT).replace('.', '_');
    }

    public void clearAppliedModifiers(UUID playerId) {
        appliedItemModifierKeys.remove(playerId);
    }

    public record ItemBuildResult(ItemStack itemStack, String summary) {
    }

    public record ItemDeliveryResult(boolean dropped) {
    }

    private void applyVanillaAttributes(LivingEntity entity, Set<String> touchedAttributes) {
        if (entity == null || touchedAttributes.isEmpty()) {
            return;
        }

        for (String attributeId : touchedAttributes) {
            entityAttributeHandler.applyVanillaAttribute(entity, attributeId);
        }
    }
}
