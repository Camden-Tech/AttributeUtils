package me.baddcamden.attributeutils.compute;

import me.baddcamden.attributeutils.model.AttributeDefinition;
import me.baddcamden.attributeutils.model.AttributeInstance;
import me.baddcamden.attributeutils.model.AttributeValueStages;
import me.baddcamden.attributeutils.model.CapConfig;
import me.baddcamden.attributeutils.model.ModifierEntry;
import me.baddcamden.attributeutils.model.ModifierOperation;
import me.baddcamden.attributeutils.model.MultiplierApplicability;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttributeComputationEngineTest {

    private static final double EPSILON = 1.0E-6;

    @Test
    void appliesTemporaryAdditivesBeforePermanentMultipliers() {
        AttributeDefinition definition = new AttributeDefinition(
                "test",
                "Test",
                false,
                10.0,
                10.0,
                new CapConfig(-1_000_000, 1_000_000, Map.of()),
                MultiplierApplicability.allowAllMultipliers()
        );

        AttributeInstance globalInstance = definition.newInstance();
        globalInstance.addModifier(new ModifierEntry("perm-mult", ModifierOperation.MULTIPLY, 2.0, false, true, false, false, Set.of()));
        globalInstance.addModifier(new ModifierEntry("temp-add", ModifierOperation.ADD, 5.0, true, true, false, false, Set.of()));

        AttributeComputationEngine engine = new AttributeComputationEngine();
        AttributeValueStages stages = engine.compute(definition, globalInstance, null, null, null);

        assertEquals(10.0, stages.rawDefault(), EPSILON);
        assertEquals(20.0, stages.defaultPermanent(), EPSILON);
        assertEquals(30.0, stages.defaultFinal(), EPSILON);
    }

    @Test
    void mirrorsOutlineOrderingAcrossDefaultAndCurrentStages() {
        AttributeDefinition definition = new AttributeDefinition(
                "max_health",
                "Max Health",
                false,
                20.0,
                20.0,
                new CapConfig(-1_000_000, 1_000_000, Map.of()),
                MultiplierApplicability.allowAllMultipliers()
        );

        AttributeInstance globalInstance = definition.newInstance();
        globalInstance.addModifier(new ModifierEntry("event.global", ModifierOperation.ADD, 4.0, false, true, false, false, Set.of()));
        globalInstance.addModifier(new ModifierEntry("festival.buff", ModifierOperation.MULTIPLY, 1.1, true, true, false, false, Set.of()));

        AttributeInstance playerInstance = definition.newInstance();
        playerInstance.addModifier(new ModifierEntry("lifesteal.pvp", ModifierOperation.ADD, 2.0, false, false, true, false, Set.of()));
        playerInstance.addModifier(new ModifierEntry("adrenaline", ModifierOperation.ADD, 3.0, true, false, true, false, Set.of()));
        playerInstance.addModifier(new ModifierEntry("setbonus", ModifierOperation.MULTIPLY, 1.05, false, false, true, false, Set.of()));
        playerInstance.addModifier(new ModifierEntry("potion", ModifierOperation.MULTIPLY, 1.2, true, false, true, false, Set.of()));

        AttributeComputationEngine engine = new AttributeComputationEngine();
        AttributeValueStages stages = engine.compute(definition, globalInstance, playerInstance, null, null);

        assertEquals(20.0, stages.rawDefault(), EPSILON);
        assertEquals(24.0, stages.defaultPermanent(), EPSILON);
        assertEquals(26.4, stages.defaultFinal(), EPSILON);
        assertEquals(26.4, stages.rawCurrent(), EPSILON);
        assertEquals(29.82, stages.currentPermanent(), EPSILON);
        assertEquals(39.564, stages.currentFinal(), EPSILON);
    }

    @Test
    void capOverrideClampsComputedValuesWithoutMutatingCurrentBase() {
        CapConfig capConfig = new CapConfig(0.0, 100.0, new HashMap<>());
        AttributeDefinition definition = new AttributeDefinition(
                "attack_speed",
                "Attack Speed",
                true,
                10.0,
                10.0,
                capConfig,
                MultiplierApplicability.allowAllMultipliers()
        );

        AttributeInstance playerInstance = definition.newInstance();
        playerInstance.setCapOverrideKey("player-123");
        playerInstance.setCurrentBaseValue(12.0);
        capConfig.overrideMaxValues().put("player-123", 5.0);

        AttributeComputationEngine engine = new AttributeComputationEngine();
        AttributeValueStages stages = engine.compute(definition, null, playerInstance, null, null);

        assertEquals(12.0, playerInstance.getCurrentBaseValue(), EPSILON);
        assertEquals(5.0, stages.defaultFinal(), EPSILON);
        assertEquals(5.0, stages.currentFinal(), EPSILON);
    }
}
