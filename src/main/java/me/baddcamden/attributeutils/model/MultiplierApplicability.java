package me.baddcamden.attributeutils.model;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public record MultiplierApplicability(boolean applyAll, Set<String> allowedKeys) {

    public MultiplierApplicability {
        allowedKeys = allowedKeys == null ? Collections.emptySet() : allowedKeys.stream()
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static MultiplierApplicability applyAll() {
        return new MultiplierApplicability(true, Collections.emptySet());
    }

    public static MultiplierApplicability optIn(Set<String> allowedKeys) {
        return new MultiplierApplicability(false, allowedKeys);
    }

    public boolean canApply(String modifierKey) {
        return applyAll || allowedKeys.contains(modifierKey.toLowerCase());
    }
}
