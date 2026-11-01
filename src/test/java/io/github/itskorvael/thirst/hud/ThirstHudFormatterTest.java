package io.github.itskorvael.thirst.hud;

import io.github.itskorvael.thirst.config.BarStyle;
import io.github.itskorvael.thirst.config.ThirstConfig;
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
        return new ThirstConfig.Hud(30, style, segments, "<aqua>", "<red>", "<green>", true,
                3000L, label, none);
    }

    /** render with no Salmonella. */
    private static String render(int level, ThirstConfig.Thirst thirst, ThirstConfig.Hud hud) {
        return ThirstHudFormatter.render(level, 0, thirst, hud);
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
        assertEquals("<aqua>100% Thirst", render(20, thirst(20, 30), hud(BarStyle.PERCENT, 10, "Thirst")));
    }

    @Test
    void rendersCriticalColourAtThreshold() {
        assertEquals("<red>30% Thirst", render(6, thirst(20, 30), hud(BarStyle.PERCENT, 10, "Thirst")));
    }

    @Test
    void infectedColourAndIndicatorOverrideEverything() {
        // critical thirst + infected -> still infected colour, with ☣ suffix
        assertEquals("<green>30% Thirst ☣3",
                ThirstHudFormatter.render(6, 3, thirst(20, 30), hud(BarStyle.PERCENT, 10, "Thirst")));
        assertEquals("<green>[|||||-----] ☣1",
                ThirstHudFormatter.render(10, 1, thirst(20, 30), hud(BarStyle.BAR, 10, "Thirst")));
    }

    // ── percent style ───────────────────────────────────────────────────────

    @Test
    void percentRoundsToNearest() {
        assertEquals("<aqua>65% Thirst", render(13, thirst(20, 30), hud(BarStyle.PERCENT, 10, "Thirst")));
    }

    @Test
    void percentBlankLabelOmitsSuffix() {
        assertEquals("<aqua>50%", render(10, thirst(20, 30), hud(BarStyle.PERCENT, 10, "")));
    }

    // ── bar style ───────────────────────────────────────────────────────────

    @Test
    void barFullAndEmpty() {
        assertEquals("<aqua>[||||||||||]", render(20, thirst(20, 30), hud(BarStyle.BAR, 10, "Thirst")));
        assertEquals("<red>[----------]", render(0, thirst(20, 30), hud(BarStyle.BAR, 10, "Thirst")));
    }

    @Test
    void barHalf() {
        assertEquals("<aqua>[|||||-----]", render(10, thirst(20, 30), hud(BarStyle.BAR, 10, "Thirst")));
    }

    @Test
    void barSegmentCountConfigurable() {
        String out = render(15, thirst(20, 30), hud(BarStyle.BAR, 20, "Thirst"));
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
