package io.github.itskorvael.thirst.drinking;

import io.github.itskorvael.thirst.water.WaterQuality;
import org.jspecify.annotations.Nullable;

/** The pure rules of the Pitcher Filter, kept apart from Bukkit so they can be unit-tested. */
public final class FilterRules {

    private FilterRules() {
    }

    /**
     * The quality a filter turns {@code current} into: one step better (DIRTY to NEUTRAL, NEUTRAL to FRESH),
     * but never better than {@code max}.
     *
     * @return the improved quality, or {@code null} when the water can't be filtered any cleaner
     */
    public static @Nullable WaterQuality improve(WaterQuality current, WaterQuality max) {
        WaterQuality next = switch (current) {
            case DIRTY -> WaterQuality.NEUTRAL;
            case NEUTRAL -> WaterQuality.FRESH;
            case FRESH -> null;
        };
        if (next == null) {
            return null;
        }
        // FRESH has the lowest ordinal, so "better than max" means a lower ordinal than max.
        return next.ordinal() < max.ordinal() ? null : next;
    }

    /** Can a filter with {@code current} uses take a recharge? Not once it is at or past {@code maxUses}. */
    public static boolean canRecharge(int current, int maxUses) {
        return current < maxUses;
    }

    /** Uses after a recharge: {@code add} more, but never past {@code maxUses} (and never lowered if already higher). */
    public static int recharged(int current, int add, int maxUses) {
        return Math.max(current, Math.min(current + add, maxUses));
    }

    /** Uses one container costs: 1 for a bottle, {@code bucketCost} for a bucket. */
    public static int cost(boolean bucket, int bucketCost) {
        return bucket ? bucketCost : 1;
    }
}
