package dev.thirst.thirst;

import dev.thirst.config.ThirstConfig;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Single source of truth for per-player thirst state.
 *
 * <p>State lives in the player's {@link PersistentDataContainer} (so it survives
 * restarts and follows the player between servers) and is mirrored in a small
 * in-memory cache while the player is online. Level changes are written through
 * to the PDC immediately; frequently-updated exhaustion is only flushed by the
 * tick task (see {@link #flush(Player)}), which keeps {@code PlayerMoveEvent}
 * cheap.
 *
 * <p><b>Threading:</b> all methods must be called from the server main thread.
 */
public final class ThirstManager {

    private final ThirstKeys keys;
    private final Supplier<ThirstConfig> config;
    private final Map<UUID, PlayerThirstState> states = new HashMap<>();

    public ThirstManager(ThirstKeys keys, Supplier<ThirstConfig> config) {
        this.keys = keys;
        this.config = config;
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    /** Loads (or initialises) state for a joining player. */
    public PlayerThirstState load(Player player) {
        PlayerThirstState state = readFromPdc(player);
        states.put(player.getUniqueId(), state);
        return state;
    }

    /** Flushes and drops state for a leaving player. */
    public void unload(Player player) {
        PlayerThirstState state = states.remove(player.getUniqueId());
        if (state != null) {
            writeToPdc(player, state);
        }
    }

    /** Returns the cached state, loading it from the PDC if the player was not tracked yet. */
    public PlayerThirstState state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), id -> readFromPdc(player));
    }

    // ── Reads ───────────────────────────────────────────────────────────────

    public int getLevel(Player player) {
        return state(player).getLevel();
    }

    public double getExhaustion(Player player) {
        return state(player).getExhaustion();
    }

    public int getSalmonellaStacks(Player player) {
        return state(player).getSalmonellaStacks();
    }

    public int getSalmonellaTicksRemaining(Player player) {
        return state(player).getSalmonellaTicksRemaining();
    }

    public long getLastDrinkTimestamp(Player player) {
        return state(player).getLastDrinkTimestamp();
    }

    /** Current thirst as a fraction of the configured maximum, in {@code [0, 1]}. */
    public double getFraction(Player player) {
        int max = config.get().thirst().max();
        return max <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, (double) getLevel(player) / max));
    }

    // ── Mutations ───────────────────────────────────────────────────────────

    /** Sets thirst to {@code level}, clamped to {@code [0, max]}, and persists immediately. */
    public void setLevel(Player player, int level) {
        PlayerThirstState state = state(player);
        state.setLevel(ThirstMath.clampLevel(level, config.get().thirst().max()));
        writeToPdc(player, state);
    }

    /** Adds {@code delta} (may be negative) to thirst, clamped and persisted. */
    public void addThirst(Player player, int delta) {
        if (delta == 0) {
            return;
        }
        setLevel(player, getLevel(player) + delta);
    }

    /**
     * Adds exhaustion. Not persisted here — the tick task flushes it once per
     * second, so this is safe to call from high-frequency events.
     */
    public void addExhaustion(Player player, double amount) {
        if (amount <= 0.0 || !Double.isFinite(amount)) {
            return;
        }
        PlayerThirstState state = state(player);
        state.setExhaustion(state.getExhaustion() + amount);
    }

    public void setSalmonella(Player player, int stacks, int ticksRemaining) {
        PlayerThirstState state = state(player);
        state.setSalmonella(stacks, ticksRemaining);
        writeToPdc(player, state);
    }

    public void setLastDrinkTimestamp(Player player, long timestampMillis) {
        PlayerThirstState state = state(player);
        state.setLastDrinkTimestamp(timestampMillis);
        writeToPdc(player, state);
    }

    /** Resets thirst to full and clears exhaustion / illness — used on respawn. */
    public void resetToFull(Player player) {
        PlayerThirstState state = state(player);
        state.setLevel(config.get().thirst().max());
        state.setExhaustion(0.0);
        state.setSalmonella(0, 0);
        state.setSecondsAtZeroThirst(0);
        writeToPdc(player, state);
    }

    // ── Persistence plumbing ────────────────────────────────────────────────

    /** Writes the player's state to the PDC if it has unsaved changes. */
    public void flush(Player player) {
        PlayerThirstState state = states.get(player.getUniqueId());
        if (state != null && state.isDirty()) {
            writeToPdc(player, state);
        }
    }

    private PlayerThirstState readFromPdc(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int max = config.get().thirst().max();

        PlayerThirstState state = new PlayerThirstState();
        state.setLevel(ThirstMath.clampLevel(
                pdc.getOrDefault(keys.level(), PersistentDataType.INTEGER, max), max));
        state.setExhaustion(orZero(pdc.get(keys.exhaustion(), PersistentDataType.DOUBLE)));
        state.setSalmonella(
                pdc.getOrDefault(keys.salmonellaStacks(), PersistentDataType.INTEGER, 0),
                pdc.getOrDefault(keys.salmonellaTicksRemaining(), PersistentDataType.INTEGER, 0));
        state.setLastDrinkTimestamp(
                pdc.getOrDefault(keys.lastDrinkTimestamp(), PersistentDataType.LONG, 0L));
        state.markClean();
        return state;
    }

    private void writeToPdc(Player player, PlayerThirstState state) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(keys.level(), PersistentDataType.INTEGER, state.getLevel());
        pdc.set(keys.exhaustion(), PersistentDataType.DOUBLE, state.getExhaustion());
        pdc.set(keys.salmonellaStacks(), PersistentDataType.INTEGER, state.getSalmonellaStacks());
        pdc.set(keys.salmonellaTicksRemaining(), PersistentDataType.INTEGER, state.getSalmonellaTicksRemaining());
        pdc.set(keys.lastDrinkTimestamp(), PersistentDataType.LONG, state.getLastDrinkTimestamp());
        state.markClean();
    }

    private static double orZero(@Nullable Double value) {
        return value == null || !Double.isFinite(value) ? 0.0 : value;
    }
}
