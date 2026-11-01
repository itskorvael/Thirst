package io.github.itskorvael.thirst.listener;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import io.github.itskorvael.thirst.config.ThirstConfig;
import io.github.itskorvael.thirst.thirst.ThirstManager;
import io.github.itskorvael.thirst.thirst.ThirstRules;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.function.Supplier;

/**
 * Vanilla-style exhaustion sources: movement (sprint / walk / swim, per metre),
 * jumping, block breaking and combat (dealing and taking damage). Each simply
 * feeds {@link ThirstManager#addExhaustion}; the tick task turns accumulated
 * exhaustion into thirst loss.
 *
 * <p>All of these events fire on the main thread, matching {@code ThirstManager}'s
 * threading requirement.
 */
public final class ThirstExhaustionListener implements Listener {

    /** Ignore single-event jumps larger than this (teleport, pearl, /tp). */
    private static final double MAX_TRACKED_MOVE = 8.0;

    private final ThirstManager manager;
    private final Supplier<ThirstConfig> config;

    public ThirstExhaustionListener(ThirstManager manager, Supplier<ThirstConfig> config) {
        this.manager = manager;
        this.config = config;
    }

    private ThirstConfig.Exhaustion exhaustion() {
        return config.get().thirst().exhaustion();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!ThirstRules.affected(player) || player.isGliding()) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return;
        }

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt((dx * dx) + (dz * dz));
        if (horizontal < 1.0e-4 || horizontal > MAX_TRACKED_MOVE) {
            return;
        }

        ThirstConfig.Exhaustion ex = exhaustion();
        double perMetre;
        if (player.isSwimming()) {
            perMetre = ex.perSwimMeter();
        } else if (player.isSprinting()) {
            perMetre = ex.perSprintMeter();
        } else {
            perMetre = ex.perWalkMeter();
        }
        if (perMetre > 0.0) {
            manager.addExhaustion(player, horizontal * perMetre);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (!ThirstRules.affected(player)) {
            return;
        }
        ThirstConfig.Exhaustion ex = exhaustion();
        manager.addExhaustion(player, player.isSprinting() ? ex.sprintJump() : ex.jump());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (ThirstRules.affected(player)) {
            manager.addExhaustion(player, exhaustion().breakBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        ThirstConfig.Exhaustion ex = exhaustion();

        // Attacker: melee hit landed by a player.
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player attacker
                && ThirstRules.affected(attacker)) {
            manager.addExhaustion(attacker, ex.attack());
        }

        // Victim: a player taking damage (but not from our own starvation tick).
        if (event.getEntity() instanceof Player victim
                && ThirstRules.affected(victim)
                && event.getCause() != EntityDamageEvent.DamageCause.STARVATION) {
            manager.addExhaustion(victim, ex.takeDamage());
        }
    }
}
