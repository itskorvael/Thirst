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
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.function.Supplier;

/**
 * Takes the point out of sprinting while thirst is at/below the critical
 * threshold, mirroring vanilla hunger at 6/20:
 *
 * <ul>
 *   <li>the sprint <em>speed</em> bonus is cancelled via
 *       {@link CriticalSpeedModifier} (movement attribute);</li>
 *   <li>the sprint-<em>jump</em> boost — a separate flat velocity kick the
 *       attribute can't reach — is undone here by clamping horizontal air speed
 *       just after the jump.</li>
 * </ul>
 *
 * <p>A true "can't sprint" is impossible from the server (the client owns that
 * decision), so both effects make sprinting pointless instead.
 */
public final class ThirstSprintListener implements Listener {

    private final Plugin plugin;
    private final ThirstManager manager;
    private final Supplier<ThirstConfig> config;
    private final CriticalSpeedModifier speedModifier;

    public ThirstSprintListener(Plugin plugin, ThirstManager manager, Supplier<ThirstConfig> config,
                                CriticalSpeedModifier speedModifier) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;
        this.speedModifier = speedModifier;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        syncSpeed(event.getPlayer(), event.getPlayer().isSprinting());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        syncSpeed(event.getPlayer(), event.isSprinting());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (!player.isSprinting()) {
            return;
        }
        ThirstConfig.Thirst thirst = config.get().thirst();
        if (!ThirstRules.sprintBlocked(player, manager.getLevel(player), thirst)) {
            return;
        }
        double cap = thirst.criticalJumpHorizontalCap();
        // The jump's velocity kick lands this tick; clamp on the next one.
        plugin.getServer().getScheduler().runTask(plugin, () -> clampHorizontalSpeed(player, cap));
    }

    private void syncSpeed(Player player, boolean sprinting) {
        ThirstConfig.Thirst thirst = config.get().thirst();
        boolean apply = sprinting
                && ThirstRules.sprintBlocked(player, manager.getLevel(player), thirst);
        speedModifier.sync(player, apply, thirst.criticalSprintSpeedMultiplier());
    }

    private static void clampHorizontalSpeed(Player player, double cap) {
        if (!player.isValid() || player.isOnGround()) {
            return;
        }
        Vector velocity = player.getVelocity();
        double horizontal = Math.hypot(velocity.getX(), velocity.getZ());
        if (horizontal <= cap || horizontal < 1.0e-6) {
            return;
        }
        double scale = cap / horizontal;
        player.setVelocity(new Vector(velocity.getX() * scale, velocity.getY(), velocity.getZ() * scale));
    }
}
