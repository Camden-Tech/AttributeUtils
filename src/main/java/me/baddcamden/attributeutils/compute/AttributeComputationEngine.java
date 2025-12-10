package me.baddcamden.attributeutils.compute;

import me.baddcamden.attributeutils.api.VanillaAttributeSupplier;
import me.baddcamden.attributeutils.model.AttributeDefinition;
import me.baddcamden.attributeutils.model.AttributeInstance;
import me.baddcamden.attributeutils.model.AttributeValueStages;
import me.baddcamden.attributeutils.model.ModifierEntry;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class AttributeComputationEngine {

    public AttributeValueStages compute(AttributeDefinition definition,
                                        AttributeInstance globalInstance,
                                        AttributeInstance playerInstance,
                                        VanillaAttributeSupplier vanillaSupplier,
                                        Player player) {

        double defaultBaseline = resolveDefaultBase(definition, globalInstance, playerInstance);
        String capKey = resolveCapKey(globalInstance, playerInstance);
        double rawDefault = definition.capConfig().clamp(defaultBaseline, capKey);
        double defaultPermanent = apply(rawDefault,
                collectModifiers(globalInstance, playerInstance, AttributeInstance::getDefaultPermanentAdditives),
                collectModifiers(globalInstance, playerInstance, AttributeInstance::getDefaultPermanentMultipliers),
                definition,
                capKey,
                true);
        double defaultFinal = apply(defaultPermanent,
                collectModifiers(globalInstance, playerInstance, AttributeInstance::getDefaultTemporaryAdditives),
                collectModifiers(globalInstance, playerInstance, AttributeInstance::getDefaultTemporaryMultipliers),
                definition,
                capKey,
                true);

        if (!definition.dynamic()) {
            syncCurrentBaseline(definition, baselineTarget(playerInstance, globalInstance), defaultFinal, capKey);
        }

        double rawCurrent = buildCurrentBaseline(definition, vanillaSupplier, player, globalInstance, playerInstance, rawDefault, defaultFinal);
        double currentPermanent = apply(rawCurrent,
                collectModifiers(globalInstance, playerInstance, AttributeInstance::getCurrentPermanentAdditives),
                collectModifiers(globalInstance, playerInstance, AttributeInstance::getCurrentPermanentMultipliers),
                definition,
                capKey,
                false);
        double currentFinal = apply(currentPermanent,
                collectModifiers(globalInstance, playerInstance, AttributeInstance::getCurrentTemporaryAdditives),
                collectModifiers(globalInstance, playerInstance, AttributeInstance::getCurrentTemporaryMultipliers),
                definition,
                capKey,
                false);

        return new AttributeValueStages(rawDefault, defaultPermanent, defaultFinal, rawCurrent, currentPermanent, currentFinal);
    }

    private double resolveDefaultBase(AttributeDefinition definition, AttributeInstance globalInstance, AttributeInstance playerInstance) {
        if (playerInstance != null) {
            return playerInstance.getDefaultBaseValue();
        }
        if (globalInstance != null) {
            return globalInstance.getDefaultBaseValue();
        }
        return definition.defaultBaseValue();
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
                adjusted += playerInstance.getCurrentBaseValue() - definition.defaultCurrentValue();
            } else if (globalInstance != null) {
                adjusted += globalInstance.getCurrentBaseValue() - definition.defaultCurrentValue();
            }
            return definition.capConfig().clamp(adjusted, resolveCapKey(globalInstance, playerInstance));
        }

        double base = playerInstance != null
                ? playerInstance.getCurrentBaseValue()
                : globalInstance != null ? globalInstance.getCurrentBaseValue() : definition.defaultCurrentValue();
        return definition.capConfig().clamp(base, resolveCapKey(globalInstance, playerInstance));
    }

    private double apply(double start,
                         Collection<ModifierEntry> additiveModifiers,
                         Collection<ModifierEntry> multiplierModifiers,
                         AttributeDefinition definition,
                         String capKey,
                         boolean defaultLayer) {
        Collection<ModifierEntry> applicableMultipliers = multiplierModifiers.stream()
                .filter(modifier -> definition.multiplierApplicability().canApply(modifier.key()))
                .toList();

        double value = start * aggregateMultiplier(applicableMultipliers, null, defaultLayer);

        for (ModifierEntry modifier : additiveModifiers) {
            double multiplier = aggregateMultiplier(applicableMultipliers, modifier, defaultLayer);
            value += modifier.amount() * multiplier;
        }

        return definition.capConfig().clamp(value, capKey);
    }

    private Collection<ModifierEntry> collectModifiers(AttributeInstance globalInstance,
                                                      AttributeInstance playerInstance,
                                                      Function<AttributeInstance, Map<String, ModifierEntry>> extractor) {
        if (globalInstance == null && playerInstance == null) {
            return Collections.emptyList();
        }

        if (globalInstance == null) {
            return extractor.apply(playerInstance).values();
        }
        if (playerInstance == null) {
            return extractor.apply(globalInstance).values();
        }

        List<ModifierEntry> combined = new ArrayList<>(extractor.apply(globalInstance).values());
        combined.addAll(extractor.apply(playerInstance).values());
        return combined;
    }

    private void syncCurrentBaseline(AttributeDefinition definition,
                                     AttributeInstance baselineTarget,
                                     double defaultFinal,
                                     String capKey) {
        if (baselineTarget == null) {
            return;
        }

        double knownDefault = baselineTarget.getLastKnownDefaultFinal();
        if (Double.isNaN(knownDefault)) {
            baselineTarget.setLastKnownDefaultFinal(defaultFinal);
            return;
        }

        double delta = defaultFinal - knownDefault;
        if (Math.abs(delta) > 1.0e-9) {
            double updatedCurrent = definition.capConfig().clamp(baselineTarget.getCurrentBaseValue() + delta, capKey);
            baselineTarget.setCurrentBaseValue(updatedCurrent);
            baselineTarget.setLastKnownDefaultFinal(defaultFinal);
        }
    }

    private AttributeInstance baselineTarget(AttributeInstance playerInstance, AttributeInstance globalInstance) {
        if (playerInstance != null) {
            return playerInstance;
        }
        return globalInstance;
    }

    private double aggregateMultiplier(Collection<ModifierEntry> multipliers,
                                       ModifierEntry additive,
                                       boolean defaultLayer) {
        double multiplier = 1.0d;
        for (ModifierEntry entry : multipliers) {
            if (defaultLayer && !entry.appliesToDefault()) {
                continue;
            }
            if (!defaultLayer && !entry.appliesToCurrent()) {
                continue;
            }
            if (additive != null && !additive.appliesAllMultipliers()
                    && !additive.multiplierKeys().contains(entry.key().toLowerCase())) {
                continue;
            }
            multiplier *= entry.amount();
        }
        return multiplier;
    }
}
