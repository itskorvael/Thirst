package io.github.itskorvael.thirst.drinking;

import io.github.itskorvael.thirst.water.WaterQuality;
import io.github.itskorvael.thirst.water.WaterQualityService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Water quality for buckets:
 *
 * <ul>
 *   <li><b>fill</b> — a filled water bucket is tagged with the quality of what
 *       it scooped (chunk quality, DIRTY while raining, or a cauldron's tracked
 *       quality), the same way {@link DrinkingListener} tags bottles;</li>
 *   <li><b>empty into the world</b> — the poured block is tracked with the
 *       bucket's quality (see {@link WaterQualityService#trackPoured}), so
 *       scooping it back up can't launder it. Pouring never changes the
 *       chunk's own quality — only admins paint chunks;</li>
 *   <li><b>empty into a cauldron</b> — handled by {@link CauldronWaterTracker}.</li>
 * </ul>
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
        water.untrack(event.getBlock()); // the scooped source is gone
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getBucket() != Material.WATER_BUCKET) {
            return;
        }
        Material clicked = event.getBlockClicked().getType();
        if (clicked == Material.WATER_CAULDRON || clicked == Material.CAULDRON) {
            return; // cauldrons are the tracker's job
        }

        Player player = event.getPlayer();
        WaterQuality poured = containers.qualityOf(player.getInventory().getItem(event.getHand()));
        if (poured == null) {
            return;
        }

        water.trackPoured(event.getBlock(), poured);
    }
}
