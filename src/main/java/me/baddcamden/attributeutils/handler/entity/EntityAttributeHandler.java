package me.baddcamden.attributeutils.handler.entity;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.command.CommandParsingUtils;
import me.baddcamden.attributeutils.model.AttributeDefinition;
import me.baddcamden.attributeutils.model.AttributeValueStages;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Integrates entity interactions with the attribute computation pipeline. Responsibilities include applying computed
 * caps (current stage) to live player entities and translating command-provided baseline/cap values into persistent
 * data for spawned entities so that subsequent computations start from the correct stage values.
 */
public class EntityAttributeHandler {

    private final AttributeFacade attributeFacade;
    private final Plugin plugin;
    private final Map<String, org.bukkit.attribute.Attribute> vanillaAttributeTargets;
    private final Map<UUID, ResourceMeter> hungerMeters = new HashMap<>();
    private final Map<UUID, ResourceMeter> oxygenMeters = new HashMap<>();
    private static final int HUNGER_BARS = 20;
    private static final int OXYGEN_BUBBLES = 10;
    private static final int AIR_PER_BUBBLE = 30;
    private Method transientModifierMethod;
    private boolean transientMethodResolved;

    public EntityAttributeHandler(AttributeFacade attributeFacade,
                                  Plugin plugin,
                                  Map<String, org.bukkit.attribute.Attribute> vanillaAttributeTargets) {
        this.attributeFacade = attributeFacade;
        this.plugin = plugin;
        this.vanillaAttributeTargets = vanillaAttributeTargets;
        resolveTransientModifierMethod();
    }

    public void applyPlayerCaps(Player player) {
        if (player == null) {
            return;
        }
        syncHunger(player);
        syncOxygen(player);
    }

    public SpawnedEntityResult spawnAttributedEntity(Location location,
                                                    EntityType entityType,
                                                    List<CommandParsingUtils.AttributeDefinition> definitions) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("invalid-location");
        }

        Entity entity = location.getWorld().spawnEntity(location, entityType);

        PersistentDataContainer container = entity.getPersistentDataContainer();
        List<String> summary = new ArrayList<>();
        for (CommandParsingUtils.AttributeDefinition definition : definitions) {
            AttributeDefinition attributeDefinition = attributeFacade.getDefinition(definition.getKey().key())
                    .orElseThrow(() -> new IllegalArgumentException(definition.getKey().asString()));

            double clampedValue = attributeDefinition.capConfig().clamp(definition.getValue());
            Optional<Double> capOverride = definition.getCapOverride()
                    .map(value -> attributeDefinition.capConfig().clamp(value));

            container.set(valueKey(attributeDefinition.id()), PersistentDataType.DOUBLE, clampedValue);
            capOverride.ifPresent(cap -> container.set(capKey(attributeDefinition.id()), PersistentDataType.DOUBLE, cap));

            applyVanillaAttribute(entity, attributeDefinition.id(), clampedValue);

            String summaryLine = attributeDefinition.id() + "=" + clampedValue;
            if (capOverride.isPresent()) {
                double cap = capOverride.get();
                summaryLine += " (cap " + cap + ")";
            }
            summary.add(summaryLine);
        }

        return new SpawnedEntityResult(entity, String.join(", ", summary));
    }

    public void applyPersistentAttributes(Entity entity) {
        if (entity == null) {
            return;
        }

        PersistentDataContainer container = entity.getPersistentDataContainer();
        container.getKeys().forEach(key -> {
            String keyName = key.getKey();
            if (!key.getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT))) {
                return;
            }
            if (!keyName.startsWith("attr_")) {
                return;
            }

            boolean cap = keyName.endsWith("_cap");
            String attributeId = keyName.substring("attr_".length(), cap ? keyName.length() - 4 : keyName.length());
            Optional<AttributeDefinition> definition = attributeFacade.getDefinition(attributeId)
                    .or(() -> attributeFacade.getDefinition(attributeId.replace('_', '.')));
            if (definition.isEmpty()) {
                return;
            }

            Double value = container.get(key, PersistentDataType.DOUBLE);
            if (value == null) {
                return;
            }

            AttributeDefinition attr = definition.get();
            double clampedValue = attr.capConfig().clamp(value);
            if (cap) {
                applyVanillaAttribute(entity, attr.id(), clampedValue);
                return;
            }

            applyVanillaAttribute(entity, attr.id(), clampedValue);
            if (entity instanceof Player player) {
                attributeFacade.getOrCreatePlayerInstance(player.getUniqueId(), attr.id())
                        .setCurrentBaseValue(clampedValue);
            }
        });
    }

    private void applyVanillaAttribute(Entity entity, String attributeId, double value) {
        if (entity == null || attributeId == null || attributeId.isBlank()) {
            return;
        }

        Attribute target = vanillaAttributeTargets.get(attributeId.toLowerCase(Locale.ROOT));
        if (target == null) {
            target = resolveAttribute(attributeId);
        }
        if (target == null) {
            return;
        }

        if (!(entity instanceof org.bukkit.attribute.Attributable attributable)) {
            return;
        }

        org.bukkit.attribute.AttributeInstance instance = attributable.getAttribute(target);
        if (instance == null) {
            return;
        }

        instance.setBaseValue(value);
        if (entity instanceof LivingEntity living && (target == Attribute.MAX_HEALTH)) {
            double safeHealth = Math.max(0.0001d, value);
            living.setHealth(safeHealth);
        }
    }

    private NamespacedKey valueKey(String attributeId) {
        return new NamespacedKey(plugin, "attr_" + sanitize(attributeId));
    }

    private NamespacedKey capKey(String attributeId) {
        return new NamespacedKey(plugin, "attr_" + sanitize(attributeId) + "_cap");
    }

    private String sanitize(String attributeId) {
        return attributeId.toLowerCase(Locale.ROOT).replace('.', '_');
    }

    public record SpawnedEntityResult(Entity entity, String summary) {
    }

    public void applyVanillaAttribute(Player player, String attributeId) {
        if (player == null || attributeId == null || attributeId.isBlank()) {
            return;
        }
        org.bukkit.attribute.Attribute target = vanillaAttributeTargets.get(attributeId.toLowerCase(Locale.ROOT));
        if (target == null && isHunger(attributeId)) {
            syncHunger(player);
            return;
        }
        if (target == null && isOxygen(attributeId)) {
            syncOxygen(player);
            return;
        }
        if (target == null) {
            target = resolveAttribute(attributeId);
        }
        if (target == null) {
            return;
        }

        org.bukkit.attribute.AttributeInstance instance = player.getAttribute(target);
        if (instance == null) {
            return;
        }

        java.util.UUID modifierId = java.util.UUID.nameUUIDFromBytes(("attributeutils:" + attributeId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        instance.getModifiers().stream()
                .filter(modifier -> modifier.getUniqueId().equals(modifierId))
                .findFirst()
                .ifPresent(instance::removeModifier);

        double baseline = instance.getValue();
        double computed = attributeFacade.compute(attributeId, player).currentFinal();
        double delta = computed - baseline;
        if (Math.abs(delta) < 0.0000001) {
            return;
        }

        org.bukkit.attribute.AttributeModifier modifier = new org.bukkit.attribute.AttributeModifier(
                modifierId,
                "attributeutils:" + attributeId,
                delta,
                org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER
        );
        addModifier(instance, modifier);
    }

    private Attribute resolveAttribute(String attributeId) {
        String normalized = attributeId.toUpperCase(Locale.ROOT);
        try {
            return Attribute.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            // continue
        }

        try {
            return Attribute.valueOf("GENERIC_" + normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isHunger(String attributeId) {
        return "max_hunger".equalsIgnoreCase(attributeId);
    }

    private boolean isOxygen(String attributeId) {
        return "max_oxygen".equalsIgnoreCase(attributeId) || "oxygen_bonus".equalsIgnoreCase(attributeId);
    }

    public int handleFoodLevelChange(Player player, int requestedFoodLevel) {
        ResourceMeter meter = resolveHungerMeter(player, computeHungerCap(player));
        double delta = requestedFoodLevel - player.getFoodLevel();
        meter.applyDelta(delta);
        return meter.asDisplay(HUNGER_BARS);
    }

    public int handleAirChange(Player player, int requestedAirAmount) {
        ResourceMeter meter = resolveOxygenMeter(player, computeOxygenCap(player));
        double vanillaDelta = requestedAirAmount - player.getRemainingAir();
        double pluginDelta = vanillaDelta / AIR_PER_BUBBLE;
        meter.applyDelta(pluginDelta);
        player.setMaximumAir(AIR_PER_BUBBLE * OXYGEN_BUBBLES);
        return meter.asDisplay(OXYGEN_BUBBLES) * AIR_PER_BUBBLE;
    }

    public void clearPlayerData(UUID playerId) {
        hungerMeters.remove(playerId);
        oxygenMeters.remove(playerId);
    }

    private void resolveTransientModifierMethod() {
        try {
            transientModifierMethod = org.bukkit.attribute.AttributeInstance.class
                    .getMethod("addTransientModifier", org.bukkit.attribute.AttributeModifier.class);
        } catch (ReflectiveOperationException ignored) {
            transientModifierMethod = null;
        } finally {
            transientMethodResolved = true;
        }
    }

    private void addModifier(org.bukkit.attribute.AttributeInstance instance,
                             org.bukkit.attribute.AttributeModifier modifier) {
        if (!transientMethodResolved) {
            resolveTransientModifierMethod();
        }

        if (transientModifierMethod != null) {
            try {
                transientModifierMethod.invoke(instance, modifier);
                return;
            } catch (ReflectiveOperationException ignored) {
                transientModifierMethod = null;
            }
        }

        instance.addModifier(modifier);
    }

    private void syncHunger(Player player) {
        ResourceMeter meter = resolveHungerMeter(player, computeHungerCap(player));
        player.setFoodLevel(meter.asDisplay(HUNGER_BARS));
    }

    private void syncOxygen(Player player) {
        ResourceMeter meter = resolveOxygenMeter(player, computeOxygenCap(player));
        player.setMaximumAir(AIR_PER_BUBBLE * OXYGEN_BUBBLES);
        player.setRemainingAir(meter.asDisplay(OXYGEN_BUBBLES) * AIR_PER_BUBBLE);
    }

    private double computeHungerCap(Player player) {
        return Math.max(attributeFacade.compute("max_hunger", player).currentFinal(), 0);
    }

    private double computeOxygenCap(Player player) {
        AttributeValueStages oxygen = attributeFacade.compute("max_oxygen", player);
        AttributeValueStages oxygenBonus = attributeFacade.compute("oxygen_bonus", player);
        return Math.max(oxygen.currentFinal() + oxygenBonus.currentFinal(), 0);
    }

    private ResourceMeter resolveHungerMeter(Player player, double cap) {
        return resolveMeter(player.getUniqueId(), hungerMeters, cap,
                () -> ResourceMeter.fromDisplay(player.getFoodLevel(), HUNGER_BARS, cap));
    }

    private ResourceMeter resolveOxygenMeter(Player player, double cap) {
        int cappedAir = Math.max(0, Math.min(player.getRemainingAir(), AIR_PER_BUBBLE * OXYGEN_BUBBLES));
        int bubbleDisplay = (int) Math.ceil(cappedAir / (double) AIR_PER_BUBBLE);
        return resolveMeter(player.getUniqueId(), oxygenMeters, cap,
                () -> ResourceMeter.fromDisplay(bubbleDisplay, OXYGEN_BUBBLES, cap));
    }

    private ResourceMeter resolveMeter(UUID playerId,
                                       Map<UUID, ResourceMeter> meters,
                                       double cap,
                                       Supplier<ResourceMeter> creator) {
        ResourceMeter meter = meters.computeIfAbsent(playerId, ignored -> creator.get());
        meter.updateMax(cap);
        return meter;
    }

    private static final class ResourceMeter {
        private double current;
        private double max;

        private ResourceMeter(double current, double max) {
            this.max = Math.max(max, 0);
            this.current = clamp(current, this.max);
        }

        static ResourceMeter fromDisplay(int displayValue, int displayMax, double max) {
            double clampedDisplay = clamp(displayValue, displayMax);
            double fraction = displayMax > 0 ? clampedDisplay / (double) displayMax : 0;
            return new ResourceMeter(fraction * Math.max(max, 0), Math.max(max, 0));
        }

        void updateMax(double newMax) {
            double cappedMax = Math.max(newMax, 0);
            double fraction = max > 0 ? current / max : 0;
            max = cappedMax;
            current = clamp(fraction * max, max);
        }

        void applyDelta(double delta) {
            current = clamp(current + delta, max);
        }

        int asDisplay(int displayMax) {
            if (max <= 0) {
                return 0;
            }
            double fraction = current / max;
            return (int) Math.ceil(Math.min(fraction, 1.0) * displayMax);
        }

        private static double clamp(double value, double max) {
            return Math.max(0, Math.min(value, max));
        }
    }
}
