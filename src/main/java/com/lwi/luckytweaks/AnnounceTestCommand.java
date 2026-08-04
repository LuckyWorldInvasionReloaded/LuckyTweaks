package com.lwi.luckytweaks;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /lwannouncetest [block]} — show both rare-drop chat lines to yourself.
 *
 * <p>The real announcement needs two players connected and fires on a 2 to 5 second delay after a roll
 * that is rare by design, which leaves no practical way to check the wording, the colours or the block
 * name while working alone. This prints them on demand, to the caller only. Op-only, dev helper.
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID)
public final class AnnounceTestCommand {
    private AnnounceTestCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lwannouncetest")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> preview(ctx.getSource().getPlayerOrException(), null))
                .then(Commands.argument("block", ResourceLocationArgument.id())
                        .executes(ctx -> preview(ctx.getSource().getPlayerOrException(),
                                ResourceLocationArgument.getId(ctx, "block")))));
    }

    private static int preview(ServerPlayer player, ResourceLocation blockId) {
        player.sendSystemMessage(RareDropAnnouncer.preview(player, blockId, false));
        player.sendSystemMessage(RareDropAnnouncer.preview(player, blockId, true));
        return Command.SINGLE_SUCCESS;
    }
}
