package dev.thirst.hud;

import dev.thirst.config.BarStyle;
import dev.thirst.config.ThirstConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThirstHudFormatterTest {

    private static final double EPS = 1.0e-6;

    private static ThirstConfig.Exhaustion noExhaustion() {
        return new ThirstConfig.Exhaustion(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /** max, critical% */
    private static ThirstConfig.Thirst thirst(int max, int criticalPercent) {
        return new ThirstConfig.Thirst(
                max, 120, criticalPercent, true, 0.75, true, 1.0, 4, 1.0, 4.0, noExhaustion());
    }

    private static ThirstConfig.Hud hud(BarStyle style, int segments, String label) {
        ThirstConfig.ExternalSegment none = new ThirstConfig.ExternalSegment(false, "", true, " | ");
        return new ThirstConfig.Hud(30, style, segments, "<aqua>", "<red>", 3000L, label, none);
    }

    // ── critical detection ──────────────────────────────────────────────────

    @Test
    void criticalMatchesConfiguredThreshold() {
        ThirstConfig.Thirst t = thirst(20, 30); // critical level = 6
        assertTrue(ThirstHudFormatter.isCritical(6, t));
        assertTrue(ThirstHudFormatter.isCritical(0, t));
        assertFalse(ThirstHudFormatter.isCritical(7, t));
        assertFalse(ThirstHudFormatter.isCritical(20, t));
    }

    // ── colour selection ────────────────────────────────────────────────────

    @Test
    void rendersNormalColourAboveThreshold() {
        String out = ThirstHudFormatter.render(20, thirst(20, 30), hud(BarStyle.PERCENT, 10, "Thirst"));
        assertEquals("<aqua>100% Thirst", out);
    }

    @Test
    void rendersCriticalColourAtThreshold() {
        String out = ThirstHudFormatter.render(6, thirst(20, 30), hud(BarStyle.PERCENT, 10, "Thirst"));
        assertEquals("<red>30% Thirst", out);
    }

    // ── percent style ───────────────────────────────────────────────────────

    @Test
    void percentRoundsToNearest() {
        // 13/20 = 65%
        assertEquals("<aqua>65% Thirst",
                ThirstHudFormatter.render(13, thirst(20, 30), hud(BarStyle.PERCENT, 10, "Thirst")));
    }

    @Test
    void percentBlankLabelOmitsSuffix() {
        assertEquals("<aqua>50%",
                ThirstHudFormatter.render(10, thirst(20, 30), hud(BarStyle.PERCENT, 10, "")));
    }

    // ── bar style ───────────────────────────────────────────────────────────

    @Test
    void barFullAndEmpty() {
        assertEquals("<aqua>[||||||||||]",
                ThirstHudFormatter.render(20, thirst(20, 30), hud(BarStyle.BAR, 10, "Thirst")));
        assertEquals("<red>[----------]",
                ThirstHudFormatter.render(0, thirst(20, 30), hud(BarStyle.BAR, 10, "Thirst")));
    }

    @Test
    void barHalf() {
        assertEquals("<aqua>[|||||-----]",
                ThirstHudFormatter.render(10, thirst(20, 30), hud(BarStyle.BAR, 10, "Thirst")));
    }

    @Test
    void barSegmentCountConfigurable() {
        String out = ThirstHudFormatter.render(15, thirst(20, 30), hud(BarStyle.BAR, 20, "Thirst"));
        assertEquals("<aqua>[|||||||||||||||-----]", out);
        assertEquals(22, out.substring("<aqua>".length()).length()); // 20 segments + 2 brackets
    }

    // ── boss-bar fraction ───────────────────────────────────────────────────

    @Test
    void fractionClampedToUnitInterval() {
        assertEquals(0.5f, ThirstHudFormatter.fractionOf(10, 20), EPS);
        assertEquals(1.0f, ThirstHudFormatter.fractionOf(25, 20), EPS);
        assertEquals(0.0f, ThirstHudFormatter.fractionOf(-3, 20), EPS);
        assertEquals(0.0f, ThirstHudFormatter.fractionOf(5, 0), EPS);
    }
}
