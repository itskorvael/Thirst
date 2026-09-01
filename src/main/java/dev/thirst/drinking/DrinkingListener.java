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
 *       fills a quality-tagged {@link WaterBottles water bottle} instead of the
 *       plain vanilla one;</li>
 *   <li>drinking that bottle restores thirst — more for FRESH, less for DIRTY —
 *       and DIRTY rolls {@code salmonella-chance} to add a Salmonella stack
 *       (Phase 6 turns stacks into effects).</li>
 * </ul>
 */
public final class DrinkingListener implements Listener {

    private static final double FILL_REACH = 5.0;

    private final ThirstManager manager;
    private final WaterQualityService water;
    private final WaterBottles bottles;
    private final RainCauldronTracker rainCauldrons;
    private final Supplier<ThirstConfig> config;

    public DrinkingListener(ThirstManager manager, WaterQualityService water, WaterBottles bottles,
                            RainCauldronTracker rainCauldrons, Supplier<ThirstConfig> config) {
        this.manager = manager;
        this.water = water;
        this.bottles = bottles;
        this.rainCauldrons = rainCauldrons;
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
        } else if (bottles.qualityOf(held) != null) {
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

        ThirstConfig cfg = config.get();
        WaterQuality quality = target.quality(water, rainCauldrons, cfg);
        boolean survival = player.getGameMode() != GameMode.CREATIVE;

        if (survival) {
            ItemStack inHand = player.getInventory().getItemInMainHand();
            inHand.subtract();
            player.getInventory().setItemInMainHand(inHand);
            if (target.cauldron()) {
                lowerCauldron(target.block());
            }
        }

        giveOrDrop(player, bottles.create(quality));
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOTTLE_FILL, 1.0f, 1.0f);
        player.swingMainHand();
    }

    private @Nullable FillTarget resolveFillTarget(Player player) {
        RayTraceResult ray = player.rayTraceBlocks(FILL_REACH, FluidCollisionMode.SOURCE_ONLY);
        Block hit = ray == null ? null : ray.getHitBlock();
        if (hit == null) {
            return null;
        }
        if (hit.getType() == Material.WATER_CAULDRON) {
            return new FillTarget(hit, true);
        }
        if (hit.getType() == Material.WATER && isSource(hit)) {
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
            rainCauldrons.forget(cauldron);
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
        WaterQuality quality = bottles.qualityOf(event.getItem());
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

    private static boolean isSource(Block block) {
        return block.getBlockData() instanceof Levelled levelled && levelled.getLevel() == 0;
    }

    private static void giveOrDrop(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private record FillTarget(Block block, boolean cauldron) {

        WaterQuality quality(WaterQualityService water, RainCauldronTracker rainCauldrons, ThirstConfig cfg) {
            ThirstConfig.Drinking drinking = cfg.drinking();
            // Active rain/storm makes every source dirty; reverts when it clears.
            if (drinking.dirtyWhileRaining() && block.getWorld().hasStorm()) {
                return WaterQuality.DIRTY;
            }
            // Rain/dripstone-filled cauldrons hold dirty runoff even after the rain stops.
            if (cauldron && drinking.rainCauldronAlwaysDirty() && rainCauldrons.isRainFilled(block)) {
                return WaterQuality.DIRTY;
            }
            return water.qualityAt(block.getLocation());
        }
    }
}
