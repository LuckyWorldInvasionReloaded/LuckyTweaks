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
 * Lets Artifacts' Kitty Slippers win over Enhanced AI's creeper swell.
 *
 * <p>Enhanced AI's swell goal keeps a creeper swelling at its target even while the slippers try to push it
 * away: its {@code canUse} short-circuits on {@code getSwellDir() > 0}, and the target is re-acquired every
 * tick. We force that {@code canUse} to false whenever a Kitty Slippers wearer is the creeper's target or is
 * within range, so the goal stops and the creeper de-swells (its {@code stop()} sets the swell back to -1).
 * The slippers' own AvoidEntityGoal then makes it flee, exactly as intended.
 *
 * <p>{@code m_8036_} is the SRG name of {@code Goal.canUse()} in the (reobfuscated) Enhanced AI jar; the
 * class is targeted by name with {@code remap = false, require = 0}, so this is inert when Enhanced AI is
 * absent.
 */
@Mixin(targets = "insane96mcp.enhancedai.modules.creeper.swell.EAICreeperSwellGoal", remap = false)
public class EaiCreeperSwellSlippersMixin {

    @Shadow
    @Final
    protected Creeper swellingCreeper;

    @Inject(method = "m_8036_", at = @At("RETURN"), cancellable = true, require = 0)
    private void luckytweaks_cancelSwellNearKittySlippers(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && KittySlippersCompat.isRepelled(this.swellingCreeper)) {
            cir.setReturnValue(false);
        }
    }
}
