package com.lwi.luckytweaks.seal;

import com.lwi.luckytweaks.LuckyTweaksMod;
import com.lwi.luckytweaks.TweaksConfig;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * When the Lucky Tweaks config loads or is changed mid-game (e.g. via the in-game screen), taint
 * every online player's run if the config now differs from defaults. New/offline players are caught
 * by the join evaluation in {@link SealEvents}.
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SealConfigWatch {
    private SealConfigWatch() {}

    @SubscribeEvent
    public static void onConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() != TweaksConfig.COMMON_SPEC) {
            return;
        }
        if (SealService.statsLoaded()) {
            SealService.taintConfigOnline();
        }
    }
}
