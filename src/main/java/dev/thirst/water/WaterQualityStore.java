package dev.thirst.water;

import org.jspecify.annotations.Nullable;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite persistence for per-chunk water quality.
 *
 * <p>All JDBC work runs on one dedicated daemon thread holding a single
 * {@link Connection}, so the connection is never touched concurrently and the
 * main thread never blocks on the database. {@link #initialize()} and
 * {@link #loadAll()} are the exception — they are meant to be called once, at
 * enable, and block until done.
 */
public final class WaterQualityStore {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS chunk_water_quality (
                world_name TEXT    NOT NULL,
                chunk_x    INTEGER NOT NULL,
                chunk_z    INTEGER NOT NULL,
                quality    TEXT    NOT NULL CHECK (quality IN ('FRESH', 'NEUTRAL', 'DIRTY')),
                PRIMARY KEY (world_name, chunk_x, chunk_z)
            )
            """;

    private static final String UPSERT = """
            INSERT INTO chunk_water_quality (world_name, chunk_x, chunk_z, quality)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (world_name, chunk_x, chunk_z) DO UPDATE SET quality = excluded.quality
            """;

    private static final String SELECT_ALL =
            "SELECT world_name, chunk_x, chunk_z, quality FROM chunk_water_quality";

    private final String jdbcUrl;
    private final Logger logger;
    private final ExecutorService executor;

    private @Nullable Connection connection;

    public WaterQualityStore(String jdbcUrl, Logger logger) {
        this.jdbcUrl = jdbcUrl;
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Thirst-WaterDB");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Opens the connection and creates the schema. Blocks; call once on enable. */
    public void initialize() {
        run(() -> {
            SQLiteConfig sqliteConfig = new SQLiteConfig();
            sqliteConfig.setJournalMode(SQLiteConfig.JournalMode.WAL);
            sqliteConfig.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);

            SQLiteDataSource dataSource = new SQLiteDataSource(sqliteConfig);
            dataSource.setUrl(jdbcUrl);

            Connection conn = dataSource.getConnection();
            try (Statement statement = conn.createStatement()) {
                statement.execute(CREATE_TABLE);
            }
            migrateSchema(conn);
            this.connection = conn;
        }).join();
    }

    /**
     * Bring an older schema up to date: earlier builds stored the safe quality
     * as {@code CLEAN}, and the {@code CHECK} constraint has since gained
     * {@code NEUTRAL}. Rebuild the table (remapping {@code CLEAN -> FRESH}) when
     * its stored {@code CREATE} statement predates {@code NEUTRAL}. No-op on a
     * fresh or already-current database.
     */
    private void migrateSchema(Connection conn) throws SQLException {
        String tableSql = null;
        try (Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'chunk_water_quality'")) {
            if (rs.next()) {
                tableSql = rs.getString(1);
            }
        }
        if (tableSql == null || tableSql.contains("'NEUTRAL'")) {
            return;
        }

        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (Statement statement = conn.createStatement()) {
            statement.execute("ALTER TABLE chunk_water_quality RENAME TO chunk_water_quality_legacy");
            statement.execute(CREATE_TABLE);
            statement.execute("""
                    INSERT INTO chunk_water_quality (world_name, chunk_x, chunk_z, quality)
                    SELECT world_name, chunk_x, chunk_z,
                           CASE quality WHEN 'CLEAN' THEN 'FRESH' ELSE quality END
                    FROM chunk_water_quality_legacy
                    """);
            statement.execute("DROP TABLE chunk_water_quality_legacy");
            conn.commit();
            logger.info("Migrated the water-quality database to the current schema");
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    /** Reads every stored chunk into a fresh map. Blocks; call once on enable. */
    public Map<ChunkKey, WaterQuality> loadAll() {
        return supply(() -> {
            Map<ChunkKey, WaterQuality> out = new HashMap<>();
            try (Statement statement = requireConnection().createStatement();
                 ResultSet rs = statement.executeQuery(SELECT_ALL)) {
                while (rs.next()) {
                    WaterQuality quality = WaterQuality.parse(rs.getString(4));
                    if (quality != null) {
                        out.put(new ChunkKey(rs.getString(1), rs.getInt(2), rs.getInt(3)), quality);
                    }
                }
            }
            return out;
        }).join();
    }

    /** Write-through for a single chunk. Fire-and-forget; failures are logged. */
    public CompletableFuture<Void> upsert(ChunkKey key, WaterQuality quality) {
        return run(() -> {
            try (PreparedStatement ps = requireConnection().prepareStatement(UPSERT)) {
                bind(ps, key, quality);
                ps.executeUpdate();
            }
        });
    }

    /** Batched write-through for many chunks in one transaction. */
    public CompletableFuture<Void> upsertAll(Map<ChunkKey, WaterQuality> entries) {
        Map<ChunkKey, WaterQuality> snapshot = Map.copyOf(entries);
        return run(() -> {
            Connection conn = requireConnection();
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(UPSERT)) {
                for (Map.Entry<ChunkKey, WaterQuality> entry : snapshot.entrySet()) {
                    bind(ps, entry.getKey(), entry.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        });
    }

    /** Flushes queued work and closes the connection. Blocks up to 10s; call on disable. */
    public void close() {
        executor.execute(() -> {
            Connection conn = this.connection;
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "Failed to close the water-quality database", ex);
                }
            }
        });
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Water-quality database did not finish writing within 10s");
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private static void bind(PreparedStatement ps, ChunkKey key, WaterQuality quality) throws SQLException {
        ps.setString(1, key.worldName());
        ps.setInt(2, key.x());
        ps.setInt(3, key.z());
        ps.setString(4, quality.name());
    }

    private Connection requireConnection() {
        Connection conn = this.connection;
        if (conn == null) {
            throw new IllegalStateException("WaterQualityStore has not been initialize()d");
        }
        return conn;
    }

    private CompletableFuture<Void> run(SqlRunnable task) {
        return supply(() -> {
            task.run();
            return null;
        });
    }

    private <T> CompletableFuture<T> supply(SqlSupplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                future.complete(task.get());
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Water-quality database operation failed", ex);
                future.completeExceptionally(ex);
            }
        });
        return future;
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws Exception;
    }
}
