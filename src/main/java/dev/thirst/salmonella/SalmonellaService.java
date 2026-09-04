package dev.thirst.salmonella;

import dev.thirst.config.ThirstConfig;
import dev.thirst.thirst.ThirstManager;
import dev.thirst.thirst.ThirstMath;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Owns the Salmonella illness: stack count + decay timer live in the player PDC
 * (written by {@link ThirstManager}); this service applies the per-stack effects,
 * decays a stack every {@code decay-interval-seconds}, deals the severe-stack
 * damage and clears everything on a milk cure.
 *
 * <p>{@link #tick(Player)} is driven once per second by
 * {@code dev.thirst.thirst.ThirstTickTask} — no task of its own.
 */
public final class SalmonellaService {

    private static final int TICKS_PER_SECOND = 20;

    private final ThirstManager manager;
    private final Supplier<ThirstConfig> config;

    /** Player UUID -> seconds since the last severe-stack damage. Main thread only. */
    private final Map<UUID, Integer> secondsSinceDamage = new ConcurrentHashMap<>();

    public SalmonellaService(ThirstManager manager, Supplier<ThirstConfig> config) {
        this.manager = manager;
        this.config = config;
    }

    // ── Infection / cure ────────────────────────────────────────────────────

    /** Add a stack (capped) and refresh the decay timer. Called on a failed dirty-water roll. */
    public void infect(Player player) {
        ThirstConfig.Salmonella cfg = config.get().salmonella();
        int stack = Math.min(manager.getSalmonellaStacks(player) + 1, cfg.maxStacks());
        manager.setSalmonella(player, stack, cfg.decayIntervalSeconds() * TICKS_PER_SECOND);
        applyEffects(player, stack, cfg);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.4f, 0.6f);
    }

    /** Whether {@code player} currently has any Salmonella stacks. */
    public boolean isInfected(Player player) {
        return manager.getSalmonellaStacks(player) > 0;
    }

    /** Feedback for a milk-cure roll that missed — still infected. */
    public void missedCure(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
    }

    /** Remove {@code stacks} stacks immediately (milk). Clears effects if it reaches zero. */
    public void cure(Player player, int stacks) {
        int current = manager.getSalmonellaStacks(player);
        if (current <= 0) {
            return;
        }
        int next = Math.max(0, current - Math.max(1, stacks));
        if (next == 0) {
            clear(player);
        } else {
            ThirstConfig.Salmonella cfg = config.get().salmonella();
            manager.setSalmonella(player, next, cfg.decayIntervalSeconds() * TICKS_PER_SECOND);
            applyEffects(player, next, cfg);
        }
    }

    /** Forget the per-player damage timer (call on quit). */
    public void forget(UUID playerId) {
        secondsSinceDamage.remove(playerId);
    }

    // ── Per-second tick ─────────────────────────────────────────────────────

    public void tick(Player player) {
        int stacks = manager.getSalmonellaStacks(player);
        if (stacks <= 0) {
            secondsSinceDamage.remove(player.getUniqueId());
            return;
        }
        ThirstConfig.Salmonella cfg = config.get().salmonella();

        DecayStep step = decay(stacks, manager.getSalmonellaTicksRemaining(player),
                TICKS_PER_SECOND, cfg.decayIntervalSeconds() * TICKS_PER_SECOND);
        if (step.cured()) {
            clear(player);
            return;
        }
        if (step.changed()) {
            manager.setSalmonella(player, step.stacks(), step.ticksRemaining());
        } else {
            manager.setSalmonella(player, stacks, step.ticksRemaining());
        }
        stacks = step.stacks();

        applyEffects(player, stacks, cfg);

        if (stacks >= cfg.vomitFromStack() && ThreadLocalRandom.current().nextInt(4) == 0) {
            vomit(player);
        }
        applyDamage(player, stacks, cfg);
    }

    /**
     * Pure decay arithmetic: subtract {@code elapsedTicks} from the timer; when
     * it runs out, drop one stack and reset the timer (or report a cure).
     */
    static DecayStep decay(int stacks, int ticksRemaining, int elapsedTicks, int decayResetTicks) {
        if (stacks <= 0) {
            return new DecayStep(0, 0, false, true);
        }
        int remaining = ticksRemaining - elapsedTicks;
        if (remaining > 0) {
            return new DecayStep(stacks, remaining, false, false);
        }
        int next = stacks - 1;
        if (next <= 0) {
            return new DecayStep(0, 0, true, true);
        }
        return new DecayStep(next, decayResetTicks, true, false);
    }

    record DecayStep(int stacks, int ticksRemaining, boolean changed, boolean cured) {
    }

    // ── Effects / damage / cosmetics ────────────────────────────────────────

    private void applyEffects(Player player, int stack, ThirstConfig.Salmonella cfg) {
        int durationTicks = cfg.durationSecondsForStack(stack) * TICKS_PER_SECOND;
        for (PotionEffect effect : SalmonellaEffects.forStack(stack, durationTicks, cfg.nauseaFromStack())) {
            player.addPotionEffect(effect);
        }
    }

    private void clear(Player player) {
        manager.setSalmonella(player, 0, 0);
        secondsSinceDamage.remove(player.getUniqueId());
        for (PotionEffectType type : SalmonellaEffects.ALL_TYPES) {
            player.removePotionEffect(type);
        }
    }

    private void applyDamage(Player player, int stacks, ThirstConfig.Salmonella cfg) {
        if (stacks < cfg.damageFromStack() || cfg.damageAmount() <= 0.0) {
            secondsSinceDamage.remove(player.getUniqueId());
            return;
        }
        int elapsed = secondsSinceDamage.merge(player.getUniqueId(), 1, Integer::sum);
        if (elapsed < cfg.damageIntervalSeconds()) {
            return;
        }
        secondsSinceDamage.put(player.getUniqueId(), 0);

        double floor = stacks >= cfg.lethalFromStack() ? 0.0 : 1.0;
        double damage = ThirstMath.starvationDamage(player.getHealth(), cfg.damageAmount(), floor);
        if (damage > 0.0) {
            player.damage(damage, DamageSource.builder(DamageType.MAGIC).build());
        }
    }

    private void vomit(Player player) {
        Location mouth = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(0.4));
        player.getWorld().spawnParticle(Particle.ITEM_SLIME, mouth, 10, 0.12, 0.1, 0.12, 0.03);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.8f, 0.5f);
    }
}
