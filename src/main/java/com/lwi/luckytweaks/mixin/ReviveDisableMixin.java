package com.lwi.luckytweaks.mixin;

import com.lwi.luckytweaks.SharedLives;
import com.lwi.luckytweaks.TweaksConfig;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hard-disables PlayerRevive when our {@code enablePlayerRevive} toggle is OFF (the default). PlayerRevive
 * gates its WHOLE bleeding/revive system behind the static {@code ReviveEventServer.isReviveActive(Entity)},
 * which its {@code playerDied(LivingDeathEvent)} handler (priority HIGHEST) checks BEFORE it cancels the
 * death. Forcing it to {@code false} here means PlayerRevive never cancels the death, never starts bleeding,
 * sends no chat messages and attaches no bleeding capability — the player just dies the vanilla way (no
 * "lying down", no "is bleeding out", no second death after the bleed timer).
 *
 * <p>Replaces the earlier {@code LivingDeathEvent} un-cancel approach, which let PlayerRevive run first and
 * only undid it afterwards — leaving the bleeding side effects + a delayed re-death. Targets the class by
 * name ({@code remap=false}, {@code require=0}) so Lucky Tweaks is inert when PlayerRevive is absent, exactly
 * like {@code YakurumTooltipGuardMixin}.
 */
@Mixin(targets = "team.creative.playerrevive.server.ReviveEventServer", remap = false)
public class ReviveDisableMixin {

    @Inject(
            method = "isReviveActive(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void luckytweaks$disableRevive(Entity player, CallbackInfoReturnable<Boolean> cir) {
        // Second case: a team wipe is being executed (the shared pool just ran dry). PlayerRevive must not
        // catch those deaths and knock everyone down instead of letting the run end.
        if (!TweaksConfig.ENABLE_PLAYER_REVIVE.get() || SharedLives.isGameOver()) {
            cir.setReturnValue(false);
        }
    }
}
