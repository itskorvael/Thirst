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
 * Creates and recognises the quality-tagged water containers: bottles
 * ({@code Bottle of Fresh/Dirty Water}) and buckets ({@code Bucket of
 * Fresh/Dirty Water}).
 *
 * <p>A bottle is a vanilla water potion ({@link PotionType#WATER}) — it keeps
 * the drink animation and the free glass-bottle return — plus a
 * {@link org.bukkit.persistence.PersistentDataContainer} quality tag, a tint, a
 * config name and a {@code USE_COOLDOWN} component. A bucket is a plain
 * {@link Material#WATER_BUCKET} with the same tag and a config name.
 */
public final class WaterContainers {

    private static final Color FRESH_COLOUR = Color.fromRGB(0x3F76E4);
    private static final Color NEUTRAL_COLOUR = Color.fromRGB(0x3E5A9A);
    private static final Color DIRTY_COLOUR = Color.fromRGB(0x6B6246);

    private final NamespacedKey qualityKey;
    private final NamespacedKey cooldownGroup;
    private final Supplier<ThirstConfig> config;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public WaterContainers(Plugin plugin, Supplier<ThirstConfig> config) {
        this.qualityKey = new NamespacedKey(plugin, "water_bottle_quality");
        this.cooldownGroup = new NamespacedKey(plugin, "water_bottle");
        this.config = config;
    }

    // ── Creation ────────────────────────────────────────────────────────────

    /** A single drinkable water bottle of the given quality. */
    @SuppressWarnings("UnstableApiUsage")
    public ItemStack createBottle(WaterQuality quality) {
        ThirstConfig.Drinking drinking = config.get().drinking();
        Component name = miniMessage.deserialize(drinking.bottleName(quality));

        ItemStack item = ItemStack.of(Material.POTION);
        item.editMeta(PotionMeta.class, meta -> {
            meta.setBasePotionType(PotionType.WATER);
            meta.setColor(colourOf(quality));
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

    /** A single water bucket of the given quality. */
    public ItemStack createBucket(WaterQuality quality) {
        Component name = miniMessage.deserialize(config.get().drinking().bucketName(quality));

        ItemStack item = ItemStack.of(Material.WATER_BUCKET);
        item.editMeta(meta -> {
            meta.itemName(name);
            meta.getPersistentDataContainer().set(qualityKey, PersistentDataType.STRING, quality.name());
        });
        return item;
    }

    // ── Recognition ─────────────────────────────────────────────────────────

    /**
     * @return the quality tag on this bottle or bucket, or {@code null} if it is
     *         not one of ours
     */
    public @Nullable WaterQuality qualityOf(@Nullable ItemStack item) {
        if (item == null) {
            return null;
        }
        Material type = item.getType();
        if ((type != Material.POTION && type != Material.WATER_BUCKET) || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer()
                .get(qualityKey, PersistentDataType.STRING);
        return WaterQuality.parse(raw);
    }

    private static Color colourOf(WaterQuality quality) {
        return switch (quality) {
            case FRESH -> FRESH_COLOUR;
            case NEUTRAL -> NEUTRAL_COLOUR;
            case DIRTY -> DIRTY_COLOUR;
        };
    }
}

