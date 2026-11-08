package io.github.itskorvael.thirst.water;

import io.github.itskorvael.thirst.config.ThirstConfig;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The in-memory per-chunk water-quality cache, with write-through to
 * {@link WaterQualityStore}.
 *
 * <p>Reads ({@code qualityAt}) are synchronous and cheap — safe on the main
 * thread. Writes update the cache immediately and persist asynchronously, so a
 * drink right after {@code /water setchunk} already sees the new value.
 *
 * <p>Resolution order for a chunk:
 * <ol>
 *   <li>explicitly painted by an admin (in the cache / DB) wins outright;</li>
 *   <li>otherwise, if {@code water.detect-water} is on and the chunk has surface
 *       water, {@code water.natural-water-quality} (default DIRTY);</li>
 *   <li>otherwise {@code default-water-quality} (default NEUTRAL).</li>
 * </ol>
 * Detection is read-only — it never persists, so a painted chunk is never
 * overwritten.
 *
 * <p>Water poured from a tagged bucket is tracked block by block
 * ({@link PlacedWaterTracker}); {@link #qualityForSource} prefers that over the
 * chunk's quality, so poured water keeps the quality it was poured with.
 */
public final class WaterQualityService {

    private final WaterQualityStore store;
    private final Supplier<ThirstConfig> config;
    private final Map<ChunkKey, WaterQuality> cache = new ConcurrentHashMap<>();

    /** {@code true}/{@code false} once a chunk has been scanned for surface water this session. */
    private final Map<ChunkKey, Boolean> waterPresence = new ConcurrentHashMap<>();

    /** Poured-water blocks examined per maintenance pass. */
    private static final int PRUNE_BUDGET = 5_000;

    private final PlacedWaterTracker placed;

    public WaterQualityService(WaterQualityStore store, Supplier<ThirstConfig> config, Consumer<String> warn) {
        this.store = store;
        this.config = config;
        this.placed = new PlacedWaterTracker(() -> config.get().water().trackedBlockLimit(), warn);
    }

    /** Fills the caches from the database. Blocks; call once on enable. */
    public void load() {
        cache.putAll(store.loadAll());
        placed.load(store.loadPlaced());
    }

    /** Number of poured-water blocks currently tracked. */
    public int trackedWaterCount() {
        return placed.size();
    }

    public int cachedChunkCount() {
        return cache.size();
    }

    public WaterQuality defaultQuality() {
        return config.get().defaultWaterQuality();
    }

    public WaterQuality qualityAt(ChunkKey key) {
        WaterQuality painted = cache.get(key);
        if (painted != null) {
            return painted;
        }
        ThirstConfig cfg = config.get();
        if (cfg.water().detectWater() && chunkHasWater(key)) {
            return cfg.water().naturalWaterQuality();
        }
        return cfg.defaultWaterQuality();
    }

    public WaterQuality qualityAt(World world, int blockX, int blockZ) {
        return qualityAt(ChunkKey.fromBlock(world, blockX, blockZ));
    }

    public WaterQuality qualityAt(Location location) {
        return qualityAt(ChunkKey.fromLocation(location));
    }

    /**
     * Effective quality of an open water source at {@code block}: DIRTY while its
     * world has active rain/storm (if {@code dirty-while-raining}), otherwise the
     * chunk's quality.
     */
    public WaterQuality qualityForSource(Block block) {
        ChunkKey key = ChunkKey.fromBlock(block.getWorld(), block.getX(), block.getZ());
        // The block we're resolving is water — so its chunk has water. Prime the
        // detection cache directly rather than trusting a (possibly stale)
        // height-map scan for freshly placed / flowing water.
        if (block.getType() == Material.WATER) {
            waterPresence.put(key, Boolean.TRUE);
        }
        if (config.get().drinking().dirtyWhileRaining() && block.getWorld().hasStorm()) {
            return WaterQuality.DIRTY;
        }
        if (block.getType() == Material.WATER && config.get().water().trackPlacedWater()) {
            WaterQuality tracked = placed.qualityOf(BlockKey.of(block));
            if (tracked != null) {
                return tracked;
            }
        }
        return qualityAt(key);
    }

    // ── Poured-water tracking ───────────────────────────────────────────────

    /** Remember that {@code block} now holds water poured with {@code quality}. */
    public void trackPoured(Block block, WaterQuality quality) {
        if (config.get().water().trackPlacedWater()) {
            placed.record(BlockKey.of(block), quality);
        }
    }

    /** Forget {@code block}, e.g. after its source was scooped up. */
    public void untrack(Block block) {
        if (!placed.isEmpty()) {
            placed.remove(BlockKey.of(block));
        }
    }

    /** Water flowing out of a tracked block carries its quality to where it flows. */
    public void propagateFlow(Block from, Block to) {
        if (placed.isEmpty() || !config.get().water().trackPlacedWater()) {
            return;
        }
        WaterQuality quality = placed.qualityOf(BlockKey.of(from));
        if (quality != null && from.getType() == Material.WATER) {
            placed.merge(BlockKey.of(to), quality);
        }
    }

    /**
     * Periodic upkeep: drops tracked blocks that are no longer water (in loaded
     * chunks) and writes pending changes to the database. Main thread.
     */
    public void maintainPlaced() {
        placed.prune(PRUNE_BUDGET, WaterQualityService::stillWaterOrUnknown);
        flushPlaced();
    }

    /** Writes pending placed-water changes to the database (asynchronously). */
    public void flushPlaced() {
        PlacedWaterTracker.Changes changes = placed.drainChanges();
        if (!changes.isEmpty()) {
            store.applyPlaced(changes);
        }
    }

    private static boolean stillWaterOrUnknown(BlockKey key) {
        World world = Bukkit.getWorld(key.worldName());
        if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
            return true; // can't tell right now — keep it
        }
        return world.getBlockAt(key.x(), key.y(), key.z()).getType() == Material.WATER;
    }

    /** Whether an admin has explicitly set this chunk. */
    public boolean isExplicitlySet(ChunkKey key) {
        return cache.containsKey(key);
    }

    /**
     * Whether this chunk has a surface water block. Scanned once per session and
     * cached; a chunk that could not be scanned (world / chunk not loaded) is
     * not cached, so it is retried later.
     */
    public boolean chunkHasWater(ChunkKey key) {
        Boolean known = waterPresence.get(key);
        if (known != null) {
            return known;
        }
        World world = Bukkit.getWorld(key.worldName());
        if (world == null || !world.isChunkLoaded(key.x(), key.z())) {
            return false;
        }
        boolean present = scanForSurfaceWater(world, key);
        waterPresence.put(key, present);
        return present;
    }

    /** Set one chunk: cache now, database asynchronously. */
    public void setChunk(ChunkKey key, WaterQuality quality) {
        cache.put(key, quality);
        store.upsert(key, quality);
    }

    /**
     * Set every chunk in {@code area}: cache now, database in one async
     * transaction.
     *
     * @return the number of chunks affected
     */
    public int setRegion(ChunkArea area, WaterQuality quality) {
        Map<ChunkKey, WaterQuality> changed = new HashMap<>();
        area.forEachChunk(key -> changed.put(key, quality));
        cache.putAll(changed);
        store.upsertAll(changed);
        return changed.size();
    }

    private static boolean scanForSurfaceWater(World world, ChunkKey key) {
        int baseX = key.x() << 4;
        int baseZ = key.z() << 4;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = baseX + dx;
                int z = baseZ + dz;
                // Check the surface plus a short way down — catches water under
                // leaves, overhangs and shallow banks.
                int top = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
                for (int y = top; y >= top - 4 && y >= world.getMinHeight(); y--) {
                    if (world.getBlockAt(x, y, z).getType() == Material.WATER) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
