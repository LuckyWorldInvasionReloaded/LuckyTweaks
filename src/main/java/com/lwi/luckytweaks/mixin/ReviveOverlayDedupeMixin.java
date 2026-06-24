package com.lwi.luckytweaks.mixin;

import com.lwi.luckytweaks.client.PlayerReviveOverlayFix;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PlayerRevive's bleeding overlay handler fires on every {@code RenderGuiOverlayEvent.Post} (once per HUD
 * layer) without filtering the layer, so its text is redrawn once per active overlay — looking doubled/bold.
 * We cancel every call after the first one of the frame, so it draws exactly once (see
 * {@link PlayerReviveOverlayFix}). Targets the class by name (remap=false, require=0) so Lucky Tweaks
 * degrades cleanly when PlayerRevive is absent — exactly like {@code YakurumTooltipGuardMixin}.
 */
@Mixin(targets = "team.creative.playerrevive.client.ReviveEventClient", remap = false)
public class ReviveOverlayDedupeMixin {

    @Inject(
            method = "tick(Lnet/minecraftforge/client/event/RenderGuiOverlayEvent$Post;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void luckytweaks$renderOverlayOnce(RenderGuiOverlayEvent.Post event, CallbackInfo ci) {
        if (PlayerReviveOverlayFix.shouldSkipThisLayer()) {
            ci.cancel();
        }
    }
}
