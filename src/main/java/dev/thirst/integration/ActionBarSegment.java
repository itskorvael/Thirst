package dev.thirst.integration;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Supplies an extra piece of text to sit next to the thirst bar in the action
 * bar (see {@code hud.external-segment}). Implementations must be safe to call
 * every HUD tick on the main thread.
 */
public interface ActionBarSegment {

    /**
     * @return the segment for this player, or {@link Optional#empty()} when
     *         disabled, unavailable, or the resolved text is blank
     */
    Optional<Component> resolve(Player player);
}
