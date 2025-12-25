package me.baddcamden.attributeutils;

import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class VanillaAttributeResolverTest {

    @Test
    void scrubsLegacyModifiersBeforeResolvingVanillaValues() {
        AttributeModifier legacy = new AttributeModifier(
                UUID.randomUUID(),
                "attributeutils.damage",
                5.0d,
                AttributeModifier.Operation.ADD_NUMBER
        );
        AttributeModifier namespaced = new AttributeModifier(
                UUID.randomUUID(),
                VanillaAttributeResolver.ATTRIBUTEUTILS_PREFIX + "damage",
                3.0d,
                AttributeModifier.Operation.ADD_NUMBER
        );
        AttributeModifier vanilla = new AttributeModifier(
                UUID.randomUUID(),
                "sword-bonus",
                2.0d,
                AttributeModifier.Operation.ADD_NUMBER
        );

        AttributeInstance instance = Mockito.mock(AttributeInstance.class);
        Collection<AttributeModifier> modifiers = new ArrayList<>(List.of(legacy, namespaced, vanilla));

        Mockito.when(instance.getModifiers()).thenAnswer(invocation -> modifiers);
        Mockito.when(instance.getBaseValue()).thenReturn(10.0d);
        Mockito.doAnswer(invocation -> modifiers.remove(invocation.getArgument(0))).when(instance).removeModifier(any(AttributeModifier.class));

        VanillaAttributeResolver.scrubLegacyPluginModifiers(instance);

        assertFalse(modifiers.contains(legacy), "Legacy AttributeUtils modifier should be purged before vanilla reconstruction");
        assertTrue(modifiers.contains(namespaced), "Properly namespaced modifier should remain for AttributeUtils filtering");

        double vanillaValue = VanillaAttributeResolver.resolveVanillaValue(instance, 0.0d);
        assertEquals(12.0d, vanillaValue, 0.0001d);
    }
}

