package io.github.itskorvael.thirst;

import io.github.itskorvael.thirst.command.ThirstCommand;
import io.github.itskorvael.thirst.command.WaterCommand;
import io.github.itskorvael.thirst.config.ThirstConfig;
import io.github.itskorvael.thirst.drinking.BucketListener;
import io.github.itskorvael.thirst.drinking.CauldronWaterTracker;
import io.github.itskorvael.thirst.drinking.DrinkingListener;
import io.github.itskorvael.thirst.drinking.PlacedWaterListener;
import io.github.itskorvael.thirst.drinking.WaterContainers;
import io.github.itskorvael.thirst.hud.ThirstHudTask;
import io.github.itskorvael.thirst.integration.ActionBarSegment;
import io.github.itskorvael.thirst.integration.PlaceholderApiSegment;
import io.github.itskorvael.thirst.listener.ThirstExhaustionListener;
import io.github.itskorvael.thirst.listener.ThirstLifecycleListener;
import io.github.itskorvael.thirst.listener.ThirstSprintListener;
import io.github.itskorvael.thirst.salmonella.SalmonellaListener;
import io.github.itskorvael.thirst.salmonella.SalmonellaService;
import io.github.itskorvael.thirst.thirst.CriticalSpeedModifier;
import io.github.itskorvael.thirst.thirst.ThirstKeys;
import io.github.itskorvael.thirst.thirst.ThirstManager;
import io.github.itskorvael.thirst.thirst.ThirstTickTask;
import io.github.itskorvael.thirst.water.WaterQualityService;
import io.github.itskorvael.thirst.water.WaterQualityStore;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Entry point for the Thirst plugin.
 *
 * <p>Wires together configuration, the thirst core ({@link ThirstManager} and
 * its drain task), the HUD ({@link ThirstHudTask}), per-chunk water quality
 * ({@link WaterQualityService} over a {@link WaterQualityStore} SQLite store),
 * bottle drinking, Salmonella ({@link SalmonellaService}) and the {@code /thirst}
 * and {@code /water} commands.
 */
@NullMarked
public final class ThirstPlugin extends JavaPlugin {

    private volatile ThirstConfig config;

    private @Nullable ThirstManager thirstManager;
    private @Nullable ThirstTickTask tickTask;
    private @Nullable ThirstHudTask hudTask;
    private @Nullable CriticalSpeedModifier speedModifier;
    private @Nullable ActionBarSegment externalSegment;
    private @Nullable WaterQualityStore waterStore;
    private @Nullable WaterQualityService waterQuality;
    private @Nullable SalmonellaService salmonellaService;
    private @Nullable BukkitTask placedWaterTask;

    /** Tracks the last-seen PlaceholderAPI availability so transitions are logged once. */
    private boolean placeholderApiActive;

    /** Warnings from the most recent config load, for {@code /thirst reload} feedback. */
    private volatile List<String> lastConfigWarnings = List.of();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPluginConfig();

        File databaseFile = new File(getDataFolder(), "water_quality.db");
        this.waterStore = new WaterQualityStore("jdbc:sqlite:" + databaseFile.getAbsolutePath(), getLogger());
        this.waterStore.initialize();
        this.waterQuality = new WaterQualityService(waterStore, this::config, getLogger()::warning);
        this.waterQuality.load();
        getLogger().info(() -> "Loaded water quality for " + waterQuality.cachedChunkCount()
                + " chunk(s) and " + waterQuality.trackedWaterCount() + " poured-water block(s).");

        ThirstKeys keys = new ThirstKeys(this);
        this.thirstManager = new ThirstManager(keys, this::config);

        // Pick up anyone already online (e.g. plugin reloaded on a live server).
        for (Player player : getServer().getOnlinePlayers()) {
            thirstManager.load(player);
        }

        this.hudTask = new ThirstHudTask(this, thirstManager, this::config, refreshExternalSegment());

        getServer().getPluginManager().registerEvents(
                new ThirstLifecycleListener(thirstManager, hudTask), this);
        this.speedModifier = new CriticalSpeedModifier(this);

        getServer().getPluginManager().registerEvents(
                new ThirstExhaustionListener(thirstManager, this::config), this);
        getServer().getPluginManager().registerEvents(
                new ThirstSprintListener(thirstManager, this::config, speedModifier), this);

        this.salmonellaService = new SalmonellaService(thirstManager, this::config);
        getServer().getPluginManager().registerEvents(
                new SalmonellaListener(salmonellaService, this::config), this);

        WaterContainers waterContainers = new WaterContainers(this, this::config);
        CauldronWaterTracker cauldronWaterTracker = new CauldronWaterTracker(waterContainers, this::config);
        getServer().getPluginManager().registerEvents(cauldronWaterTracker, this);
        getServer().getPluginManager().registerEvents(new DrinkingListener(
                thirstManager, waterQuality, waterContainers, cauldronWaterTracker, salmonellaService,
                this::config), this);
        getServer().getPluginManager().registerEvents(new BucketListener(
                waterContainers, waterQuality, cauldronWaterTracker), this);
        getServer().getPluginManager().registerEvents(new PlacedWaterListener(
                waterContainers, waterQuality), this);
        schedulePlacedWaterMaintenance();

        this.tickTask = new ThirstTickTask(this, thirstManager, this::config, salmonellaService::tick);
        this.tickTask.start();
        this.hudTask.start();

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(ThirstCommand.build(this), "Thirst admin / debug commands");
            event.registrar().register(WaterCommand.build(this), "Per-chunk water quality");
        });

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
        if (speedModifier != null) {
            // Transient modifiers vanish on relog anyway, but players staying
            // online through a disable would keep it stuck until then.
            for (Player player : getServer().getOnlinePlayers()) {
                speedModifier.clear(player);
            }
            speedModifier = null;
        }
        if (placedWaterTask != null) {
            placedWaterTask.cancel();
            placedWaterTask = null;
        }
        if (waterQuality != null) {
            waterQuality.flushPlaced();
        }
        if (waterStore != null) {
            waterStore.close();
            waterStore = null;
        }
        waterQuality = null;
        salmonellaService = null;
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
     * The per-chunk water quality cache / API. Never {@code null} while enabled.
     */
    public WaterQualityService water() {
        WaterQualityService service = this.waterQuality;
        if (service == null) {
            throw new IllegalStateException("WaterQualityService accessed before enable / after disable");
        }
        return service;
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
     * The Salmonella service. Never {@code null} while the plugin is enabled.
     */
    public SalmonellaService salmonella() {
        SalmonellaService service = this.salmonellaService;
        if (service == null) {
            throw new IllegalStateException("SalmonellaService accessed before enable / after disable");
        }
        return service;
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
     * (Re-)builds the {@code hud.external-segment} source. Checked at enable
     * <em>and</em> on every {@code /thirst reload}, so it self-heals if
     * PlaceholderAPI happened to load after Thirst. Logs only on a change.
     */
    private @Nullable ActionBarSegment refreshExternalSegment() {
        boolean present = getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        if (present != placeholderApiActive) {
            placeholderApiActive = present;
            getLogger().info(present
                    ? "PlaceholderAPI detected — hud.external-segment is available."
                    : "PlaceholderAPI is no longer present — hud.external-segment disabled.");
        }
        if (!present && config.hud().externalSegment().enabled()) {
            getLogger().warning("hud.external-segment is enabled but PlaceholderAPI is not "
                    + "loaded; run /thirst reload once it is, or restart.");
        }
        this.externalSegment = present ? new PlaceholderApiSegment(this::config) : null;
        return externalSegment;
    }

    /** The live external HUD segment source, or {@code null} when unavailable. */
    public @Nullable ActionBarSegment externalSegment() {
        return externalSegment;
    }

    /**
     * One-line description of the external HUD segment state, for
     * {@code /thirst reload} feedback.
     */
    public String externalSegmentStatus() {
        if (!config.hud().externalSegment().enabled()) {
            return "hud.external-segment: off";
        }
        return getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")
                ? "hud.external-segment: active (PlaceholderAPI ok)"
                : "hud.external-segment: enabled, but PlaceholderAPI is missing";
    }

    /**
     * Re-reads {@code config.yml}, swaps in a fresh validated snapshot,
     * re-checks PlaceholderAPI, and restarts the HUD task so a changed update
     * interval takes effect. Every validation warning is both logged and kept
     * for {@code /thirst reload} to echo back to the command sender — see
     * {@link #lastConfigWarnings()}.
     */
    public void loadPluginConfig() {
        reloadConfig();
        List<String> warnings = new ArrayList<>();
        this.config = ThirstConfig.load(getConfig(), message -> {
            warnings.add(message);
            getLogger().warning(message);
        });
        this.lastConfigWarnings = List.copyOf(warnings);
        if (hudTask != null) {
            hudTask.setExternalSegment(refreshExternalSegment());
            hudTask.restart();
        }
        if (waterQuality != null) {
            schedulePlacedWaterMaintenance();
        }
    }

    /** (Re-)starts the periodic placed-water upkeep at the configured interval. */
    private void schedulePlacedWaterMaintenance() {
        WaterQualityService service = this.waterQuality;
        if (service == null) {
            return;
        }
        if (placedWaterTask != null) {
            placedWaterTask.cancel();
        }
        long periodTicks = config.water().placedWaterPruneSeconds() * 20L;
        this.placedWaterTask = getServer().getScheduler()
                .runTaskTimer(this, service::maintainPlaced, periodTicks, periodTicks);
    }

    /**
     * Validation warnings from the most recent {@link #loadPluginConfig()} call
     * (enable or {@code /thirst reload}), for immediate in-game feedback instead
     * of requiring a console check. Empty when the config was clean.
     */
    public List<String> lastConfigWarnings() {
        return lastConfigWarnings;
    }
}
