package dev.thirst.salmonella;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * The per-stack potion effects, following the spec's severity table:
 *
 * <pre>
 *   1  Nausea I
 *   2  Nausea I + Slowness I
 *   3  Nausea I + Weakness I
 *   4  Nausea I + Slowness I + Weakness I  (+ periodic damage)
 *   5  Nausea II + Slowness I + Weakness II (+ lethal damage)
 * </pre>
 *
 * <p>Only the worst stack amplifies to level II — stacks 1-4 add more level-I
 * effects rather than stronger ones, so the illness reads as "increasingly
 * inconvenient" before it becomes "severe" right at the top.
 *
 * <p>The effect <em>structure</em> is code-defined (it maps a fixed illness
 * progression, not tuning numbers); the durations, decay, damage and cure are
 * all in {@code config.yml}.
 */
final class SalmonellaEffects {

    static final List<PotionEffectType> ALL_TYPES = List.of(
            PotionEffectType.NAUSEA, PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS);

    private SalmonellaEffects() {
    }

    /**
     * Builds the effects for {@code stack} (clamped to 1..5), each with the given
     * duration. Ambient, no particles, icon shown (so it reads as the vanilla
     * Nausea indicator).
     */
    static List<PotionEffect> forStack(int stack, int durationTicks) {
        int clamped = Math.max(1, Math.min(stack, 5));
        int amplify = clamped >= 5 ? 1 : 0; // only the top stack reaches level II
        return switch (clamped) {
            case 1 -> List.of(effect(PotionEffectType.NAUSEA, 0, durationTicks));
            case 2 -> List.of(
                    effect(PotionEffectType.NAUSEA, 0, durationTicks),
                    effect(PotionEffectType.SLOWNESS, 0, durationTicks));
            case 3 -> List.of(
                    effect(PotionEffectType.NAUSEA, 0, durationTicks),
                    effect(PotionEffectType.WEAKNESS, 0, durationTicks));
            case 4 -> List.of(
                    effect(PotionEffectType.NAUSEA, 0, durationTicks),
                    effect(PotionEffectType.SLOWNESS, 0, durationTicks),
                    effect(PotionEffectType.WEAKNESS, 0, durationTicks));
            default -> List.of(
                    effect(PotionEffectType.NAUSEA, amplify, durationTicks),
                    effect(PotionEffectType.SLOWNESS, 0, durationTicks),
                    effect(PotionEffectType.WEAKNESS, amplify, durationTicks));
        };
    }

    private static PotionEffect effect(PotionEffectType type, int amplifier, int durationTicks) {
        return new PotionEffect(type, durationTicks, amplifier, true, false, true);
    }
}
