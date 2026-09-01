package dev.thirst.thirst;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Neutralises the sprint speed bonus while a player is critically thirsty.
 *
 * <p>A true "can't sprint" is not possible from the server — the client decides
 * whether to sprint from its own copy of the food level. Instead, for as long as
 * the player is <em>both</em> critical and sprinting, a transient negative
 * {@link Attribute#MOVEMENT_SPEED} modifier cancels the ~30% sprint boost, so
 * sprinting is no faster than walking. It is removed the instant either
 * condition stops, so ordinary walking is never slowed.
 *
 * <p>The modifier is {@linkplain AttributeInstance#addTransientModifier transient}
 * — never written to player data — so a crash cannot leave it stuck past a
 * relog. {@link #clear(Player)} handles the plugin-disable case.
 */
public final class CriticalSpeedModifier {

    private final NamespacedKey key;

    public CriticalSpeedModifier(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "critical_thirst_sprint_penalty");
    }

    /**
     * @param apply      whether the penalty should currently be in effect
     *                   (critical &amp;&amp; sprinting)
     * @param multiplier {@code critical-sprint-speed-multiplier}, in {@code (0, 1]}
     */
    public void sync(Player player, boolean apply, double multiplier) {
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }
        AttributeModifier existing = attribute.getModifier(key);

        if (!apply || multiplier >= 1.0) {
            if (existing != null) {
                attribute.removeModifier(key);
            }
            return;
        }

        // ADD_SCALAR: final value = base * (1 + Σ scalar amounts).
        double amount = multiplier - 1.0;
        if (existing != null) {
            if (Math.abs(existing.getAmount() - amount) < 1.0e-9) {
                return;
            }
            attribute.removeModifier(key);
        }
        attribute.addTransientModifier(
                new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_SCALAR));
    }

    /** Remove the penalty unconditionally (plugin disable). */
    public void clear(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute != null && attribute.getModifier(key) != null) {
            attribute.removeModifier(key);
        }
    }
}
