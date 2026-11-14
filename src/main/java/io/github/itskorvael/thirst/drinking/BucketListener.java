package io.github.itskorvael.thirst.drinking;

import io.github.itskorvael.thirst.water.WaterQuality;
import io.github.itskorvael.thirst.water.WaterQualityService;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
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
 *   <li><b>fill from a cauldron</b> — vanilla does not report this through the
 *       normal bucket event, so it is handled directly: a full water cauldron
 *       empties into a bucket tagged with the cauldron's tracked quality;</li>
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

    /**
     * Right-clicking a full water cauldron with an empty bucket. Runs late and
     * respects cancellation, so claim / protection plugins decide first.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCauldronBucketFill(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        Block cauldron = event.getClickedBlock();
        Player player = event.getPlayer();
        if (hand == null || cauldron == null || player.isSneaking()
                || cauldron.getType() != Material.WATER_CAULDRON
                || !(cauldron.getBlockData() instanceof Levelled level)
                || level.getLevel() < level.getMaximumLevel()) { // vanilla only fills from a full cauldron
            return;
        }
        ItemStack held = player.getInventory().getItem(hand);
        if (held.getType() != Material.BUCKET) {
            return;
        }
        event.setCancelled(true); // we do the fill ourselves

        WaterQuality tracked = cauldrons.qualityOf(cauldron);
        WaterQuality quality = tracked != null ? tracked : water.qualityForSource(cauldron);

        cauldron.setType(Material.CAULDRON);
        cauldrons.forget(cauldron);

        ItemStack filled = containers.createBucket(quality);
        if (player.getGameMode() != GameMode.CREATIVE) {
            if (held.getAmount() == 1) {
                player.getInventory().setItem(hand, filled);
            } else {
                held.subtract();
                player.getInventory().setItem(hand, held);
                DrinkingListener.giveOrDrop(player, filled);
            }
        } else if (!player.getInventory().containsAtLeast(filled, 1)) {
            DrinkingListener.giveOrDrop(player, filled);
        }
        player.getWorld().playSound(cauldron.getLocation().add(0.5, 0.5, 0.5), Sound.ITEM_BUCKET_FILL, 1.0f, 1.0f);
        player.swingHand(hand);
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
