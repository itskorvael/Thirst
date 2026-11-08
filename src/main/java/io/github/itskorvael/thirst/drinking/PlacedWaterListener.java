package io.github.itskorvael.thirst.drinking;

import io.github.itskorvael.thirst.water.WaterQuality;
import io.github.itskorvael.thirst.water.WaterQualityService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps poured-water tracking accurate beyond hand-poured buckets: water that
 * flows out of a tracked block carries its quality with it, and a dispenser
 * emptying a tagged bucket tracks the block it fills.
 */
@NullMarked
public final class PlacedWaterListener implements Listener {

    private final WaterContainers containers;
    private final WaterQualityService water;

    public PlacedWaterListener(WaterContainers containers, WaterQualityService water) {
        this.containers = containers;
        this.water = water;
    }

    /** Fires for every water flow update; {@code propagateFlow} bails out on the first cheap check. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        water.propagateFlow(event.getBlock(), event.getToBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (event.getItem().getType() != Material.WATER_BUCKET) {
            return;
        }
        WaterQuality quality = containers.qualityOf(event.getItem());
        if (quality == null || !(event.getBlock().getBlockData() instanceof Directional facing)) {
            return;
        }
        Block target = event.getBlock().getRelative(facing.getFacing());
        water.trackPoured(target, quality);
    }
}
