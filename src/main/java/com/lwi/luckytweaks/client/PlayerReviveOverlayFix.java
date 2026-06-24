package com.lwi.luckytweaks.client;

import com.lwi.luckytweaks.LuckyTweaksMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Works around a PlayerRevive rendering bug. Its bleeding overlay handler
 * ({@code ReviveEventClient#tick(RenderGuiOverlayEvent.Post)}) runs on EVERY
 * {@code RenderGuiOverlayEvent.Post} — i.e. once per HUD layer — without checking which layer just drew.
 * So the "Wait for help / Time left" text is redrawn once per active overlay, which reads as doubled/bold
 * text (and gets worse the more HUD layers a pack adds — e.g. our Lucky XP / Lucky Stats / radar overlays).
 *
 * <p>Fix (see {@code mixin.ReviveOverlayDedupeMixin}): let PlayerRevive's handler draw only on the FIRST
 * overlay layer of each frame, and reset the gate here at the start of the next frame's HUD. Entirely
 * inert when PlayerRevive isn't installed (the mixin then never applies; this gate just flips an unused
 * boolean once a frame).
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID, value = Dist.CLIENT)
public final class PlayerReviveOverlayFix {
    private static boolean drawnThisFrame;

    private PlayerReviveOverlayFix() {}

    /** Called from the mixin at the head of PlayerRevive's overlay handler; returns true to skip this layer. */
    public static boolean shouldSkipThisLayer() {
        if (drawnThisFrame) {
            return true;
        }
        drawnThisFrame = true;
        return false;
    }

    @SubscribeEvent
    public static void onPreGui(RenderGuiEvent.Pre event) {
        drawnThisFrame = false;
    }
}
