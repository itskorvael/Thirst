package dev.thirst;

import dev.thirst.config.ThirstConfig;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NullMarked;

/**
 * Entry point for the Thirst plugin.
 *
 * <p>Phase 1 wires up only configuration loading and the plugin lifecycle.
 * Managers, listeners and scheduled tasks are added in later phases:
 * <ol>
 *   <li>Phase 2 — thirst core ({@code ThirstManager}, drain + exhaustion task)</li>
 *   <li>Phase 3 — action-bar HUD</li>
 *   <li>Phase 4 — per-chunk water quality (SQLite + cache)</li>
 *   <li>Phase 5 — bottle-based drinking</li>
 *   <li>Phase 6 — Salmonella</li>
 *   <li>Phase 7 — commands, permissions, {@code /thirst reload}</li>
 * </ol>
 */
@NullMarked
public final class ThirstPlugin extends JavaPlugin {

    /**
     * Current configuration snapshot. Replaced wholesale on reload; reads are
     * lock-free. Never {@code null} once {@link #onEnable()} has run.
     */
    private volatile ThirstConfig config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPluginConfig();

        getLogger().info(() -> "Thirst v" + getPluginMeta().getVersion()
                + " enabled — HUD mode " + config.hudMode()
                + ", default water quality " + config.defaultWaterQuality() + ".");
    }

    @Override
    public void onDisable() {
        getLogger().info("Thirst disabled.");
    }

    /**
     * The live configuration snapshot. Safe to call from any thread; callers
     * should re-fetch rather than cache the reference so {@code /thirst reload}
     * takes effect.
     *
     * @return the current, validated configuration
     */
    public ThirstConfig config() {
        return config;
    }

    /**
     * Re-reads {@code config.yml} from disk and swaps in a fresh validated
     * snapshot. Invoked once on enable and again by {@code /thirst reload}.
     */
    public void loadPluginConfig() {
        reloadConfig();
        this.config = ThirstConfig.load(getConfig(), message -> getLogger().warning(message));
    }
}
