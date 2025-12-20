package me.baddcamden.attributeutils.handler.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityAttributeHandlerTest {

    @Test
    void appliesLossPerFoodBarAgainstScaledCap() {
        EntityAttributeHandler.ResourceMeter meter = EntityAttributeHandler.ResourceMeter.fromDisplay(20, 20, 40);

        meter.applyDisplayDelta(-1, 40.0 / 20.0);

        assertEquals(38.0, meter.getCurrent(), 0.0001);
        assertEquals(19, meter.asDisplay(20));
    }

    @Test
    void appliesGainPerFoodBarAgainstScaledCap() {
        EntityAttributeHandler.ResourceMeter meter = EntityAttributeHandler.ResourceMeter.fromDisplay(10, 20, 40);

        meter.applyDisplayDelta(1, 40.0 / 20.0);

        assertEquals(22.0, meter.getCurrent(), 0.0001);
        assertEquals(11, meter.asDisplay(20));
    }

    @Test
    void scalesOxygenMeterAndAirWhenCapExceedsVanillaBubbles() {
        double oxygenCapInBubbles = 20.0; // Represents 600 air ticks
        double airPerBubble = 30.0;
        double vanillaMaxBubbles = 10.0;
        EntityAttributeHandler.ResourceMeter meter = EntityAttributeHandler.ResourceMeter.fromDisplay(10, 10, oxygenCapInBubbles);

        double ticksPerBubble = (vanillaMaxBubbles * airPerBubble) / oxygenCapInBubbles;
        double tickDelta = -1.0; // Lose one vanilla air tick while submerged
        meter.applyDelta(tickDelta / ticksPerBubble);

        assertEquals(19.9333, meter.getCurrent(), 0.0001);
        double expectedVanillaAir = (meter.getCurrent() / meter.getMax()) * vanillaMaxBubbles * airPerBubble;
        assertEquals(299.0, expectedVanillaAir, 0.0001);
        assertEquals(600.0, oxygenCapInBubbles * airPerBubble, 0.0001);
    }

    @Test
    void restoresOxygenToCapWhenSurfacing() {
        EntityAttributeHandler.ResourceMeter meter = EntityAttributeHandler.ResourceMeter.fromDisplay(5, 10, 12.0);

        meter.applyDelta(-4.0);
        meter.restoreToMax();

        assertEquals(12.0, meter.getCurrent(), 0.0001);
        assertEquals(10, meter.asDisplay(10));
    }
}
