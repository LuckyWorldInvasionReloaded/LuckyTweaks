package com.lwi.luckytweaks.client;

import com.lwi.luckytweaks.LuckyTweaksMod;
import com.lwi.luckytweaks.seal.SealDisplay;
import com.lwi.luckytweaks.seal.SealService;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only wiring: register {@link LuckyTweaksConfigScreen} as the "Config" button target in the
 * Mods list. With our own factory present, Configured leaves Lucky Tweaks to us (it still serves the
 * sibling mods) -- which is what lets us show auto-detected per-block checkboxes instead of a flat
 * config page. The two advanced lists (luckCaps, harmfulMarkers) stay editable in the config file.
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LuckyTweaksClient {
    private LuckyTweaksClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ModLoadingContext.get().registerExtensionPoint(
                    ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory(
                            (mc, parent) -> new LuckyTweaksConfigScreen(parent)));
            // Run-integrity seal shows as a section on the Lucky Stats screen (when that mod is present).
            if (SealService.statsLoaded()) {
                SealDisplay.register();
            }
        });
    }
}
