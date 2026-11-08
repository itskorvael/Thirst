package io.github.itskorvael.thirst.water;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockQualityTrackerTest {

    private final List<String> warnings = new ArrayList<>();

    private static BlockKey key(int x) {
        return new BlockKey("world", x, 64, 0);
    }

    private BlockQualityTracker tracker(int limit) {
        return new BlockQualityTracker(() -> limit, warnings::add);
    }

    @Test
    void recordedWaterKeepsItsQuality() {
        BlockQualityTracker tracker = tracker(100);
        tracker.record(key(1), WaterQuality.DIRTY);

        assertEquals(WaterQuality.DIRTY, tracker.qualityOf(key(1)));
        assertNull(tracker.qualityOf(key(2)));
    }

    @Test
    void recordReplacesWhateverWasThere() {
        BlockQualityTracker tracker = tracker(100);
        tracker.record(key(1), WaterQuality.DIRTY);
        tracker.record(key(1), WaterQuality.FRESH);

        assertEquals(WaterQuality.FRESH, tracker.qualityOf(key(1)));
    }

    @Test
    void mergeLetsDirtyBeatFreshBeatsNeutral() {
        BlockQualityTracker tracker = tracker(100);
        tracker.record(key(1), WaterQuality.FRESH);
        tracker.merge(key(1), WaterQuality.DIRTY);
        assertEquals(WaterQuality.DIRTY, tracker.qualityOf(key(1)));

        // dirty stays dirty when fresher water flows in
        tracker.merge(key(1), WaterQuality.FRESH);
        assertEquals(WaterQuality.DIRTY, tracker.qualityOf(key(1)));

        tracker.merge(key(2), WaterQuality.NEUTRAL);
        tracker.merge(key(2), WaterQuality.FRESH);
        assertEquals(WaterQuality.FRESH, tracker.qualityOf(key(2)));
    }

    @Test
    void mergeIntoAnUntrackedBlockJustRecords() {
        BlockQualityTracker tracker = tracker(100);
        tracker.merge(key(5), WaterQuality.DIRTY);

        assertEquals(WaterQuality.DIRTY, tracker.qualityOf(key(5)));
    }

    @Test
    void removeForgetsTheBlock() {
        BlockQualityTracker tracker = tracker(100);
        tracker.record(key(1), WaterQuality.DIRTY);
        tracker.remove(key(1));

        assertNull(tracker.qualityOf(key(1)));
        assertTrue(tracker.isEmpty());
    }

    @Test
    void pastTheLimitTheOldestEntriesAreDropped() {
        BlockQualityTracker tracker = tracker(3);
        for (int i = 1; i <= 5; i++) {
            tracker.record(key(i), WaterQuality.DIRTY);
        }

        assertEquals(3, tracker.size());
        assertNull(tracker.qualityOf(key(1)));
        assertNull(tracker.qualityOf(key(2)));
        assertEquals(WaterQuality.DIRTY, tracker.qualityOf(key(5)));
    }

    @Test
    void reRecordingAnEntryMakesItTheNewest() {
        BlockQualityTracker tracker = tracker(2);
        tracker.record(key(1), WaterQuality.DIRTY);
        tracker.record(key(2), WaterQuality.DIRTY);
        tracker.record(key(1), WaterQuality.FRESH); // key 1 is now newer than key 2
        tracker.record(key(3), WaterQuality.DIRTY);

        assertNull(tracker.qualityOf(key(2)));
        assertEquals(WaterQuality.FRESH, tracker.qualityOf(key(1)));
    }

    @Test
    void hittingTheLimitWarnsOnlyOnce() {
        BlockQualityTracker tracker = tracker(1);
        tracker.record(key(1), WaterQuality.DIRTY);
        tracker.record(key(2), WaterQuality.DIRTY);
        tracker.record(key(3), WaterQuality.DIRTY);

        assertEquals(1, warnings.size());
    }

    @Test
    void pruneRemovesEntriesThatAreNoLongerWater() {
        BlockQualityTracker tracker = tracker(100);
        tracker.record(key(1), WaterQuality.DIRTY);
        tracker.record(key(2), WaterQuality.DIRTY);
        tracker.record(key(3), WaterQuality.DIRTY);

        int removed = tracker.prune(100, k -> k.x() != 2); // block 2 dried up

        assertEquals(1, removed);
        assertNull(tracker.qualityOf(key(2)));
        assertEquals(WaterQuality.DIRTY, tracker.qualityOf(key(1)));
        assertEquals(WaterQuality.DIRTY, tracker.qualityOf(key(3)));
    }

    @Test
    void pruneWorksThroughEverythingOverSeveralBoundedPasses() {
        BlockQualityTracker tracker = tracker(100);
        for (int i = 1; i <= 10; i++) {
            tracker.record(key(i), WaterQuality.DIRTY);
        }

        int removed = 0;
        for (int pass = 0; pass < 5; pass++) { // budget 2 per pass => 10 entries in 5 passes
            removed += tracker.prune(2, k -> false);
        }

        assertEquals(10, removed);
        assertTrue(tracker.isEmpty());
    }

    @Test
    void pruneKeepsEntriesItCannotCheck() {
        BlockQualityTracker tracker = tracker(100);
        tracker.record(key(1), WaterQuality.DIRTY);

        assertEquals(0, tracker.prune(100, k -> true)); // "true" = keep (also used for unloaded chunks)
        assertEquals(WaterQuality.DIRTY, tracker.qualityOf(key(1)));
    }

    @Test
    void changesAreBatchedAndDrainedOnce() {
        BlockQualityTracker tracker = tracker(100);
        tracker.record(key(1), WaterQuality.DIRTY);
        tracker.record(key(2), WaterQuality.FRESH);

        BlockQualityTracker.Changes first = tracker.drainChanges();
        assertEquals(Map.of(key(1), WaterQuality.DIRTY, key(2), WaterQuality.FRESH), first.upserts());
        assertTrue(first.deletes().isEmpty());
        assertTrue(tracker.drainChanges().isEmpty());
    }

    @Test
    void removingBeforeTheFlushCancelsTheInsertAndQueuesADelete() {
        BlockQualityTracker tracker = tracker(100);
        tracker.record(key(1), WaterQuality.DIRTY);
        tracker.remove(key(1));

        BlockQualityTracker.Changes changes = tracker.drainChanges();
        assertTrue(changes.upserts().isEmpty());
        assertEquals(Set.of(key(1)), changes.deletes());
    }

    @Test
    void recordingAgainAfterARemoveCancelsTheDelete() {
        BlockQualityTracker tracker = tracker(100);
        tracker.record(key(1), WaterQuality.DIRTY);
        tracker.drainChanges();
        tracker.remove(key(1));
        tracker.record(key(1), WaterQuality.FRESH);

        BlockQualityTracker.Changes changes = tracker.drainChanges();
        assertEquals(Map.of(key(1), WaterQuality.FRESH), changes.upserts());
        assertFalse(changes.deletes().contains(key(1)));
    }

    @Test
    void loadedEntriesAreNotQueuedForWriting() {
        BlockQualityTracker tracker = tracker(100);
        tracker.load(Map.of(key(1), WaterQuality.DIRTY));

        assertEquals(WaterQuality.DIRTY, tracker.qualityOf(key(1)));
        assertTrue(tracker.drainChanges().isEmpty());
    }

    @Test
    void evictedEntriesAreQueuedForDeletion() {
        BlockQualityTracker tracker = tracker(1);
        tracker.record(key(1), WaterQuality.DIRTY);
        tracker.drainChanges();
        tracker.record(key(2), WaterQuality.DIRTY);

        Set<BlockKey> deletes = new HashSet<>(tracker.drainChanges().deletes());
        assertEquals(Set.of(key(1)), deletes);
    }
}
