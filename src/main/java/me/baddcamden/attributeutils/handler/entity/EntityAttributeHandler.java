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

/**
 * Integrates entity interactions with the attribute computation pipeline. Responsibilities include applying computed
 * caps (current stage) to live player entities and translating command-provided baseline/cap values into persistent
 * data for spawned entities so that subsequent computations start from the correct stage values.
 */
public class EntityAttributeHandler {

    private final AttributeFacade attributeFacade;
    private final Plugin plugin;
    private final Map<String, org.bukkit.attribute.Attribute> vanillaAttributeTargets;
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
        AttributeValueStages hunger = attributeFacade.compute("max_hunger", player);
        applyHungerCap(player, hunger);

        applyOxygenCaps(player);
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

            String summaryLine = attributeDefinition.id() + "=" + clampedValue;
            if (capOverride.isPresent()) {
                double cap = capOverride.get();
                summaryLine += " (cap " + cap + ")";
            }
            summary.add(summaryLine);
        }

        return new SpawnedEntityResult(entity, String.join(", ", summary));
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
            AttributeValueStages hunger = attributeFacade.compute("max_hunger", player);
            applyHungerCap(player, hunger);
            return;
        }
        if (target == null && isOxygen(attributeId)) {
            applyOxygenCaps(player);
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
        org.bukkit.attribute.AttributeModifier existing = instance.getModifier(modifierId);
        if (existing != null) {
            instance.removeModifier(existing);
        }

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

    private void applyHungerCap(Player player, AttributeValueStages hunger) {
        int cappedHunger = (int) Math.round(hunger.currentFinal());
        player.setFoodLevel(cappedHunger);
    }

    private void applyOxygenCaps(Player player) {
        AttributeValueStages oxygen = attributeFacade.compute("max_oxygen", player);
        AttributeValueStages oxygenBonus = attributeFacade.compute("oxygen_bonus", player);
        int maxAir = (int) Math.round(oxygen.currentFinal() + oxygenBonus.currentFinal());

        int priorMaxAir = player.getMaximumAir();
        player.setMaximumAir(maxAir);

        int currentAir = player.getRemainingAir();
        int clampedAir = Math.max(currentAir, 0);
        double priorMax = Math.max(priorMaxAir, 0);
        double ratio = priorMax > 0 ? Math.min(clampedAir / priorMax, 1.0) : 0;
        int scaledAir = (int) Math.round(maxAir * ratio);
        player.setRemainingAir(scaledAir);
    }
}
