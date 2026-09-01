package dev.thirst.drinking;

import dev.thirst.config.ThirstConfig;
import dev.thirst.water.WaterQuality;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.UseCooldown;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionType;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Creates and recognises the tagged "Bottle of Fresh/Dirty Water" items.
 *
 * <p>Each is a vanilla water bottle ({@link PotionType#WATER}) — keeping the
 * drink animation and the free glass-bottle return — with a
 * {@link org.bukkit.persistence.PersistentDataContainer} tag recording the
 * {@link WaterQuality} of the chunk it was filled in, a tinted colour, a config
 * name, and a shared use-cooldown so drinking one briefly locks the rest.
 */
public final class WaterBottles {

    private static final Color FRESH_COLOUR = Color.fromRGB(0x3F76E4);
    private static final Color DIRTY_COLOUR = Color.fromRGB(0x6B6246);

    private final NamespacedKey qualityKey;
    private final NamespacedKey cooldownGroup;
    private final Supplier<ThirstConfig> config;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public WaterBottles(Plugin plugin, Supplier<ThirstConfig> config) {
        this.qualityKey = new NamespacedKey(plugin, "water_bottle_quality");
        this.cooldownGroup = new NamespacedKey(plugin, "water_bottle");
        this.config = config;
    }

    /** A single filled bottle of the given quality, using the current config. */
    @SuppressWarnings("UnstableApiUsage")
    public ItemStack create(WaterQuality quality) {
        ThirstConfig.Drinking drinking = config.get().drinking();
        String nameSource = quality == WaterQuality.FRESH
                ? drinking.freshBottleName()
                : drinking.dirtyBottleName();
        Component name = miniMessage.deserialize(nameSource);

        ItemStack item = ItemStack.of(Material.POTION);
        item.editMeta(PotionMeta.class, meta -> {
            meta.setBasePotionType(PotionType.WATER);
            meta.setColor(quality == WaterQuality.FRESH ? FRESH_COLOUR : DIRTY_COLOUR);
            meta.itemName(name);
            meta.getPersistentDataContainer().set(qualityKey, PersistentDataType.STRING, quality.name());
        });

        int cooldownTicks = drinking.cooldownTicks();
        if (cooldownTicks > 0) {
            item.setData(DataComponentTypes.USE_COOLDOWN, UseCooldown.useCooldown(cooldownTicks / 20.0f)
                    .cooldownGroup(cooldownGroup)
                    .build());
        }
        return item;
    }

    /**
     * @return the quality this item was tagged with, or {@code null} if it is
     *         not one of our water bottles
     */
    public @Nullable WaterQuality qualityOf(@Nullable ItemStack item) {
        if (item == null || item.getType() != Material.POTION || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer()
                .get(qualityKey, PersistentDataType.STRING);
        return WaterQuality.parse(raw);
    }
}
