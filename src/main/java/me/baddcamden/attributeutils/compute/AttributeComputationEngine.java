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
 * <p>
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
     * Computes the staged values for a single attribute definition.
     * <p>
     * The returned {@link AttributeValueStages} contains six checkpoints: raw and permanent stages
     * for both the default and current buckets, followed by the fully computed totals. Global and
     * player instances are merged, respecting any override keys on the player instance.
     *
     * @param definition     description of the attribute, including defaults and caps
     * @param globalInstance global bucket of modifiers and overrides (may be {@code null})
     * @param playerInstance player-specific bucket of modifiers and overrides (may be {@code null})
     * @param vanillaSupplier optional supplier for retrieving vanilla values when the attribute is
     *                        dynamic (can be {@code null} for static attributes)
     * @param player         player whose vanilla values may be sampled; ignored for static
     *                       attributes
     * @return staged attribute values ready for persistence or immediate application
     */
    public AttributeValueStages compute(AttributeDefinition definition,
                                        AttributeInstance globalInstance,
                                        AttributeInstance playerInstance,
                                        VanillaAttributeSupplier vanillaSupplier,
                                        Player player) {

        // Establish the default stage using whichever instance is available before applying caps.
        double defaultBaseline = resolveDefaultBase(definition, globalInstance, playerInstance);
        String capKey = resolveCapKey(globalInstance, playerInstance);
        double rawDefault = definition.capConfig().clamp(defaultBaseline, capKey);
        Collection<ModifierEntry> defaultPermanentAdditives = collectModifiers(globalInstance, playerInstance, AttributeInstance::getDefaultPermanentAdditives);
        Collection<ModifierEntry> defaultTemporaryAdditives = collectModifiers(globalInstance, playerInstance, AttributeInstance::getDefaultTemporaryAdditives);
        Collection<ModifierEntry> defaultPermanentMultipliers = collectModifiers(globalInstance, playerInstance, AttributeInstance::getDefaultPermanentMultipliers);
        Collection<ModifierEntry> defaultTemporaryMultipliers = collectModifiers(globalInstance, playerInstance, AttributeInstance::getDefaultTemporaryMultipliers);
        double defaultPermanent = apply(
                rawDefault,
                defaultPermanentAdditives,
                Collections.emptyList(),
                defaultPermanentMultipliers,
                Collections.emptyList(),
                definition,
                capKey);
        double defaultFinal = apply(
                rawDefault,
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
        double currentPermanent = apply(
                rawCurrent,
                currentPermanentAdditives,
                Collections.emptyList(),
                currentPermanentMultipliers,
                Collections.emptyList(),
                definition,
                capKey);
        double currentFinal = apply(
                rawCurrent,
                currentPermanentAdditives,
                currentTemporaryAdditives,
                currentPermanentMultipliers,
                currentTemporaryMultipliers,
                definition,
                capKey);

        return new AttributeValueStages(rawDefault, defaultPermanent, defaultFinal, rawCurrent, currentPermanent, currentFinal);
    }

    /**
     * Chooses the default baseline value, preferring per-player overrides, then global overrides,
     * before falling back to the definition's default value.
     *
     * @param definition     attribute definition containing the fallback default
     * @param globalInstance global overrides (may be {@code null})
     * @param playerInstance player overrides (may be {@code null})
     * @return the default baseline value prior to cap clamping
     */
    private double resolveDefaultBase(AttributeDefinition definition, AttributeInstance globalInstance, AttributeInstance playerInstance) {
        if (playerInstance != null) {
            return playerInstance.getDefaultBaseValue();
        }
        if (globalInstance != null) {
            return globalInstance.getDefaultBaseValue();
        }
        return definition.defaultBaseValue();
    }

    /**
     * Resolves the cap override key, selecting the more specific player instance when available.
     *
     * @param globalInstance global overrides (may be {@code null})
     * @param playerInstance player overrides (may be {@code null})
     * @return override key to use during clamping, or {@code null} when no override exists
     */
    private String resolveCapKey(AttributeInstance globalInstance, AttributeInstance playerInstance) {
        if (playerInstance != null) {
            return playerInstance.getCapOverrideKey();
        }
        if (globalInstance != null) {
            return globalInstance.getCapOverrideKey();
        }
        return null;
    }

    /**
     * Establishes the current baseline value prior to applying current-stage modifiers.
     * <p>
     * Static attributes synchronize their current baseline with the computed default final value,
     * while dynamic attributes sample from the vanilla supplier and adjust by the difference from
     * configured defaults.
     *
     * @param definition      attribute definition describing defaults and dynamic behavior
     * @param vanillaSupplier supplier used for dynamic attributes (ignored when {@code null} or
     *                        when the attribute is static)
     * @param player          player whose vanilla attributes may be read
     * @param globalInstance  global modifiers and overrides (may be {@code null})
     * @param playerInstance  player modifiers and overrides (may be {@code null})
     * @param rawDefault      clamped default baseline
     * @param defaultFinal    computed default final value after modifiers
     * @return clamped current baseline ready for current-stage modifiers
     */
    private double buildCurrentBaseline(AttributeDefinition definition,
                                        VanillaAttributeSupplier vanillaSupplier,
                                        Player player,
                                        AttributeInstance globalInstance,
                                        AttributeInstance playerInstance,
                                        double rawDefault,
                                        double defaultFinal) {
        if (definition.dynamic()) {
            double vanilla = vanillaSupplier == null || player == null
                    ? definition.defaultCurrentValue()
                    : vanillaSupplier.getVanillaValue(player);
            double adjusted = vanilla;
            if (playerInstance != null) {
                adjusted += playerInstance.getCurrentBaseValue() - definition.defaultCurrentValue();
            } else if (globalInstance != null) {
                adjusted += globalInstance.getCurrentBaseValue() - definition.defaultCurrentValue();
            }
            // VAGUE/IMPROVEMENT NEEDED clarify whether vanilla sampling should be skipped when the
            // definition supplies a current override; current behavior always pulls vanilla when
            // dynamic even if override fully dictates the value.
            return definition.capConfig().clamp(adjusted, resolveCapKey(globalInstance, playerInstance));
        }

        double base = playerInstance != null
                ? playerInstance.getCurrentBaseValue()
                : globalInstance != null ? globalInstance.getCurrentBaseValue() : definition.defaultCurrentValue();
        return definition.capConfig().clamp(base, resolveCapKey(globalInstance, playerInstance));
    }

    /**
     * Applies additive and multiplier modifiers to produce a clamped value for a single stage.
     * <p>
     * Additives that specify multiplier keys are accumulated separately because they only benefit
     * from the subset of multipliers matching their keys. Unscoped additives receive the full
     * multiplier product for the stage.
     *
     * @param start                    starting baseline value
     * @param permanentAdditives       additives that persist across refreshes
     * @param temporaryAdditives       additives that expire when temporary effects clear
     * @param permanentMultipliers     multipliers that persist across refreshes
     * @param temporaryMultipliers     multipliers that expire when temporary effects clear
     * @param definition               attribute definition containing multiplier applicability and
     *                                 cap configuration
     * @param capKey                   optional cap override key
     * @return stage output after all applicable modifiers are applied and clamped
     */
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

    /**
     * Multiplies all applicable multiplier amounts together.
     *
     * @param multipliers collection of multipliers to include
     * @param allowedKeys optional subset of keys; when present only modifiers matching the keys are
     *                    used
     * @return combined multiplier value (defaults to {@code 1.0d} when none are provided)
     */
    private double multiplierProduct(Collection<ModifierEntry> multipliers, Set<String> allowedKeys) {
        if (multipliers == null || multipliers.isEmpty()) {
            return 1.0d;
        }

        return multipliers.stream()
                .filter(modifier -> allowedKeys == null || allowedKeys.contains(modifier.key().toLowerCase()))
                .mapToDouble(ModifierEntry::amount)
                .reduce(1.0d, (left, right) -> left * right);
    }

    /**
     * Sums additive modifiers, separating scoped (uses multiplier keys) and unscoped contributions.
     *
     * @param additives                      modifiers to sum
     * @param applicablePermanentMultipliers permanent multipliers already filtered by applicability
     * @param applicableTemporaryMultipliers temporary multipliers already filtered by applicability
     * @param scopedOnly                     when {@code true}, only modifiers that declare
     *                                       multiplier keys are included
     * @return additive contribution for the requested subset
     */
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

    /**
     * Computes the product of permanent and temporary multipliers for a specific set of keys.
     *
     * @param applicablePermanentMultipliers permanent multipliers already filtered by applicability
     * @param applicableTemporaryMultipliers temporary multipliers already filtered by applicability
     * @param allowedKeys keys the additive is allowed to use
     * @return combined multiplier value honoring the provided key filter
     */
    private double scopedMultiplierProduct(Collection<ModifierEntry> applicablePermanentMultipliers,
                                           Collection<ModifierEntry> applicableTemporaryMultipliers,
                                           Set<String> allowedKeys) {
        return multiplierProduct(applicablePermanentMultipliers, allowedKeys)
                * multiplierProduct(applicableTemporaryMultipliers, allowedKeys);
    }

    /**
     * Collects modifiers from global and player instances, concatenating them when both are
     * present.
     *
     * @param globalInstance global source of modifiers (may be {@code null})
     * @param playerInstance player source of modifiers (may be {@code null})
     * @param extractor      accessor used to pull the relevant modifier map from each instance
     * @return merged view of modifiers for the requested bucket
     */
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

    /**
     * Aligns the current baseline with the computed default value for static attributes.
     * <p>
     * Dynamic attributes pull their current baseline from the vanilla supplier, so no
     * synchronization is required.
     *
     * @param definition     attribute definition determining whether the attribute is dynamic
     * @param globalInstance global instance to update when no player instance exists
     * @param playerInstance player instance to update when present
     * @param defaultFinal   computed default final value used as the new baseline for static
     *                       attributes
     */
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
