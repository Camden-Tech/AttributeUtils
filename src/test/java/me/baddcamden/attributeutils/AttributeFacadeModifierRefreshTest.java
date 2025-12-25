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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttributeFacadeModifierRefreshTest {

    private static final double EPSILON = 1.0E-6;

    @Test
    void staticGlobalModifierTriggersRefresh() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("static-global-refresh"));

        AttributeFacade facade = new AttributeFacade(plugin, new AttributeComputationEngine());
        AttributeDefinition definition = registerDefinition(facade, false, 4.0, 50.0);

        RecordingRefreshListener listener = new RecordingRefreshListener(facade);
        facade.setAttributeRefreshListener(listener);

        ModifierEntry globalAdditive = new ModifierEntry("attributeutils.test.global", ModifierOperation.ADD, 3.0,
                false, true, true, false, java.util.Set.of());

        facade.setGlobalModifier(definition.id(), globalAdditive);

        assertEquals(7.0, listener.getGlobalValue(definition.id()), EPSILON);
    }

    @Test
    void playerModifierReplacementRefreshesBeforeAndAfterMultipliersApply() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("player-reapply-refresh"));

        AttributeFacade facade = new AttributeFacade(plugin, new AttributeComputationEngine());
        AttributeDefinition definition = registerDefinition(facade, true, 2.0, 25.0);

        RecordingRefreshListener listener = new RecordingRefreshListener(facade);
        facade.setAttributeRefreshListener(listener);

        UUID playerId = UUID.randomUUID();

        ModifierEntry firstMultiplier = new ModifierEntry("attributeutils.test.multiplier", ModifierOperation.MULTIPLY, 2.0,
                false, true, true, false, java.util.Set.of());
        ModifierEntry secondMultiplier = new ModifierEntry("attributeutils.test.multiplier", ModifierOperation.MULTIPLY, 3.0,
                false, true, true, false, java.util.Set.of());

        facade.setPlayerModifier(playerId, definition.id(), firstMultiplier);

        facade.setPlayerModifier(playerId, definition.id(), secondMultiplier);

        List<Double> expectedPlayerValues = List.of(4.0, 2.0, 6.0);
        assertIterableEquals(expectedPlayerValues, listener.getPlayerValueHistory(playerId));
    }

    private AttributeDefinition registerDefinition(AttributeFacade facade, boolean dynamic, double defaultBase, double capMax) {
        AttributeDefinition definition = new AttributeDefinition(
                "attack_damage",
                "attack_damage",
                dynamic,
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
        private final List<Double> globalValues = new ArrayList<>();
        private final List<Double> playerValues = new ArrayList<>();

        RecordingRefreshListener(AttributeFacade facade) {
            this.facade = facade;
        }

        @Override
        public void refreshAttributeForPlayer(UUID playerId, String attributeId) {
            playerValues.add(facade.compute(attributeId, playerId, (Player) null).currentFinal());
        }

        @Override
        public void refreshAttributeForAll(String attributeId) {
            globalValues.add(facade.compute(attributeId, (Player) null).currentFinal());
        }

        double getGlobalValue(String attributeId) {
            return globalValues.isEmpty() ? 0.0 : globalValues.get(globalValues.size() - 1);
        }

        List<Double> getPlayerValueHistory(UUID playerId) {
            return List.copyOf(playerValues);
        }
    }
}
