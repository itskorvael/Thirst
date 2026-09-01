package dev.thirst.listener;

import dev.thirst.config.ThirstConfig;
import dev.thirst.thirst.CriticalSpeedModifier;
import dev.thirst.thirst.ThirstManager;
import dev.thirst.thirst.ThirstRules;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

import java.util.function.Supplier;

/**
 * Neutralises the sprint speed bonus while thirst is at/below the critical
 * threshold — see {@link CriticalSpeedModifier}. Sprinting still "works"
 * (the client owns that), it just gets you no faster than walking.
 *
 * <p>The sync runs on movement (so it takes effect within a tick of it
 * mattering) and on sprint toggle (so releasing sprint clears it immediately
 * even while standing still).
 */
public final class ThirstSprintListener implements Listener {

    private final ThirstManager manager;
    private final Supplier<ThirstConfig> config;
    private final CriticalSpeedModifier speedModifier;

    public ThirstSprintListener(ThirstManager manager, Supplier<ThirstConfig> config,
                                CriticalSpeedModifier speedModifier) {
        this.manager = manager;
        this.config = config;
        this.speedModifier = speedModifier;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        sync(event.getPlayer(), event.getPlayer().isSprinting());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        sync(event.getPlayer(), event.isSprinting());
    }

    private void sync(Player player, boolean sprinting) {
        ThirstConfig.Thirst thirst = config.get().thirst();
        boolean apply = sprinting
                && ThirstRules.sprintBlocked(player, manager.getLevel(player), thirst);
        speedModifier.sync(player, apply, thirst.criticalSprintSpeedMultiplier());
    }
}
