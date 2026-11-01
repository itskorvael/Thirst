package io.github.itskorvael.thirst.water;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
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
        store.upsert(key, WaterQuality.FRESH).join();

        assertEquals(Map.of(key, WaterQuality.FRESH), store.loadAll());
    }

    @Test
    void upsertOverwritesExistingQuality() {
        ChunkKey key = new ChunkKey("world", 0, 0);
        store.upsert(key, WaterQuality.DIRTY).join();
        store.upsert(key, WaterQuality.FRESH).join();

        assertEquals(WaterQuality.FRESH, store.loadAll().get(key));
    }

    @Test
    void separateWorldsAndCoordinatesAreDistinctRows() {
        store.upsert(new ChunkKey("a", 1, 1), WaterQuality.FRESH).join();
        store.upsert(new ChunkKey("b", 1, 1), WaterQuality.DIRTY).join();
        store.upsert(new ChunkKey("a", 2, 1), WaterQuality.DIRTY).join();

        assertEquals(3, store.loadAll().size());
    }

    @Test
    void upsertAllWritesEveryChunkInOneTransaction() {
        Map<ChunkKey, WaterQuality> batch = new HashMap<>();
        for (int x = 0; x < 250; x++) {
            batch.put(new ChunkKey("world", x, 0), x % 2 == 0 ? WaterQuality.FRESH : WaterQuality.DIRTY);
        }
        store.upsertAll(batch).join();

        Map<ChunkKey, WaterQuality> loaded = store.loadAll();
        assertEquals(250, loaded.size());
        assertEquals(WaterQuality.FRESH, loaded.get(new ChunkKey("world", 100, 0)));
        assertEquals(WaterQuality.DIRTY, loaded.get(new ChunkKey("world", 101, 0)));
    }

    @Test
    void initializeMigratesLegacyCleanRowsToFresh(@TempDir Path tempDir) throws Exception {
        String url = "jdbc:sqlite:" + tempDir.resolve("legacy.db");

        SQLiteDataSource seed = new SQLiteDataSource();
        seed.setUrl(url);
        try (Connection conn = seed.getConnection(); Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE chunk_water_quality (
                        world_name TEXT NOT NULL, chunk_x INTEGER NOT NULL, chunk_z INTEGER NOT NULL,
                        quality TEXT NOT NULL CHECK (quality IN ('CLEAN', 'DIRTY')),
                        PRIMARY KEY (world_name, chunk_x, chunk_z))
                    """);
            st.execute("INSERT INTO chunk_water_quality VALUES ('w', 1, 1, 'CLEAN'), ('w', 2, 2, 'DIRTY')");
        }

        WaterQualityStore migrated = new WaterQualityStore(url, Logger.getLogger("thirst-test"));
        migrated.initialize();
        try {
            Map<ChunkKey, WaterQuality> loaded = migrated.loadAll();
            assertEquals(WaterQuality.FRESH, loaded.get(new ChunkKey("w", 1, 1)));
            assertEquals(WaterQuality.DIRTY, loaded.get(new ChunkKey("w", 2, 2)));

            // The rebuilt CHECK must now allow FRESH and NEUTRAL.
            migrated.upsert(new ChunkKey("w", 3, 3), WaterQuality.NEUTRAL).join();
            assertEquals(WaterQuality.NEUTRAL, migrated.loadAll().get(new ChunkKey("w", 3, 3)));
            migrated.upsert(new ChunkKey("w", 3, 3), WaterQuality.FRESH).join();
            assertEquals(WaterQuality.FRESH, migrated.loadAll().get(new ChunkKey("w", 3, 3)));
        } finally {
            migrated.close();
        }
    }
}
