package io.github.itskorvael.thirst.config;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * How the thirst indicator is rendered.
 */
public enum HudMode {
    /** Text bar / percent drawn in the action bar (above the hotbar). Default. */
    ACTIONBAR_TEXT,
    /** Boss bar at the top of the screen. Debug / fallback only. */
    BOSSBAR,
    /**
     * Reserved for a future release: pixel-positioned droplet glyphs supplied by
     * a resource pack, falling back to {@link #ACTIONBAR_TEXT} when the player
     * has not accepted the pack. Not implemented yet.
     */
    ACTIONBAR_RESOURCEPACK;

    public static @Nullable HudMode parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
