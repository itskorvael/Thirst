package dev.thirst.salmonella;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * The per-stack potion effects, following the spec's severity table:
 *
 * <pre>
 *   1  Nausea I
 *   2  Nausea I  + Slowness I
 *   3  Nausea II + Weakness I
 *   4  Nausea II + Weakness II            (+ periodic damage)
 *   5  Nausea II + Slowness I + Weakness II (+ lethal damage)
 * </pre>
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
        int nausea = stack >= 3 ? 1 : 0;
        return switch (Math.max(1, Math.min(stack, 5))) {
            case 1 -> List.of(effect(PotionEffectType.NAUSEA, nausea, durationTicks));
            case 2 -> List.of(
                    effect(PotionEffectType.NAUSEA, nausea, durationTicks),
                    effect(PotionEffectType.SLOWNESS, 0, durationTicks));
            case 3 -> List.of(
                    effect(PotionEffectType.NAUSEA, nausea, durationTicks),
                    effect(PotionEffectType.WEAKNESS, 0, durationTicks));
            case 4 -> List.of(
                    effect(PotionEffectType.NAUSEA, nausea, durationTicks),
                    effect(PotionEffectType.WEAKNESS, 1, durationTicks));
            default -> List.of(
                    effect(PotionEffectType.NAUSEA, nausea, durationTicks),
                    effect(PotionEffectType.SLOWNESS, 0, durationTicks),
                    effect(PotionEffectType.WEAKNESS, 1, durationTicks));
        };
    }

    private static PotionEffect effect(PotionEffectType type, int amplifier, int durationTicks) {
        return new PotionEffect(type, durationTicks, amplifier, true, false, true);
    }
}
