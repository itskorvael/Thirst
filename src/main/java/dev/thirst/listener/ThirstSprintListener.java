package dev.thirst.listener;

import dev.thirst.config.ThirstConfig;
import dev.thirst.thirst.ThirstManager;
import dev.thirst.thirst.ThirstRules;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSprintEvent;

import java.util.function.Supplier;

/**
 * Blocks sprinting while thirst is at/below the critical threshold, mirroring
 * vanilla hunger disabling sprint at 6/20.
 *
 * <p>This stops a player <em>starting</em> a sprint. A player already sprinting
 * when their thirst ticks down into the critical band is stopped by
 * {@link dev.thirst.thirst.ThirstTickTask} on its next pass (≤ 1s). The client
 * may briefly show the sprint FOV before it corrects — the speed benefit is
 * gone immediately either way.
 */
public final class ThirstSprintListener implements Listener {

    private final ThirstManager manager;
    private final Supplier<ThirstConfig> config;

    public ThirstSprintListener(ThirstManager manager, Supplier<ThirstConfig> config) {
        this.manager = manager;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        if (!event.isSprinting()) {
            return; // stopping a sprint is always allowed
        }
        Player player = event.getPlayer();
        if (ThirstRules.sprintBlocked(player, manager.getLevel(player), config.get().thirst())) {
            event.setCancelled(true);
        }
    }
}
