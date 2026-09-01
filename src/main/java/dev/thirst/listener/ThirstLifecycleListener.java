package dev.thirst.listener;

import dev.thirst.thirst.ThirstManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Loads thirst state when a player joins, flushes and drops it when they quit,
 * and refills thirst on respawn (mirroring vanilla hunger, which resets to full
 * on death).
 */
public final class ThirstLifecycleListener implements Listener {

    private final ThirstManager manager;

    public ThirstLifecycleListener(ThirstManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        manager.load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        manager.unload(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        manager.resetToFull(event.getPlayer());
    }
}
