package dev.thirst.hud;

import dev.thirst.config.ThirstConfig;
import dev.thirst.integration.ActionBarSegment;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws the thirst bar as action-bar text (above the hotbar) via
 * {@link Player#sendActionBar(Component)} — the intended default HUD.
 *
 * <p>When {@code actionbar-overwrite-others} is false, a cooperative yield map
 * lets other plugins ask Thirst to skip its update briefly (see
 * {@link ThirstHudTask#requestActionBarYield}). Automatic detection of a
 * third-party plugin's action-bar packets is not possible through the public
 * Paper API, so this — plus the optional {@code hud.external-segment} — is the
 * extent of the coexistence handling.
 */
final class ActionBarHudRenderer implements HudRenderer {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private volatile @Nullable ActionBarSegment externalSegment;

    /** Player UUID -> {@code System.currentTimeMillis()} until which to stay quiet. Written from other threads. */
    private final Map<UUID, Long> yieldUntil = new ConcurrentHashMap<>();

    ActionBarHudRenderer(@Nullable ActionBarSegment externalSegment) {
        this.externalSegment = externalSegment;
    }

    /** Swap the external segment source (e.g. after {@code /thirst reload} re-checks PlaceholderAPI). */
    void setSegment(@Nullable ActionBarSegment segment) {
        this.externalSegment = segment;
    }

    @Override
    public void update(Player player, int level, ThirstConfig config) {
        if (!config.actionbarOverwriteOthers() && isYielding(player.getUniqueId())) {
            return;
        }

        Component bar = miniMessage.deserialize(
                ThirstHudFormatter.render(level, config.thirst(), config.hud()));

        Component message = bar;
        ActionBarSegment segment = this.externalSegment;
        if (segment != null) {
            Component external = segment.resolve(player).orElse(null);
            if (external != null) {
                ThirstConfig.ExternalSegment settings = config.hud().externalSegment();
                Component separator = miniMessage.deserialize(settings.separator());
                message = ActionBarComposer.compose(bar, external, separator, settings.placeBefore());
            }
        }

        player.sendActionBar(message);
    }

    @Override
    public void remove(Player player) {
        yieldUntil.remove(player.getUniqueId());
        // Action-bar text fades on its own; nothing to actively clear.
    }

    void requestYield(UUID playerId, long millis) {
        if (millis <= 0) {
            yieldUntil.remove(playerId);
            return;
        }
        long until = System.currentTimeMillis() + millis;
        yieldUntil.merge(playerId, until, Math::max);
    }

    private boolean isYielding(UUID playerId) {
        Long until = yieldUntil.get(playerId);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            yieldUntil.remove(playerId, until);
            return false;
        }
        return true;
    }
}
