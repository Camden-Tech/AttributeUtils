package me.baddcamden.attributeutils.handler.item;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.command.CommandParsingUtils;
import me.baddcamden.attributeutils.model.AttributeDefinition;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class ItemAttributeHandler {

    private final AttributeFacade attributeFacade;
    private final Plugin plugin;

    public ItemAttributeHandler(AttributeFacade attributeFacade, Plugin plugin) {
        this.attributeFacade = attributeFacade;
        this.plugin = plugin;
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
            capOverride.ifPresent(cap -> loreLine += ChatColor.GRAY + " (cap " + cap + ")");
            lore.add(loreLine);

            String summaryLine = attributeDefinition.id() + "=" + clampedValue;
            capOverride.ifPresent(cap -> summaryLine += " (cap " + cap + ")");
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
