package me.baddcamden.attributeutils;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.compute.AttributeComputationEngine;
import me.baddcamden.attributeutils.model.AttributeDefinition;
import me.baddcamden.attributeutils.model.CapConfig;
import me.baddcamden.attributeutils.model.ModifierEntry;
import me.baddcamden.attributeutils.model.ModifierOperation;
import me.baddcamden.attributeutils.model.MultiplierApplicability;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttributeFacadeRemovalRefreshTest {

    private static final double EPSILON = 1.0E-6;

    @Test
    void playerModifierRemovalReappliesComputedValue() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("player-removal-refresh"));

        AttributeFacade facade = new AttributeFacade(plugin, new AttributeComputationEngine());
        AttributeDefinition definition = registerDynamic(facade, "attack_damage", 1.0, 100.0);
        UUID playerId = UUID.randomUUID();

        RecordingRefreshListener listener = new RecordingRefreshListener(facade);
        facade.setAttributeRefreshListener(listener);

        ModifierEntry playerModifier = new ModifierEntry("attributeutils.test.buff",
                ModifierOperation.ADD,
                5.0,
                false,
                true,
                true,
                false,
                java.util.Set.of());
        facade.setPlayerModifier(playerId, definition.id(), playerModifier);

        listener.refreshAttributeForPlayer(playerId, definition.id());
        assertEquals(6.0, listener.getPlayerValue(playerId), EPSILON);

        facade.removePlayerModifier(playerId, definition.id(), playerModifier.key());

        assertNotEquals(6.0, listener.getPlayerValue(playerId), EPSILON);
        assertEquals(1.0, listener.getPlayerValue(playerId), EPSILON);
    }

    @Test
    void globalModifierRemovalRefreshesAll() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("global-removal-refresh"));

        AttributeFacade facade = new AttributeFacade(plugin, new AttributeComputationEngine());
        AttributeDefinition definition = registerDynamic(facade, "movement_speed", 0.2, 10.0);

        RecordingRefreshListener listener = new RecordingRefreshListener(facade);
        facade.setAttributeRefreshListener(listener);

        ModifierEntry globalModifier = new ModifierEntry("attributeutils.test.global",
                ModifierOperation.ADD,
                0.3,
                false,
                true,
                true,
                false,
                java.util.Set.of());
        facade.setGlobalModifier(definition.id(), globalModifier);

        listener.refreshAttributeForAll(definition.id());
        assertEquals(0.5, listener.getGlobalValue(definition.id()), EPSILON);

        facade.removeGlobalModifier(definition.id(), globalModifier.key());

        assertNotEquals(0.5, listener.getGlobalValue(definition.id()), EPSILON);
        assertEquals(0.2, listener.getGlobalValue(definition.id()), EPSILON);
    }

    private AttributeDefinition registerDynamic(AttributeFacade facade, String id, double defaultBase, double capMax) {
        AttributeDefinition definition = new AttributeDefinition(
                id,
                id,
                true,
                defaultBase,
                defaultBase,
                new CapConfig(0, capMax, Map.of()),
                MultiplierApplicability.allowAllMultipliers()
        );
        facade.registerDefinition(definition);
        return definition;
    }

    private static class RecordingRefreshListener implements AttributeFacade.AttributeRefreshListener {

        private final AttributeFacade facade;
        private final Map<UUID, Double> playerValues = new HashMap<>();
        private final Map<String, Double> globalValues = new HashMap<>();

        RecordingRefreshListener(AttributeFacade facade) {
            this.facade = facade;
        }

        @Override
        public void refreshAttributeForPlayer(UUID playerId, String attributeId) {
            playerValues.put(playerId, facade.compute(attributeId, playerId, (Player) null).currentFinal());
        }

        @Override
        public void refreshAttributeForAll(String attributeId) {
            globalValues.put(attributeId, facade.compute(attributeId, (Player) null).currentFinal());
        }

        double getPlayerValue(UUID playerId) {
            return playerValues.getOrDefault(playerId, 0.0);
        }

        double getGlobalValue(String attributeId) {
            return globalValues.getOrDefault(attributeId, 0.0);
        }
    }
}
