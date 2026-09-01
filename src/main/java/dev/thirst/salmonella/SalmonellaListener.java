package dev.thirst.salmonella;

import dev.thirst.config.ThirstConfig;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.function.Supplier;

/**
 * Milk cures Salmonella (one stack per {@code milk-cure-stacks}), and quitters
 * drop their damage timer.
 */
public final class SalmonellaListener implements Listener {

    private final SalmonellaService salmonella;
    private final Supplier<ThirstConfig> config;

    public SalmonellaListener(SalmonellaService salmonella, Supplier<ThirstConfig> config) {
        this.salmonella = salmonella;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrinkMilk(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.MILK_BUCKET) {
            return;
        }
        ThirstConfig.Salmonella cfg = config.get().salmonella();
        if (cfg.cureWithMilk()) {
            salmonella.cure(event.getPlayer(), cfg.milkCureStacks());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        salmonella.forget(event.getPlayer().getUniqueId());
    }
}
