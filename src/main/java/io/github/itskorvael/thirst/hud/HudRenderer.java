package io.github.itskorvael.thirst.hud;

import io.github.itskorvael.thirst.config.ThirstConfig;
import org.bukkit.entity.Player;

/**
 * Strategy for drawing the thirst HUD for a single player. One implementation
 * per {@link io.github.itskorvael.thirst.config.HudMode}; a future resource-pack renderer slots
 * in here without touching {@link ThirstHudTask}.
 */
interface HudRenderer {

    /** Draw / refresh the HUD for {@code player} at the given thirst level + Salmonella stacks. */
    void update(Player player, int level, int salmonellaStacks, ThirstConfig config);

    /** Tear down any per-player resources (e.g. a boss bar). Safe to call when nothing is shown. */
    void remove(Player player);
}
