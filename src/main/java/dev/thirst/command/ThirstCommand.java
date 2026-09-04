package dev.thirst.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.thirst.ThirstPlugin;
import dev.thirst.salmonella.SalmonellaService;
import dev.thirst.thirst.ThirstManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * {@code /thirst} — status checks for everyone, admin management behind a
 * separate permission:
 *
 * <pre>
 *   /thirst get [&lt;target&gt;]
 *   /thirst set &lt;target&gt; &lt;0..max&gt;
 *   /thirst reload
 *   /thirst salmonella get [&lt;target&gt;]
 *   /thirst salmonella set &lt;target&gt; &lt;stacks&gt;
 *   /thirst salmonella cure &lt;target&gt;
 * </pre>
 *
 * <p>The no-target {@code get} forms report the sender's own status and only
 * need {@link #PERMISSION_USE} (default: everyone). Everything that targets
 * another player, mutates state, or reloads the config needs
 * {@link #PERMISSION_ADMIN} (default: op).
 */
public final class ThirstCommand {

    /** Check your own thirst / Salmonella status. Granted to everyone by default. */
    public static final String PERMISSION_USE = "thirst.command.thirst";

    /** Target other players, mutate thirst/Salmonella state, or reload the plugin. */
    public static final String PERMISSION_ADMIN = "thirst.admin.thirst";

    private static final int TICKS_PER_SECOND = 20;

    private ThirstCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(ThirstPlugin plugin) {
        ThirstManager manager = plugin.thirst();
        SalmonellaService salmonella = plugin.salmonella();

        return Commands.literal("thirst")
                .requires(source -> hasUse(source) || hasAdmin(source))
                .then(Commands.literal("get")
                        .requires(ThirstCommand::hasUse)
                        .executes(ctx -> getSelf(ctx, plugin, manager))
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .requires(ThirstCommand::hasAdmin)
                                .executes(ctx -> getTarget(ctx, plugin, manager))))
                .then(Commands.literal("set")
                        .requires(ThirstCommand::hasAdmin)
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .then(Commands.argument("level", IntegerArgumentType.integer(0))
                                        .executes(ctx -> setLevel(ctx, plugin, manager)))))
                .then(Commands.literal("reload")
                        .requires(ThirstCommand::hasAdmin)
                        .executes(ctx -> reload(ctx, plugin)))
                .then(salmonellaNode(manager, salmonella))
                .build();
    }

    // ── thirst get / set / reload ──────────────────────────────────────────

    private static int getSelf(CommandContext<CommandSourceStack> ctx, ThirstPlugin plugin, ThirstManager manager) {
        Player self = playerOrFail(ctx, "Run /thirst get as a player, or specify a target.");
        if (self == null) {
            return 0;
        }
        sendThirstStatus(ctx, plugin, manager, self);
        return Command.SINGLE_SUCCESS;
    }

    private static int getTarget(CommandContext<CommandSourceStack> ctx, ThirstPlugin plugin, ThirstManager manager)
            throws CommandSyntaxException {
        sendThirstStatus(ctx, plugin, manager, resolvePlayer(ctx, "target"));
        return Command.SINGLE_SUCCESS;
    }

    private static void sendThirstStatus(CommandContext<CommandSourceStack> ctx, ThirstPlugin plugin,
                                         ThirstManager manager, Player target) {
        int max = plugin.config().thirst().max();
        ctx.getSource().getSender().sendMessage(Component.text(
                target.getName() + " — thirst " + manager.getLevel(target) + "/" + max
                        + ", exhaustion " + String.format(Locale.ROOT, "%.2f", manager.getExhaustion(target))
                        + ", salmonella " + manager.getSalmonellaStacks(target),
                NamedTextColor.AQUA));
    }

    private static int setLevel(CommandContext<CommandSourceStack> ctx, ThirstPlugin plugin, ThirstManager manager)
            throws CommandSyntaxException {
        Player target = resolvePlayer(ctx, "target");
        int max = plugin.config().thirst().max();
        int requested = IntegerArgumentType.getInteger(ctx, "level");
        int applied = Math.min(requested, max);
        manager.setLevel(target, applied);

        Component note = applied == requested
                ? Component.empty()
                : Component.text(" (clamped from " + requested + ")", NamedTextColor.GRAY);
        ctx.getSource().getSender().sendMessage(Component.text(
                "Set " + target.getName() + " thirst to " + applied + "/" + max,
                NamedTextColor.GREEN).append(note));
        return Command.SINGLE_SUCCESS;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx, ThirstPlugin plugin) {
        plugin.loadPluginConfig();
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(Component.text("Thirst configuration reloaded.", NamedTextColor.GREEN));

        List<String> warnings = plugin.lastConfigWarnings();
        if (warnings.isEmpty()) {
            sender.sendMessage(Component.text("No config problems found.", NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text(
                    warnings.size() + " config warning(s) (also logged to console):", NamedTextColor.YELLOW));
            for (String warning : warnings) {
                sender.sendMessage(Component.text("  " + warning, NamedTextColor.YELLOW));
            }
        }

        sender.sendMessage(Component.text(plugin.externalSegmentStatus(), NamedTextColor.GRAY));
        var segment = plugin.externalSegment();
        if (segment != null && ctx.getSource().getExecutor() instanceof Player player) {
            sender.sendMessage(Component.text("  " + segment.diagnose(player), NamedTextColor.DARK_GRAY));
        }
        return Command.SINGLE_SUCCESS;
    }

    // ── thirst salmonella get / set / cure ─────────────────────────────────

    private static LiteralCommandNode<CommandSourceStack> salmonellaNode(
            ThirstManager manager, SalmonellaService salmonella) {
        return Commands.literal("salmonella")
                .then(Commands.literal("get")
                        .requires(ThirstCommand::hasUse)
                        .executes(ctx -> salmonellaGetSelf(ctx, manager))
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .requires(ThirstCommand::hasAdmin)
                                .executes(ctx -> salmonellaGetTarget(ctx, manager))))
                .then(Commands.literal("set")
                        .requires(ThirstCommand::hasAdmin)
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .then(Commands.argument("stacks", IntegerArgumentType.integer(0))
                                        .executes(ctx -> salmonellaSet(ctx, salmonella)))))
                .then(Commands.literal("cure")
                        .requires(ThirstCommand::hasAdmin)
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .executes(ctx -> salmonellaCure(ctx, salmonella))))
                .build();
    }

    private static int salmonellaGetSelf(CommandContext<CommandSourceStack> ctx, ThirstManager manager) {
        Player self = playerOrFail(ctx, "Run /thirst salmonella get as a player, or specify a target.");
        if (self == null) {
            return 0;
        }
        sendSalmonellaStatus(ctx, manager, self);
        return Command.SINGLE_SUCCESS;
    }

    private static int salmonellaGetTarget(CommandContext<CommandSourceStack> ctx, ThirstManager manager)
            throws CommandSyntaxException {
        sendSalmonellaStatus(ctx, manager, resolvePlayer(ctx, "target"));
        return Command.SINGLE_SUCCESS;
    }

    private static void sendSalmonellaStatus(CommandContext<CommandSourceStack> ctx, ThirstManager manager, Player target) {
        int stacks = manager.getSalmonellaStacks(target);
        String decay = stacks > 0
                ? " (next decay in " + (manager.getSalmonellaTicksRemaining(target) / TICKS_PER_SECOND) + "s)"
                : "";
        ctx.getSource().getSender().sendMessage(Component.text(
                target.getName() + " — salmonella stacks: " + stacks + decay, NamedTextColor.AQUA));
    }

    private static int salmonellaSet(CommandContext<CommandSourceStack> ctx, SalmonellaService salmonella)
            throws CommandSyntaxException {
        Player target = resolvePlayer(ctx, "target");
        int stacks = IntegerArgumentType.getInteger(ctx, "stacks");
        salmonella.setStacks(target, stacks);
        ctx.getSource().getSender().sendMessage(Component.text(
                "Set " + target.getName() + "'s salmonella stacks to " + stacks
                        + " (clamped to the configured maximum).",
                NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int salmonellaCure(CommandContext<CommandSourceStack> ctx, SalmonellaService salmonella)
            throws CommandSyntaxException {
        Player target = resolvePlayer(ctx, "target");
        salmonella.cureAll(target);
        ctx.getSource().getSender().sendMessage(Component.text(
                "Cured " + target.getName() + "'s salmonella.", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static boolean hasUse(CommandSourceStack source) {
        return source.getSender().hasPermission(PERMISSION_USE);
    }

    private static boolean hasAdmin(CommandSourceStack source) {
        return source.getSender().hasPermission(PERMISSION_ADMIN);
    }

    private static Player resolvePlayer(CommandContext<CommandSourceStack> ctx, String argName)
            throws CommandSyntaxException {
        List<Player> players = ctx.getArgument(argName, PlayerSelectorArgumentResolver.class)
                .resolve(ctx.getSource());
        return players.getFirst();
    }

    private static @Nullable Player playerOrFail(CommandContext<CommandSourceStack> ctx, String message) {
        if (ctx.getSource().getExecutor() instanceof Player player) {
            return player;
        }
        ctx.getSource().getSender().sendMessage(Component.text(message, NamedTextColor.RED));
        return null;
    }
}
