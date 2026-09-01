package dev.thirst;

import dev.thirst.command.ThirstCommand;
import dev.thirst.config.ThirstConfig;
import dev.thirst.hud.ThirstHudTask;
import dev.thirst.integration.ActionBarSegment;
import dev.thirst.integration.PlaceholderApiSegment;
import dev.thirst.listener.ThirstExhaustionListener;
import dev.thirst.listener.ThirstLifecycleListener;
import dev.thirst.listener.ThirstSprintListener;
import dev.thirst.thirst.ThirstKeys;
import dev.thirst.thirst.ThirstManager;
import dev.thirst.thirst.ThirstTickTask;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Entry point for the Thirst plugin.
 *
 * <p>Wired up so far:
 * <ol>
 *   <li>Phase 1 — configuration loading + validation</li>
 *   <li>Phase 2 — thirst core: {@link ThirstManager}, exhaustion listeners,
 *       the per-second drain / starvation task, and a minimal {@code /thirst}
 *       debug command</li>
 *   <li>Phase 3 — the HUD ({@link ThirstHudTask}): action-bar text, or a boss
 *       bar in debug/fallback mode</li>
 * </ol>
 * Still to come: water quality (4), drinking (5), Salmonella (6),
 * full commands (7).
 */
@NullMarked
public final class ThirstPlugin extends JavaPlugin {

    private volatile ThirstConfig config;

    private @Nullable ThirstManager thirstManager;
    private @Nullable ThirstTickTask tickTask;
    private @Nullable ThirstHudTask hudTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPluginConfig();

        ThirstKeys keys = new ThirstKeys(this);
        this.thirstManager = new ThirstManager(keys, this::config);

        // Pick up anyone already online (e.g. plugin reloaded on a live server).
        for (Player player : getServer().getOnlinePlayers()) {
            thirstManager.load(player);
        }

        this.hudTask = new ThirstHudTask(this, thirstManager, this::config, resolveExternalSegment());

        getServer().getPluginManager().registerEvents(
                new ThirstLifecycleListener(thirstManager, hudTask), this);
        getServer().getPluginManager().registerEvents(
                new ThirstExhaustionListener(thirstManager, this::config), this);
        getServer().getPluginManager().registerEvents(
                new ThirstSprintListener(thirstManager, this::config), this);

        this.tickTask = new ThirstTickTask(this, thirstManager, this::config);
        this.tickTask.start();
        this.hudTask.start();

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(ThirstCommand.build(this), "Thirst admin / debug commands"));

        getLogger().info(() -> "Thirst v" + getPluginMeta().getVersion()
                + " enabled — HUD mode " + config.hudMode()
                + ", default water quality " + config.defaultWaterQuality() + ".");
    }

    @Override
    public void onDisable() {
        if (hudTask != null) {
            hudTask.stop();
            hudTask = null;
        }
        if (tickTask != null) {
            tickTask.stop();
            tickTask = null;
        }
        if (thirstManager != null) {
            for (Player player : getServer().getOnlinePlayers()) {
                thirstManager.unload(player);
            }
            thirstManager = null;
        }
        getLogger().info("Thirst disabled.");
    }

    /**
     * The live configuration snapshot. Safe from any thread; re-fetch rather than
     * cache so {@code /thirst reload} takes effect.
     */
    public ThirstConfig config() {
        return config;
    }

    /**
     * The thirst state manager. Never {@code null} while the plugin is enabled.
     */
    public ThirstManager thirst() {
        ThirstManager manager = this.thirstManager;
        if (manager == null) {
            throw new IllegalStateException("ThirstManager accessed before enable / after disable");
        }
        return manager;
    }

    /**
     * The HUD task. Other plugins can use it to cooperatively yield the action
     * bar (see {@link ThirstHudTask#requestActionBarYield}).
     */
    public ThirstHudTask hud() {
        ThirstHudTask task = this.hudTask;
        if (task == null) {
            throw new IllegalStateException("ThirstHudTask accessed before enable / after disable");
        }
        return task;
    }

    /**
     * Builds the {@code hud.external-segment} source if PlaceholderAPI is
     * present; otherwise returns {@code null} (and warns if the feature was
     * switched on without it).
     */
    private @Nullable ActionBarSegment resolveExternalSegment() {
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            getLogger().info("PlaceholderAPI detected — hud.external-segment is available.");
            return new PlaceholderApiSegment(this::config);
        }
        if (config.hud().externalSegment().enabled()) {
            getLogger().warning("hud.external-segment is enabled but PlaceholderAPI is not "
                    + "installed; the extra segment will not be shown.");
        }
        return null;
    }

    /**
     * Re-reads {@code config.yml}, swaps in a fresh validated snapshot, and
     * restarts the HUD task so a changed update interval takes effect.
     */
    public void loadPluginConfig() {
        reloadConfig();
        this.config = ThirstConfig.load(getConfig(), message -> getLogger().warning(message));
        if (hudTask != null) {
            hudTask.restart();
        }
    }
}
