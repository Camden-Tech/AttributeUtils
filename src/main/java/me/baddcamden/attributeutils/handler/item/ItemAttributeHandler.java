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
import org.bukkit.inventory.EquipmentSlotGroup;
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
 * Coordinates item metadata with the attribute pipeline. Baseline values are attached to items so the calculation
 * engine can merge item, player, and global modifiers during stage evaluation, while respecting any cap overrides
 * saved on the item itself.
 */
public class ItemAttributeHandler {

    private final AttributeFacade attributeFacade;
    private final EntityAttributeHandler entityAttributeHandler;
    private final Plugin plugin;
    /**
     * Records the modifier keys applied for each player, enabling precise cleanup when equipment changes invalidate
     * earlier entries.
     */
    private final Map<UUID, Map<String, String>> appliedItemModifierKeys = new HashMap<>();

    /**
     * Constructs a handler that translates item metadata into the attribute pipeline and reuses vanilla application
     * helpers from the entity handler for post-processing.
     */
    public ItemAttributeHandler(AttributeFacade attributeFacade,
                               Plugin plugin,
                               EntityAttributeHandler entityAttributeHandler) {
        this.attributeFacade = attributeFacade;
        this.plugin = plugin;
        this.entityAttributeHandler = entityAttributeHandler;
    }

    /**
     * Builds an {@link ItemStack} from the requested material and encodes attribute definitions into persistent data
     * and lore for later retrieval.
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
        Multimap<Attribute, AttributeModifier> defaultAttributeModifiers = meta.getAttributeModifiers();
        if (defaultAttributeModifiers == null || defaultAttributeModifiers.isEmpty()) {
            defaultAttributeModifiers = material.getDefaultAttributeModifiers(material.getEquipmentSlot());
        }
        // Preserve vanilla defaults on the meta so future refreshes that remove plugin modifiers do not discard the
        // native entries when the item initially reported none.
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
            ModifierOperation operation = definition.getOperation()
                    .orElse(attributeDefinition.defaultOperation());

            container.set(valueKey(attributeDefinition.id()), PersistentDataType.DOUBLE, clampedValue);
            capOverride.ifPresent(cap -> container.set(capKey(attributeDefinition.id()), PersistentDataType.DOUBLE, cap));
            container.set(criterionKey(attributeDefinition.id()), PersistentDataType.STRING, criterion.key());
            container.set(operationKey(attributeDefinition.id()), PersistentDataType.STRING, operation.name());

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
            summaryLine += " [" + criterion.key() + "|" + operation.name().toLowerCase(Locale.ROOT) + "]";
            summary.add(summaryLine);
        }

        meta.setLore(lore);
        itemStack.setItemMeta(meta);
        return new ItemBuildResult(itemStack, String.join(", ", summary));
    }

    /**
     * Attempts to give an attributed item to the target player, dropping overflow at their feet if the inventory is
     * already full.
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
     * Reads all item-based attribute metadata from a player's inventory and converts it to temporary modifier entries
     * on the player's {@link me.baddcamden.attributeutils.model.AttributeInstance} records. The temporary bucket is
     * cleared first so modifiers only reflect the current equipment state.
     *
     * @param player player whose inventory should be scanned
     */
    public void applyPersistentAttributes(Player player) {
        applyPersistentAttributes((LivingEntity) player);
    }

    /**
     * Applies item-based modifiers for any living entity, allowing non-player equipment to contribute attribute
     * metadata from their gear.
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

        previousKeys.forEach((modifierKey, attributeId) -> {
            attributeFacade.removePlayerModifier(ownerId, attributeId, modifierKey);
            touchedAttributes.add(attributeId);
        });

        if (entity instanceof Player player) {
            // Use storage contents to avoid double-counting armor/off-hand slots that Bukkit includes in getContents.
            scanItems(player.getInventory().getStorageContents(), TriggerCriterion.ItemSlotContext.Bucket.INVENTORY, player, heldSlot, activeKeys, currentKeyAttributes, touchedAttributes);
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

        appliedItemModifierKeys.put(ownerId, currentKeyAttributes);

        applyVanillaAttributes(entity, touchedAttributes);
    }

    /**
     * Scans the provided items for attribute metadata, applying modifiers that satisfy trigger criteria while tracking
     * active modifier keys for subsequent cleanup.
     */
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

                ModifierOperation operation = resolveOperation(container, resolvedId);
                applyModifier(entity, resolvedId, effective, criterion, operation, context, activeKeys, currentKeyAttributes, touchedAttributes);
            }
        }
    }

    /**
     * Persists a modifier entry for the given attribute when the associated trigger criterion is satisfied, storing
     * bookkeeping keys so the modifier can be refreshed or removed during future scans.
     */
    private void applyModifier(LivingEntity entity,
                               String attributeId,
                               double value,
                               TriggerCriterion criterion,
                               ModifierOperation operation,
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
                operation,
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

    /**
     * Constructs a deterministic key describing where a modifier originated, including bucket, slot, criterion, and
     * attribute identifiers.
     */
    private String buildModifierKey(TriggerCriterion.ItemSlotContext context, TriggerCriterion criterion, String attributeId) {
        String bucketLabel = context.bucket().name().toLowerCase(Locale.ROOT);
        return "attributeutils." + bucketLabel + "." + context.slot() + "." + criterion.key() + "." + attributeId;
    }

    /**
     * Resolves the stored trigger criterion for an attribute on an item, falling back to the default when missing or
     * invalid.
     */
    private TriggerCriterion resolveCriterion(PersistentDataContainer container, String attributeId) {
        NamespacedKey criteriaKey = criterionKey(attributeId);
        String stored = container.get(criteriaKey, PersistentDataType.STRING);
        return TriggerCriterion.fromRaw(stored).orElse(TriggerCriterion.defaultCriterion());
    }

    private ModifierOperation resolveOperation(PersistentDataContainer container, String attributeId) {
        NamespacedKey key = operationKey(attributeId);
        String stored = container.get(key, PersistentDataType.STRING);
        if (stored != null && !stored.isBlank()) {
            try {
                return ModifierOperation.valueOf(stored.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // fall through to definition default
            }
        }

        return attributeFacade.getDefinition(attributeId)
                .map(AttributeDefinition::defaultOperation)
                .orElse(ModifierOperation.ADD);
    }

    /**
     * Attempts to resolve an attribute id, accepting both sanitized (underscore) and dotted variants for flexibility
     * when reading stored keys.
     */
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

    /**
     * Builds the persistent data key used to store an attribute value on an item.
     */
    private NamespacedKey valueKey(String attributeId) {
        return new NamespacedKey(plugin, "attr_" + sanitize(attributeId));
    }

    /**
     * Builds the persistent data key used to store an attribute cap override on an item.
     */
    private NamespacedKey capKey(String attributeId) {
        return new NamespacedKey(plugin, "attr_" + sanitize(attributeId) + "_cap");
    }

    /**
     * Builds the persistent data key used to store trigger criteria for an attribute on an item.
     */
    private NamespacedKey criterionKey(String attributeId) {
        return new NamespacedKey(plugin, "attr_" + sanitize(attributeId) + "_criteria");
    }

    /**
     * Builds the persistent data key used to store modifier operations for an attribute on an item.
     */
    private NamespacedKey operationKey(String attributeId) {
        return new NamespacedKey(plugin, "attr_" + sanitize(attributeId) + "_operation");
    }

    /**
     * Normalizes attribute identifiers to a safe, namespace-friendly form for persistent data keys.
     */
    private String sanitize(String attributeId) {
        return attributeId.toLowerCase(Locale.ROOT).replace('.', '_');
    }

    /**
     * Clears cached modifier bookkeeping for a player, typically when the player disconnects.
     */
    public void clearAppliedModifiers(UUID playerId) {
        appliedItemModifierKeys.remove(playerId);
    }

    /**
     * Bundles the built item and a concise textual description of the attributes applied to it.
     *
     * @param itemStack the finalized item with persistent attribute metadata and lore
     * @param summary   comma-separated summary of applied attributes, caps, and trigger keys
     */
    public record ItemBuildResult(ItemStack itemStack, String summary) {
    }

    /**
     * Communicates whether overflow occurred when attempting to deliver an attribute item to a player.
     *
     * @param dropped {@code true} if one or more items were dropped at the player's location due to lack of space
     */
    public record ItemDeliveryResult(boolean dropped) {
    }

    /**
     * Applies any touched attributes to vanilla Bukkit fields after item modifiers have been reconciled.
     */
    private void applyVanillaAttributes(LivingEntity entity, Set<String> touchedAttributes) {
        if (entity == null || touchedAttributes.isEmpty()) {
            return;
        }

        for (String attributeId : touchedAttributes) {
            entityAttributeHandler.applyVanillaAttribute(entity, attributeId);
        }
    }
}
