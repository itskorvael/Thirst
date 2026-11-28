package io.github.itskorvael.thirst.drinking;

import io.github.itskorvael.thirst.config.ThirstConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * The Pitcher Filter item and its crafting recipe.
 *
 * <p>The item is a {@link Material#PITCHER_POD} with a use counter in its persistent data. A pod has no crafting
 * or smelting use in vanilla, and {@link PitcherFilterListener} cancels planting it. It never stacks, so each filter
 * keeps its own number of uses.
 */
public final class PitcherFilter {

    private final Plugin plugin;
    private final NamespacedKey usesKey;
    private final NamespacedKey recipeKey;
    private final NamespacedKey rechargeKey;
    private final Supplier<ThirstConfig> config;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PitcherFilter(Plugin plugin, Supplier<ThirstConfig> config) {
        this.plugin = plugin;
        this.usesKey = new NamespacedKey(plugin, "pitcher_filter_uses");
        this.recipeKey = new NamespacedKey(plugin, "pitcher_filter");
        this.rechargeKey = new NamespacedKey(plugin, "pitcher_filter_recharge");
        this.config = config;
    }

    public NamespacedKey recipeKey() {
        return recipeKey;
    }

    public NamespacedKey rechargeKey() {
        return rechargeKey;
    }

    // ── Item ────────────────────────────────────────────────────────────────

    /** A filter with {@code uses} uses left. */
    public ItemStack create(int uses) {
        ItemStack item = ItemStack.of(Material.PITCHER_POD);
        ItemMeta meta = item.getItemMeta();
        meta.setMaxStackSize(1);
        meta.setEnchantmentGlintOverride(true);
        Component name = miniMessage.deserialize(config.get().filter().name())
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        meta.itemName(name);
        meta.displayName(name);
        meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, uses);
        applyLore(meta, uses);
        item.setItemMeta(meta);
        return item;
    }

    /** @return the uses left, or {@code null} if this is not a Pitcher Filter */
    public @Nullable Integer usesOf(@Nullable ItemStack item) {
        if (item == null || item.getType() != Material.PITCHER_POD || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(usesKey, PersistentDataType.INTEGER);
    }

    public boolean isFilter(@Nullable ItemStack item) {
        return usesOf(item) != null;
    }

    /** Sets the uses left on this filter and rewrites its lore. */
    public void setUses(ItemStack item, int uses) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, uses);
        applyLore(meta, uses);
        item.setItemMeta(meta);
    }

    private void applyLore(ItemMeta meta, int uses) {
        ThirstConfig.Filter cfg = config.get().filter();
        List<Component> lore = new ArrayList<>();
        lore.add(miniMessage.deserialize(cfg.loreUses().replace("{uses}", Integer.toString(uses)))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        if (!cfg.loreHint().isBlank()) {
            lore.add(miniMessage.deserialize(cfg.loreHint())
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        meta.lore(lore);
    }

    // ── Recipe ──────────────────────────────────────────────────────────────

    /**
     * (Re-)registers the crafting recipe from the current config, or removes it when the filter is disabled.
     * Safe to call on every reload.
     */
    public void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);
        Bukkit.removeRecipe(rechargeKey);
        ThirstConfig.Filter cfg = config.get().filter();
        if (!cfg.enabled()) {
            return;
        }
        registerRechargeRecipe(cfg);
        List<Material> ingredients = resolveIngredients(cfg.recipe());
        if (ingredients.isEmpty()) {
            ingredients = resolveIngredients(ThirstConfig.DEFAULT_FILTER_RECIPE);
        }
        ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, create(cfg.uses()));
        for (Material material : ingredients) {
            recipe.addIngredient(material);
        }
        Bukkit.addRecipe(recipe);
        Bukkit.getOnlinePlayers().forEach(this::discoverFor);
    }

    /** Puts the filter and recharge recipes in a player's recipe book (a recipe that is not registered is skipped). */
    public void discoverFor(org.bukkit.entity.Player player) {
        if (config.get().filter().enabled()) {
            player.discoverRecipe(recipeKey);
            player.discoverRecipe(rechargeKey);
        }
    }

    /** The recharge recipe: a pod (the filter), the fragments and a charcoal. The result is decided when it is crafted. */
    private void registerRechargeRecipe(ThirstConfig.Filter cfg) {
        ThirstConfig.Recharge recharge = cfg.recharge();
        if (!recharge.enabled()) {
            return;
        }
        Material fragment = Material.matchMaterial(recharge.item().trim().toUpperCase(Locale.ROOT));
        if (fragment == null || !fragment.isItem() || fragment.isAir()) {
            plugin.getLogger().warning("Config: filter.recharge.item '" + recharge.item()
                    + "' is not an item; filters can't be recharged.");
            return;
        }
        if (NamespacedKey.fromString(recharge.key()) == null) {
            plugin.getLogger().warning("Config: filter.recharge.key '" + recharge.key()
                    + "' is not a valid key (namespace:key); filters can't be recharged.");
            return;
        }
        ShapelessRecipe recipe = new ShapelessRecipe(rechargeKey, create(cfg.uses()));
        recipe.addIngredient(Material.PITCHER_POD);
        for (int i = 0; i < recharge.fragments(); i++) {
            recipe.addIngredient(fragment);
        }
        recipe.addIngredient(Material.CHARCOAL);
        Bukkit.addRecipe(recipe);
    }

    /** Is this a marked fragment (right material is not enough: the other plugin's key must be on it)? */
    public boolean isFragment(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ThirstConfig.Recharge recharge = config.get().filter().recharge();
        NamespacedKey key = NamespacedKey.fromString(recharge.key());
        Material material = Material.matchMaterial(recharge.item().trim().toUpperCase(Locale.ROOT));
        return key != null && material != null && item.getType() == material
                && item.getItemMeta().getPersistentDataContainer().has(key);
    }

    public void unregisterRecipe() {
        Bukkit.removeRecipe(recipeKey);
        Bukkit.removeRecipe(rechargeKey);
    }

    private List<Material> resolveIngredients(List<String> names) {
        List<Material> out = new ArrayList<>();
        for (String name : names) {
            Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (material == null || !material.isItem() || material.isAir()) {
                plugin.getLogger().warning("Config: filter.recipe has an unknown item '" + name + "'; skipping it.");
                continue;
            }
            out.add(material);
        }
        return out;
    }
}
