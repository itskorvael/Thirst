package io.github.itskorvael.thirst.thirst;

/**
 * Pure thirst arithmetic — no Bukkit types, fully unit-testable.
 *
 * <p>The model mirrors vanilla hunger's exhaustion mechanic: activity (and, in
 * this plugin, the passage of time) accumulates <em>exhaustion</em>; every whole
 * multiple of {@code exhaustionThreshold} removes one thirst point.
 */
public final class ThirstMath {

    private ThirstMath() {
    }

    /**
     * Outcome of applying {@link #drain(double, double, int)}.
     *
     * @param pointsLost           whole thirst points to subtract (>= 0)
     * @param remainingExhaustion  exhaustion left over after the whole points were taken
     */
    public record DrainResult(int pointsLost, double remainingExhaustion) {
    }

    /**
     * Converts accumulated exhaustion into whole thirst points lost.
     *
     * @param exhaustion current exhaustion; negatives are treated as {@code 0}
     * @param threshold  exhaustion required per point; {@code <= 0} disables draining
     * @param maxPoints  safety cap on points removed in one call (guards against a
     *                   misconfigured tiny threshold); {@code <= 0} disables draining
     * @return the points to remove and the exhaustion remainder to store back
     */
    public static DrainResult drain(double exhaustion, double threshold, int maxPoints) {
        if (exhaustion < 0.0) {
            return new DrainResult(0, 0.0);
        }
        if (threshold <= 0.0 || maxPoints <= 0 || exhaustion < threshold) {
            return new DrainResult(0, exhaustion);
        }
        double remaining = exhaustion;
        int lost = 0;
        while (remaining >= threshold && lost < maxPoints) {
            remaining -= threshold;
            lost++;
        }
        return new DrainResult(lost, remaining);
    }

    /**
     * Exhaustion to add per second so that an otherwise-idle player loses one
     * thirst point every {@code baseDrainIntervalSeconds}.
     *
     * @param threshold                exhaustion per thirst point
     * @param baseDrainIntervalSeconds seconds of idle time per point; {@code <= 0} disables passive drain
     */
    public static double passiveExhaustionPerSecond(double threshold, int baseDrainIntervalSeconds) {
        if (baseDrainIntervalSeconds <= 0 || threshold <= 0.0) {
            return 0.0;
        }
        return threshold / baseDrainIntervalSeconds;
    }

    /**
     * Clamps a thirst level into {@code [0, max]}.
     */
    public static int clampLevel(int level, int max) {
        if (level < 0) {
            return 0;
        }
        return Math.min(level, Math.max(0, max));
    }

    /**
     * Damage to actually apply on a starvation tick, honouring a health floor.
     *
     * <ul>
     *   <li>{@code minimumHealth <= 0} — full damage; the player may die.</li>
     *   <li>{@code currentHealth <= minimumHealth} — no damage; hold at the floor.</li>
     *   <li>otherwise — {@code requestedDamage}, clamped so health lands exactly on the floor.</li>
     * </ul>
     *
     * @return a value in {@code [0, requestedDamage]}; {@code 0} means "do not damage"
     */
    public static double starvationDamage(double currentHealth, double requestedDamage, double minimumHealth) {
        if (requestedDamage <= 0.0) {
            return 0.0;
        }
        if (minimumHealth <= 0.0) {
            return requestedDamage;
        }
        if (currentHealth <= minimumHealth) {
            return 0.0;
        }
        return Math.min(requestedDamage, currentHealth - minimumHealth);
    }

    /**
     * Absolute thirst value at or below which the HUD should switch to its
     * critical colour.
     *
     * @param max                     configured maximum thirst
     * @param criticalThresholdPercent percentage of {@code max} (0–100)
     */
    public static double criticalLevel(int max, int criticalThresholdPercent) {
        return max * (criticalThresholdPercent / 100.0);
    }
}
