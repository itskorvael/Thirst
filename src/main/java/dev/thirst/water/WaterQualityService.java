package dev.thirst.water;

import dev.thirst.config.ThirstConfig;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The in-memory per-chunk water-quality cache, with write-through to
 * {@link WaterQualityStore}.
 *
 * <p>Reads ({@code qualityAt}) are synchronous and cheap — safe on the main
 * thread. Writes update the cache immediately and persist asynchronously, so a
 * drink right after {@code /water setchunk} already sees the new value.
 *
 * <p>Chunks with no stored value resolve to {@code default-water-quality}.
 */
public final class WaterQualityService {

    private final WaterQualityStore store;
    private final Supplier<ThirstConfig> config;
    private final Map<ChunkKey, WaterQuality> cache = new ConcurrentHashMap<>();

    public WaterQualityService(WaterQualityStore store, Supplier<ThirstConfig> config) {
        this.store = store;
        this.config = config;
    }

    /** Fills the cache from the database. Blocks; call once on enable. */
    public void load() {
        cache.putAll(store.loadAll());
    }

    public int cachedChunkCount() {
        return cache.size();
    }

    public WaterQuality defaultQuality() {
        return config.get().defaultWaterQuality();
    }

    public WaterQuality qualityAt(ChunkKey key) {
        return cache.getOrDefault(key, defaultQuality());
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
        if (config.get().drinking().dirtyWhileRaining() && block.getWorld().hasStorm()) {
            return WaterQuality.DIRTY;
        }
        return qualityAt(ChunkKey.fromBlock(block.getWorld(), block.getX(), block.getZ()));
    }

    /** Whether an admin has explicitly painted this chunk (vs. falling back to the default). */
    public boolean isExplicitlySet(ChunkKey key) {
        return cache.containsKey(key);
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
}
