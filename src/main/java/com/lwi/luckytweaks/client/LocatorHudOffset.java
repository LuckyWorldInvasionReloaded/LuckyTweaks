package com.lwi.luckytweaks.client;

import com.lwi.luckytweaks.LuckyTweaksMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The "lift the rest of the HUD out of the way" half of the locator's Tab feature. While the name
 * plaques are showing ({@link LocatorOverlay#currentHudOffset()} &gt; 0), the bottom status cluster
 * slides up so the plaques sitting above the XP bar don't cover it.
 *
 * <p>Done with the shared-PoseStack technique (the same one Quark's hotbar swapper and Lucky XP use),
 * NOT a fixed list of vanilla overlays: a "lift" overlay just before the status bars translates the
 * shared HUD matrix up by the offset, and a "restore" overlay just before the XP bar undoes it.
 * Because Forge renders every overlay in one pass without resetting the matrix, EVERYTHING drawn in
 * that window rides up together -- hearts, armour, food, air, AND any other mod's icons drawn on them
 * -- instead of only a hardcoded set. The XP bar and the locator itself (rendered after the restore)
 * stay put, so the markers remain glued to the bar. Composes with Lucky XP's own lift (both just
 * translate the shared matrix); the lift and restore read the same offset in one frame, so the net
 * translate is always balanced.
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LocatorHudOffset {
    private LocatorHudOffset() {}

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        // Lift everything from the status bars up to (not including) the XP bar by the locator's offset.
        event.registerBelow(VanillaGuiOverlay.PLAYER_HEALTH.id(), "locator_lift",
                (gui, g, partialTick, w, h) -> {
                    float offset = LocatorOverlay.currentHudOffset();
                    if (offset > 0f) g.pose().translate(0f, -offset, 0f);
                });
        // Undo it before the XP bar so the bars + the locator markers are not shifted.
        event.registerBelow(VanillaGuiOverlay.EXPERIENCE_BAR.id(), "locator_restore",
                (gui, g, partialTick, w, h) -> {
                    float offset = LocatorOverlay.currentHudOffset();
                    if (offset > 0f) g.pose().translate(0f, offset, 0f);
                });
    }
}
