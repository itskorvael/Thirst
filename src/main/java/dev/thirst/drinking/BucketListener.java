package dev.thirst.drinking;

import dev.thirst.water.WaterQuality;
import dev.thirst.water.WaterQualityService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Tags a filled water bucket with the quality of what it scooped, the same way
 * {@link DrinkingListener} tags bottles.
 *
 * <p>From a water source: chunk quality (or DIRTY while raining). From a
 * cauldron: whatever {@link CauldronWaterTracker} says is in it. Emptying a
 * tagged bucket into the world does not tag the placed water (loose water uses
 * the chunk's flag); emptying it into a cauldron is handled by the tracker.
 */
public final class BucketListener implements Listener {

    private final WaterContainers containers;
    private final WaterQualityService water;
    private final CauldronWaterTracker cauldrons;

    public BucketListener(WaterContainers containers, WaterQualityService water, CauldronWaterTracker cauldrons) {
        this.containers = containers;
        this.water = water;
        this.cauldrons = cauldrons;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        ItemStack result = event.getItemStack();
        if (result == null || result.getType() != Material.WATER_BUCKET) {
            return; // lava, powder snow, fish, milk, …
        }

        Block clicked = event.getBlockClicked();
        WaterQuality quality;
        if (clicked.getType() == Material.WATER_CAULDRON) {
            WaterQuality tracked = cauldrons.qualityOf(clicked);
            quality = tracked != null ? tracked : water.qualityForSource(clicked);
        } else {
            quality = water.qualityForSource(event.getBlock());
        }

        event.setItemStack(containers.createBucket(quality));
    }
}
