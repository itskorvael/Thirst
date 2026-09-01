package dev.thirst.listener;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
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
 * Takes the point out of sprinting while thirst is at/below the critical
 * threshold, mirroring vanilla hunger at 6/20:
 *
 * <ul>
 *   <li>the sprint <em>speed</em> bonus is cancelled via
 *       {@link CriticalSpeedModifier} (a movement attribute the client honours);</li>
 *   <li>jumping is blocked entirely <em>while sprinting</em> — otherwise the
 *       sprint-jump lunge (a client-side velocity kick the server can't reliably
 *       dampen) lets you bunny-hop around at speed. Releasing sprint restores
 *       normal jumping.</li>
 * </ul>
 *
 * <p>A true "can't sprint" is impossible from the server — the client owns that
 * decision — so these just make sprinting pointless.
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        syncSpeed(event.getPlayer(), event.getPlayer().isSprinting());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        syncSpeed(event.getPlayer(), event.isSprinting());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (player.isSprinting()
                && ThirstRules.sprintBlocked(player, manager.getLevel(player), config.get().thirst())) {
            event.setCancelled(true);
        }
    }

    private void syncSpeed(Player player, boolean sprinting) {
        ThirstConfig.Thirst thirst = config.get().thirst();
        boolean apply = sprinting
                && ThirstRules.sprintBlocked(player, manager.getLevel(player), thirst);
        speedModifier.sync(player, apply, thirst.criticalSprintSpeedMultiplier());
    }
}
