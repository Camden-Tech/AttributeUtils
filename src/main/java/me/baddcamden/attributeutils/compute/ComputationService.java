package me.baddcamden.attributeutils.compute;

import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class ComputationService {

    public ComputationSnapshot compute(AttributeInstance attributeInstance) {
        double rawDefault = attributeInstance.getDefaultValue();
        Collection<AttributeModifier> allModifiers = attributeInstance.getModifiers();
        Collection<AttributeModifier> persistentModifiers = filterModifiers(allModifiers, false);

        double defaultWithoutTemps = apply(rawDefault, persistentModifiers);
        double defaultFinal = apply(rawDefault, allModifiers);

        double rawCurrent = buildCurrentBaseline(attributeInstance, rawDefault, defaultFinal);
        double currentWithoutTemps = apply(rawCurrent, persistentModifiers);
        double currentFinal = apply(rawCurrent, allModifiers);

        return new ComputationSnapshot(rawDefault, defaultWithoutTemps, defaultFinal, rawCurrent, currentWithoutTemps, currentFinal);
    }

    public double getRawDefault(AttributeInstance attributeInstance) {
        return compute(attributeInstance).getRawDefault();
    }

    public double getDefaultWithoutTemporary(AttributeInstance attributeInstance) {
        return compute(attributeInstance).getDefaultWithoutTemporary();
    }

    public double getDefaultFinal(AttributeInstance attributeInstance) {
        return compute(attributeInstance).getDefaultFinal();
    }

    public double getRawCurrent(AttributeInstance attributeInstance) {
        return compute(attributeInstance).getRawCurrent();
    }

    public double getCurrentWithoutTemporary(AttributeInstance attributeInstance) {
        return compute(attributeInstance).getCurrentWithoutTemporary();
    }

    public double getCurrentFinal(AttributeInstance attributeInstance) {
        return compute(attributeInstance).getCurrentFinal();
    }

    private double apply(double baseValue, Collection<AttributeModifier> modifiers) {
        double value = baseValue;
        List<AttributeModifier> additive = new ArrayList<>();
        List<AttributeModifier> multiplyBase = new ArrayList<>();
        List<AttributeModifier> multiplyTotal = new ArrayList<>();

        for (AttributeModifier modifier : modifiers) {
            switch (modifier.getOperation()) {
                case ADD_NUMBER -> additive.add(modifier);
                case MULTIPLY_SCALAR_1 -> multiplyBase.add(modifier);
                case ADD_SCALAR -> multiplyTotal.add(modifier);
                default -> {
                }
            }
        }

        for (AttributeModifier modifier : additive) {
            value += modifier.getAmount();
        }

        double additiveBase = value;

        for (AttributeModifier modifier : multiplyBase) {
            value += additiveBase * modifier.getAmount();
        }

        for (AttributeModifier modifier : multiplyTotal) {
            value *= 1 + modifier.getAmount();
        }

        return value;
    }

    private double buildCurrentBaseline(AttributeInstance attributeInstance, double rawDefault, double defaultFinal) {
        double baseValue = attributeInstance.getBaseValue();
        if (isStaticAttribute(attributeInstance)) {
            double defaultDelta = defaultFinal - rawDefault;
            baseValue += defaultDelta;
        }
        return baseValue;
    }

    private boolean isStaticAttribute(AttributeInstance attributeInstance) {
        return "minecraft".equalsIgnoreCase(attributeInstance.getAttribute().getKey().getNamespace());
    }

    private Collection<AttributeModifier> filterModifiers(Collection<AttributeModifier> modifiers, boolean includeTemporary) {
        if (includeTemporary) {
            return modifiers;
        }
        List<AttributeModifier> filtered = new ArrayList<>();
        for (AttributeModifier modifier : modifiers) {
            if (!isTemporary(modifier)) {
                filtered.add(modifier);
            }
        }
        return filtered;
    }

    private boolean isTemporary(AttributeModifier modifier) {
        Boolean persistent = getPersistentFlag(modifier);
        if (persistent != null) {
            return !persistent;
        }
        return modifier.getName().toLowerCase(Locale.ROOT).contains("temp");
    }

    private Boolean getPersistentFlag(AttributeModifier modifier) {
        try {
            Method method = AttributeModifier.class.getMethod("isPersistent");
            Object result = method.invoke(modifier);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    public static final class ComputationSnapshot {
        private final double rawDefault;
        private final double defaultWithoutTemporary;
        private final double defaultFinal;
        private final double rawCurrent;
        private final double currentWithoutTemporary;
        private final double currentFinal;

        public ComputationSnapshot(
                double rawDefault,
                double defaultWithoutTemporary,
                double defaultFinal,
                double rawCurrent,
                double currentWithoutTemporary,
                double currentFinal
        ) {
            this.rawDefault = rawDefault;
            this.defaultWithoutTemporary = defaultWithoutTemporary;
            this.defaultFinal = defaultFinal;
            this.rawCurrent = rawCurrent;
            this.currentWithoutTemporary = currentWithoutTemporary;
            this.currentFinal = currentFinal;
        }

        public double getRawDefault() {
            return rawDefault;
        }

        public double getDefaultWithoutTemporary() {
            return defaultWithoutTemporary;
        }

        public double getDefaultFinal() {
            return defaultFinal;
        }

        public double getRawCurrent() {
            return rawCurrent;
        }

        public double getCurrentWithoutTemporary() {
            return currentWithoutTemporary;
        }

        public double getCurrentFinal() {
            return currentFinal;
        }
    }
}
