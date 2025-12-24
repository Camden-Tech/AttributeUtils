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
import java.util.Set;
import java.util.function.Function;

/**
 * Responsible for turning attribute state plus modifier buckets into a staged value snapshot.
 * The engine evaluates in the following order:
 * <ol>
 *     <li>Establish the default baseline (global/player/current definition) and clamp to caps.</li>
 *     <li>Apply default permanent additives, default temporary additives, default permanent
 *     multipliers, and default temporary multipliers in that order.</li>
 *     <li>Synchronize the current baseline with the computed default final value when the
 *     attribute is static.</li>
 *     <li>Establish the current baseline (vanilla-aware for dynamic attributes) and clamp.</li>
 *     <li>Apply current permanent additives, current temporary additives, current permanent
 *     multipliers, and current temporary multipliers in that order.</li>
 *     <li>Clamp after each stage using {@link me.baddcamden.attributeutils.model.CapConfig} to
 *     respect override keys.</li>
 * </ol>
 * Additive modifiers stack onto the baseline before multipliers are applied. The multiplier product
 * is assembled by stage, and each additive either uses the full stage multiplier or the subset
 * referenced in {@link ModifierEntry#multiplierKeys()} when
 * {@link ModifierEntry#useMultiplierKeys()} is true.
 */
public class AttributeComputationEngine {

    /**
     * Computes all stages for a single attribute, combining global and player modifier buckets.
     * Caps, defaults and current baselines are resolved in one pass so callers can persist or apply
     * the staged values directly.
     */
    public AttributeValueStages compute(AttributeDefinition definition,
                                        AttributeInstance globalInstance,
                                        AttributeInstance playerInstance,
                                        VanillaAttributeSupplier vanillaSupplier,
                                        Player player) {

        double defaultBaseline = resolveDefaultBase(definition, globalInstance, playerInstance);
        String capKey = resolveCapKey(globalInstance, playerInstance);
        double rawDefault = definition.capConfig().clamp(defaultBaseline, capKey);
        Collection<ModifierEntry> defaultPermanentAdditives = collectModifiers(globalInstance, playerInstance, AttributeInstance::getDefaultPermanentAdditives);
        Collection<ModifierEntry> defaultTemporaryAdditives = collectModifiers(globalInstance, playerInstance, AttributeInstance::getDefaultTemporaryAdditives);
        Collection<ModifierEntry> defaultPermanentMultipliers = collectModifiers(globalInstance, playerInstance, AttributeInstance::getDefaultPermanentMultipliers);
        Collection<ModifierEntry> defaultTemporaryMultipliers = collectModifiers(globalInstance, playerInstance, AttributeInstance::getDefaultTemporaryMultipliers);
        double defaultPermanent = apply(rawDefault,
                defaultPermanentAdditives,
                Collections.emptyList(),
                defaultPermanentMultipliers,
                Collections.emptyList(),
                definition,
                capKey);
        double defaultFinal = apply(rawDefault,
                defaultPermanentAdditives,
                defaultTemporaryAdditives,
                defaultPermanentMultipliers,
                defaultTemporaryMultipliers,
                definition,
                capKey);

        synchronizeCurrentBaseline(definition, globalInstance, playerInstance, defaultFinal);

        double rawCurrent = buildCurrentBaseline(definition, vanillaSupplier, player, globalInstance, playerInstance, rawDefault, defaultFinal);
        Collection<ModifierEntry> currentPermanentAdditives = collectModifiers(globalInstance, playerInstance, AttributeInstance::getCurrentPermanentAdditives);
        Collection<ModifierEntry> currentTemporaryAdditives = collectModifiers(globalInstance, playerInstance, AttributeInstance::getCurrentTemporaryAdditives);
        Collection<ModifierEntry> currentPermanentMultipliers = collectModifiers(globalInstance, playerInstance, AttributeInstance::getCurrentPermanentMultipliers);
        Collection<ModifierEntry> currentTemporaryMultipliers = collectModifiers(globalInstance, playerInstance, AttributeInstance::getCurrentTemporaryMultipliers);
        double currentPermanent = apply(rawCurrent,
                currentPermanentAdditives,
                Collections.emptyList(),
                currentPermanentMultipliers,
                Collections.emptyList(),
                definition,
                capKey);
        double currentFinal = apply(rawCurrent,
                currentPermanentAdditives,
                currentTemporaryAdditives,
                currentPermanentMultipliers,
                currentTemporaryMultipliers,
                definition,
                capKey);

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
                //VAGUE/IMPROVEMENT NEEDED assumes the stored base delta should be transferred onto the live
                //VAGUE/IMPROVEMENT NEEDED vanilla reading instead of the definition's default, which may not
                //VAGUE/IMPROVEMENT NEEDED reflect how the original base was computed.
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
                         Collection<ModifierEntry> permanentAdditives,
                         Collection<ModifierEntry> temporaryAdditives,
                         Collection<ModifierEntry> permanentMultipliers,
                         Collection<ModifierEntry> temporaryMultipliers,
                         AttributeDefinition definition,
                         String capKey) {
        List<ModifierEntry> applicablePermanentMultipliers = permanentMultipliers.stream()
                .filter(modifier -> definition.multiplierApplicability().canApply(modifier.key()))
                .toList();
        List<ModifierEntry> applicableTemporaryMultipliers = temporaryMultipliers.stream()
                .filter(modifier -> definition.multiplierApplicability().canApply(modifier.key()))
                .toList();

        double unrestrictedSubtotal = start;
        double keyedContribution = 0.0d;

        unrestrictedSubtotal += sumAdditives(permanentAdditives, applicablePermanentMultipliers, applicableTemporaryMultipliers, false);
        unrestrictedSubtotal += sumAdditives(temporaryAdditives, applicablePermanentMultipliers, applicableTemporaryMultipliers, false);
        keyedContribution += sumAdditives(permanentAdditives, applicablePermanentMultipliers, applicableTemporaryMultipliers, true);
        keyedContribution += sumAdditives(temporaryAdditives, applicablePermanentMultipliers, applicableTemporaryMultipliers, true);

        double combinedMultiplier = multiplierProduct(applicablePermanentMultipliers, null)
                * multiplierProduct(applicableTemporaryMultipliers, null);
        double value = (unrestrictedSubtotal * combinedMultiplier) + keyedContribution;

        return definition.capConfig().clamp(value, capKey);
    }

    private double multiplierProduct(Collection<ModifierEntry> multipliers, Set<String> allowedKeys) {
        if (multipliers == null || multipliers.isEmpty()) {
            return 1.0d;
        }

        return multipliers.stream()
                .filter(modifier -> allowedKeys == null || allowedKeys.contains(modifier.key().toLowerCase()))
                .mapToDouble(ModifierEntry::amount)
                .reduce(1.0d, (left, right) -> left * right);
    }

    private double sumAdditives(Collection<ModifierEntry> additives,
                                Collection<ModifierEntry> applicablePermanentMultipliers,
                                Collection<ModifierEntry> applicableTemporaryMultipliers,
                                boolean scopedOnly) {
        if (additives == null || additives.isEmpty()) {
            return 0.0d;
        }

        return additives.stream()
                .filter(modifier -> modifier.useMultiplierKeys() == scopedOnly)
                .mapToDouble(modifier -> scopedOnly
                        ? modifier.amount() * scopedMultiplierProduct(applicablePermanentMultipliers,
                        applicableTemporaryMultipliers,
                        modifier.multiplierKeys())
                        : modifier.amount())
                .sum();
    }

    private double scopedMultiplierProduct(Collection<ModifierEntry> applicablePermanentMultipliers,
                                           Collection<ModifierEntry> applicableTemporaryMultipliers,
                                           Set<String> allowedKeys) {
        return multiplierProduct(applicablePermanentMultipliers, allowedKeys)
                * multiplierProduct(applicableTemporaryMultipliers, allowedKeys);
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

    private void synchronizeCurrentBaseline(AttributeDefinition definition,
                                            AttributeInstance globalInstance,
                                            AttributeInstance playerInstance,
                                            double defaultFinal) {
        if (definition.dynamic()) {
            return;
        }

        if (playerInstance != null) {
            playerInstance.synchronizeCurrentBaseWithDefault(defaultFinal, definition.capConfig());
            return;
        }

        if (globalInstance != null) {
            globalInstance.synchronizeCurrentBaseWithDefault(defaultFinal, definition.capConfig());
        }
    }
}
