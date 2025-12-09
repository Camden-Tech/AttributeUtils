package me.baddcamden.attributeutils.handler.item;

import me.baddcamden.attributeutils.attributes.model.AttributeInstance;
import me.baddcamden.attributeutils.attributes.model.AttributeTrigger;
import me.baddcamden.attributeutils.service.AttributeService;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ItemAttributeHandler {

    private final AttributeService attributeService;
    private final NamespacedKey attributesKey;
    private final NamespacedKey definitionsKey;
    private final NamespacedKey modifiersKey;
    private final NamespacedKey valueKey;
    private final NamespacedKey maxValueKey;

    public ItemAttributeHandler(Plugin plugin, AttributeService attributeService) {
        this.attributeService = attributeService;
        this.attributesKey = new NamespacedKey(plugin, "attributes");
        this.definitionsKey = new NamespacedKey(plugin, "definitions");
        this.modifiersKey = new NamespacedKey(plugin, "modifiers");
        this.valueKey = new NamespacedKey(plugin, "value");
        this.maxValueKey = new NamespacedKey(plugin, "max_value");
    }

    public void applyDefaults(PlayerInventory inventory) {
        attributeService.getAttributes().forEach((key, attribute) -> {
            // Placeholder for applying item-based attributes to the player's inventory.
        });
    }

    public List<AttributeInstance> readItemAttributes(ItemStack itemStack, AttributeTrigger triggerFilter) {
        if (itemStack == null) {
            return Collections.emptyList();
        }

        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return Collections.emptyList();
        }

        PersistentDataContainer dataContainer = meta.getPersistentDataContainer();
        return parseAttributeContainer(dataContainer, triggerFilter);
    }

    public void applyItemAttributes(ItemStack itemStack, AttributeTrigger triggerFilter) {
        readItemAttributes(itemStack, triggerFilter).forEach(instance -> instance.apply(attributeService));
    }

    private List<AttributeInstance> parseAttributeContainer(PersistentDataContainer dataContainer, AttributeTrigger triggerFilter) {
        PersistentDataContainer attributesContainer = dataContainer.get(attributesKey, PersistentDataType.TAG_CONTAINER);

        if (attributesContainer == null) {
            return Collections.emptyList();
        }

        List<AttributeInstance> instances = new ArrayList<>();

        for (NamespacedKey triggerKey : attributesContainer.getKeys()) {
            Optional<AttributeTrigger> trigger = AttributeTrigger.fromKey(triggerKey.getKey());

            if (trigger.isEmpty()) {
                continue;
            }

            if (triggerFilter != null && triggerFilter != trigger.get()) {
                continue;
            }

            PersistentDataContainer triggerContainer = attributesContainer.get(triggerKey, PersistentDataType.TAG_CONTAINER);

            if (triggerContainer == null) {
                continue;
            }

            collectDefinitions(triggerContainer, trigger.get(), instances);
            collectModifiers(triggerContainer, trigger.get(), instances);
        }

        return instances;
    }

    private void collectDefinitions(PersistentDataContainer triggerContainer, AttributeTrigger trigger, List<AttributeInstance> output) {
        PersistentDataContainer definitions = triggerContainer.get(definitionsKey, PersistentDataType.TAG_CONTAINER);

        if (definitions == null) {
            return;
        }

        Set<NamespacedKey> attributeKeys = definitions.getKeys();

        for (NamespacedKey attributeKey : attributeKeys) {
            PersistentDataContainer definitionContainer = definitions.get(attributeKey, PersistentDataType.TAG_CONTAINER);

            if (definitionContainer == null) {
                continue;
            }

            Double value = definitionContainer.get(valueKey, PersistentDataType.DOUBLE);

            if (value == null) {
                continue;
            }

            Double maxValue = definitionContainer.get(maxValueKey, PersistentDataType.DOUBLE);

            output.add(AttributeInstance.definition(attributeKey.getKey(), value, maxValue, trigger));
        }
    }

    private void collectModifiers(PersistentDataContainer triggerContainer, AttributeTrigger trigger, List<AttributeInstance> output) {
        PersistentDataContainer modifiers = triggerContainer.get(modifiersKey, PersistentDataType.TAG_CONTAINER);

        if (modifiers == null) {
            return;
        }

        for (NamespacedKey modifierKey : modifiers.getKeys()) {
            Double amount = modifiers.get(modifierKey, PersistentDataType.DOUBLE);

            if (amount == null) {
                continue;
            }

            output.add(AttributeInstance.modifier(modifierKey.getKey(), amount, trigger));
        }
    }
}
