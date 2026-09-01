package dev.thirst.integration;

import dev.thirst.config.ThirstConfig;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@link ActionBarSegment} backed by PlaceholderAPI.
 *
 * <p>Only instantiate this when {@code PlaceholderAPI} is enabled — it references
 * PlaceholderAPI classes directly. {@link dev.thirst.ThirstPlugin} does the
 * {@code isPluginEnabled} check before constructing it.
 *
 * <p>The {@code hud.external-segment} config (enable flag, placeholder string,
 * …) is read fresh on every call, so {@code /thirst reload} is picked up with no
 * re-wiring.
 */
public final class PlaceholderApiSegment implements ActionBarSegment {

    private static final char SECTION = '§';

    private final Supplier<ThirstConfig> config;

    public PlaceholderApiSegment(Supplier<ThirstConfig> config) {
        this.config = config;
    }

    @Override
    public Optional<Component> resolve(Player player) {
        ThirstConfig.ExternalSegment settings = config.get().hud().externalSegment();
        String template = settings.placeholder();
        if (!settings.enabled() || template.isBlank()) {
            return Optional.empty();
        }

        String resolved = PlaceholderAPI.setPlaceholders(player, template);
        if (resolved == null || resolved.isBlank()) {
            return Optional.empty();
        }
        // Unchanged and still contains a placeholder token -> the expansion is
        // missing; don't show a literal "%rs_temperature%".
        if (resolved.equals(template) && resolved.indexOf('%') >= 0) {
            return Optional.empty();
        }

        // Expansions emit legacy colour codes; normalise § to & and parse both.
        String ampersand = resolved.replace(SECTION, '&');
        return Optional.of(LegacyComponentSerializer.legacyAmpersand().deserialize(ampersand));
    }
}
