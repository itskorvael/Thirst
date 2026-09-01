package dev.thirst.thirst;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * The {@link NamespacedKey}s used to persist per-player thirst state in the
 * player's {@link org.bukkit.persistence.PersistentDataContainer}.
 *
 * <p>Key names match the data-model spec so stored values are recognisable with
 * external tooling.
 */
public final class ThirstKeys {

    private final NamespacedKey level;
    private final NamespacedKey exhaustion;
    private final NamespacedKey salmonellaStacks;
    private final NamespacedKey salmonellaTicksRemaining;
    private final NamespacedKey lastDrinkTimestamp;

    public ThirstKeys(Plugin plugin) {
        this.level = new NamespacedKey(plugin, "thirst_level");
        this.exhaustion = new NamespacedKey(plugin, "thirst_exhaustion");
        this.salmonellaStacks = new NamespacedKey(plugin, "salmonella_stacks");
        this.salmonellaTicksRemaining = new NamespacedKey(plugin, "salmonella_ticks_remaining");
        this.lastDrinkTimestamp = new NamespacedKey(plugin, "last_drink_timestamp");
    }

    /** {@code int} 0..max — current thirst, vanilla-hunger scale. */
    public NamespacedKey level() {
        return level;
    }

    /** {@code double} — accumulated exhaustion (stored as PDC double; the spec's "float"). */
    public NamespacedKey exhaustion() {
        return exhaustion;
    }

    /** {@code int} 0..5 — Salmonella severity (Phase 6). */
    public NamespacedKey salmonellaStacks() {
        return salmonellaStacks;
    }

    /** {@code int} — server ticks left on the current Salmonella effect (Phase 6). */
    public NamespacedKey salmonellaTicksRemaining() {
        return salmonellaTicksRemaining;
    }

    /** {@code long} — {@code System.currentTimeMillis()} of the last drink (Phase 5/6). */
    public NamespacedKey lastDrinkTimestamp() {
        return lastDrinkTimestamp;
    }
}
