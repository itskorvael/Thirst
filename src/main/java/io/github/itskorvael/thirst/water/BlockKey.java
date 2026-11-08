package io.github.itskorvael.thirst.water;

import org.bukkit.block.Block;

/**
 * Identifies one block across all worlds. A value type — safe as a map key and
 * matches the {@code placed_water} primary key.
 */
public record BlockKey(String worldName, int x, int y, int z) {

    public static BlockKey of(Block block) {
        return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }
}
