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
    void restoresMeterToCap() {
        EntityAttributeHandler.ResourceMeter meter = EntityAttributeHandler.ResourceMeter.fromDisplay(5, 10, 12.0);

        meter.applyDelta(-4.0);
        meter.restoreToMax();

        assertEquals(12.0, meter.getCurrent(), 0.0001);
        assertEquals(10, meter.asDisplay(10));
    }
}
