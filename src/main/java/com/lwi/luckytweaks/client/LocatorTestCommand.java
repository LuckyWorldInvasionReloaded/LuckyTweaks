package com.lwi.luckytweaks.client;

import com.lwi.luckytweaks.LuckyTweaksMod;
import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only dev helper: {@code /lwlocatortest} toggles a ring of fake players on the locator bar so
 * the overlay can be eyeballed in singleplayer (colour per player, distance fade, bearing vs. yaw).
 * Forge event bus (client commands are registered there), client dist only.
 */
// Player Locator is WIP and not ready: the /lwlocatortest dev command is NOT registered for 1.2.14.
// The class is kept for a future release -- un-comment the annotation below to re-enable it.
// @Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID, value = Dist.CLIENT)
public final class LocatorTestCommand {
    private LocatorTestCommand() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lwlocatortest")
                .executes(ctx -> {
                    LocatorOverlay.toggleTest();
                    return Command.SINGLE_SUCCESS;
                }));
    }
}
