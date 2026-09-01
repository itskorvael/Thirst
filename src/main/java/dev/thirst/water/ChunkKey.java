package dev.thirst.water;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Identifies one chunk column, across all worlds. A value type — safe as a map
 * key and matches the {@code chunk_water_quality} primary key.
 */
public record ChunkKey(String worldName, int x, int z) {

    public static ChunkKey of(Chunk chunk) {
        return new ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public static ChunkKey of(World world, int chunkX, int chunkZ) {
        return new ChunkKey(world.getName(), chunkX, chunkZ);
    }

    /** The chunk containing the given block coordinates — pure, testable. */
    public static ChunkKey fromBlockCoords(String worldName, int blockX, int blockZ) {
        return new ChunkKey(worldName, blockX >> 4, blockZ >> 4);
    }

    /** The chunk containing the given block coordinates — never loads the chunk. */
    public static ChunkKey fromBlock(World world, int blockX, int blockZ) {
        return fromBlockCoords(world.getName(), blockX, blockZ);
    }

    public static ChunkKey fromLocation(Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location has no world");
        }
        return fromBlockCoords(world.getName(), location.getBlockX(), location.getBlockZ());
    }
}
