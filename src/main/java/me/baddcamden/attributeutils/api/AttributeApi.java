package me.baddcamden.attributeutils.api;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Minimal API exposed to other plugins for registering attributes and querying computed values.
 * <p>
 * The API keeps two parallel concepts of a baseline:
 * <ul>
 *     <li><strong>Default baseline</strong>: the configured {@link AttributeDefinition#baseValue()} used when no
 *     vanilla supplier is registered.</li>
 *     <li><strong>Vanilla baseline</strong>: a supplier-driven value pulled for a specific player to reflect their
 *     current in-game state. When absent, the default baseline is used.</li>
 * </ul>
 * Modifiers are tracked separately for global scope and per player using {@link AttributeModifierSet} buckets. When
 * {@link #queryAttribute(String, org.bukkit.entity.Player)} is invoked the calculation proceeds as follows:
 * <ol>
 *     <li>Resolve the vanilla baseline (player-aware) and default baseline (definition value).</li>
 *     <li>Sum permanent and temporary entries from the global and player modifier buckets.</li>
 *     <li>Add the modifier totals to the default baseline to obtain a raw value.</li>
 *     <li>Apply the definition's cap; no other implicit clamps are used.</li>
 * </ol>
 */
public class AttributeApi {

    private final ConcurrentMap<String, AttributeDefinition> definitions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AttributeModifierSet> globalModifiers = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<String, AttributeModifierSet>> playerModifiers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, VanillaAttributeSupplier> vanillaSuppliers = new ConcurrentHashMap<>();

    public void registerAttributeDefinition(AttributeDefinition definition) {
        String normalizedKey = definition.normalizedKey();
        definitions.put(normalizedKey, definition);
    }

    public void registerVanillaBaseline(String attributeKey, VanillaAttributeSupplier supplier) {
        vanillaSuppliers.put(normalize(attributeKey), supplier);
    }

    public Collection<AttributeDefinition> getRegisteredDefinitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public Optional<AttributeDefinition> getDefinition(String attributeKey) {
        return Optional.ofNullable(definitions.get(normalize(attributeKey)));
    }

    public Optional<AttributeComputation> queryAttribute(String attributeKey) {
        return queryAttribute(attributeKey, null);
    }

    public Optional<AttributeComputation> queryAttribute(String attributeKey, Player player) {
        String normalizedKey = normalize(attributeKey);
        AttributeDefinition definition = definitions.get(normalizedKey);
        if (definition == null) {
            return Optional.empty();
        }

        double vanillaBaseline = player == null ? definition.baseValue() : getVanillaBaseline(normalizedKey, player);
        double baseValue = definition.baseValue();
        double globalTotal = globalModifiers.getOrDefault(normalizedKey, new AttributeModifierSet()).combinedValue();
        double playerTotal = player == null ? 0 : getPlayerModifierSet(player.getUniqueId(), normalizedKey).combinedValue();

        double rawTotal = baseValue + globalTotal + playerTotal;
        double cappedTotal = Math.min(rawTotal, definition.maxValue());

        return Optional.of(new AttributeComputation(
                normalizedKey,
                vanillaBaseline,
                baseValue,
                globalTotal,
                playerTotal,
                cappedTotal,
                definition.maxValue()
        ));
    }

    public double getVanillaBaseline(String attributeKey, Player player) {
        String normalizedKey = normalize(attributeKey);
        VanillaAttributeSupplier supplier = vanillaSuppliers.get(normalizedKey);
        if (supplier == null) {
            return definitions.getOrDefault(normalizedKey, new AttributeDefinition(normalizedKey, 0, Double.MAX_VALUE)).baseValue();
        }
        return supplier.getVanillaValue(player);
    }

    public void setGlobalModifier(String attributeKey, String sourceKey, double value, boolean temporary) {
        String normalizedKey = normalize(attributeKey);
        globalModifiers.computeIfAbsent(normalizedKey, ignored -> new AttributeModifierSet())
                .setModifier(sourceKey, value, temporary);
    }

    public void removeGlobalModifier(String attributeKey, String sourceKey) {
        AttributeModifierSet modifierSet = globalModifiers.get(normalize(attributeKey));
        if (modifierSet != null) {
            modifierSet.removeModifier(sourceKey);
        }
    }

    public void setPlayerModifier(UUID playerId, String attributeKey, String sourceKey, double value, boolean temporary) {
        String normalizedKey = normalize(attributeKey);
        getOrCreatePlayerStore(playerId)
                .computeIfAbsent(normalizedKey, ignored -> new AttributeModifierSet())
                .setModifier(sourceKey, value, temporary);
    }

    public void removePlayerModifier(UUID playerId, String attributeKey, String sourceKey) {
        Map<String, AttributeModifierSet> modifiers = playerModifiers.get(playerId);
        if (modifiers == null) {
            return;
        }

        AttributeModifierSet modifierSet = modifiers.get(normalize(attributeKey));
        if (modifierSet != null) {
            modifierSet.removeModifier(sourceKey);
        }
    }

    public void clearPlayer(UUID playerId) {
        playerModifiers.remove(playerId);
    }

    public Map<String, AttributeModifierSet> getGlobalModifierStore() {
        return Collections.unmodifiableMap(globalModifiers);
    }

    public Map<UUID, ConcurrentMap<String, AttributeModifierSet>> getPlayerModifierStore() {
        return Collections.unmodifiableMap(playerModifiers);
    }

    private AttributeModifierSet getPlayerModifierSet(UUID playerId, String attributeKey) {
        Map<String, AttributeModifierSet> modifiers = playerModifiers.get(playerId);
        if (modifiers == null) {
            return new AttributeModifierSet();
        }
        return modifiers.getOrDefault(attributeKey, new AttributeModifierSet());
    }

    private Map<String, AttributeModifierSet> getOrCreatePlayerStore(UUID playerId) {
        return playerModifiers.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>());
    }

    private String normalize(String attributeKey) {
        return attributeKey.toLowerCase(Locale.ROOT);
    }
}
