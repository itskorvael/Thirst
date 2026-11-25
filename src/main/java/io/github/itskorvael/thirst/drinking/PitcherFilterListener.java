package io.github.itskorvael.thirst.drinking;

import io.github.itskorvael.thirst.config.ThirstConfig;
import io.github.itskorvael.thirst.water.WaterQuality;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Using the Pitcher Filter: hold it in the main hand with a quality-tagged bottle or bucket in the off hand and
 * right-click. The water goes up one quality step (capped by {@code filter.max-quality}) and the filter loses uses.
 */
public final class PitcherFilterListener implements Listener {

    private final PitcherFilter filter;
    private final WaterContainers containers;
    private final Supplier<ThirstConfig> config;
    private final Map<UUID, Long> lastUse = new HashMap<>();

    public PitcherFilterListener(PitcherFilter filter, WaterContainers containers, Supplier<ThirstConfig> config) {
        this.filter = filter;
        this.containers = containers;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack held = event.getItem();
        Integer uses = filter.usesOf(held);
        if (uses == null) {
            return;
        }
        ThirstConfig.Filter cfg = config.get().filter();
        Player player = event.getPlayer();
        ItemStack off = player.getInventory().getItemInOffHand();
        WaterQuality quality = containers.qualityOf(off);
        if (quality == null) {
            return; // nothing to filter; BlockPlaceEvent still stops the pod from being planted
        }
        event.setCancelled(true);
        if (!cfg.enabled()) {
            return;
        }

        WaterQuality improved = FilterRules.improve(quality, cfg.maxQuality());
        if (improved == null) {
            player.sendActionBar(Component.text("This water can't be filtered any cleaner.", NamedTextColor.GRAY));
            return;
        }
        boolean bucket = off.getType() == Material.WATER_BUCKET;
        int cost = FilterRules.cost(bucket, cfg.bucketCost());
        boolean survival = player.getGameMode() != GameMode.CREATIVE;
        if (survival && uses < cost) {
            player.sendActionBar(Component.text("The filter is too worn for that (needs " + cost + " uses).",
                    NamedTextColor.GRAY));
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUniqueId());
        if (last != null && now - last < cfg.cooldownTicks() * 50L) {
            return;
        }
        lastUse.put(player.getUniqueId(), now);

        ItemStack replacement = bucket ? containers.createBucket(improved) : containers.createBottle(improved);
        if (off.getAmount() > 1) {
            off.subtract();
            player.getInventory().setItemInOffHand(off);
            DrinkingListener.giveOrDrop(player, replacement);
        } else {
            player.getInventory().setItemInOffHand(replacement);
        }

        if (survival) {
            int remaining = uses - cost;
            if (remaining <= 0) {
                player.getInventory().setItemInMainHand(null);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                player.sendActionBar(Component.text("The filter wore out.", NamedTextColor.YELLOW));
            } else {
                filter.setUses(held, remaining);
                player.getInventory().setItemInMainHand(held);
                player.sendActionBar(Component.text("Water filtered. " + remaining + " uses left.",
                        NamedTextColor.GREEN));
            }
        } else {
            player.sendActionBar(Component.text("Water filtered.", NamedTextColor.GREEN));
        }
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOTTLE_FILL, 1.0f, 1.3f);
        player.getWorld().spawnParticle(Particle.FALLING_WATER,
                player.getLocation().add(0.0, 1.3, 0.0), 6, 0.2, 0.1, 0.2, 0.0);
    }

    /** A filter is a pitcher pod in vanilla terms: stop it being planted. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (filter.isFilter(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    /** A worn filter can't be used as the pitcher pod of a new filter, or its uses would reset. */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed) || !keyed.getKey().equals(filter.recipeKey())) {
            return;
        }
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (filter.isFilter(ingredient)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    /**
     * The recharge recipe is a pod, fragments and a charcoal, matched by material only, so this decides what really
     * comes out: it needs one real filter, the right number of marked fragments (a plain item of the same kind is
     * refused) and a filter that is not already at the limit. The result is that filter with more uses.
     */
    @EventHandler
    public void onPrepareRecharge(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed) || !keyed.getKey().equals(filter.rechargeKey())) {
            return;
        }
        ThirstConfig.Filter cfg = config.get().filter();
        ItemStack worn = null;
        int fragments = 0;
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (ingredient == null || ingredient.getType() == Material.AIR) {
                continue;
            }
            if (filter.isFilter(ingredient)) {
                if (worn != null) {
                    event.getInventory().setResult(null);
                    return;
                }
                worn = ingredient;
            } else if (filter.isFragment(ingredient)) {
                fragments++;
            } else if (ingredient.getType() == Material.PITCHER_POD) {
                event.getInventory().setResult(null); // a plain pod is not a filter
                return;
            } else if (ingredient.getType() != Material.CHARCOAL) {
                event.getInventory().setResult(null); // a vanilla item of the fragment's material, not a marked one
                return;
            }
        }
        Integer uses = filter.usesOf(worn);
        if (!cfg.enabled() || !cfg.recharge().enabled() || uses == null || fragments != cfg.recharge().fragments()
                || !FilterRules.canRecharge(uses, cfg.recharge().maxUses())) {
            event.getInventory().setResult(null);
            return;
        }
        ItemStack result = worn.clone();
        filter.setUses(result, FilterRules.recharged(uses, cfg.recharge().addUses(), cfg.recharge().maxUses()));
        event.getInventory().setResult(result);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastUse.remove(event.getPlayer().getUniqueId());
    }
}
