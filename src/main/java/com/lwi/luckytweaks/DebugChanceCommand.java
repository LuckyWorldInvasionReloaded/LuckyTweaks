package com.lwi.luckytweaks;

import com.lwi.luckytweaks.util.LuckyBlocks;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * DEV/TEST command {@code /luckychance} (op 2): adds/removes a "debug" CHANCE contribution to the
 * player so the per-block luck % can be validated without grinding the ring.
 *
 * <ul>
 *   <li>{@code /luckychance <delta>} -- add (or remove, if negative) percentile points</li>
 *   <li>{@code /luckychance reset} -- back to 0</li>
 *   <li>{@code /luckychance} -- show the current debug chance + the full Luck modifier total</li>
 * </ul>
 *
 * <p>Lives in Lucky Tweaks because the luck system is Lucky Tweaks'. It stores its value in the shared
 * {@code luck_modifiers} compound via Lucky Stats (so it shows in the HUD, summed with ring + malus)
 * and applies it at break time through {@link com.lwi.luckytweaks.api.LuckyTweaksApi#addChance}.
 * All Lucky Stats access is isolated in {@link DebugChanceStats}, reached only when that mod is
 * loaded. NOT shipped balance -- remove or keep op-gated before release.
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID)
public final class DebugChanceCommand {
    private DebugChanceCommand() {}

    private static boolean statsLoaded() {
        return ModList.get().isLoaded("luckystats");
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("luckychance")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> showState(ctx.getSource()))
                        .then(Commands.literal("reset").executes(ctx -> {
                            if (!requireStats(ctx.getSource())) {
                                return 0;
                            }
                            DebugChanceStats.reset(ctx.getSource().getPlayerOrException());
                            return showState(ctx.getSource());
                        }))
                        .then(Commands.argument("delta", IntegerArgumentType.integer(-200, 200))
                                .executes(ctx -> {
                                    if (!requireStats(ctx.getSource())) {
                                        return 0;
                                    }
                                    DebugChanceStats.add(ctx.getSource().getPlayerOrException(),
                                            IntegerArgumentType.getInteger(ctx, "delta"));
                                    return showState(ctx.getSource());
                                }))
                        .then(Commands.literal("debug").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            boolean on = DebugReport.toggle(player);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Per-break luck debug " + (on ? "ON" : "OFF")).withStyle(ChatFormatting.AQUA), false);
                            return Command.SINGLE_SUCCESS;
                        })));
    }

    private static boolean requireStats(CommandSourceStack source) {
        if (!statsLoaded()) {
            source.sendFailure(Component.literal("Lucky Stats is not loaded; /luckychance needs it."));
            return false;
        }
        return true;
    }

    private static int showState(CommandSourceStack source) throws CommandSyntaxException {
        if (!requireStats(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        int debug = DebugChanceStats.get(player);
        int total = DebugChanceStats.total(player);
        source.sendSuccess(() -> Component.literal(
                        "Debug chance = " + sign(debug) + "%   |   total Luck modifier = " + sign(total) + "%")
                .withStyle(ChatFormatting.AQUA), false);
        return Command.SINGLE_SUCCESS;
    }

    /** Apply the player's debug chance at HIGH (after Lucky Tweaks resets the state at HIGHEST). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLuckyBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!LuckyBlocks.isLuckyBlock(event.getState()) || !statsLoaded()) {
            return;
        }
        DebugChanceStats.apply(player);
    }

    private static String sign(int value) {
        return (value >= 0 ? "+" : "") + value;
    }
}
