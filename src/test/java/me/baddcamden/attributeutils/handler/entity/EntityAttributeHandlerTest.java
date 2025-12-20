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
        double displayScale = oxygenCapInBubbles / 10.0;
        EntityAttributeHandler.ResourceMeter meter = EntityAttributeHandler.ResourceMeter.fromDisplay(10, 10, oxygenCapInBubbles);

        double vanillaDelta = -airPerBubble; // Lose one vanilla bubble (30 air ticks)
        double bubbleDelta = vanillaDelta / airPerBubble;
        meter.applyDisplayDelta(bubbleDelta, displayScale);

        assertEquals(18.0, meter.getCurrent(), 0.0001);
        assertEquals(9, meter.asDisplay(10));
        double vanillaMaxAirTicks = 10.0 * airPerBubble;
        double expectedVanillaAir = (meter.getCurrent() / meter.getMax()) * vanillaMaxAirTicks;
        assertEquals(270.0, expectedVanillaAir, 0.0001);
        assertEquals(600.0, oxygenCapInBubbles * airPerBubble, 0.0001);
    }
}
