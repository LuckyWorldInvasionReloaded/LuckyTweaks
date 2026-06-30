package com.lwi.luckytweaks.mixin;

import com.lwi.luckytweaks.compat.KittySlippersCompat;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets Artifacts' Kitty Slippers win over Enhanced AI's creeper launch.
 *
 * <p>Enhanced AI's launch goal lets a creeper ignite and hurl itself at its target from 8-12 blocks away --
 * outside the slippers' 6-block flee range -- so a wearer gets blown up before the flee ever takes effect.
 * We force both {@code canUse} ({@code m_8036_}, prevents the launch from starting) and {@code canContinueToUse}
 * ({@code m_8045_}, aborts one mid-charge) to false whenever a Kitty Slippers wearer is the creeper's target
 * or within range; the goal's {@code stop()} then un-ignites the creeper.
 *
 * <p>Targeted by name with {@code remap = false, require = 0}, so this is inert when Enhanced AI is absent.
 */
@Mixin(targets = "insane96mcp.enhancedai.modules.creeper.launch.EAICreeperLaunchGoal", remap = false)
public class EaiCreeperLaunchSlippersMixin {

    @Shadow
    @Final
    protected Creeper launchingCreeper;

    @Inject(method = "m_8036_", at = @At("RETURN"), cancellable = true, require = 0)
    private void luckytweaks_cancelLaunchStartNearKittySlippers(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && KittySlippersCompat.isRepelled(this.launchingCreeper)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "m_8045_", at = @At("RETURN"), cancellable = true, require = 0)
    private void luckytweaks_cancelLaunchContinueNearKittySlippers(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && KittySlippersCompat.isRepelled(this.launchingCreeper)) {
            cir.setReturnValue(false);
        }
    }
}
