package dev.thirst.drinking;

import dev.thirst.config.ThirstConfig;
import dev.thirst.thirst.ThirstManager;
import dev.thirst.water.WaterQuality;
import dev.thirst.water.WaterQualityService;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * The bottle-based drinking loop:
 *
 * <ul>
 *   <li>right-clicking a water source or cauldron with an empty glass bottle
 *       fills a quality-tagged {@link WaterContainers water bottle} instead of
 *       the plain vanilla one;</li>
 *   <li>drinking that bottle restores thirst — more for FRESH, less for DIRTY —
 *       and DIRTY rolls {@code salmonella-chance} to add a Salmonella stack
 *       (Phase 6 turns stacks into effects).</li>
 * </ul>
 *
 * <p>Bucket filling lives in {@link BucketListener}; cauldron water quality in
 * {@link CauldronWaterTracker}.
 */
public final class DrinkingListener implements Listener {

    private static final double FILL_REACH = 5.0;

    private final ThirstManager manager;
    private final WaterQualityService water;
    private final WaterContainers containers;
    private final CauldronWaterTracker cauldrons;
    private final Supplier<ThirstConfig> config;

    public DrinkingListener(ThirstManager manager, WaterQualityService water, WaterContainers containers,
                            CauldronWaterTracker cauldrons, Supplier<ThirstConfig> config) {
        this.manager = manager;
        this.water = water;
        this.containers = containers;
        this.cauldrons = cauldrons;
        this.config = config;
    }

    // ── Filling ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack held = event.getItem();
        if (held == null) {
            return;
        }

        if (held.getType() == Material.GLASS_BOTTLE) {
            fillBottle(event);
        } else if (containers.qualityOf(held) != null && held.getType() == Material.POTION) {
            enforceDrinkCooldown(event);
        }
    }

    private void fillBottle(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        FillTarget target = resolveFillTarget(player);
        if (target == null) {
            return;
        }
        event.setCancelled(true);

        WaterQuality quality = target.quality(water, cauldrons);
        boolean survival = player.getGameMode() != GameMode.CREATIVE;

        if (survival) {
            ItemStack inHand = player.getInventory().getItemInMainHand();
            inHand.subtract();
            player.getInventory().setItemInMainHand(inHand);
            if (target.cauldron()) {
                lowerCauldron(target.block());
            }
        }

        giveOrDrop(player, containers.createBottle(quality));
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOTTLE_FILL, 1.0f, 1.0f);
        player.swingMainHand();
    }

    private @Nullable FillTarget resolveFillTarget(Player player) {
        // ALWAYS (not SOURCE_ONLY): a bottle fills from any water block —
        // natural or placed, source or flowing.
        RayTraceResult ray = player.rayTraceBlocks(FILL_REACH, FluidCollisionMode.ALWAYS);
        Block hit = ray == null ? null : ray.getHitBlock();
        if (hit == null) {
            return null;
        }
        if (hit.getType() == Material.WATER_CAULDRON) {
            return new FillTarget(hit, true);
        }
        if (hit.getType() == Material.WATER) {
            return new FillTarget(hit, false);
        }
        return null;
    }

    private void lowerCauldron(Block cauldron) {
        if (!(cauldron.getBlockData() instanceof Levelled levelled)) {
            return;
        }
        int next = levelled.getLevel() - 1;
        if (next < levelled.getMinimumLevel()) {
            cauldron.setType(Material.CAULDRON);
            cauldrons.forget(cauldron);
        } else {
            levelled.setLevel(next);
            cauldron.setBlockData(levelled);
        }
    }

    // ── Drinking ────────────────────────────────────────────────────────────

    private void enforceDrinkCooldown(PlayerInteractEvent event) {
        long cooldownMillis = config.get().drinking().cooldownTicks() * 50L;
        long sinceLast = System.currentTimeMillis() - manager.getLastDrinkTimestamp(event.getPlayer());
        if (sinceLast < cooldownMillis) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.POTION) {
            return;
        }
        WaterQuality quality = containers.qualityOf(event.getItem());
        if (quality == null) {
            return;
        }
        Player player = event.getPlayer();
        ThirstConfig cfg = config.get();

        manager.addThirst(player, cfg.drinking().restoreFor(quality));
        manager.setLastDrinkTimestamp(player, System.currentTimeMillis());

        Location loc = player.getLocation();
        player.getWorld().playSound(loc, Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.0f);

        if (quality == WaterQuality.FRESH) {
            player.getWorld().spawnParticle(Particle.FALLING_WATER,
                    loc.clone().add(0.0, 1.4, 0.0), 8, 0.25, 0.15, 0.25, 0.0);
        } else {
            player.getWorld().playSound(loc, Sound.ENTITY_PLAYER_BURP, 0.7f, 1.0f);
            rollSalmonella(player, cfg);
        }
    }

    private void rollSalmonella(Player player, ThirstConfig cfg) {
        if (ThreadLocalRandom.current().nextDouble() >= cfg.drinking().salmonellaChance()) {
            return;
        }
        ThirstConfig.Salmonella salmonella = cfg.salmonella();
        int nextStack = Math.min(manager.getSalmonellaStacks(player) + 1, salmonella.maxStacks());
        int ticks = salmonella.durationSecondsForStack(nextStack) * 20;
        manager.setSalmonella(player, nextStack, ticks);
        // Phase 6's scheduler turns these stacks into escalating effects, decay and cure.
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static void giveOrDrop(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private record FillTarget(Block block, boolean cauldron) {

        WaterQuality quality(WaterQualityService water, CauldronWaterTracker cauldrons) {
            if (cauldron) {
                WaterQuality tracked = cauldrons.qualityOf(block);
                return tracked != null ? tracked : water.qualityForSource(block);
            }
            return water.qualityForSource(block);
        }
    }
}
