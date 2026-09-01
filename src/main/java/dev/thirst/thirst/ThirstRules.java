package dev.thirst.thirst;

import dev.thirst.config.ThirstConfig;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Small shared predicates for "does the thirst mechanic apply to this player".
 */
public final class ThirstRules {

    private ThirstRules() {
    }

    /**
     * Whether thirst should drain / cause damage for this player right now.
     *
     * <p>Mirrors vanilla hunger: Creative and Spectator players are exempt.
     * Dead players are skipped until they respawn.
     */
    public static boolean affected(Player player) {
        if (player.isDead() || !player.isOnline()) {
            return false;
        }
        GameMode mode = player.getGameMode();
        return mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR;
    }

    /**
     * Whether sprinting should be blocked for this player right now — thirst at
     * or below the critical threshold, with {@code disable-sprint-when-critical}
     * on. Mirrors vanilla hunger disabling sprint at food level 6/20.
     *
     * @param thirstLevel the player's current thirst level
     */
    public static boolean sprintBlocked(Player player, int thirstLevel, ThirstConfig.Thirst thirst) {
        return thirst.disableSprintWhenCritical()
                && affected(player)
                && thirstLevel <= thirst.criticalLevel();
    }
}
