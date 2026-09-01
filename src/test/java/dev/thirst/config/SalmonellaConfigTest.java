package dev.thirst.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SalmonellaConfigTest {

    private static ThirstConfig.Salmonella salmonella(List<Integer> durations) {
        return new ThirstConfig.Salmonella(300, durations, true);
    }

    @Test
    void maxStacksIsTheNumberOfConfiguredDurations() {
        assertEquals(5, salmonella(List.of(30, 45, 60, 90, 120)).maxStacks());
        assertEquals(3, salmonella(List.of(10, 20, 30)).maxStacks());
    }

    @Test
    void durationForStackClampsIntoRange() {
        ThirstConfig.Salmonella s = salmonella(List.of(30, 45, 60, 90, 120));
        assertEquals(30, s.durationSecondsForStack(1));
        assertEquals(60, s.durationSecondsForStack(3));
        assertEquals(120, s.durationSecondsForStack(5));
        assertEquals(30, s.durationSecondsForStack(0));    // below range
        assertEquals(120, s.durationSecondsForStack(99));  // above range
    }

    @Test
    void durationsAreDefensivelyCopied() {
        List<Integer> mutable = new ArrayList<>(List.of(30, 45));
        ThirstConfig.Salmonella s = salmonella(mutable);
        mutable.set(0, 999);
        assertEquals(30, s.durationSecondsForStack(1));
    }
}
