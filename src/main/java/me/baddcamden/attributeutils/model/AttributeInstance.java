package me.baddcamden.attributeutils.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Holds the mutable state for a single attribute, including the baseline values and the
 * individual modifier buckets used during computation.
 *
 * <p>Baselines come in two flavours:
 * <ul>
 *     <li><strong>Default</strong>: the configured base and its computed final value, used as
 *     the reference point for all players.</li>
 *     <li><strong>Current</strong>: the live base value for a specific player/global instance.
 *     For static attributes the current baseline tracks the default baseline via
 *     {@link #synchronizeCurrentBaseWithDefault(double, CapConfig)} so persisted deltas remain
 *     meaningful after recomputation.</li>
 * </ul></p>
 *
 * <p>Modifiers are split into additive and multiplicative buckets for both the default and
 * current layers, and further divided into permanent and temporary buckets. This mirrors the
 * computation stages in {@code AttributeComputationEngine}: permanent buckets run before
 * temporary ones, and default buckets run before current buckets. Multipliers are always
 * re-applied to additive stacks so the owning engine can reproduce vanilla-style stacking.</p>
 */
public final class AttributeInstance {

    /**
     * Definition that governs caps, defaults, and applicability rules for this instance.
     */
    private final AttributeDefinition definition;
    /**
     * Persisted base value shared by all players before any modifiers are applied.
     */
    private double defaultBaseValue;
    /**
     * Player- or context-specific base value that can diverge from the default baseline.
     */
    private double currentBaseValue;
    /**
     * Cached result of the last default computation. Used to adjust the current baseline by
     * the same delta when static attributes change so the persisted "current" deltas keep
     * matching player-visible values.
     */
    private double defaultFinalBaseline;
    /**
     * All registered modifiers keyed by normalized key. Buckets below hold references to the same
     * entries to avoid duplication.
     */
    private final Map<String, ModifierEntry> modifiers = new LinkedHashMap<>();
    /**
     * Permanent additive modifiers applied during the default-layer computation stage.
     */
    private final Map<String, ModifierEntry> defaultPermanentAdditives = new LinkedHashMap<>();
    /**
     * Temporary additive modifiers applied during the default-layer computation stage.
     */
    private final Map<String, ModifierEntry> defaultTemporaryAdditives = new LinkedHashMap<>();
    /**
     * Permanent multiplier modifiers applied during the default-layer computation stage.
     */
    private final Map<String, ModifierEntry> defaultPermanentMultipliers = new LinkedHashMap<>();
    /**
     * Temporary multiplier modifiers applied during the default-layer computation stage.
     */
    private final Map<String, ModifierEntry> defaultTemporaryMultipliers = new LinkedHashMap<>();
    /**
     * Permanent additive modifiers applied during the current-layer computation stage.
     */
    private final Map<String, ModifierEntry> currentPermanentAdditives = new LinkedHashMap<>();
    /**
     * Temporary additive modifiers applied during the current-layer computation stage.
     */
    private final Map<String, ModifierEntry> currentTemporaryAdditives = new LinkedHashMap<>();
    /**
     * Permanent multiplier modifiers applied during the current-layer computation stage.
     */
    private final Map<String, ModifierEntry> currentPermanentMultipliers = new LinkedHashMap<>();
    /**
     * Temporary multiplier modifiers applied during the current-layer computation stage.
     */
    private final Map<String, ModifierEntry> currentTemporaryMultipliers = new LinkedHashMap<>();
    /**
     * Optional key used to override the definition's default cap selection.
     */
    private String capOverrideKey;

    /**
     * Builds an instance seeded with the definition's configured defaults and no cap override.
     * The current layer starts at {@link AttributeDefinition#defaultCurrentValue()} so static
     * attributes can diverge from the default baseline without losing the original default base.
     */
    public AttributeInstance(AttributeDefinition definition) {
        this(definition, definition.defaultBaseValue(), definition.defaultCurrentValue(), null);
    }

    /**
     * Builds an instance with explicit baseline values and an optional cap override key. The
     * default final baseline cache is primed with the definition's configured current baseline so
     * {@link #synchronizeCurrentBaseWithDefault(double, CapConfig)} can compute deltas correctly.
     */
    public AttributeInstance(AttributeDefinition definition, double defaultBaseValue, double currentBaseValue, String capOverrideKey) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.defaultBaseValue = defaultBaseValue;
        this.currentBaseValue = currentBaseValue;
        this.defaultFinalBaseline = definition.defaultCurrentValue(); //VAGUE/IMPROVEMENT NEEDED defaultFinalBaseline ignores the provided baselines and assumes the definition's defaults
        this.capOverrideKey = capOverrideKey;
    }

    /**
     * Returns the attribute definition driving bucket applicability, caps, and baseline defaults.
     */
    public AttributeDefinition getDefinition() {
        return definition;
    }

    /**
     * Returns the current default-layer base value before modifiers or caps are applied.
     */
    public double getDefaultBaseValue() {
        return defaultBaseValue;
    }

    /**
     * Sets the default-layer base value without touching the current layer. This is used when only
     * the shared baseline should change and per-player deltas must remain intact.
     */
    public void setDefaultBaseValue(double defaultBaseValue) {
        this.defaultBaseValue = defaultBaseValue;
    }

    /**
     * Alias for {@link #getDefaultBaseValue()} retained for backwards compatibility with callers
     * that expect the older method name.
     */
    public double getBaseValue() {
        return defaultBaseValue;
    }

    /**
     * Updates both default and current baselines to the same value, effectively resetting any
     * player-specific deltas. Useful when re-seeding from configuration defaults.
     */
    public void setBaseValue(double baseValue) {
        this.defaultBaseValue = baseValue;
        this.currentBaseValue = baseValue;
    }

    /**
     * Returns the current-layer base value before computation. This value may diverge from the
     * default base when attributes are dynamic or have player-specific offsets.
     */
    public double getCurrentBaseValue() {
        return currentBaseValue;
    }

    /**
     * Sets the current-layer base value without affecting the default baseline, preserving any
     * shared reference value.
     */
    public void setCurrentBaseValue(double currentBaseValue) {
        this.currentBaseValue = currentBaseValue;
    }

    /**
     * Returns the last computed default final value, used to calculate deltas when syncing the
     * current baseline after recomputation.
     */
    public double getDefaultFinalBaseline() {
        return defaultFinalBaseline;
    }

    /**
     * Updates the cached default final value that represents the last fully-computed default
     * baseline. This should be called after re-running the computation engine.
     */
    public void setDefaultFinalBaseline(double defaultFinalBaseline) {
        this.defaultFinalBaseline = defaultFinalBaseline;
    }

    /**
     * Returns an immutable snapshot of all registered modifiers keyed by normalized key.
     */
    public Map<String, ModifierEntry> getModifiers() {
        return Map.copyOf(modifiers);
    }

    /**
     * Snapshot of permanent additive modifiers targeting the default layer. Entries share the
     * instances stored in {@link #getModifiers()}.
     */
    public Map<String, ModifierEntry> getDefaultPermanentAdditives() {
        return Map.copyOf(defaultPermanentAdditives);
    }

    /**
     * Snapshot of temporary additive modifiers targeting the default layer.
     */
    public Map<String, ModifierEntry> getDefaultTemporaryAdditives() {
        return Map.copyOf(defaultTemporaryAdditives);
    }

    /**
     * Snapshot of permanent multiplier modifiers targeting the default layer.
     */
    public Map<String, ModifierEntry> getDefaultPermanentMultipliers() {
        return Map.copyOf(defaultPermanentMultipliers);
    }

    /**
     * Snapshot of temporary multiplier modifiers targeting the default layer.
     */
    public Map<String, ModifierEntry> getDefaultTemporaryMultipliers() {
        return Map.copyOf(defaultTemporaryMultipliers);
    }

    /**
     * Snapshot of permanent additive modifiers targeting the current layer.
     */
    public Map<String, ModifierEntry> getCurrentPermanentAdditives() {
        return Map.copyOf(currentPermanentAdditives);
    }

    /**
     * Snapshot of temporary additive modifiers targeting the current layer.
     */
    public Map<String, ModifierEntry> getCurrentTemporaryAdditives() {
        return Map.copyOf(currentTemporaryAdditives);
    }

    /**
     * Snapshot of permanent multiplier modifiers targeting the current layer.
     */
    public Map<String, ModifierEntry> getCurrentPermanentMultipliers() {
        return Map.copyOf(currentPermanentMultipliers);
    }

    /**
     * Snapshot of temporary multiplier modifiers targeting the current layer.
     */
    public Map<String, ModifierEntry> getCurrentTemporaryMultipliers() {
        return Map.copyOf(currentTemporaryMultipliers);
    }

    /**
     * Registers a modifier and distributes it into the appropriate buckets for both the default
     * and current stages. Buckets only retain a single entry per key; calling this method again
     * with the same key replaces the previous modifier everywhere. Keys are normalized to lowercase
     * so lookups remain consistent across commands and persistence.
     */
    public void addModifier(ModifierEntry modifier) {
        Objects.requireNonNull(modifier, "modifier");
        String key = normalizeKey(modifier.key());
        removeModifier(key);
        modifiers.put(key, modifier);
        addToBucket(key, modifier, true);
        addToBucket(key, modifier, false);
    }

    /**
     * Removes a modifier from the flat map and from every bucket. Safe to call with unknown or
     * null keys.
     */
    public void removeModifier(String key) {
        if (key == null) {
            return;
        }
        String normalized = normalizeKey(key);
        modifiers.remove(normalized);
        removeFromBuckets(normalized);
    }

    /**
     * Drops all temporary modifiers across both layers and buckets while preserving permanent
     * modifiers. The main modifier map is cleaned alongside the bucket maps to keep references in
     * sync for future updates.
     */
    public void purgeTemporaryModifiers() {
        modifiers.entrySet().removeIf(entry -> {
            if (entry.getValue().isTemporary()) {
                removeFromBuckets(entry.getKey());
                return true;
            }
            return false;
        });
        clearTemporaryBuckets();
    }

    /**
     * Returns the override key used when resolving cap maxima for this instance.
     */
    public String getCapOverrideKey() {
        return capOverrideKey;
    }

    /**
     * Assigns the cap override key that will be honored during value clamping. A {@code null} or
     * blank key means the global maximum will be used.
     */
    public void setCapOverrideKey(String capOverrideKey) {
        this.capOverrideKey = capOverrideKey;
    }

    /**
     * Aligns the current baseline with a newly computed default value using the definition's cap
     * configuration. This overload preserves the previous behaviour of clamping with the
     * definition's cap configuration and any override key stored on the instance.
     */
    public void synchronizeCurrentBaseWithDefault(double defaultFinal) {
        synchronizeCurrentBaseWithDefault(defaultFinal, definition.capConfig());
    }

    /**
     * Adjusts the current baseline by the same delta applied to the default baseline during
     * computation, then clamps using the provided cap config and override key. This keeps current
     * values that were previously persisted in line with recalculated defaults and ensures cap
     * overrides remain effective during the sync.
     */
    public void synchronizeCurrentBaseWithDefault(double defaultFinal, CapConfig capConfig) {
        double delta = defaultFinal - defaultFinalBaseline;
        if (Math.abs(delta) < 1.0E-9) {
            return;
        }
        this.currentBaseValue = capConfig.clamp(currentBaseValue + delta, capOverrideKey);
        this.defaultFinalBaseline = defaultFinal;
    }

    /**
     * Removes any remaining temporary bucket entries to guarantee clean state after purging the
     * main modifier map.
     */
    private void clearTemporaryBuckets() {
        defaultTemporaryAdditives.clear();
        defaultTemporaryMultipliers.clear();
        currentTemporaryAdditives.clear();
        currentTemporaryMultipliers.clear();
    }

    /**
     * Places a modifier into the correct permanent/temporary bucket for the selected layer.
     */
    private void addToBucket(String key, ModifierEntry modifier, boolean defaultLayer) {
        if (defaultLayer && !modifier.appliesToDefault()) {
            return;
        }
        if (!defaultLayer && !modifier.appliesToCurrent()) {
            return;
        }

        Map<String, ModifierEntry> target = resolveBucket(modifier, defaultLayer);
        target.put(key, modifier);
    }

    /**
     * Resolves the bucket that matches the modifier's operation, duration, and target layer.
     */
    private Map<String, ModifierEntry> resolveBucket(ModifierEntry modifier, boolean defaultLayer) {
        boolean temporary = modifier.isTemporary();
        if (modifier.operation() == ModifierOperation.ADD) {
            if (defaultLayer) {
                return temporary ? defaultTemporaryAdditives : defaultPermanentAdditives;
            }
            return temporary ? currentTemporaryAdditives : currentPermanentAdditives;
        }

        if (defaultLayer) {
            return temporary ? defaultTemporaryMultipliers : defaultPermanentMultipliers;
        }
        return temporary ? currentTemporaryMultipliers : currentPermanentMultipliers;
    }

    /**
     * Removes a modifier from every bucket, regardless of which layer it previously targeted.
     */
    private void removeFromBuckets(String key) {
        defaultPermanentAdditives.remove(key);
        defaultTemporaryAdditives.remove(key);
        defaultPermanentMultipliers.remove(key);
        defaultTemporaryMultipliers.remove(key);
        currentPermanentAdditives.remove(key);
        currentTemporaryAdditives.remove(key);
        currentPermanentMultipliers.remove(key);
        currentTemporaryMultipliers.remove(key);
    }

    /**
     * Normalizes modifier keys to lowercase to allow case-insensitive storage and lookups.
     */
    private String normalizeKey(String key) {
        return key.toLowerCase();
    }
}
