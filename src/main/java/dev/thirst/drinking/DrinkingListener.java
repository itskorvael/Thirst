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
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Logger;

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

    private static final int FILL_REACH = 5;

    private final ThirstManager manager;
    private final WaterQualityService water;
    private final WaterContainers containers;
    private final CauldronWaterTracker cauldrons;
    private final Supplier<ThirstConfig> config;
    private final Logger logger;

    public DrinkingListener(ThirstManager manager, WaterQualityService water, WaterContainers containers,
                            CauldronWaterTracker cauldrons, Supplier<ThirstConfig> config, Logger logger) {
        this.manager = manager;
        this.water = water;
        this.containers = containers;
        this.cauldrons = cauldrons;
        this.config = config;
        this.logger = logger;
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
        FillTarget target = resolveFillTarget(event);
        logger.info(() -> "[fill] " + player.getName() + " action=" + event.getAction()
                + " clicked=" + (event.getClickedBlock() == null ? "-" : event.getClickedBlock().getType())
                + " target=" + (target == null ? "NONE"
                        : target.block().getType() + (target.cauldron() ? "(cauldron)" : "")));
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

        ItemStack bottle = containers.createBottle(quality);
        logger.info(() -> "[fill] created quality=" + quality + " readback="
                + containers.qualityOf(bottle) + " keys=" + keysOf(bottle));
        giveOrDrop(player, bottle);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOTTLE_FILL, 1.0f, 1.0f);
        player.swingMainHand();
    }

    private @Nullable FillTarget resolveFillTarget(PlayerInteractEvent event) {
        // 1. A water / cauldron block clicked directly.
        FillTarget clicked = classify(event.getClickedBlock());
        if (clicked != null) {
            return clicked;
        }

        Player player = event.getPlayer();

        // 2. A water / cauldron block in the crosshair (respects real shapes).
        FillTarget aimed = classify(player.getTargetBlockExact(FILL_REACH, FluidCollisionMode.ALWAYS));
        if (aimed != null) {
            return aimed;
        }

        // 3. Walk the crosshair ray by hand — catches thin flowing-water shapes
        //    the precise ray trace passes through.
        Location point = player.getEyeLocation();
        var step = point.getDirection().multiply(0.5);
        for (int i = 0; i < FILL_REACH * 2; i++) {
            Block block = point.getBlock();
            FillTarget target = classify(block);
            if (target != null) {
                return target;
            }
            if (block.getType().isOccluding()) {
                break;
            }
            point.add(step);
        }

        // 4. Head submerged.
        return classify(player.getEyeLocation().getBlock());
    }

    private static @Nullable FillTarget classify(@Nullable Block block) {
        if (block == null) {
            return null;
        }
        return switch (block.getType()) {
            case WATER_CAULDRON -> new FillTarget(block, true);
            case WATER -> new FillTarget(block, false);
            default -> null;
        };
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
        Player player = event.getPlayer();

        WaterQuality quality = containers.qualityOf(event.getItem());
        ItemStack handItem = event.getHand() != null ? player.getInventory().getItem(event.getHand()) : null;
        if (quality == null && handItem != null) {
            // Safety net: some paths hand back a stripped item on the event.
            quality = containers.qualityOf(handItem);
        }
        WaterQuality resolved = quality;
        logger.info(() -> "[drink] " + player.getName()
                + " event=" + event.getItem().getType() + "/" + keysOf(event.getItem())
                + " hand=" + (handItem == null ? "-" : handItem.getType() + "/" + keysOf(handItem))
                + " => " + resolved);
        if (resolved == null) {
            return;
        }
        ThirstConfig cfg = config.get();

        int before = manager.getLevel(player);
        manager.addThirst(player, cfg.drinking().restoreFor(resolved));
        logger.info(() -> "[drink] thirst " + before + " -> " + manager.getLevel(player));
        manager.setLastDrinkTimestamp(player, System.currentTimeMillis());

        Location loc = player.getLocation();
        player.getWorld().playSound(loc, Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.0f);

        switch (resolved) {
            case FRESH -> player.getWorld().spawnParticle(Particle.FALLING_WATER,
                    loc.clone().add(0.0, 1.4, 0.0), 8, 0.25, 0.15, 0.25, 0.0);
            case DIRTY -> {
                player.getWorld().playSound(loc, Sound.ENTITY_PLAYER_BURP, 0.7f, 1.0f);
                rollSalmonella(player, cfg);
            }
            case NEUTRAL -> {
                // plain water — just the drink sound
            }
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

    private static String keysOf(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return "no-meta";
        }
        return item.getItemMeta().getPersistentDataContainer().getKeys().toString();
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
