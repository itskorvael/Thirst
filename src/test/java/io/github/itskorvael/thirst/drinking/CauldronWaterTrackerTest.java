package io.github.itskorvael.thirst.drinking;

import io.github.itskorvael.thirst.water.WaterQuality;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CauldronWaterTrackerTest {

    @Test
    void mixingDirtyBeatsFreshBeatsNeutral() {
        assertEquals(WaterQuality.DIRTY, CauldronWaterTracker.mix(WaterQuality.FRESH, WaterQuality.DIRTY));
        assertEquals(WaterQuality.DIRTY, CauldronWaterTracker.mix(WaterQuality.DIRTY, WaterQuality.NEUTRAL));
        assertEquals(WaterQuality.DIRTY, CauldronWaterTracker.mix(WaterQuality.DIRTY, WaterQuality.DIRTY));

        assertEquals(WaterQuality.FRESH, CauldronWaterTracker.mix(WaterQuality.FRESH, WaterQuality.FRESH));
        assertEquals(WaterQuality.FRESH, CauldronWaterTracker.mix(WaterQuality.NEUTRAL, WaterQuality.FRESH));

        assertEquals(WaterQuality.NEUTRAL, CauldronWaterTracker.mix(WaterQuality.NEUTRAL, WaterQuality.NEUTRAL));
    }
}
