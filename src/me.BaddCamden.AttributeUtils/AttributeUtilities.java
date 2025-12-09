package me.BaddCamden.AttributeUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Core data structures for describing plugin-provided attributes and the
 * per-holder state used to apply them.
 */
public final class AttributeUtilities {

    private static final Pattern NAMESPACED_KEY_PATTERN = Pattern.compile("^[^\\.\\s]+\\.[^\\.\\s]+$");

    private AttributeUtilities() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Validates a namespaced key of the format "[plugin].[key]".
     *
     * @param namespacedKey the namespaced key to validate
     * @throws IllegalArgumentException if the key is null or does not match the required format
     */
    public static void validateNamespacedKey(String namespacedKey) {
        if (namespacedKey == null || !NAMESPACED_KEY_PATTERN.matcher(namespacedKey).matches()) {
            throw new IllegalArgumentException("Attribute keys must be in the form [plugin].[key]");
        }
    }

    /**
     * Definition of a plugin-supplied attribute.
     */
    public static final class AttributeDefinition {
        private final String pluginNamespace;
        private final String attributeKey;
        private final boolean dynamic;

        public AttributeDefinition(String namespacedKey, boolean dynamic) {
            AttributeUtilities.validateNamespacedKey(namespacedKey);
            String[] parts = namespacedKey.split("\\.", 2);
            this.pluginNamespace = parts[0];
            this.attributeKey = parts[1];
            this.dynamic = dynamic;
        }

        public String getPluginNamespace() {
            return pluginNamespace;
        }

        public String getAttributeKey() {
            return attributeKey;
        }

        public boolean isDynamic() {
            return dynamic;
        }

        public String getNamespacedKey() {
            return pluginNamespace + '.' + attributeKey;
        }
    }

    /**
     * Per-holder runtime state for an attribute.
     */
    public static final class AttributeHolderState {
        private final AttributeDefinition definition;
        private final Map<String, Double> permanentAdditives = new HashMap<>();
        private final Map<String, Double> temporaryAdditives = new HashMap<>();
        private final Map<String, Double> defaultMultipliers = new HashMap<>();
        private final Map<String, Double> currentMultipliers = new HashMap<>();
        private boolean applyDefaultMultipliers = true;
        private boolean applyTemporaryMultipliers = true;
        private Double vanillaSnapshot;

        public AttributeHolderState(AttributeDefinition definition) {
            this.definition = Objects.requireNonNull(definition, "Attribute definition is required");
        }

        public AttributeDefinition getDefinition() {
            return definition;
        }

        public Map<String, Double> getPermanentAdditives() {
            return Collections.unmodifiableMap(permanentAdditives);
        }

        public Map<String, Double> getTemporaryAdditives() {
            return Collections.unmodifiableMap(temporaryAdditives);
        }

        public Map<String, Double> getDefaultMultipliers() {
            return Collections.unmodifiableMap(defaultMultipliers);
        }

        public Map<String, Double> getCurrentMultipliers() {
            return Collections.unmodifiableMap(currentMultipliers);
        }

        public boolean shouldApplyDefaultMultipliers() {
            return applyDefaultMultipliers;
        }

        public void setApplyDefaultMultipliers(boolean applyDefaultMultipliers) {
            this.applyDefaultMultipliers = applyDefaultMultipliers;
        }

        public boolean shouldApplyTemporaryMultipliers() {
            return applyTemporaryMultipliers;
        }

        public void setApplyTemporaryMultipliers(boolean applyTemporaryMultipliers) {
            this.applyTemporaryMultipliers = applyTemporaryMultipliers;
        }

        public void setPermanentAdditive(String source, double value) {
            permanentAdditives.put(source, value);
        }

        public void setTemporaryAdditive(String source, double value) {
            temporaryAdditives.put(source, value);
        }

        public void setDefaultMultiplier(String source, double value) {
            defaultMultipliers.put(source, value);
        }

        public void setCurrentMultiplier(String source, double value) {
            currentMultipliers.put(source, value);
        }

        public void clearTemporaryAdditives() {
            temporaryAdditives.clear();
        }

        public void resetCurrentMultipliers() {
            currentMultipliers.clear();
            currentMultipliers.putAll(defaultMultipliers);
        }

        /**
         * Snapshot the underlying vanilla value when working with dynamic attributes.
         * The supplier is only invoked once and subsequent calls will reuse the cached value.
         *
         * @param vanillaSupplier supplier for the underlying vanilla value
         * @return the cached vanilla value
         */
        public double snapshotVanillaValue(Supplier<Double> vanillaSupplier) {
            Objects.requireNonNull(vanillaSupplier, "Vanilla supplier is required");
            if (!definition.isDynamic()) {
                return vanillaSupplier.get();
            }
            if (vanillaSnapshot == null) {
                vanillaSnapshot = vanillaSupplier.get();
            }
            return vanillaSnapshot;
        }

        public Double getVanillaSnapshot() {
            return vanillaSnapshot;
        }
    }
}

