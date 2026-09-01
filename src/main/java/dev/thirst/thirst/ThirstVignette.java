package dev.thirst.thirst;

import dev.thirst.config.ThirstConfig;
import org.bukkit.Bukkit;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

/**
 * Red screen tint at critical thirst, with no resource pack involved.
 *
 * <p>The trick: {@link WorldBorder#setWarningDistance(int)} is documented by
 * Bukkit to tint the screen red once the player is within that many blocks of
 * the border. We give the player a virtual, per-player border
 * ({@link Bukkit#createWorldBorder()}) centred on themselves and re-centred
 * every tick, sized so the player is always well within the warning distance —
 * the actual barrier wall never renders close enough to be seen, only the red
 * tint. Border damage is disabled, so this is purely cosmetic.
 *
 * <p>Whatever border the player already had (world default, or one set by
 * another plugin) is restored once thirst rises back above critical.
 */
final class ThirstVignette {

    private ThirstVignette() {
    }

    /** Called once per tick per player; turns the vignette on/off as thirst crosses the critical threshold. */
    static void update(Player player, PlayerThirstState state, ThirstConfig.Thirst thirst) {
        boolean shouldShow = thirst.lowThirstVignetteEnabled()
                && state.getLevel() <= thirst.criticalLevel();

        if (!shouldShow) {
            clear(player, state);
            return;
        }

        if (!state.isVignetteActive()) {
            WorldBorder border = Bukkit.createWorldBorder();
            double radius = Math.max(1.0, thirst.lowThirstVignetteRadius());
            border.setSize(radius * 2.0);
            border.setWarningDistance((int) Math.ceil(radius) + 1);
            border.setWarningTimeTicks(0);
            border.setDamageAmount(0.0);
            border.setCenter(player.getLocation());

            WorldBorder previous = player.getWorldBorder();
            player.setWorldBorder(border);
            state.setVignetteActive(true, previous);
        } else {
            WorldBorder current = player.getWorldBorder();
            if (current != null) {
                current.setCenter(player.getLocation());
            }
        }
    }

    /** Restores whatever border the player had before, if the vignette is currently active. */
    static void clear(Player player, PlayerThirstState state) {
        if (state.isVignetteActive()) {
            player.setWorldBorder(state.getPreVignetteBorder());
            state.setVignetteActive(false, null);
        }
    }
}
