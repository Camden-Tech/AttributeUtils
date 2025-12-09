package me.BaddCamden.AttributeUtils;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Captures vanilla attribute defaults so we can compute deltas when persisting player state.
 */
public class AttributeDefaultsCache {
    private final Map<Attribute, Double> cachedDefaults = new EnumMap<>(Attribute.class);

    public void capture(Player player) {
        for (Attribute attribute : Attribute.values()) {
            if (cachedDefaults.containsKey(attribute)) {
                continue;
            }
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                cachedDefaults.put(attribute, instance.getDefaultValue());
            }
        }
    }

    public double getVanillaDefault(Attribute attribute, Player player) {
        capture(player);
        return Optional.ofNullable(cachedDefaults.get(attribute)).orElseGet(() -> {
            AttributeInstance instance = player.getAttribute(attribute);
            return instance != null ? instance.getDefaultValue() : 0.0;
        });
    }
}
