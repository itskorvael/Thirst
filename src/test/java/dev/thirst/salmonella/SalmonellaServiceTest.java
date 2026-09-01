package dev.thirst.salmonella;

import dev.thirst.salmonella.SalmonellaService.DecayStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SalmonellaServiceTest {

    private static final int RESET = 300 * 20; // decay-interval-seconds * ticks

    @Test
    void timerJustCountsDownWhileItHasRoomLeft() {
        DecayStep step = SalmonellaService.decay(3, 200, 20, RESET);
        assertEquals(3, step.stacks());
        assertEquals(180, step.ticksRemaining());
        assertFalse(step.changed());
        assertFalse(step.cured());
    }

    @Test
    void expiringTheTimerDropsOneStackAndResets() {
        DecayStep step = SalmonellaService.decay(3, 10, 20, RESET);
        assertEquals(2, step.stacks());
        assertEquals(RESET, step.ticksRemaining());
        assertTrue(step.changed());
        assertFalse(step.cured());
    }

    @Test
    void expiringTheLastStackIsACure() {
        DecayStep step = SalmonellaService.decay(1, 0, 20, RESET);
        assertEquals(0, step.stacks());
        assertTrue(step.changed());
        assertTrue(step.cured());
    }

    @Test
    void noStacksIsAlreadyCured() {
        DecayStep step = SalmonellaService.decay(0, 0, 20, RESET);
        assertEquals(0, step.stacks());
        assertTrue(step.cured());
    }
}
