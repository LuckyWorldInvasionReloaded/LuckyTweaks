package com.lwi.luckytweaks;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * DEV/TEST command {@code /luckylives} (op 2) for the shared-lives pool: read it, refill it, or spend
 * lives on demand. Needed because the pool has no HUD yet — and because a feature that intercepts death in
 * a hardcore pack must be testable without actually throwing a real run away.
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID)
public final class SharedLivesCommand {

    private SharedLivesCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("luckylives")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("reset").executes(ctx -> setUsed(ctx.getSource(), 0)))
                .then(Commands.literal("used")
                        .then(Commands.argument("count", IntegerArgumentType.integer(0, 100))
                                .executes(ctx -> setUsed(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"))))));
    }

    private static int status(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        int left = SharedLives.remaining(server);
        int max = SharedLives.maxLives(server);
        String mode = server.isPublished() ? "multiplayer" : "singleplayer";
        src.sendSuccess(() -> Component.literal(
                        left + "/" + max + " shared lives left (" + mode + " allowance)")
                .withStyle(ChatFormatting.GOLD), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setUsed(CommandSourceStack src, int used) {
        MinecraftServer server = src.getServer();
        SharedLives.setUsed(server, used);
        com.lwi.luckytweaks.net.SharedLivesNet.broadcast(server);   // refresh the hearts HUD
        return status(src);
    }
}
