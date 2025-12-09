package me.baddcamden.attributeutils.compute;

import me.baddcamden.attributeutils.api.VanillaAttributeSupplier;
import me.baddcamden.attributeutils.model.AttributeDefinition;
import me.baddcamden.attributeutils.model.AttributeInstance;
import me.baddcamden.attributeutils.model.AttributeValueStages;
import me.baddcamden.attributeutils.model.ModifierEntry;
import me.baddcamden.attributeutils.model.ModifierOperation;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class AttributeComputationEngine {

    public AttributeValueStages compute(AttributeDefinition definition,
                                        AttributeInstance globalInstance,
                                        AttributeInstance playerInstance,
                                        VanillaAttributeSupplier vanillaSupplier,
                                        Player player) {

        double baseDefault = resolveBase(definition, globalInstance, playerInstance);
        Collection<ModifierEntry> allModifiers = collectAllModifiers(globalInstance, playerInstance);

        List<ModifierEntry> defaultModifiers = allModifiers.stream()
                .filter(ModifierEntry::isDefaultModifier)
                .collect(Collectors.toList());

        List<ModifierEntry> permanentModifiers = allModifiers.stream()
                .filter(ModifierEntry::isPermanent)
                .filter(entry -> !entry.isDefaultModifier())
                .collect(Collectors.toList());

        List<ModifierEntry> nonDefaultModifiers = allModifiers.stream()
                .filter(entry -> !entry.isDefaultModifier())
                .collect(Collectors.toList());

        String capKey = resolveCapKey(globalInstance, playerInstance);
        double rawDefault = definition.capConfig().clamp(baseDefault, capKey);
        double defaultOnly = apply(rawDefault, defaultModifiers, definition, capKey);
        double defaultFinal = apply(defaultOnly, filterTemporary(defaultModifiers, true), definition, capKey);

        double rawCurrent = buildCurrentBaseline(definition, vanillaSupplier, player, globalInstance, playerInstance, rawDefault, defaultFinal);
        double permanentOnly = apply(rawCurrent, permanentModifiers, definition, capKey);
        double currentFinal = apply(permanentOnly, filterTemporary(nonDefaultModifiers, true), definition, capKey);

        return new AttributeValueStages(rawDefault, defaultOnly, defaultFinal, rawCurrent, permanentOnly, currentFinal);
    }

    private double resolveBase(AttributeDefinition definition, AttributeInstance globalInstance, AttributeInstance playerInstance) {
        if (playerInstance != null) {
            return playerInstance.getBaseValue();
        }
        if (globalInstance != null) {
            return globalInstance.getBaseValue();
        }
        return definition.defaultCurrentValue();
    }

    private String resolveCapKey(AttributeInstance globalInstance, AttributeInstance playerInstance) {
        if (playerInstance != null) {
            return playerInstance.getCapOverrideKey();
        }
        if (globalInstance != null) {
            return globalInstance.getCapOverrideKey();
        }
        return null;
    }

    private Collection<ModifierEntry> collectAllModifiers(AttributeInstance globalInstance, AttributeInstance playerInstance) {
        if (globalInstance == null && playerInstance == null) {
            return List.of();
        }

        if (globalInstance == null) {
            return playerInstance.getModifiers().values();
        }
        if (playerInstance == null) {
            return globalInstance.getModifiers().values();
        }

        List<ModifierEntry> combined = globalInstance.getModifiers().values().stream().collect(Collectors.toList());
        combined.addAll(playerInstance.getModifiers().values());
        return combined;
    }

    private double buildCurrentBaseline(AttributeDefinition definition,
                                        VanillaAttributeSupplier vanillaSupplier,
                                        Player player,
                                        AttributeInstance globalInstance,
                                        AttributeInstance playerInstance,
                                        double rawDefault,
                                        double defaultFinal) {
        if (definition.dynamic()) {
            double vanilla = vanillaSupplier == null || player == null ? definition.defaultCurrentValue() : vanillaSupplier.getVanillaValue(player);
            double adjusted = vanilla;
            if (playerInstance != null) {
                adjusted += playerInstance.getBaseValue() - definition.defaultCurrentValue();
            } else if (globalInstance != null) {
                adjusted += globalInstance.getBaseValue() - definition.defaultCurrentValue();
            }
            return definition.capConfig().clamp(adjusted, resolveCapKey(globalInstance, playerInstance));
        }

        double base = playerInstance != null ? playerInstance.getBaseValue() : definition.defaultBaseValue();
        double defaultDelta = defaultFinal - rawDefault;
        double rawCurrent = base + defaultDelta;
        return definition.capConfig().clamp(rawCurrent, resolveCapKey(globalInstance, playerInstance));
    }

    private double apply(double start, Collection<ModifierEntry> modifiers, AttributeDefinition definition, String capKey) {
        double value = start;
        double multiplier = 1.0d;

        for (ModifierEntry modifier : modifiers) {
            if (modifier.operation() == ModifierOperation.ADD) {
                value += modifier.amount();
            } else if (definition.multiplierApplicability().canApply(modifier.key())) {
                multiplier *= modifier.amount();
            }
        }

        return definition.capConfig().clamp(value * multiplier, capKey);
    }

    private Collection<ModifierEntry> filterTemporary(Collection<ModifierEntry> modifiers, boolean includeTemporary) {
        if (includeTemporary) {
            return modifiers;
        }
        return modifiers.stream()
                .filter(ModifierEntry::isPermanent)
                .collect(Collectors.toList());
    }
}
