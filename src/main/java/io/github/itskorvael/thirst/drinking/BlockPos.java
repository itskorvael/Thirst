package io.github.itskorvael.thirst.drinking;

import org.bukkit.block.Block;

/** Immutable world + block coordinates, usable as a set key. */
record BlockPos(String world, int x, int y, int z) {

    static BlockPos of(Block block) {
        return new BlockPos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }
}
