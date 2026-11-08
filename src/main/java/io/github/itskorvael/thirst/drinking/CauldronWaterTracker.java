package io.github.itskorvael.thirst.drinking;

import io.github.itskorvael.thirst.config.ThirstConfig;
import io.github.itskorvael.thirst.water.BlockKey;
import io.github.itskorvael.thirst.water.BlockQualityTracker;
import io.github.itskorvael.thirst.water.WaterQuality;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason;
import org.bukkit.inventory.PlayerInventory;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Tracks the quality of the water currently held by each cauldron.
 *
 * <p>The water in a cauldron is whatever was poured in — a tagged bucket or
 * bottle carries its quality across, rain / dripstone fills it with dirty
 * runoff — <em>not</em> the chunk's flag. Mixing follows a simple rule: any
 * dirty component makes the whole pot dirty.
 *
 * <p>The quality is saved to the database (see {@link BlockQualityTracker}), so it
 * survives restarts. A cauldron that was never tracked (filled before this
 * plugin was installed) falls back to the source-quality of its own location
 * until it is emptied and refilled.
 */
public final class CauldronWaterTracker implements Listener {

    private final WaterContainers containers;
    private final Supplier<ThirstConfig> config;
    private final BlockQualityTracker tracker;

    public CauldronWaterTracker(WaterContainers containers, BlockQualityTracker tracker,
                                Supplier<ThirstConfig> config) {
        this.containers = containers;
        this.tracker = tracker;
        this.config = config;
    }

    /** The tracked quality of this cauldron's water, or {@code null} if unknown. */
    public @Nullable WaterQuality qualityOf(Block cauldron) {
        return tracker.qualityOf(BlockKey.of(cauldron));
    }

    /** Drop the entry for a cauldron the drinking listener has just emptied. */
    public void forget(Block cauldron) {
        tracker.remove(BlockKey.of(cauldron));
    }

    /** Combine two water qualities: dirty beats fresh beats neutral. */
    static WaterQuality mix(WaterQuality existing, WaterQuality added) {
        return WaterQuality.mix(existing, added);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCauldronLevelChange(CauldronLevelChangeEvent event) {
        BlockKey pos = BlockKey.of(event.getBlock());

        if (event.getNewState().getType() != Material.WATER_CAULDRON) {
            tracker.remove(pos); // emptied, or turned to lava / powder snow
            return;
        }
        if (event.getBlock().getType() != Material.WATER_CAULDRON) {
            tracker.remove(pos); // filling an empty cauldron: drop any stale entry first
        }

        WaterQuality added = switch (event.getReason()) {
            case NATURAL_FILL -> config.get().drinking().rainCauldronAlwaysDirty()
                    ? WaterQuality.DIRTY
                    : null;
            case BUCKET_EMPTY, BOTTLE_EMPTY -> heldContainerQuality(event.getEntity());
            default -> null;
        };
        if (added != null) {
            tracker.merge(pos, added);
        }
    }

    private @Nullable WaterQuality heldContainerQuality(@Nullable Entity entity) {
        if (!(entity instanceof Player player)) {
            return null;
        }
        PlayerInventory inventory = player.getInventory();
        WaterQuality mainHand = containers.qualityOf(inventory.getItemInMainHand());
        return mainHand != null ? mainHand : containers.qualityOf(inventory.getItemInOffHand());
    }
}
