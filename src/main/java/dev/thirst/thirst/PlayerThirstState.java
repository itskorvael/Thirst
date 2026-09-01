package dev.thirst.thirst;

import org.bukkit.WorldBorder;
import org.jspecify.annotations.Nullable;

/**
 * Mutable in-memory copy of a player's thirst state while they are online.
 *
 * <p>Owned by {@link ThirstManager}; only touched on the server main thread.
 * Persistent fields set a {@link #isDirty() dirty} flag so the tick task can
 * skip writing unchanged players back to the PDC. Transient fields (the
 * starvation timer) are runtime-only and never persisted.
 */
public final class PlayerThirstState {

    // ── Persistent ──────────────────────────────────────────────────────────
    private int level;
    private double exhaustion;
    private int salmonellaStacks;
    private int salmonellaTicksRemaining;
    private long lastDrinkTimestamp;

    // ── Transient (runtime only) ────────────────────────────────────────────
    private int secondsAtZeroThirst;
    private boolean vignetteActive;
    private @Nullable WorldBorder preVignetteBorder;

    private boolean dirty;

    public int getLevel() {
        return level;
    }

    public double getExhaustion() {
        return exhaustion;
    }

    public int getSalmonellaStacks() {
        return salmonellaStacks;
    }

    public int getSalmonellaTicksRemaining() {
        return salmonellaTicksRemaining;
    }

    public long getLastDrinkTimestamp() {
        return lastDrinkTimestamp;
    }

    public int getSecondsAtZeroThirst() {
        return secondsAtZeroThirst;
    }

    public boolean isDirty() {
        return dirty;
    }

    public boolean isVignetteActive() {
        return vignetteActive;
    }

    public @Nullable WorldBorder getPreVignetteBorder() {
        return preVignetteBorder;
    }

    void setLevel(int level) {
        if (this.level != level) {
            this.level = level;
            this.dirty = true;
        }
    }

    void setExhaustion(double exhaustion) {
        double clamped = Math.max(0.0, exhaustion);
        if (Double.compare(this.exhaustion, clamped) != 0) {
            this.exhaustion = clamped;
            this.dirty = true;
        }
    }

    void setSalmonella(int stacks, int ticksRemaining) {
        int s = Math.max(0, stacks);
        int t = Math.max(0, ticksRemaining);
        if (this.salmonellaStacks != s || this.salmonellaTicksRemaining != t) {
            this.salmonellaStacks = s;
            this.salmonellaTicksRemaining = t;
            this.dirty = true;
        }
    }

    void setLastDrinkTimestamp(long lastDrinkTimestamp) {
        if (this.lastDrinkTimestamp != lastDrinkTimestamp) {
            this.lastDrinkTimestamp = lastDrinkTimestamp;
            this.dirty = true;
        }
    }

    void setSecondsAtZeroThirst(int secondsAtZeroThirst) {
        this.secondsAtZeroThirst = Math.max(0, secondsAtZeroThirst);
    }

    /** Marks the vignette on/off and, when turning on, records the border to restore later. */
    void setVignetteActive(boolean active, @Nullable WorldBorder borderToRestore) {
        this.vignetteActive = active;
        this.preVignetteBorder = active ? borderToRestore : null;
    }

    /** Marks the state as matching what is stored in the PDC. */
    void markClean() {
        this.dirty = false;
    }
}
