package io.github.itskorvael.thirst.water;

import java.util.function.Consumer;

/**
 * An inclusive rectangle of chunk columns in one world, used by
 * {@code /water setregion}. Corners are normalised so {@code min <= max}.
 */
public record ChunkArea(String worldName, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {

    public ChunkArea {
        if (minChunkX > maxChunkX) {
            int tmp = minChunkX;
            minChunkX = maxChunkX;
            maxChunkX = tmp;
        }
        if (minChunkZ > maxChunkZ) {
            int tmp = minChunkZ;
            minChunkZ = maxChunkZ;
            maxChunkZ = tmp;
        }
    }

    /** Build from two block-coordinate corners (inclusive). */
    public static ChunkArea ofBlocks(String worldName, int blockX1, int blockZ1, int blockX2, int blockZ2) {
        return new ChunkArea(worldName, blockX1 >> 4, blockZ1 >> 4, blockX2 >> 4, blockZ2 >> 4);
    }

    /** Number of chunk columns covered ({@code long} to survive absurd inputs). */
    public long chunkCount() {
        long width = (long) maxChunkX - minChunkX + 1;
        long depth = (long) maxChunkZ - minChunkZ + 1;
        return width * depth;
    }

    public void forEachChunk(Consumer<ChunkKey> action) {
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                action.accept(new ChunkKey(worldName, cx, cz));
            }
        }
    }
}
