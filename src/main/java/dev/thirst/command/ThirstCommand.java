package dev.thirst.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.thirst.ThirstPlugin;
import dev.thirst.thirst.ThirstManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Minimal {@code /thirst} command for exercising the Phase 2 core:
 *
 * <pre>
 *   /thirst get &lt;player&gt;
 *   /thirst set &lt;player&gt; &lt;0..max&gt;
 *   /thirst reload
 * </pre>
 *
 * <p>Phase 7 replaces this with the full command set (permission split,
 * {@code /water}, richer feedback and validation).
 */
public final class ThirstCommand {

    public static final String PERMISSION = "thirst.admin.thirst";

    private ThirstCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(ThirstPlugin plugin) {
        ThirstManager manager = plugin.thirst();

        return Commands.literal("thirst")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("get")
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .executes(ctx -> {
                                    Player target = resolvePlayer(ctx.getSource(),
                                            ctx.getArgument("target", PlayerSelectorArgumentResolver.class));
                                    int max = plugin.config().thirst().max();
                                    ctx.getSource().getSender().sendMessage(Component.text(
                                            target.getName() + " — thirst " + manager.getLevel(target) + "/" + max
                                                    + ", exhaustion " + String.format(Locale.ROOT, "%.2f", manager.getExhaustion(target))
                                                    + ", salmonella " + manager.getSalmonellaStacks(target),
                                            NamedTextColor.AQUA));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("set")
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .then(Commands.argument("level", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            Player target = resolvePlayer(ctx.getSource(),
                                                    ctx.getArgument("target", PlayerSelectorArgumentResolver.class));
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
                                        }))))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            plugin.loadPluginConfig();
                            var sender = ctx.getSource().getSender();
                            sender.sendMessage(
                                    Component.text("Thirst configuration reloaded.", NamedTextColor.GREEN));
                            sender.sendMessage(
                                    Component.text(plugin.externalSegmentStatus(), NamedTextColor.GRAY));

                            var segment = plugin.externalSegment();
                            if (segment != null && ctx.getSource().getExecutor() instanceof Player player) {
                                sender.sendMessage(Component.text(
                                        "  " + segment.diagnose(player), NamedTextColor.DARK_GRAY));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private static Player resolvePlayer(CommandSourceStack source, PlayerSelectorArgumentResolver resolver)
            throws CommandSyntaxException {
        List<Player> players = resolver.resolve(source);
        return players.getFirst();
    }
}
