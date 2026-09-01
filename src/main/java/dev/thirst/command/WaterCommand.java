package dev.thirst.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.thirst.ThirstPlugin;
import dev.thirst.water.ChunkArea;
import dev.thirst.water.ChunkKey;
import dev.thirst.water.WaterQuality;
import dev.thirst.water.WaterQualityService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * {@code /water} — admin painting of per-chunk water quality.
 *
 * <pre>
 *   /water get
 *   /water setchunk &lt;clean|dirty&gt;
 *   /water setregion &lt;clean|dirty&gt; &lt;x1&gt; &lt;z1&gt; &lt;x2&gt; &lt;z2&gt; [world]
 * </pre>
 *
 * <p>{@code get} / {@code setchunk} act on the chunk the sender is standing in
 * (players only). {@code setregion} takes two block-coordinate corners and works
 * from the console too when a world is given.
 */
public final class WaterCommand {

    public static final String PERMISSION = "thirst.admin.water";

    private WaterCommand() {
    }

    public static com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> build(ThirstPlugin plugin) {
        WaterQualityService water = plugin.water();

        var lastCorner = Commands.argument("z2", IntegerArgumentType.integer())
                .executes(ctx -> setRegion(ctx, plugin, water, null))
                .then(Commands.argument("world", ArgumentTypes.world())
                        .executes(ctx -> setRegion(ctx, plugin, water, ctx.getArgument("world", World.class))));

        var setRegion = Commands.literal("setregion")
                .then(qualityArgument()
                        .then(Commands.argument("x1", IntegerArgumentType.integer())
                                .then(Commands.argument("z1", IntegerArgumentType.integer())
                                        .then(Commands.argument("x2", IntegerArgumentType.integer())
                                                .then(lastCorner)))));

        return Commands.literal("water")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("get")
                        .executes(ctx -> get(ctx, water)))
                .then(Commands.literal("setchunk")
                        .then(qualityArgument()
                                .executes(ctx -> setChunk(ctx, water))))
                .then(setRegion)
                .build();
    }

    // ── Subcommands ─────────────────────────────────────────────────────────

    private static int get(CommandContext<CommandSourceStack> ctx, WaterQualityService water) {
        Player player = playerOrNull(ctx);
        if (player == null) {
            return fail(ctx, "Run /water get as a player.");
        }
        ChunkKey key = ChunkKey.fromLocation(player.getLocation());
        WaterQuality quality = water.qualityAt(key);
        boolean explicit = water.isExplicitlySet(key);
        ctx.getSource().getSender().sendMessage(Component.text(
                "Chunk (" + key.x() + ", " + key.z() + ") in " + key.worldName() + ": ",
                NamedTextColor.GRAY)
                .append(Component.text(quality.name(), colourOf(quality)))
                .append(Component.text(explicit ? "" : " (default)", NamedTextColor.DARK_GRAY)));
        return Command.SINGLE_SUCCESS;
    }

    private static int setChunk(CommandContext<CommandSourceStack> ctx, WaterQualityService water) {
        Player player = playerOrNull(ctx);
        if (player == null) {
            return fail(ctx, "Run /water setchunk as a player.");
        }
        WaterQuality quality = quality(ctx);
        if (quality == null) {
            return fail(ctx, "Water quality must be 'clean' or 'dirty'.");
        }
        ChunkKey key = ChunkKey.fromLocation(player.getLocation());
        water.setChunk(key, quality);
        ctx.getSource().getSender().sendMessage(Component.text(
                "Set chunk (" + key.x() + ", " + key.z() + ") in " + key.worldName() + " to ",
                NamedTextColor.GREEN)
                .append(Component.text(quality.name(), colourOf(quality))));
        return Command.SINGLE_SUCCESS;
    }

    private static int setRegion(CommandContext<CommandSourceStack> ctx, ThirstPlugin plugin,
                                 WaterQualityService water, @Nullable World explicitWorld) {
        WaterQuality quality = quality(ctx);
        if (quality == null) {
            return fail(ctx, "Water quality must be 'clean' or 'dirty'.");
        }

        World world = explicitWorld;
        if (world == null) {
            Player player = playerOrNull(ctx);
            if (player == null) {
                return fail(ctx, "Specify a world: /water setregion <clean|dirty> <x1> <z1> <x2> <z2> <world>");
            }
            world = player.getWorld();
        }

        ChunkArea area = ChunkArea.ofBlocks(world.getName(),
                IntegerArgumentType.getInteger(ctx, "x1"),
                IntegerArgumentType.getInteger(ctx, "z1"),
                IntegerArgumentType.getInteger(ctx, "x2"),
                IntegerArgumentType.getInteger(ctx, "z2"));

        long count = area.chunkCount();
        int cap = plugin.config().water().maxRegionChunks();
        if (count > cap) {
            return fail(ctx, "That region is " + count + " chunks; the cap (water.max-region-chunks) is " + cap + ".");
        }

        int affected = water.setRegion(area, quality);
        ctx.getSource().getSender().sendMessage(Component.text(
                "Set " + affected + " chunk" + (affected == 1 ? "" : "s") + " in " + world.getName() + " to ",
                NamedTextColor.GREEN)
                .append(Component.text(quality.name(), colourOf(quality))));
        return Command.SINGLE_SUCCESS;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static RequiredArgumentBuilder<CommandSourceStack, String> qualityArgument() {
        return Commands.argument("quality", StringArgumentType.word())
                .suggests(WaterCommand::suggestQuality);
    }

    private static CompletableFuture<Suggestions> suggestQuality(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String prefix = builder.getRemainingLowerCase();
        for (String option : new String[]{"clean", "dirty"}) {
            if (option.startsWith(prefix)) {
                builder.suggest(option);
            }
        }
        return builder.buildFuture();
    }

    private static @Nullable WaterQuality quality(CommandContext<CommandSourceStack> ctx) {
        return WaterQuality.parse(StringArgumentType.getString(ctx, "quality"));
    }

    private static @Nullable Player playerOrNull(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getExecutor() instanceof Player player ? player : null;
    }

    private static NamedTextColor colourOf(WaterQuality quality) {
        return quality == WaterQuality.CLEAN ? NamedTextColor.AQUA : NamedTextColor.GOLD;
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String message) {
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
        return 0;
    }
}
