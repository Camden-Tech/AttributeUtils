package me.baddcamden.attributeutils;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.compute.AttributeComputationEngine;
import me.baddcamden.attributeutils.model.AttributeDefinition;
import me.baddcamden.attributeutils.model.AttributeValueStages;
import me.baddcamden.attributeutils.model.CapConfig;
import me.baddcamden.attributeutils.model.MultiplierApplicability;
import me.baddcamden.attributeutils.model.ModifierEntry;
import me.baddcamden.attributeutils.model.ModifierOperation;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttributeUtilitiesVanillaBaselineTest {

    private static final double EPSILON = 1.0E-6;

    @Test
    void vanillaBaselinesIgnoreAttributeUtilsModifiers() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("vanilla-baseline-test"));

        AttributeFacade facade = new AttributeFacade(plugin, new AttributeComputationEngine());
        registerDynamic(facade, "attack_speed", 4.0, 40.0);
        registerDynamic(facade, "interaction_range", 3.0, 64.0);
        registerDynamic(facade, "armor", 0.0, 40.0);
        registerDynamic(facade, "armor_toughness", 0.0, 20.0);
        registerDynamic(facade, "knockback_resistance", 0.0, 1.0);
        registerDynamic(facade, "damage_reduction", 0.0, 1.0);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        AttributeInstance attackSpeed = mockedAttributeInstance(
                4.0,
                new AttributeModifier(UUID.randomUUID(), "attributeutils:attack_speed", 5.0, AttributeModifier.Operation.ADD_NUMBER),
                new AttributeModifier(UUID.randomUUID(), "vanilla:weapon", 1.0, AttributeModifier.Operation.ADD_NUMBER),
                new AttributeModifier(UUID.randomUUID(), "vanilla:smithing", 0.25, AttributeModifier.Operation.ADD_SCALAR),
                new AttributeModifier(UUID.randomUUID(), "vanilla:haste", 0.1, AttributeModifier.Operation.MULTIPLY_SCALAR_1)
        );
        when(player.getAttribute(Attribute.ATTACK_SPEED)).thenReturn(attackSpeed);
        facade.registerVanillaBaseline("attack_speed", p -> VanillaAttributeResolver.resolveVanillaValue(attackSpeed, 4.0));

        AttributeInstance interactionRange = mockedAttributeInstance(
                3.0,
                new AttributeModifier(UUID.randomUUID(), "attributeutils:interaction_range", 2.0, AttributeModifier.Operation.ADD_NUMBER),
                new AttributeModifier(UUID.randomUUID(), "vanilla:skill", 0.5, AttributeModifier.Operation.ADD_NUMBER)
        );
        when(player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE)).thenReturn(interactionRange);
        facade.registerVanillaBaseline("interaction_range", p -> VanillaAttributeResolver.resolveVanillaValue(interactionRange, 3.0));

        AttributeInstance armor = mockedAttributeInstance(
                0.0,
                new AttributeModifier(UUID.randomUUID(), "attributeutils:armor", 8.0, AttributeModifier.Operation.ADD_NUMBER),
                new AttributeModifier(UUID.randomUUID(), "vanilla:chestplate", 6.0, AttributeModifier.Operation.ADD_NUMBER)
        );
        when(player.getAttribute(Attribute.ARMOR)).thenReturn(armor);
        facade.registerVanillaBaseline("armor", p -> VanillaAttributeResolver.resolveVanillaValue(armor, 0.0));

        AttributeInstance armorToughness = mockedAttributeInstance(
                0.0,
                new AttributeModifier(UUID.randomUUID(), "attributeutils:armor_toughness", 2.0, AttributeModifier.Operation.ADD_NUMBER),
                new AttributeModifier(UUID.randomUUID(), "vanilla:chestplate", 1.0, AttributeModifier.Operation.ADD_NUMBER)
        );
        when(player.getAttribute(Attribute.ARMOR_TOUGHNESS)).thenReturn(armorToughness);
        facade.registerVanillaBaseline("armor_toughness", p -> VanillaAttributeResolver.resolveVanillaValue(armorToughness, 0.0));

        AttributeInstance knockbackResistance = mockedAttributeInstance(
                0.0,
                new AttributeModifier(UUID.randomUUID(), "attributeutils:knockback_resistance", 0.4, AttributeModifier.Operation.ADD_NUMBER),
                new AttributeModifier(UUID.randomUUID(), "vanilla:netherite", 0.2, AttributeModifier.Operation.ADD_NUMBER)
        );
        when(player.getAttribute(Attribute.KNOCKBACK_RESISTANCE)).thenReturn(knockbackResistance);
        facade.registerVanillaBaseline("knockback_resistance", p -> VanillaAttributeResolver.resolveVanillaValue(knockbackResistance, 0.0));

        facade.registerVanillaBaseline("damage_reduction", p -> 0.0);

        assertBaseline(facade.compute("attack_speed", player), attackSpeed, 4.0);
        assertBaseline(facade.compute("interaction_range", player), interactionRange, 3.0);
        assertBaseline(facade.compute("armor", player), armor, 0.0);
        assertBaseline(facade.compute("armor_toughness", player), armorToughness, 0.0);
        assertBaseline(facade.compute("knockback_resistance", player), knockbackResistance, 0.0);
        assertEquals(0.0, facade.compute("damage_reduction", player).rawCurrent(), EPSILON);
    }

    @Test
    void additiveModifiersDoNotCompoundAcrossRecomputes() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("vanilla-baseline-test"));
        AttributeFacade facade = new AttributeFacade(plugin, new AttributeComputationEngine());
        registerDynamic(facade, "attack_speed", 4.0, 40.0);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        AttributeModifier pluginModifier = new AttributeModifier(UUID.randomUUID(), "attributeutils:attack_speed", 15.0, AttributeModifier.Operation.ADD_NUMBER);
        AttributeModifier vanillaModifier = new AttributeModifier(UUID.randomUUID(), "vanilla:training", 2.0, AttributeModifier.Operation.ADD_NUMBER);
        AttributeInstance instance = mockedAttributeInstance(4.0, pluginModifier, vanillaModifier);
        when(player.getAttribute(Attribute.ATTACK_SPEED)).thenReturn(instance);
        facade.registerVanillaBaseline("attack_speed", p -> VanillaAttributeResolver.resolveVanillaValue(instance, 4.0));

        ModifierEntry additive = new ModifierEntry(
                "test.additive",
                ModifierOperation.ADD,
                15.0,
                false,
                true,
                true,
                false,
                Set.of()
        );
        facade.setPlayerModifier(playerId, "attack_speed", additive);

        AttributeValueStages first = facade.compute("attack_speed", player);
        AttributeValueStages second = facade.compute("attack_speed", player);

        double expectedVanilla = VanillaAttributeResolver.resolveVanillaValue(instance, 4.0);
        double expectedFinal = expectedVanilla + additive.amount();
        assertEquals(expectedFinal, first.currentFinal(), EPSILON);
        assertEquals(expectedFinal, second.currentFinal(), EPSILON);
    }

    @Test
    void multiplierModifiersDoNotCompoundAcrossRecomputes() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("vanilla-baseline-test"));
        AttributeFacade facade = new AttributeFacade(plugin, new AttributeComputationEngine());
        registerDynamic(facade, "attack_damage", 1.0, 40.0);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        AttributeModifier pluginModifier = new AttributeModifier(UUID.randomUUID(), "attributeutils:attack_damage", 0.5, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        AttributeModifier vanillaModifier = new AttributeModifier(UUID.randomUUID(), "vanilla:smithing", 0.2, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        AttributeInstance instance = mockedAttributeInstance(10.0, pluginModifier, vanillaModifier);
        when(player.getAttribute(Attribute.ATTACK_DAMAGE)).thenReturn(instance);
        facade.registerVanillaBaseline("attack_damage", p -> VanillaAttributeResolver.resolveVanillaValue(instance, 10.0));

        ModifierEntry multiplier = new ModifierEntry(
                "test.multiplier",
                ModifierOperation.MULTIPLY,
                1.5,
                false,
                true,
                true,
                false,
                Set.of()
        );
        facade.setPlayerModifier(playerId, "attack_damage", multiplier);

        AttributeValueStages first = facade.compute("attack_damage", player);
        AttributeValueStages second = facade.compute("attack_damage", player);

        double expectedVanilla = VanillaAttributeResolver.resolveVanillaValue(instance, 10.0);
        double expectedFinal = expectedVanilla * multiplier.amount();
        assertEquals(expectedFinal, first.currentFinal(), EPSILON);
        assertEquals(expectedFinal, second.currentFinal(), EPSILON);
    }

    private void assertBaseline(AttributeValueStages stages, AttributeInstance instance, double fallback) {
        double expected = VanillaAttributeResolver.resolveVanillaValue(instance, fallback);
        assertEquals(expected, stages.rawCurrent(), EPSILON);
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

    private AttributeInstance mockedAttributeInstance(double baseValue, AttributeModifier... modifiers) {
        AttributeInstance instance = mock(AttributeInstance.class);
        when(instance.getBaseValue()).thenReturn(baseValue);
        when(instance.getModifiers()).thenReturn(java.util.Set.of(modifiers));
        return instance;
    }
}
