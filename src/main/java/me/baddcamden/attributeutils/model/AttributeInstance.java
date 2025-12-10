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

    private final AttributeDefinition definition;
    private double defaultBaseValue;
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
    private final Map<String, ModifierEntry> defaultPermanentAdditives = new LinkedHashMap<>();
    private final Map<String, ModifierEntry> defaultTemporaryAdditives = new LinkedHashMap<>();
    private final Map<String, ModifierEntry> defaultPermanentMultipliers = new LinkedHashMap<>();
    private final Map<String, ModifierEntry> defaultTemporaryMultipliers = new LinkedHashMap<>();
    private final Map<String, ModifierEntry> currentPermanentAdditives = new LinkedHashMap<>();
    private final Map<String, ModifierEntry> currentTemporaryAdditives = new LinkedHashMap<>();
    private final Map<String, ModifierEntry> currentPermanentMultipliers = new LinkedHashMap<>();
    private final Map<String, ModifierEntry> currentTemporaryMultipliers = new LinkedHashMap<>();
    private String capOverrideKey;

    public AttributeInstance(AttributeDefinition definition) {
        this(definition, definition.defaultBaseValue(), definition.defaultCurrentValue(), null);
    }

    public AttributeInstance(AttributeDefinition definition, double defaultBaseValue, double currentBaseValue, String capOverrideKey) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.defaultBaseValue = defaultBaseValue;
        this.currentBaseValue = currentBaseValue;
        this.defaultFinalBaseline = definition.defaultCurrentValue();
        this.capOverrideKey = capOverrideKey;
    }

    public AttributeDefinition getDefinition() {
        return definition;
    }

    public double getDefaultBaseValue() {
        return defaultBaseValue;
    }

    public void setDefaultBaseValue(double defaultBaseValue) {
        this.defaultBaseValue = defaultBaseValue;
    }

    public double getBaseValue() {
        return defaultBaseValue;
    }

    public void setBaseValue(double baseValue) {
        this.defaultBaseValue = baseValue;
        this.currentBaseValue = baseValue;
    }

    public double getCurrentBaseValue() {
        return currentBaseValue;
    }

    public void setCurrentBaseValue(double currentBaseValue) {
        this.currentBaseValue = currentBaseValue;
    }

    public double getDefaultFinalBaseline() {
        return defaultFinalBaseline;
    }

    public void setDefaultFinalBaseline(double defaultFinalBaseline) {
        this.defaultFinalBaseline = defaultFinalBaseline;
    }

    public Map<String, ModifierEntry> getModifiers() {
        return Map.copyOf(modifiers);
    }

    public Map<String, ModifierEntry> getDefaultPermanentAdditives() {
        return Map.copyOf(defaultPermanentAdditives);
    }

    public Map<String, ModifierEntry> getDefaultTemporaryAdditives() {
        return Map.copyOf(defaultTemporaryAdditives);
    }

    public Map<String, ModifierEntry> getDefaultPermanentMultipliers() {
        return Map.copyOf(defaultPermanentMultipliers);
    }

    public Map<String, ModifierEntry> getDefaultTemporaryMultipliers() {
        return Map.copyOf(defaultTemporaryMultipliers);
    }

    public Map<String, ModifierEntry> getCurrentPermanentAdditives() {
        return Map.copyOf(currentPermanentAdditives);
    }

    public Map<String, ModifierEntry> getCurrentTemporaryAdditives() {
        return Map.copyOf(currentTemporaryAdditives);
    }

    public Map<String, ModifierEntry> getCurrentPermanentMultipliers() {
        return Map.copyOf(currentPermanentMultipliers);
    }

    public Map<String, ModifierEntry> getCurrentTemporaryMultipliers() {
        return Map.copyOf(currentTemporaryMultipliers);
    }

    /**
     * Registers a modifier and distributes it into the appropriate buckets for both the default
     * and current stages. Buckets only retain a single entry per key; calling this method again
     * with the same key replaces the previous modifier everywhere.
     */
    public void addModifier(ModifierEntry modifier) {
        Objects.requireNonNull(modifier, "modifier");
        String key = modifier.key().toLowerCase();
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
        String normalized = key.toLowerCase();
        modifiers.remove(normalized);
        removeFromBuckets(normalized);
    }

    public void purgeTemporaryModifiers() {
        modifiers.entrySet().removeIf(entry -> {
            if (entry.getValue().isTemporary()) {
                removeFromBuckets(entry.getKey());
                return true;
            }
            return false;
        });
        defaultTemporaryAdditives.clear();
        defaultTemporaryMultipliers.clear();
        currentTemporaryAdditives.clear();
        currentTemporaryMultipliers.clear();
    }

    public String getCapOverrideKey() {
        return capOverrideKey;
    }

    public void setCapOverrideKey(String capOverrideKey) {
        this.capOverrideKey = capOverrideKey;
    }

    public void synchronizeCurrentBaseWithDefault(double defaultFinal) {
        synchronizeCurrentBaseWithDefault(defaultFinal, definition.capConfig());
    }

    /**
     * Adjusts the current baseline by the same delta applied to the default baseline during
     * computation, then clamps using the provided cap config and override key. This keeps current
     * values that were previously persisted in line with recalculated defaults.
     */
    public void synchronizeCurrentBaseWithDefault(double defaultFinal, CapConfig capConfig) {
        double delta = defaultFinal - defaultFinalBaseline;
        if (Math.abs(delta) < 1.0E-9) {
            return;
        }
        this.currentBaseValue = capConfig.clamp(currentBaseValue + delta, capOverrideKey);
        this.defaultFinalBaseline = defaultFinal;
    }

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
}
