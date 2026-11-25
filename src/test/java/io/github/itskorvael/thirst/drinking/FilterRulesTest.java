package io.github.itskorvael.thirst.drinking;

import io.github.itskorvael.thirst.water.WaterQuality;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterRulesTest {

    @Test
    void dirtyBecomesNeutralWhenMaxIsNeutral() {
        assertEquals(WaterQuality.NEUTRAL, FilterRules.improve(WaterQuality.DIRTY, WaterQuality.NEUTRAL));
    }

    @Test
    void neutralStaysNeutralWhenMaxIsNeutral() {
        assertNull(FilterRules.improve(WaterQuality.NEUTRAL, WaterQuality.NEUTRAL));
    }

    @Test
    void freshCanNeverBeImproved() {
        assertNull(FilterRules.improve(WaterQuality.FRESH, WaterQuality.FRESH));
        assertNull(FilterRules.improve(WaterQuality.FRESH, WaterQuality.NEUTRAL));
    }

    @Test
    void neutralBecomesFreshOnlyWhenMaxIsFresh() {
        assertEquals(WaterQuality.FRESH, FilterRules.improve(WaterQuality.NEUTRAL, WaterQuality.FRESH));
    }

    @Test
    void dirtyReachesNeutralThenFreshOneStepAtATimeWhenMaxIsFresh() {
        WaterQuality step1 = FilterRules.improve(WaterQuality.DIRTY, WaterQuality.FRESH);
        assertEquals(WaterQuality.NEUTRAL, step1);
        assertEquals(WaterQuality.FRESH, FilterRules.improve(step1, WaterQuality.FRESH));
    }

    @Test
    void nothingImprovesWhenMaxIsDirty() {
        assertNull(FilterRules.improve(WaterQuality.DIRTY, WaterQuality.DIRTY));
        assertNull(FilterRules.improve(WaterQuality.NEUTRAL, WaterQuality.DIRTY));
    }

    @Test
    void costIsOnePerBottleAndTheConfiguredAmountPerBucket() {
        assertEquals(1, FilterRules.cost(false, 3));
        assertEquals(3, FilterRules.cost(true, 3));
        assertEquals(5, FilterRules.cost(true, 5));
    }

    @Test
    void aRechargeAddsUsesUpToTheLimit() {
        assertEquals(12, FilterRules.recharged(6, 6, 12));
        assertEquals(12, FilterRules.recharged(9, 6, 12));
        assertEquals(8, FilterRules.recharged(2, 6, 12));
    }

    @Test
    void aFilterAlreadyAboveTheLimitIsNeverLowered() {
        assertEquals(20, FilterRules.recharged(20, 6, 12));
    }

    @Test
    void aFilterAtTheLimitCannotBeRecharged() {
        assertTrue(FilterRules.canRecharge(11, 12));
        assertFalse(FilterRules.canRecharge(12, 12));
        assertFalse(FilterRules.canRecharge(30, 12));
    }
}
