package dev.thirst.config;

import dev.thirst.water.WaterQuality;
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
     * the {@code /water setregion} safety cap, and the (later-phase)
     * water-quality wand tool.
     *
     * @param detectWater         scan an unpainted chunk for surface water on first query
     * @param naturalWaterQuality quality of an unpainted chunk that has water (detection on)
     */
    public record Water(
            boolean detectWater,
            WaterQuality naturalWaterQuality,
            int maxRegionChunks,
            boolean wandEnabled,
            String wandMaterial
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

    /** Bottle-based drinking and the optional rain trickle. */
    public record Drinking(
            int cooldownTicks,
            int freshWaterRestoreAmount,
            int neutralWaterRestoreAmount,
            int dirtyWaterRestoreAmount,
            double salmonellaChance,
            boolean rainCauldronAlwaysDirty,
            boolean dirtyWhileRaining,
            boolean rainTrickleEnabled,
            int rainTrickleAmount,
            int rainTrickleIntervalSeconds,
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
                waterSec.getBoolean("wand-enabled", true),
                waterSec.getNonBlankString("wand-material", "BLAZE_ROD")
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
                drinkSec.getBoolean("rain-trickle-enabled", false),
                drinkSec.getInt("rain-trickle-amount", 1, 0, max),
                drinkSec.getInt("rain-trickle-interval-seconds", 20, 1, 3600),
                drinkSec.getNonBlankString("fresh-bottle-name", "<aqua>Bottle of Freshwater"),
                drinkSec.getNonBlankString("neutral-bottle-name", "<gray>Bottle of Water"),
                drinkSec.getNonBlankString("dirty-bottle-name", "<color:#8a7f4a>Bottle of Dirty Water"),
                drinkSec.getNonBlankString("fresh-bucket-name", "<aqua>Bucket of Freshwater"),
                drinkSec.getNonBlankString("neutral-bucket-name", "<gray>Bucket of Water"),
                drinkSec.getNonBlankString("dirty-bucket-name", "<color:#8a7f4a>Bucket of Dirty Water")
        );

        Reader salSec = root.section("salmonella");
        Reader salDamageSec = salSec.section("damage");
        Salmonella salmonella = new Salmonella(
                salSec.getInt("decay-interval-seconds", 300, 1, 86_400),
                salSec.getStackDurations("stack-durations-seconds", DEFAULT_STACK_DURATIONS),
                salSec.getBoolean("cure-with-milk", true),
                salSec.getDouble("milk-cure-chance", 0.5, 0.0, 1.0),
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
                defaultQuality, water, thirst, drinking, salmonella
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
