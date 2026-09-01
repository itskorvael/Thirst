package dev.thirst.water;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WaterQualityTest {

    @Test
    void parseAcceptsEachQualityCaseInsensitively() {
        assertEquals(WaterQuality.FRESH, WaterQuality.parse("fresh"));
        assertEquals(WaterQuality.NEUTRAL, WaterQuality.parse("NEUTRAL"));
        assertEquals(WaterQuality.DIRTY, WaterQuality.parse(" Dirty "));
    }

    @Test
    void parseAcceptsCleanAsAliasForFresh() {
        assertEquals(WaterQuality.FRESH, WaterQuality.parse("clean"));
    }

    @Test
    void parseRejectsUnknownAndNull() {
        assertNull(WaterQuality.parse("murky"));
        assertNull(WaterQuality.parse(""));
        assertNull(WaterQuality.parse(null));
    }
}
