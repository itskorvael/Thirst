package dev.thirst.salmonella;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * The per-stack potion effects, following the spec's severity table:
 *
 * <pre>
 *   1  Slowness I
 *   2  Slowness I + Weakness I
 *   3  Slowness I + Weakness I + Nausea I     (Nausea from nausea-from-stack)
 *   4  Slowness I + Weakness I + Nausea I     (+ periodic damage)
 *   5  Slowness I + Weakness II + Nausea II   (+ lethal damage)
 * </pre>
 *
 * <p>Only the worst stack amplifies to level II — stacks 1-4 add more level-I
 * effects rather than stronger ones, so the illness reads as "increasingly
 * inconvenient" before it becomes "severe" right at the top. Note that
 * vanilla's Nausea amplifier only changes duration handling, not the
 * screen-warp intensity — level I and II look identical — so the only real
 * dial on "how much shake" is <em>when it starts</em>
 * ({@code salmonella.nausea-from-stack}), not its level.
 *
 * <p>The effect <em>structure</em> is code-defined (it maps a fixed illness
 * progression, not tuning numbers); the durations, decay, damage, cure and the
 * Nausea threshold are all in {@code config.yml}.
 */
final class SalmonellaEffects {

    static final List<PotionEffectType> ALL_TYPES = List.of(
            PotionEffectType.NAUSEA, PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS);

    private SalmonellaEffects() {
    }

    /**
     * Builds the effects for {@code stack} (clamped to 1..5), each with the given
     * duration. Ambient, no particles, icon shown (so it reads as the vanilla
     * Nausea indicator). Nausea is only included once {@code stack >= nauseaFromStack}.
     */
    static List<PotionEffect> forStack(int stack, int durationTicks, int nauseaFromStack) {
        int clamped = Math.max(1, Math.min(stack, 5));
        int amplify = clamped >= 5 ? 1 : 0; // only the top stack reaches level II

        List<PotionEffect> effects = new ArrayList<>(3);
        effects.add(effect(PotionEffectType.SLOWNESS, 0, durationTicks));
        if (clamped >= 2) {
            effects.add(effect(PotionEffectType.WEAKNESS, amplify, durationTicks));
        }
        if (clamped >= nauseaFromStack) {
            effects.add(effect(PotionEffectType.NAUSEA, amplify, durationTicks));
        }
        return List.copyOf(effects);
    }

    private static PotionEffect effect(PotionEffectType type, int amplifier, int durationTicks) {
        return new PotionEffect(type, durationTicks, amplifier, true, false, true);
    }
}
