package io.github.itskorvael.thirst.water;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Quality flag applied per chunk (and copied onto filled water containers).
 *
 * <ul>
 *   <li>{@link #FRESH} — safe, restores the most thirst.</li>
 *   <li>{@link #NEUTRAL} — no established quality; the default for chunks an
 *       admin has not painted. Restores a middling amount, no illness.</li>
 *   <li>{@link #DIRTY} — restores the least and can cause Salmonella.</li>
 * </ul>
 */
public enum WaterQuality {
    FRESH,
    NEUTRAL,
    DIRTY;

    /**
     * Combines two qualities that end up in the same body of water: any dirty
     * part makes the whole dirty, otherwise any fresh part makes it fresh.
     */
    public static WaterQuality mix(WaterQuality a, WaterQuality b) {
        if (a == DIRTY || b == DIRTY) {
            return DIRTY;
        }
        if (a == FRESH || b == FRESH) {
            return FRESH;
        }
        return NEUTRAL;
    }

    /**
     * Parses a config / command token, case-insensitively. The legacy name
     * {@code "clean"} is still accepted as an alias for {@link #FRESH}.
     *
     * @return the matching quality, or {@code null} if {@code raw} is not valid
     */
    public static @Nullable WaterQuality parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "FRESH", "CLEAN" -> FRESH;
            case "NEUTRAL" -> NEUTRAL;
            case "DIRTY" -> DIRTY;
            default -> null;
        };
    }
}
