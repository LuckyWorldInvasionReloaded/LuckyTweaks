package com.lwi.luckytweaks.seal;

import com.lwi.luckytweaks.LuckyTweaksMod;
import com.lwi.luckytweaks.RunSeal;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server-side triggers for the run seal: evaluate the whole picture when a player joins, and stamp
 * CREATIVE the moment they switch to creative/spectator. (Config changes are handled by
 * {@link SealConfigWatch}.) All work is gated on Lucky Stats being present.
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID)
public final class SealEvents {
    private SealEvents() {}

    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!SealService.statsLoaded() || event.getEntity().level().isClientSide()) {
            return;
        }
        SealService.evaluateOnJoin(event.getEntity());
    }

    @SubscribeEvent
    public static void onGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (!SealService.statsLoaded() || event.getEntity().level().isClientSide()) {
            return;
        }
        GameType mode = event.getNewGameMode();
        if (mode == GameType.CREATIVE || mode == GameType.SPECTATOR) {
            SealService.addReason(event.getEntity(), RunSeal.CREATIVE);
        }
    }
}
