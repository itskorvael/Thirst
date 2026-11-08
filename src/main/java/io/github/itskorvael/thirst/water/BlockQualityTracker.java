package io.github.itskorvael.thirst.water;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * Remembers a water quality per block — used for water poured from a tagged
 * bucket (so scooping it back up keeps the quality it was poured with, instead
 * of taking on the chunk's) and for the water inside cauldrons. Each use gets its
 * own instance and its own database table.
 *
 * <p>Pure bookkeeping with no server dependency, so it is unit-testable. Main
 * thread only. Changes are batched: {@link #drainChanges()} hands the pending
 * inserts / deletes to whoever persists them.
 *
 * <p>Entries live until they are removed, are found to be gone by
 * {@link #prune}, or are evicted because the tracker hit its size limit (oldest
 * first).
 */
public final class BlockQualityTracker {

    /** Inserts / updates and deletes accumulated since the last {@link #drainChanges()}. */
    public record Changes(Map<BlockKey, WaterQuality> upserts, Set<BlockKey> deletes) {

        public boolean isEmpty() {
            return upserts.isEmpty() && deletes.isEmpty();
        }
    }

    private final IntSupplier limit;
    private final Consumer<String> warn;

    /** Insertion-ordered so the oldest entries are evicted first. */
    private final LinkedHashMap<BlockKey, WaterQuality> placed = new LinkedHashMap<>();
    private final Map<BlockKey, WaterQuality> pendingUpserts = new HashMap<>();
    private final Set<BlockKey> pendingDeletes = new HashSet<>();
    private final Deque<BlockKey> pruneQueue = new ArrayDeque<>();
    private boolean evictionWarned;

    public BlockQualityTracker(IntSupplier limit, Consumer<String> warn) {
        this.limit = limit;
        this.warn = warn;
    }

    /** Fills the tracker from persisted data. Does not queue anything for writing. */
    public void load(Map<BlockKey, WaterQuality> persisted) {
        placed.putAll(persisted);
    }

    public @Nullable WaterQuality qualityOf(BlockKey key) {
        return placed.get(key);
    }

    public int size() {
        return placed.size();
    }

    public boolean isEmpty() {
        return placed.isEmpty();
    }

    /** Track {@code key} as holding water of {@code quality}, replacing whatever was there. */
    public void record(BlockKey key, WaterQuality quality) {
        placed.remove(key); // re-insert at the end so it counts as the newest entry
        placed.put(key, quality);
        pendingDeletes.remove(key);
        pendingUpserts.put(key, quality);
        enforceLimit();
    }

    /** Like {@link #record} but mixes with any quality already tracked there (dirty beats fresh beats neutral). */
    public void merge(BlockKey key, WaterQuality quality) {
        WaterQuality existing = placed.get(key);
        record(key, existing == null ? quality : WaterQuality.mix(existing, quality));
    }

    public void remove(BlockKey key) {
        if (placed.remove(key) != null) {
            pendingUpserts.remove(key);
            pendingDeletes.add(key);
        }
    }

    /**
     * Checks up to {@code budget} entries and removes those {@code stillWater}
     * rejects. Successive calls walk through every entry before starting over,
     * so the work per call stays bounded however many blocks are tracked.
     *
     * @param stillWater {@code true} to keep an entry (including when it cannot be
     *                   checked, e.g. its chunk is not loaded)
     * @return how many entries were removed
     */
    public int prune(int budget, Predicate<BlockKey> stillWater) {
        if (pruneQueue.isEmpty()) {
            pruneQueue.addAll(placed.keySet());
        }
        int removed = 0;
        for (int i = 0; i < budget && !pruneQueue.isEmpty(); i++) {
            BlockKey key = pruneQueue.poll();
            if (placed.containsKey(key) && !stillWater.test(key)) {
                remove(key);
                removed++;
            }
        }
        return removed;
    }

    /** Takes (and clears) everything that still needs writing to the database. */
    public Changes drainChanges() {
        Changes changes = new Changes(Map.copyOf(pendingUpserts), Set.copyOf(pendingDeletes));
        pendingUpserts.clear();
        pendingDeletes.clear();
        return changes;
    }

    private void enforceLimit() {
        int max = limit.getAsInt();
        if (placed.size() <= max) {
            return;
        }
        if (!evictionWarned) {
            evictionWarned = true;
            warn.accept("Placed-water tracking reached water.tracked-block-limit (" + max
                    + "); the oldest entries are being dropped, so very old poured water may "
                    + "take on its chunk's quality again. Raise the limit if this keeps happening.");
        }
        Iterator<BlockKey> oldest = placed.keySet().iterator();
        while (placed.size() > max && oldest.hasNext()) {
            BlockKey key = oldest.next();
            oldest.remove();
            pendingUpserts.remove(key);
            pendingDeletes.add(key);
        }
    }
}
