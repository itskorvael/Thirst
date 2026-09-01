package dev.thirst.water;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Quality flag applied per chunk (and copied onto filled water bottles).
 *
 * <p>{@link #FRESH} water is safe and restores more thirst; {@link #DIRTY} water
 * restores less and can cause Salmonella. Chunks with no stored value fall back
 * to the {@code default-water-quality} config option.
 */
public enum WaterQuality {
    FRESH,
    DIRTY;

    /**
     * Parses a config / command token into a quality, case-insensitively. The
     * legacy name {@code "clean"} is still accepted as an alias for {@link #FRESH}.
     *
     * @param raw the token, e.g. {@code "fresh"}; may be {@code null}
     * @return the matching quality, or {@code null} if {@code raw} is not valid
     */
    public static @Nullable WaterQuality parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "FRESH", "CLEAN" -> FRESH;
            case "DIRTY" -> DIRTY;
            default -> null;
        };
    }
}
