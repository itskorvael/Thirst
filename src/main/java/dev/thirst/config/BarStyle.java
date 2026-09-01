package dev.thirst.config;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Visual style for the {@link HudMode#ACTIONBAR_TEXT} thirst indicator.
 */
public enum BarStyle {
    /** A segmented bar such as {@code [||||||----]}. */
    BAR,
    /** A plain percentage such as {@code 75%}. */
    PERCENT;

    public static @Nullable BarStyle parse(@Nullable String raw) {
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
