package me.baddcamden.attributeutils.model;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record MultiplierApplicability(boolean applyAll, Set<String> allowedKeys, Set<String> ignoredKeys) {

    public MultiplierApplicability {
        allowedKeys = normalize(allowedKeys);
        ignoredKeys = normalize(ignoredKeys);
    }

    public static MultiplierApplicability applyAll() {
        return new MultiplierApplicability(true, Collections.emptySet(), Collections.emptySet());
    }

    public static MultiplierApplicability optIn(Set<String> allowedKeys) {
        return new MultiplierApplicability(false, allowedKeys, Collections.emptySet());
    }

    public static MultiplierApplicability optOut(Set<String> ignoredKeys) {
        return new MultiplierApplicability(true, Collections.emptySet(), ignoredKeys);
    }

    public boolean canApply(String modifierKey) {
        String normalizedKey = modifierKey.toLowerCase();
        if (ignoredKeys.contains(normalizedKey)) {
            return false;
        }
        return applyAll || allowedKeys.contains(normalizedKey);
    }

    private Set<String> normalize(Set<String> keys) {
        if (keys == null) {
            return Collections.emptySet();
        }

        return keys.stream()
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
    }
}
