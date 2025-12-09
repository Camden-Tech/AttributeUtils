package me.baddcamden.attributeutils.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class AttributeModifierSet {

    private final Map<String, Double> permanent = new ConcurrentHashMap<>();
    private final Map<String, Double> temporary = new ConcurrentHashMap<>();

    void setModifier(String sourceKey, double value, boolean temporaryModifier) {
        if (temporaryModifier) {
            temporary.put(sourceKey, value);
        } else {
            permanent.put(sourceKey, value);
        }
    }

    void removeModifier(String sourceKey) {
        permanent.remove(sourceKey);
        temporary.remove(sourceKey);
    }

    double combinedValue() {
        return permanent.values().stream().mapToDouble(Double::doubleValue).sum()
                + temporary.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    Map<String, Double> getPermanentModifiers() {
        return permanent;
    }

    Map<String, Double> getTemporaryModifiers() {
        return temporary;
    }
}
