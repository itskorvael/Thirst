package dev.thirst.hud;

import dev.thirst.config.ThirstConfig;

/**
 * Pure text formatting for the thirst HUD — no Bukkit or Adventure types, so
 * it is trivially unit-testable. Produces MiniMessage source strings; callers
 * (the action-bar and boss-bar renderers) deserialize and send them.
 */
public final class ThirstHudFormatter {

    private ThirstHudFormatter() {
    }

    /** Whether {@code level} is at/below the configured critical threshold. */
    public static boolean isCritical(int level, ThirstConfig.Thirst thirst) {
        return level <= thirst.criticalLevel();
    }

    /**
     * Full MiniMessage source for the HUD, e.g. {@code "<aqua>[||||||----]"} or
     * {@code "<red>30% Thirst"}. Used for both the action bar and the boss-bar
     * title, so the two modes always agree on the numbers.
     */
    public static String render(int level, ThirstConfig.Thirst thirst, ThirstConfig.Hud hud) {
        String color = isCritical(level, thirst) ? hud.colorCritical() : hud.colorNormal();
        String body = switch (hud.style()) {
            case PERCENT -> percentText(level, thirst.max(), hud.label());
            case BAR -> barText(level, thirst.max(), hud.barSegments());
        };
        return color + body;
    }

    /** Thirst as a fraction of max, clamped to {@code [0, 1]} — boss-bar progress. */
    public static float fractionOf(int level, int max) {
        if (max <= 0) {
            return 0f;
        }
        double fraction = (double) level / max;
        return (float) Math.max(0.0, Math.min(1.0, fraction));
    }

    private static String percentText(int level, int max, String label) {
        int percent = percentOf(level, max);
        return label.isBlank() ? percent + "%" : percent + "% " + label;
    }

    private static String barText(int level, int max, int segments) {
        int safeSegments = Math.max(1, segments);
        int filled = max <= 0 ? 0 : (int) Math.round((double) safeSegments * level / max);
        filled = Math.max(0, Math.min(safeSegments, filled));
        int empty = safeSegments - filled;
        return "[" + "|".repeat(filled) + "-".repeat(empty) + "]";
    }

    private static int percentOf(int level, int max) {
        if (max <= 0) {
            return 0;
        }
        double percent = 100.0 * level / max;
        return (int) Math.round(Math.max(0.0, Math.min(100.0, percent)));
    }
}
