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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Takes the point out of sprinting while thirst is at/below the critical
 * threshold, mirroring vanilla hunger at 6/20:
 *
 * <ul>
 *   <li>the sprint <em>speed</em> bonus is cancelled via
 *       {@link CriticalSpeedModifier} (a movement attribute the client honours);</li>
 *   <li>the sprint-<em>jump</em> forward lunge — a flat velocity kick the
 *       attribute can't reach — is suppressed by clamping horizontal air speed
 *       on every move tick for a short window after the jump.</li>
 * </ul>
 *
 * <p>A true "can't sprint" is impossible from the server (the client owns that
 * decision), so both effects just make sprinting pointless.
 */
public final class ThirstSprintListener implements Listener {

    /** How long after a sprint-jump to keep clamping horizontal speed. */
    private static final long JUMP_CLAMP_WINDOW_MILLIS = 900L;

    private final ThirstManager manager;
    private final Supplier<ThirstConfig> config;
    private final CriticalSpeedModifier speedModifier;

    /** Player UUID -> {@code System.currentTimeMillis()} until which to clamp jump velocity. Main thread only. */
    private final Map<UUID, Long> jumpClampUntil = new HashMap<>();

    public ThirstSprintListener(ThirstManager manager, Supplier<ThirstConfig> config,
                                CriticalSpeedModifier speedModifier) {
        this.manager = manager;
        this.config = config;
        this.speedModifier = speedModifier;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        ThirstConfig.Thirst thirst = config.get().thirst();

        syncSpeed(player, player.isSprinting(), thirst);
        clampJumpIfPending(player, thirst);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        syncSpeed(event.getPlayer(), event.isSprinting(), config.get().thirst());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        ThirstConfig.Thirst thirst = config.get().thirst();
        if (player.isSprinting()
                && ThirstRules.sprintBlocked(player, manager.getLevel(player), thirst)) {
            jumpClampUntil.put(player.getUniqueId(),
                    System.currentTimeMillis() + JUMP_CLAMP_WINDOW_MILLIS);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        jumpClampUntil.remove(event.getPlayer().getUniqueId());
    }

    private void syncSpeed(Player player, boolean sprinting, ThirstConfig.Thirst thirst) {
        boolean apply = sprinting
                && ThirstRules.sprintBlocked(player, manager.getLevel(player), thirst);
        speedModifier.sync(player, apply, thirst.criticalSprintSpeedMultiplier());
    }

    private void clampJumpIfPending(Player player, ThirstConfig.Thirst thirst) {
        Long until = jumpClampUntil.get(player.getUniqueId());
        if (until == null) {
            return;
        }
        if (System.currentTimeMillis() >= until || player.isOnGround()) {
            jumpClampUntil.remove(player.getUniqueId());
            return;
        }
        double cap = thirst.criticalJumpHorizontalCap();
        Vector velocity = player.getVelocity();
        double horizontal = Math.hypot(velocity.getX(), velocity.getZ());
        if (horizontal > cap && horizontal > 1.0e-6) {
            double scale = cap / horizontal;
            player.setVelocity(new Vector(
                    velocity.getX() * scale, velocity.getY(), velocity.getZ() * scale));
        }
    }
}
