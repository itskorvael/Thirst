package dev.thirst.drinking;

import dev.thirst.water.WaterQuality;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CauldronWaterTrackerTest {

    @Test
    void mixingIsDirtyIfEitherPartIsDirty() {
        assertEquals(WaterQuality.FRESH, CauldronWaterTracker.mix(WaterQuality.FRESH, WaterQuality.FRESH));
        assertEquals(WaterQuality.DIRTY, CauldronWaterTracker.mix(WaterQuality.FRESH, WaterQuality.DIRTY));
        assertEquals(WaterQuality.DIRTY, CauldronWaterTracker.mix(WaterQuality.DIRTY, WaterQuality.FRESH));
        assertEquals(WaterQuality.DIRTY, CauldronWaterTracker.mix(WaterQuality.DIRTY, WaterQuality.DIRTY));
    }
}
