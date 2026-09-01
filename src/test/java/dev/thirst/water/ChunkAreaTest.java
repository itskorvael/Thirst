package dev.thirst.water;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkAreaTest {

    @Test
    void chunkKeyFromBlockCoordsFloorsTowardsNegativeInfinity() {
        assertEquals(new ChunkKey("w", 0, 0), ChunkKey.fromBlockCoords("w", 15, 15));
        assertEquals(new ChunkKey("w", 1, 0), ChunkKey.fromBlockCoords("w", 16, 0));
        assertEquals(new ChunkKey("w", -1, -1), ChunkKey.fromBlockCoords("w", -1, -16));
        assertEquals(new ChunkKey("w", -2, 0), ChunkKey.fromBlockCoords("w", -17, 0));
    }

    @Test
    void ofBlocksConvertsCornersToChunks() {
        ChunkArea area = ChunkArea.ofBlocks("w", 0, 0, 31, 47);
        assertEquals(0, area.minChunkX());
        assertEquals(1, area.maxChunkX());
        assertEquals(0, area.minChunkZ());
        assertEquals(2, area.maxChunkZ());
        assertEquals(6, area.chunkCount());
    }

    @Test
    void cornersAreNormalised() {
        ChunkArea area = new ChunkArea("w", 5, 9, 1, 2);
        assertEquals(1, area.minChunkX());
        assertEquals(5, area.maxChunkX());
        assertEquals(2, area.minChunkZ());
        assertEquals(9, area.maxChunkZ());
    }

    @Test
    void singleChunkCountsAsOne() {
        assertEquals(1, new ChunkArea("w", 4, 4, 4, 4).chunkCount());
    }

    @Test
    void hugeAreaCountDoesNotOverflow() {
        ChunkArea area = new ChunkArea("w", Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 0);
        assertEquals(1L << 32, area.chunkCount());
    }

    @Test
    void forEachChunkVisitsEveryColumnExactlyOnce() {
        ChunkArea area = new ChunkArea("w", -1, -1, 1, 1);
        Set<ChunkKey> visited = new HashSet<>();
        area.forEachChunk(visited::add);
        assertEquals(9, visited.size());
        assertEquals(area.chunkCount(), visited.size());
    }
}
