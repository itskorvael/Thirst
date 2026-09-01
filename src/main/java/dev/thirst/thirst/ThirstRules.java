package dev.thirst.thirst;

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
}
