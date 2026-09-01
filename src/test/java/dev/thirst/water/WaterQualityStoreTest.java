package dev.thirst.water;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterQualityStoreTest {

    private WaterQualityStore store;

    @BeforeEach
    void setUp() {
        store = new WaterQualityStore("jdbc:sqlite::memory:", Logger.getLogger("thirst-test"));
        store.initialize();
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @Test
    void loadAllIsEmptyForFreshDatabase() {
        assertTrue(store.loadAll().isEmpty());
    }

    @Test
    void upsertThenLoadAllRoundTrips() {
        ChunkKey key = new ChunkKey("world", 3, -7);
        store.upsert(key, WaterQuality.CLEAN).join();

        assertEquals(Map.of(key, WaterQuality.CLEAN), store.loadAll());
    }

    @Test
    void upsertOverwritesExistingQuality() {
        ChunkKey key = new ChunkKey("world", 0, 0);
        store.upsert(key, WaterQuality.DIRTY).join();
        store.upsert(key, WaterQuality.CLEAN).join();

        assertEquals(WaterQuality.CLEAN, store.loadAll().get(key));
    }

    @Test
    void separateWorldsAndCoordinatesAreDistinctRows() {
        store.upsert(new ChunkKey("a", 1, 1), WaterQuality.CLEAN).join();
        store.upsert(new ChunkKey("b", 1, 1), WaterQuality.DIRTY).join();
        store.upsert(new ChunkKey("a", 2, 1), WaterQuality.DIRTY).join();

        assertEquals(3, store.loadAll().size());
    }

    @Test
    void upsertAllWritesEveryChunkInOneTransaction() {
        Map<ChunkKey, WaterQuality> batch = new HashMap<>();
        for (int x = 0; x < 250; x++) {
            batch.put(new ChunkKey("world", x, 0), x % 2 == 0 ? WaterQuality.CLEAN : WaterQuality.DIRTY);
        }
        store.upsertAll(batch).join();

        Map<ChunkKey, WaterQuality> loaded = store.loadAll();
        assertEquals(250, loaded.size());
        assertEquals(WaterQuality.CLEAN, loaded.get(new ChunkKey("world", 100, 0)));
        assertEquals(WaterQuality.DIRTY, loaded.get(new ChunkKey("world", 101, 0)));
    }
}
