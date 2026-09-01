package dev.thirst.listener;

import dev.thirst.hud.ThirstHudTask;
import dev.thirst.thirst.ThirstManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Loads thirst state when a player joins, flushes and drops it (and tears down
 * their HUD) when they quit, and refills thirst on respawn (mirroring vanilla
 * hunger, which resets to full on death).
 */
public final class ThirstLifecycleListener implements Listener {

    private final ThirstManager manager;
    private final ThirstHudTask hud;

    public ThirstLifecycleListener(ThirstManager manager, ThirstHudTask hud) {
        this.manager = manager;
        this.hud = hud;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        manager.load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        hud.handleQuit(event.getPlayer());
        manager.unload(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        manager.resetToFull(event.getPlayer());
    }
}
