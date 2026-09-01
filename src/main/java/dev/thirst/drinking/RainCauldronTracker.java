package dev.thirst.drinking;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which water cauldrons were filled by rain or dripstone rather than
 * by a bucket, so {@code rain-cauldron-always-dirty} can treat those as
 * {@link dev.thirst.water.WaterQuality#DIRTY} runoff — even after the rain stops.
 *
 * <p>In-memory only — the set is lost on restart. A restarted server just falls
 * back to the chunk's quality for previously rain-filled cauldrons until they
 * refill.
 */
public final class RainCauldronTracker implements Listener {

    private final Set<BlockPos> rainFilled = ConcurrentHashMap.newKeySet();

    public boolean isRainFilled(Block block) {
        return rainFilled.contains(BlockPos.of(block));
    }

    /** Drop the mark for a cauldron the drinking listener has just emptied. */
    public void forget(Block block) {
        rainFilled.remove(BlockPos.of(block));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCauldronLevelChange(CauldronLevelChangeEvent event) {
        BlockPos pos = BlockPos.of(event.getBlock());

        // Anything that isn't a water cauldron afterwards (emptied, lava, powder
        // snow) can't be rain water.
        if (event.getNewState().getType() != Material.WATER_CAULDRON) {
            rainFilled.remove(pos);
            return;
        }

        ChangeReason reason = event.getReason();
        if (reason == ChangeReason.NATURAL_FILL) {
            rainFilled.add(pos);
        } else if (reason == ChangeReason.BUCKET_EMPTY) {
            rainFilled.remove(pos); // filled from a bucket now, not rain
        }
        // BOTTLE_EMPTY / washes / etc. — leave the existing mark as-is
    }
}
