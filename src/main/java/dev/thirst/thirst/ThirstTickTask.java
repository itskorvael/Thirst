package dev.thirst.thirst;

import dev.thirst.config.ThirstConfig;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Once-per-second main-thread task that applies passive + environmental drain,
 * converts accumulated exhaustion into thirst loss, and deals starvation damage
 * at zero thirst. Also flushes each player's state back to the PDC.
 *
 * <p>Uses the Bukkit scheduler — Folia's regionised schedulers are not supported.
 */
public final class ThirstTickTask implements Runnable {

    /** One real second. Starvation timing is counted in whole seconds from here. */
    private static final long PERIOD_TICKS = 20L;

    private final JavaPlugin plugin;
    private final ThirstManager manager;
    private final Supplier<ThirstConfig> config;

    private @Nullable BukkitTask handle;

    public ThirstTickTask(JavaPlugin plugin, ThirstManager manager, Supplier<ThirstConfig> config) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;
    }

    public void start() {
        stop();
        this.handle = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this, PERIOD_TICKS, PERIOD_TICKS);
    }

    public void stop() {
        if (handle != null) {
            handle.cancel();
            handle = null;
        }
    }

    @Override
    public void run() {
        ThirstConfig cfg = config.get();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            try {
                tick(player, cfg);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING,
                        "Thirst tick failed for " + player.getName(), ex);
            }
        }
    }

    private void tick(Player player, ThirstConfig cfg) {
        if (!ThirstRules.affected(player)) {
            manager.flush(player);
            return;
        }

        ThirstConfig.Thirst t = cfg.thirst();
        PlayerThirstState state = manager.state(player);

        double added = ThirstMath.passiveExhaustionPerSecond(
                t.exhaustionThreshold(), t.baseDrainIntervalSeconds());
        if (player.getWorld().getEnvironment() == World.Environment.NETHER) {
            added += t.exhaustion().netherPerSecond();
        }
        state.setExhaustion(state.getExhaustion() + added);

        ThirstMath.DrainResult drain = ThirstMath.drain(
                state.getExhaustion(), t.exhaustionThreshold(), t.max());
        if (drain.pointsLost() > 0) {
            state.setExhaustion(drain.remainingExhaustion());
            manager.setLevel(player, state.getLevel() - drain.pointsLost());
        }

        applyStarvation(player, state, t);

        manager.flush(player);
    }

    private void applyStarvation(Player player, PlayerThirstState state, ThirstConfig.Thirst t) {
        boolean starving = state.getLevel() <= 0
                && t.starvationDamageEnabled()
                && player.getWorld().getDifficulty() != Difficulty.PEACEFUL;

        if (!starving) {
            state.setSecondsAtZeroThirst(0);
            return;
        }

        int elapsed = state.getSecondsAtZeroThirst() + 1;
        if (elapsed < t.starvationDamageIntervalSeconds()) {
            state.setSecondsAtZeroThirst(elapsed);
            return;
        }
        state.setSecondsAtZeroThirst(0);

        double damage = ThirstMath.starvationDamage(
                player.getHealth(), t.starvationDamageAmount(), t.starvationMinimumHealth());
        if (damage > 0.0) {
            player.damage(damage, DamageSource.builder(DamageType.STARVE).build());
        }
    }
}
