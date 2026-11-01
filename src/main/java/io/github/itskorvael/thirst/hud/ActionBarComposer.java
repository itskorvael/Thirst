package io.github.itskorvael.thirst.hud;

import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

/**
 * Joins the thirst bar with an optional external segment (see
 * {@code hud.external-segment}). Adventure types only — no Bukkit — so it is
 * unit-testable.
 */
final class ActionBarComposer {

    private ActionBarComposer() {
    }

    /**
     * @param thirstBar    the rendered thirst bar
     * @param external     the external segment, or {@code null} to show only the bar
     * @param separator    text placed between the two when {@code external} is present
     * @param externalFirst {@code true} = external, separator, bar; {@code false} = bar, separator, external
     */
    static Component compose(Component thirstBar, @Nullable Component external,
                             Component separator, boolean externalFirst) {
        if (external == null) {
            return thirstBar;
        }
        Component first = externalFirst ? external : thirstBar;
        Component second = externalFirst ? thirstBar : external;
        return Component.empty().append(first).append(separator).append(second);
    }
}
