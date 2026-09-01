package dev.thirst.hud;

import dev.thirst.config.ThirstConfig;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Debug / fallback HUD: an Adventure {@link BossBar} at the top of the screen,
 * blue above the critical threshold and red at/below it, with progress tracking
 * thirst. Not the intended look (it renders top-centre, not above the hotbar) —
 * useful for quick testing and as a no-frills fallback.
 */
final class BossBarHudRenderer implements HudRenderer {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, BossBar> bars = new HashMap<>();

    @Override
    public void update(Player player, int level, ThirstConfig config) {
        Component name = miniMessage.deserialize(
                ThirstHudFormatter.render(level, config.thirst(), config.hud()));
        float progress = ThirstHudFormatter.fractionOf(level, config.thirst().max());
        BossBar.Color color = ThirstHudFormatter.isCritical(level, config.thirst())
                ? BossBar.Color.RED
                : BossBar.Color.BLUE;

        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(name, progress, color, BossBar.Overlay.PROGRESS);
            bars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
        } else {
            bar.name(name);
            bar.progress(progress);
            bar.color(color);
        }
    }

    @Override
    public void remove(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }
}
