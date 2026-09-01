package dev.thirst.hud;

import dev.thirst.config.HudMode;
import dev.thirst.config.ThirstConfig;
import dev.thirst.integration.ActionBarSegment;
import dev.thirst.thirst.ThirstManager;
import dev.thirst.thirst.ThirstRules;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Single repeating task that refreshes the thirst HUD for every online player.
 *
 * <p>One shared task rather than one-per-player: simpler, cheaper, and the
 * effect (clean start/stop on join/quit) is the same — joiners are picked up on
 * the next tick, leavers are cleaned via {@link #handleQuit(Player)}.
 *
 * <p>Interval comes from {@code hud.update-interval-ticks}; call {@link #restart()}
 * after a config reload to apply a changed interval.
 */
public final class ThirstHudTask implements Runnable {

    private final JavaPlugin plugin;
    private final ThirstManager manager;
    private final Supplier<ThirstConfig> config;

    private final ActionBarHudRenderer actionBar;
    private final BossBarHudRenderer bossBar = new BossBarHudRenderer();

    private @Nullable BukkitTask handle;
    private boolean warnedResourcePackFallback;

    /**
     * @param externalSegment source for {@code hud.external-segment}, or {@code null}
     *                        when PlaceholderAPI is absent
     */
    public ThirstHudTask(JavaPlugin plugin, ThirstManager manager, Supplier<ThirstConfig> config,
                         @Nullable ActionBarSegment externalSegment) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;
        this.actionBar = new ActionBarHudRenderer(externalSegment);
    }

    public void start() {
        stop();
        long period = Math.max(1L, config.get().hud().updateIntervalTicks());
        this.handle = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this, period, period);
    }

    /** Restart to pick up a changed {@code hud.update-interval-ticks}. */
    public void restart() {
        start();
    }

    public void stop() {
        if (handle != null) {
            handle.cancel();
            handle = null;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            actionBar.remove(player);
            bossBar.remove(player);
        }
    }

    /** Clean up per-player HUD resources (boss bar, yield entry) — call on quit. */
    public void handleQuit(Player player) {
        actionBar.remove(player);
        bossBar.remove(player);
    }

    /**
     * Ask Thirst to withhold its own action-bar text from {@code player} for
     * {@code millis}. No effect in BOSSBAR mode or when
     * {@code actionbar-overwrite-others} is true. See the class-level note on
     * why this is cooperative rather than automatic.
     */
    public void requestActionBarYield(Player player, long millis) {
        actionBar.requestYield(player.getUniqueId(), millis);
    }

    /** As above, using {@code hud.yield-window-millis} as the duration. */
    public void requestActionBarYield(Player player) {
        actionBar.requestYield(player.getUniqueId(), config.get().hud().yieldWindowMillis());
    }

    /** Swap the {@code hud.external-segment} source (called when {@code /thirst reload} re-checks PlaceholderAPI). */
    public void setExternalSegment(@Nullable ActionBarSegment segment) {
        actionBar.setSegment(segment);
    }

    @Override
    public void run() {
        ThirstConfig cfg = config.get();
        boolean useBossBar = effectiveMode(cfg.hudMode()) == HudMode.BOSSBAR;
        HudRenderer active = useBossBar ? bossBar : actionBar;
        HudRenderer other = useBossBar ? actionBar : bossBar;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            other.remove(player);
            if (ThirstRules.affected(player)) {
                active.update(player, manager.getLevel(player), cfg);
            } else {
                active.remove(player);
            }
        }
    }

    private HudMode effectiveMode(HudMode configured) {
        if (configured == HudMode.ACTIONBAR_RESOURCEPACK) {
            if (!warnedResourcePackFallback) {
                plugin.getLogger().warning(
                        "hud-mode ACTIONBAR_RESOURCEPACK is not implemented yet; using ACTIONBAR_TEXT.");
                warnedResourcePackFallback = true;
            }
            return HudMode.ACTIONBAR_TEXT;
        }
        return configured;
    }
}
