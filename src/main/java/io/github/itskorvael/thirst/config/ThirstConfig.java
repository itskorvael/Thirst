package io.github.itskorvael.thirst.config;

import io.github.itskorvael.thirst.water.WaterQuality;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Immutable, validated snapshot of {@code config.yml}.
 *
 * <p>A new snapshot is built on enable and on {@code /thirst reload}; live code
 * reads the current snapshot through {@code ThirstPlugin#config()}. Every value
 * is range-checked at load time — invalid entries log a warning and fall back to
 * the built-in default rather than propagating a bad number into gameplay.
 */
public record ThirstConfig(
        int configVersion,
        HudMode hudMode,
        boolean actionbarOverwriteOthers,
        String resourcePackUrl,
        Hud hud,
        WaterQuality defaultWaterQuality,
        Water water,
        Thirst thirst,
        Drinking drinking,
        Filter filter,
        Salmonella salmonella
) {

    /** HUD rendering tuning (see the {@code hud:} section of config.yml). */
    public record Hud(
            int updateIntervalTicks,
            BarStyle style,
            int barSegments,
            String colorNormal,
            String colorCritical,
            String colorInfected,
            boolean salmonellaIndicator,
            long yieldWindowMillis,
            String label,
            ExternalSegment externalSegment
    ) {}

    /**
     * Optional extra text shown alongside the action-bar thirst bar, pulled from
     * a PlaceholderAPI placeholder — lets Thirst and another action-bar plugin
     * (e.g. RealisticSeasons) share one line instead of clobbering each other.
     * Action-bar mode only.
     *
     * @param enabled     whether to resolve and show the segment
     * @param placeholder PlaceholderAPI string, e.g. {@code "%rs_temperaturecolor%%rs_temperature%"}
     * @param placeBefore {@code true} = external text then thirst bar; {@code false} = the reverse
     * @param separator   MiniMessage string placed between the two
     */
    public record ExternalSegment(
            boolean enabled,
            String placeholder,
            boolean placeBefore,
            String separator
    ) {}

    /**
     * The {@code water:} section — natural-water detection for unpainted chunks,
     * the {@code /water setregion} safety cap, and placed-water tracking.
     *
     * @param detectWater              scan an unpainted chunk for surface water on first query
     * @param naturalWaterQuality      quality of an unpainted chunk that has water (detection on)
     * @param trackPlacedWater         remember the quality of water poured from tagged buckets
     * @param trackedBlockLimit        most poured-water blocks remembered; the oldest are dropped past this
     * @param placedWaterPruneSeconds  how often tracked blocks are checked for having dried up
     */
    public record Water(
            boolean detectWater,
            WaterQuality naturalWaterQuality,
            int maxRegionChunks,
            boolean trackPlacedWater,
            int trackedBlockLimit,
            int placedWaterPruneSeconds
    ) {}

    /** Core thirst drain / damage numbers. */
    public record Thirst(
            int max,
            int baseDrainIntervalSeconds,
            int criticalThresholdPercent,
            boolean disableSprintWhenCritical,
            double criticalSprintSpeedMultiplier,
            boolean starvationDamageEnabled,
            double starvationDamageAmount,
            int starvationDamageIntervalSeconds,
            double starvationMinimumHealth,
            double exhaustionThreshold,
            Exhaustion exhaustion
    ) {
        /**
         * Absolute thirst value at or below which the HUD switches to the
         * critical colour and (if {@link #disableSprintWhenCritical()}) sprinting
         * is blocked.
         */
        public double criticalLevel() {
            return max * (criticalThresholdPercent / 100.0);
        }
    }

    /** Per-action exhaustion contributions, mirroring vanilla hunger. */
    public record Exhaustion(
            double perSprintMeter,
            double perWalkMeter,
            double perSwimMeter,
            double jump,
            double sprintJump,
            double attack,
            double breakBlock,
            double takeDamage,
            double netherPerSecond
    ) {}

    /** Bottle-based drinking: restore amounts, Salmonella chance, rain rules, item names. */
    public record Drinking(
            int cooldownTicks,
            int freshWaterRestoreAmount,
            int neutralWaterRestoreAmount,
            int dirtyWaterRestoreAmount,
            double salmonellaChance,
            boolean rainCauldronAlwaysDirty,
            boolean dirtyWhileRaining,
            String freshBottleName,
            String neutralBottleName,
            String dirtyBottleName,
            String freshBucketName,
            String neutralBucketName,
            String dirtyBucketName
    ) {
        public int restoreFor(WaterQuality quality) {
            return switch (quality) {
                case FRESH -> freshWaterRestoreAmount;
                case NEUTRAL -> neutralWaterRestoreAmount;
                case DIRTY -> dirtyWaterRestoreAmount;
            };
        }

        public String bottleName(WaterQuality quality) {
            return switch (quality) {
                case FRESH -> freshBottleName;
                case NEUTRAL -> neutralBottleName;
                case DIRTY -> dirtyBottleName;
            };
        }

        public String bucketName(WaterQuality quality) {
            return switch (quality) {
                case FRESH -> freshBucketName;
                case NEUTRAL -> neutralBucketName;
                case DIRTY -> dirtyBucketName;
            };
        }
    }

    /**
     * The crafted Pitcher Filter: a limited-use item that lifts a water bottle or
     * bucket one quality step (DIRTY to NEUTRAL, and NEUTRAL to FRESH only when
     * {@code maxQuality} allows it).
     *
     * @param enabled      whether the recipe and the item's use are active
     * @param uses         uses a freshly crafted filter has; a bottle costs 1
     * @param bucketCost   uses one bucket costs
     * @param cooldownTicks minimum ticks between two filterings by the same player
     * @param maxQuality   the best quality a filter can produce
     * @param name         item name (MiniMessage)
     * @param loreUses     lore line showing the remaining uses, {@code {uses}} is replaced
     * @param loreHint     lore line explaining how to use it
     * @param recipe       shapeless crafting ingredients, as material names (1 to 9)
     */
    public record Filter(
            boolean enabled,
            int uses,
            int bucketCost,
            int cooldownTicks,
            WaterQuality maxQuality,
            String name,
            String loreUses,
            String loreHint,
            List<String> recipe,
            Recharge recharge
    ) {
        public Filter {
            recipe = List.copyOf(recipe);
        }
    }

    /**
     * Recharging a Pitcher Filter in the crafting grid: the filter, {@code fragments} marked fragments (see
     * {@code key}) and one charcoal give the same filter back with {@code addUses} more uses, up to {@code maxUses}.
     * The fragments come from another plugin (Archaeology's Eggshell Fragment), so they are recognised by a key in the
     * item's persistent data on top of its material.
     *
     * @param enabled   whether the recharge recipe exists
     * @param item      material of the fragment (must match the other plugin's item)
     * @param key       persistent-data key (namespace:key) that marks a real fragment
     * @param fragments fragments used per recharge (1 to 6)
     * @param addUses   uses added per recharge
     * @param maxUses   a filter can't be recharged past this many uses
     */
    public record Recharge(
            boolean enabled,
            String item,
            String key,
            int fragments,
            int addUses,
            int maxUses
    ) {
    }

    /** Salmonella decay, per-stack effect durations, severe-stack damage and cure. */
    public record Salmonella(
            int decayIntervalSeconds,
            List<Integer> stackDurationsSeconds,
            boolean cureWithMilk,
            double milkCureChance,
            int milkCureStacks,
            int nauseaFromStack,
            int vomitFromStack,
            int damageFromStack,
            double damageAmount,
            int damageIntervalSeconds,
            int lethalFromStack
    ) {
        public Salmonella {
            stackDurationsSeconds = List.copyOf(stackDurationsSeconds);
        }

        /** Highest reachable stack count (one per configured duration entry, capped at 5). */
        public int maxStacks() {
            return stackDurationsSeconds.size();
        }

        /**
         * Effect duration, in seconds, applied when the player reaches {@code stack}.
         *
         * @param stack stack level, clamped into {@code [1, maxStacks()]}
         */
        public int durationSecondsForStack(int stack) {
            int index = Math.max(1, Math.min(stack, maxStacks())) - 1;
            return stackDurationsSeconds.get(index);
        }
    }

    // ── Defaults ────────────────────────────────────────────────────────────

    private static final List<Integer> DEFAULT_STACK_DURATIONS = List.of(30, 45, 60, 90, 120);
    /** Default filter recipe: the layers of a real filter, plus the pitcher pod. */
    public static final List<String> DEFAULT_FILTER_RECIPE = List.of("PITCHER_POD", "GRAVEL", "SAND", "CHARCOAL");
    private static final int MAX_FILTER_INGREDIENTS = 9;
    private static final int MAX_SALMONELLA_STACKS = 5;

    /**
     * Builds a snapshot from a loaded {@link FileConfiguration}.
     *
     * @param cfg  the parsed config file
     * @param warn sink for human-readable validation warnings (typically the plugin logger)
     */
    public static ThirstConfig load(FileConfiguration cfg, Consumer<String> warn) {
        Reader root = new Reader(cfg, "", warn);

        int configVersion = root.getInt("config-version", 1, 1, Integer.MAX_VALUE);
        HudMode hudMode = root.getEnum("hud-mode", HudMode.ACTIONBAR_TEXT, HudMode::parse);
        boolean overwrite = root.getBoolean("actionbar-overwrite-others", false);
        String packUrl = root.getString("resource-pack-url", "");

        Reader hudSec = root.section("hud");
        Reader externalSec = hudSec.section("external-segment");
        ExternalSegment externalSegment = new ExternalSegment(
                externalSec.getBoolean("enabled", false),
                externalSec.getString("placeholder", ""),
                externalSec.getBoolean("place-before", true),
                externalSec.getString("separator", " <white>|</white> ")
        );
        Hud hud = new Hud(
                hudSec.getInt("update-interval-ticks", 30, 1, 200),
                hudSec.getEnum("style", BarStyle.BAR, BarStyle::parse),
                hudSec.getInt("bar-segments", 10, 1, 40),
                hudSec.getNonBlankString("color-normal", "<aqua>"),
                hudSec.getNonBlankString("color-critical", "<red>"),
                hudSec.getNonBlankString("color-infected", "<color:#39FF14>"),
                hudSec.getBoolean("salmonella-indicator", true),
                hudSec.getLong("yield-window-millis", 3000L, 0L, 60_000L),
                hudSec.getString("label", "Thirst"),
                externalSegment
        );

        WaterQuality defaultQuality =
                root.getEnum("default-water-quality", WaterQuality.NEUTRAL, WaterQuality::parse);

        Reader waterSec = root.section("water");
        Water water = new Water(
                waterSec.getBoolean("detect-water", true),
                waterSec.getEnum("natural-water-quality", WaterQuality.DIRTY, WaterQuality::parse),
                waterSec.getInt("max-region-chunks", 50_000, 1, 10_000_000),
                waterSec.getBoolean("track-placed-water", true),
                waterSec.getInt("tracked-block-limit", 100_000, 100, 5_000_000),
                waterSec.getInt("placed-water-prune-seconds", 30, 5, 3600)
        );

        Reader thirstSec = root.section("thirst");
        int max = thirstSec.getInt("max", 20, 1, 1000);
        Reader exhSec = thirstSec.section("exhaustion");
        Exhaustion exhaustion = new Exhaustion(
                exhSec.getDouble("per-sprint-meter", 0.1, 0.0, 100.0),
                exhSec.getDouble("per-walk-meter", 0.0, 0.0, 100.0),
                exhSec.getDouble("per-swim-meter", 0.015, 0.0, 100.0),
                exhSec.getDouble("jump", 0.05, 0.0, 100.0),
                exhSec.getDouble("sprint-jump", 0.2, 0.0, 100.0),
                exhSec.getDouble("attack", 0.1, 0.0, 100.0),
                exhSec.getDouble("break-block", 0.005, 0.0, 100.0),
                exhSec.getDouble("take-damage", 0.1, 0.0, 100.0),
                exhSec.getDouble("nether-per-second", 0.05, 0.0, 100.0)
        );
        Thirst thirst = new Thirst(
                max,
                thirstSec.getInt("base-drain-interval-seconds", 120, 1, 86_400),
                thirstSec.getInt("critical-threshold-percent", 10, 0, 100),
                thirstSec.getBoolean("disable-sprint-when-critical", true),
                thirstSec.getDouble("critical-sprint-speed-multiplier", 0.75, 0.1, 1.0),
                thirstSec.getBoolean("starvation-damage-enabled", true),
                thirstSec.getDouble("starvation-damage-amount", 1.0, 0.0, 1024.0),
                thirstSec.getInt("starvation-damage-interval-seconds", 4, 1, 3600),
                thirstSec.getDouble("starvation-minimum-health", 1.0, 0.0, 1024.0),
                thirstSec.getDouble("exhaustion-threshold", 4.0, 0.01, 1000.0),
                exhaustion
        );

        Reader drinkSec = root.section("drinking");
        Drinking drinking = new Drinking(
                drinkSec.getInt("cooldown-ticks", 40, 0, 72_000),
                drinkSec.getInt("fresh-water-restore-amount", 4, 0, max),
                drinkSec.getInt("neutral-water-restore-amount", 3, 0, max),
                drinkSec.getInt("dirty-water-restore-amount", 2, 0, max),
                drinkSec.getDouble("salmonella-chance", 0.65, 0.0, 1.0),
                drinkSec.getBoolean("rain-cauldron-always-dirty", true),
                drinkSec.getBoolean("dirty-while-raining", true),
                drinkSec.getNonBlankString("fresh-bottle-name", "<aqua>Bottle of Freshwater"),
                drinkSec.getNonBlankString("neutral-bottle-name", "<gray>Bottle of Water"),
                drinkSec.getNonBlankString("dirty-bottle-name", "<color:#8a7f4a>Bottle of Dirty Water"),
                drinkSec.getNonBlankString("fresh-bucket-name", "<aqua>Bucket of Freshwater"),
                drinkSec.getNonBlankString("neutral-bucket-name", "<gray>Bucket of Water"),
                drinkSec.getNonBlankString("dirty-bucket-name", "<color:#8a7f4a>Bucket of Dirty Water")
        );

        Reader filterSec = root.section("filter");
        Reader rechargeSec = filterSec.section("recharge");
        Filter filter = new Filter(
                filterSec.getBoolean("enabled", true),
                filterSec.getInt("uses", 6, 1, 64),
                filterSec.getInt("bucket-cost", 3, 1, 64),
                filterSec.getInt("cooldown-ticks", 30, 0, 72_000),
                filterSec.getEnum("max-quality", WaterQuality.NEUTRAL, WaterQuality::parse),
                filterSec.getNonBlankString("name", "<green>Pitcher Filter"),
                filterSec.getNonBlankString("lore-uses", "<gray>Uses left: <white>{uses}"),
                filterSec.getString("lore-hint", "<dark_gray>Main hand: filter. Off hand: water. Right-click."),
                filterSec.getStringList("recipe", DEFAULT_FILTER_RECIPE, 1, MAX_FILTER_INGREDIENTS),
                new Recharge(
                        rechargeSec.getBoolean("enabled", true),
                        rechargeSec.getNonBlankString("item", "PRISMARINE_SHARD"),
                        rechargeSec.getNonBlankString("key", "archaeology:eggshell_fragment"),
                        rechargeSec.getInt("fragments", 2, 1, 6),
                        rechargeSec.getInt("uses", 6, 1, 64),
                        rechargeSec.getInt("max-uses", 12, 1, 256)
                )
        );

        Reader salSec = root.section("salmonella");
        Reader salDamageSec = salSec.section("damage");
        Salmonella salmonella = new Salmonella(
                salSec.getInt("decay-interval-seconds", 300, 1, 86_400),
                salSec.getStackDurations("stack-durations-seconds", DEFAULT_STACK_DURATIONS),
                salSec.getBoolean("cure-with-milk", true),
                salSec.getDouble("milk-cure-chance", 0.3, 0.0, 1.0),
                salSec.getInt("milk-cure-stacks", 1, 1, MAX_SALMONELLA_STACKS),
                salSec.getInt("nausea-from-stack", 3, 1, MAX_SALMONELLA_STACKS),
                salSec.getInt("vomit-from-stack", 3, 1, MAX_SALMONELLA_STACKS),
                salDamageSec.getInt("from-stack", 4, 1, MAX_SALMONELLA_STACKS),
                salDamageSec.getDouble("amount", 1.0, 0.0, 1024.0),
                salDamageSec.getInt("interval-seconds", 5, 1, 3600),
                salDamageSec.getInt("lethal-from-stack", 5, 1, MAX_SALMONELLA_STACKS)
        );

        return new ThirstConfig(
                configVersion, hudMode, overwrite, packUrl, hud,
                defaultQuality, water, thirst, drinking, filter, salmonella
        );
    }

    // ── Validating reader ───────────────────────────────────────────────────

    /**
     * Thin wrapper over a {@link ConfigurationSection} that clamps values into a
     * valid range and reports every correction through {@code warn}.
     */
    private static final class Reader {
        private final @Nullable ConfigurationSection section;
        private final String prefix;
        private final Consumer<String> warn;

        Reader(@Nullable ConfigurationSection section, String prefix, Consumer<String> warn) {
            this.section = section;
            this.prefix = prefix;
            this.warn = warn;
        }

        Reader section(String path) {
            ConfigurationSection child = section == null ? null : section.getConfigurationSection(path);
            if (section != null && child == null && section.isSet(path)) {
                warn.accept("Config: '" + full(path) + "' should be a section; using defaults for it.");
            }
            return new Reader(child, full(path) + ".", warn);
        }

        private String full(String path) {
            return prefix.isEmpty() ? path : prefix + path;
        }

        boolean getBoolean(String path, boolean def) {
            if (section == null || !section.isSet(path)) {
                return def;
            }
            if (!section.isBoolean(path)) {
                warn.accept("Config: '" + full(path) + "' must be true/false; using " + def + ".");
                return def;
            }
            return section.getBoolean(path);
        }

        String getString(String path, String def) {
            if (section == null || !section.isSet(path)) {
                return def;
            }
            String value = section.getString(path);
            return value == null ? def : value;
        }

        String getNonBlankString(String path, String def) {
            String value = getString(path, def);
            if (value.isBlank()) {
                warn.accept("Config: '" + full(path) + "' must not be blank; using '" + def + "'.");
                return def;
            }
            return value;
        }

        int getInt(String path, int def, int min, int max) {
            long value = getLong(path, def, min, max);
            return (int) value;
        }

        long getLong(String path, long def, long min, long max) {
            if (section == null || !section.isSet(path)) {
                return def;
            }
            if (!section.isInt(path) && !section.isLong(path)) {
                warn.accept("Config: '" + full(path) + "' must be a whole number; using " + def + ".");
                return def;
            }
            long value = section.getLong(path);
            return clampLong(path, value, min, max, def);
        }

        double getDouble(String path, double def, double min, double max) {
            if (section == null || !section.isSet(path)) {
                return def;
            }
            if (!section.isDouble(path) && !section.isInt(path) && !section.isLong(path)) {
                warn.accept("Config: '" + full(path) + "' must be a number; using " + def + ".");
                return def;
            }
            double value = section.getDouble(path);
            if (value < min || value > max) {
                double clamped = Math.max(min, Math.min(max, value));
                warn.accept("Config: '" + full(path) + "' (" + value + ") out of range ["
                        + min + ", " + max + "]; clamped to " + clamped + ".");
                return clamped;
            }
            return value;
        }

        <E> E getEnum(String path, E def, Function<String, E> parser) {
            if (section == null || !section.isSet(path)) {
                return def;
            }
            String raw = section.getString(path);
            E parsed = raw == null ? null : parser.apply(raw);
            if (parsed == null) {
                warn.accept("Config: '" + full(path) + "' has unknown value '" + raw + "'; using " + def + ".");
                return def;
            }
            return parsed;
        }

        List<String> getStringList(String path, List<String> def, int minSize, int maxSize) {
            if (section == null || !section.isSet(path)) {
                return def;
            }
            if (!section.isList(path)) {
                warn.accept("Config: '" + full(path) + "' must be a list; using " + def + ".");
                return def;
            }
            List<String> raw = section.getStringList(path);
            if (raw.size() < minSize) {
                warn.accept("Config: '" + full(path) + "' needs at least " + minSize + " entries; using " + def + ".");
                return def;
            }
            if (raw.size() > maxSize) {
                warn.accept("Config: '" + full(path) + "' has more than " + maxSize + " entries; ignoring the extras.");
                return List.copyOf(raw.subList(0, maxSize));
            }
            return List.copyOf(raw);
        }

        List<Integer> getStackDurations(String path, List<Integer> def) {
            if (section == null || !section.isSet(path)) {
                return def;
            }
            if (!section.isList(path)) {
                warn.accept("Config: '" + full(path) + "' must be a list of numbers; using " + def + ".");
                return def;
            }
            List<Integer> raw = section.getIntegerList(path);
            if (raw.isEmpty()) {
                warn.accept("Config: '" + full(path) + "' is empty; using " + def + ".");
                return def;
            }
            List<Integer> cleaned = new ArrayList<>(Math.min(raw.size(), MAX_SALMONELLA_STACKS));
            for (int entry : raw) {
                if (cleaned.size() == MAX_SALMONELLA_STACKS) {
                    warn.accept("Config: '" + full(path) + "' has more than " + MAX_SALMONELLA_STACKS
                            + " entries; ignoring the extras.");
                    break;
                }
                cleaned.add(entry < 1 ? 1 : entry);
            }
            return List.copyOf(cleaned);
        }

        private long clampLong(String path, long value, long min, long max, long def) {
            if (value < min || value > max) {
                long clamped = Math.max(min, Math.min(max, value));
                warn.accept("Config: '" + full(path) + "' (" + value + ") out of range ["
                        + min + ", " + max + "]; clamped to " + clamped + ".");
                return clamped;
            }
            return value;
        }
    }
}
