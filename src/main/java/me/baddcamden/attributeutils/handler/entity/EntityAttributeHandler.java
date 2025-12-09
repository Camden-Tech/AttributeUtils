package me.baddcamden.attributeutils.handler.entity;

import me.baddcamden.attributeutils.model.AttributeDefinition;
import me.baddcamden.attributeutils.attributes.model.AttributeInstance;
import me.baddcamden.attributeutils.attributes.model.AttributeModel;
import me.baddcamden.attributeutils.attributes.model.AttributeTrigger;
import me.baddcamden.attributeutils.service.AttributeService;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EntityAttributeHandler {

    private final AttributeService attributeService;
    private final NamespacedKey attributesKey;
    private final NamespacedKey definitionsKey;
    private final NamespacedKey modifiersKey;
    private final NamespacedKey valueKey;
    private final NamespacedKey maxValueKey;
    private final NamespacedKey customEntityKey;

    public EntityAttributeHandler(Plugin plugin, AttributeService attributeService) {
        this.attributeService = attributeService;
        this.attributesKey = new NamespacedKey(plugin, "attributes");
        this.definitionsKey = new NamespacedKey(plugin, "definitions");
        this.modifiersKey = new NamespacedKey(plugin, "modifiers");
        this.valueKey = new NamespacedKey(plugin, "value");
        this.maxValueKey = new NamespacedKey(plugin, "max_value");
        this.customEntityKey = new NamespacedKey(plugin, "custom-entity-id");
    }

    public void applyPlayerCaps(Player player) {
        setFoodLevel(player, attributeApi.queryAttribute("max_hunger", player));
        setOxygen(player, attributeApi.queryAttribute("max_oxygen", player));
    }

    public List<AttributeInstance> readEntityAttributes(LivingEntity entity, AttributeTrigger triggerFilter) {
        List<AttributeInstance> instances = new ArrayList<>();
        PersistentDataContainer dataContainer = entity.getPersistentDataContainer();

        instances.addAll(parseAttributeContainer(dataContainer, triggerFilter));
        collectCustomEntityDefaults(dataContainer, instances);

        return instances;
    }

    public void applyEntityAttributes(LivingEntity entity, AttributeTrigger triggerFilter) {
        readEntityAttributes(entity, triggerFilter).forEach(instance -> instance.apply(attributeService));
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

        for (NamespacedKey attributeKey : definitions.getKeys()) {
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

    private void collectCustomEntityDefaults(PersistentDataContainer dataContainer, List<AttributeInstance> output) {
        String customEntityId = dataContainer.get(customEntityKey, PersistentDataType.STRING);

        if (customEntityId == null || customEntityId.isEmpty()) {
            return;
        }

        Map<String, AttributeModel> defaults = attributeService.getEntityDefaults(customEntityId);

        defaults.values().forEach(model ->
                output.add(AttributeInstance.definition(model.getKey(), model.getValue(), model.getMaxValue(), AttributeTrigger.CUSTOM))
        );
    }

    private void setFoodLevel(Player player, Optional<AttributeModel> model) {
        model.ifPresent(attribute -> player.setFoodLevel((int) attribute.getValue()));
    }

    private void setOxygen(Player player, Optional<AttributeDefinition> definition) {
        definition.ifPresent(attribute -> player.setMaximumAir((int) attribute.capConfig().globalMax()));
    }
}
