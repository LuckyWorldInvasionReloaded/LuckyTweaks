package com.lwi.luckytweaks.achievements;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * {@code /luckyachievements} — read the raw counters behind the advancement tab.
 *
 * <p>The advancement screen shows what you have EARNED; this shows where you ARE, which is what a player
 * chasing "200 broken" actually wants, and what a pack author needs to check a threshold without grinding
 * to it. Readable by anyone for themselves (permission 0), because it is their own progress; inspecting
 * another player, or writing a counter, is op-only.
 *
 * <p>{@code set} exists for the same reason {@code /luckylives} has one: an achievement ladder that only
 * moves by playing hundreds of hours is untestable otherwise. It writes the counter and immediately
 * re-offers it, so the matching advancements are granted on the spot.
 */
@Mod.EventBusSubscriber(modid = com.lwi.luckytweaks.LuckyTweaksMod.MODID)
public final class LuckyAchievementsCommand {

    private LuckyAchievementsCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("luckyachievements")
                .executes(ctx -> report(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> report(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))
                .then(Commands.literal("set")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("stat", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (String stat : STATS) {
                                                builder.suggest(stat);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("value", IntegerArgumentType.integer(-1000000, 1000000))
                                                .executes(ctx -> set(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "stat"),
                                                        IntegerArgumentType.getInteger(ctx, "value"))))))));
    }

    /** The counters shown by the report, in reading order. */
    private static final String[] STATS = {
            AchievementData.BROKEN,
            AchievementData.BROKEN_TYPES,
            AchievementData.MAX_LUCK_BREAKS,
            AchievementData.NEGATIVE_LUCK_BREAKS,
            AchievementData.LEGENDARY,
            AchievementData.FUSED,
            AchievementData.CRAFTED_LUCK_MAX,
            AchievementData.CRAFTED_LUCK_MIN,
    };

    private static int report(CommandSourceStack src, ServerPlayer player) {
        AchievementData data = AchievementData.get(src.getServer());
        src.sendSuccess(() -> Component.literal(player.getGameProfile().getName() + " — lucky progress")
                .withStyle(ChatFormatting.GOLD), false);
        for (String stat : STATS) {
            int value = data.count(player, stat);
            src.sendSuccess(() -> Component.literal("  " + label(stat) + ": ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE)), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int set(CommandSourceStack src, ServerPlayer player, String stat, int value) {
        boolean known = false;
        for (String s : STATS) {
            if (s.equals(stat)) {
                known = true;
                break;
            }
        }
        if (!known) {
            src.sendFailure(Component.literal("Unknown counter: " + stat));
            return 0;
        }
        AchievementData data = AchievementData.get(src.getServer());
        // Write the exact value asked for: raise/lower would refuse to move a personal best downward,
        // which is the one thing a tester needs (re-checking a tier they already passed).
        int current = data.count(player, stat);
        data.increment(player, stat, value - current);
        LuckyTriggers.PROGRESS.fire(player, stat, value);
        src.sendSuccess(() -> Component.literal(
                        player.getGameProfile().getName() + " " + label(stat) + " = " + value)
                .withStyle(ChatFormatting.GOLD), true);
        return Command.SINGLE_SUCCESS;
    }

    private static String label(String stat) {
        return stat.replace('_', ' ').toLowerCase(Locale.ROOT);
    }
}
