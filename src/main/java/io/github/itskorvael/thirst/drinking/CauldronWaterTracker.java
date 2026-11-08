package io.github.itskorvael.thirst.drinking;

import io.github.itskorvael.thirst.config.ThirstConfig;
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Tracks the quality of the water currently held by each cauldron.
 *
 * <p>The water in a cauldron is whatever was poured in — a tagged bucket or
 * bottle carries its quality across, rain / dripstone fills it with dirty
 * runoff — <em>not</em> the chunk's flag. Mixing follows a simple rule: any
 * dirty component makes the whole pot dirty.
 *
 * <p>In-memory only; the map is lost on restart. A restarted server falls back
 * to the source-quality of a cauldron's own location until it is emptied and
 * refilled.
 */
public final class CauldronWaterTracker implements Listener {

    private final WaterContainers containers;
    private final Supplier<ThirstConfig> config;
    private final Map<BlockPos, WaterQuality> water = new ConcurrentHashMap<>();

    public CauldronWaterTracker(WaterContainers containers, Supplier<ThirstConfig> config) {
        this.containers = containers;
        this.config = config;
    }

    /** The tracked quality of this cauldron's water, or {@code null} if unknown. */
    public @Nullable WaterQuality qualityOf(Block cauldron) {
        return water.get(BlockPos.of(cauldron));
    }

    /** Drop the entry for a cauldron the drinking listener has just emptied. */
    public void forget(Block cauldron) {
        water.remove(BlockPos.of(cauldron));
    }

    /** Combine two water qualities: dirty beats fresh beats neutral. */
    static WaterQuality mix(WaterQuality existing, WaterQuality added) {
        return WaterQuality.mix(existing, added);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCauldronLevelChange(CauldronLevelChangeEvent event) {
        BlockPos pos = BlockPos.of(event.getBlock());

        if (event.getNewState().getType() != Material.WATER_CAULDRON) {
            water.remove(pos); // emptied, or turned to lava / powder snow
            return;
        }

        WaterQuality added = switch (event.getReason()) {
            case NATURAL_FILL -> config.get().drinking().rainCauldronAlwaysDirty()
                    ? WaterQuality.DIRTY
                    : null;
            case BUCKET_EMPTY, BOTTLE_EMPTY -> heldContainerQuality(event.getEntity());
            default -> null;
        };
        if (added != null) {
            water.merge(pos, added, CauldronWaterTracker::mix);
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
