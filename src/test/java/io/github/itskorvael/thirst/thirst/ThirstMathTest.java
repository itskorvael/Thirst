package io.github.itskorvael.thirst.thirst;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThirstMathTest {

    private static final double EPS = 1.0e-9;

    // ── drain ───────────────────────────────────────────────────────────────

    @Test
    void drainRemovesNoPointBelowThreshold() {
        ThirstMath.DrainResult result = ThirstMath.drain(3.9, 4.0, 20);
        assertEquals(0, result.pointsLost());
        assertEquals(3.9, result.remainingExhaustion(), EPS);
    }

    @Test
    void drainRemovesOnePointAtThresholdAndKeepsRemainder() {
        ThirstMath.DrainResult result = ThirstMath.drain(4.5, 4.0, 20);
        assertEquals(1, result.pointsLost());
        assertEquals(0.5, result.remainingExhaustion(), EPS);
    }

    @Test
    void drainRemovesMultiplePoints() {
        ThirstMath.DrainResult result = ThirstMath.drain(9.0, 4.0, 20);
        assertEquals(2, result.pointsLost());
        assertEquals(1.0, result.remainingExhaustion(), EPS);
    }

    @Test
    void drainRespectsMaxPointsCap() {
        ThirstMath.DrainResult result = ThirstMath.drain(1_000.0, 0.01, 20);
        assertEquals(20, result.pointsLost());
    }

    @Test
    void drainDisabledForNonPositiveThreshold() {
        ThirstMath.DrainResult result = ThirstMath.drain(50.0, 0.0, 20);
        assertEquals(0, result.pointsLost());
        assertEquals(50.0, result.remainingExhaustion(), EPS);
    }

    @Test
    void drainTreatsNegativeExhaustionAsZero() {
        ThirstMath.DrainResult result = ThirstMath.drain(-5.0, 4.0, 20);
        assertEquals(0, result.pointsLost());
        assertEquals(0.0, result.remainingExhaustion(), EPS);
    }

    // ── passive drain rate ──────────────────────────────────────────────────

    @Test
    void passiveRateReachesThresholdOverConfiguredInterval() {
        double perSecond = ThirstMath.passiveExhaustionPerSecond(4.0, 120);
        assertEquals(4.0 / 120.0, perSecond, EPS);
        assertEquals(4.0, perSecond * 120, EPS);
    }

    @Test
    void passiveRateZeroWhenIntervalNonPositive() {
        assertEquals(0.0, ThirstMath.passiveExhaustionPerSecond(4.0, 0), EPS);
    }

    // ── clamp ───────────────────────────────────────────────────────────────

    @Test
    void clampLevelBounds() {
        assertEquals(0, ThirstMath.clampLevel(-3, 20));
        assertEquals(20, ThirstMath.clampLevel(25, 20));
        assertEquals(7, ThirstMath.clampLevel(7, 20));
    }

    // ── starvation damage ───────────────────────────────────────────────────

    @Test
    void starvationDamageHoldsAtFloor() {
        assertEquals(0.0, ThirstMath.starvationDamage(1.0, 1.0, 1.0), EPS);
        assertEquals(0.0, ThirstMath.starvationDamage(0.5, 1.0, 1.0), EPS);
    }

    @Test
    void starvationDamageClampsToFloor() {
        assertEquals(0.5, ThirstMath.starvationDamage(1.5, 1.0, 1.0), EPS);
    }

    @Test
    void starvationDamageFullWhenAboveFloor() {
        assertEquals(1.0, ThirstMath.starvationDamage(10.0, 1.0, 1.0), EPS);
    }

    @Test
    void starvationDamageAllowsDeathWhenFloorZero() {
        assertEquals(1.0, ThirstMath.starvationDamage(0.5, 1.0, 0.0), EPS);
    }

    // ── critical level ──────────────────────────────────────────────────────

    @Test
    void criticalLevelIsPercentOfMax() {
        assertEquals(2.0, ThirstMath.criticalLevel(20, 10), EPS);
        assertEquals(0.0, ThirstMath.criticalLevel(20, 0), EPS);
    }
}
