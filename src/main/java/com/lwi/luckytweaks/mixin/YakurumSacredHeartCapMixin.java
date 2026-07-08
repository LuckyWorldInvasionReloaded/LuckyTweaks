package com.lwi.luckytweaks.mixin;

import com.lwi.luckytweaks.SacredHeartCap;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enforces the Sacred Heart use cap (see {@link SacredHeartCap}).
 *
 * <p>Yakurum's {@code onItemUseFinish} handler exists for one purpose: when the finished item is a
 * Sacred Heart it calls {@code applyHealthBoost(player, 2)}, the mod's only call site for that method.
 * Cancelling the handler therefore suppresses the permanent {@code MAX_HEALTH} base-value bump and
 * nothing else -- the food's own Regeneration IV and the sibling {@code onFoodEaten} heal both still
 * apply, and no other health source in the pack goes anywhere near the base value.
 *
 * <p>The event fires on both sides. We always cancel on the client so it never predicts a heart the
 * server may refuse; the server's {@code setBaseValue} marks the attribute dirty and pushes a
 * {@code ClientboundUpdateAttributesPacket} on the next tick anyway. Targeted by name with
 * {@code require = 0}, so Lucky Tweaks still loads with Yakurum absent.
 */
@Mixin(targets = "com.sokoly.yakurum.events.YakurumEntityEvents", remap = false)
public class YakurumSacredHeartCapMixin {

    @Inject(
            method = "onItemUseFinish(Lnet/minecraftforge/event/entity/living/LivingEntityUseItemEvent$Finish;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void luckytweaks_capSacredHeartUses(LivingEntityUseItemEvent.Finish event, CallbackInfo ci) {
        if (!(event.getEntity() instanceof Player player) || !SacredHeartCap.isSacredHeart(event.getItem())) {
            return;                                     // not a Sacred Heart -> Yakurum runs untouched
        }
        if (player.level().isClientSide()) {
            ci.cancel();                                // server decides; attributes re-sync next tick
            return;
        }
        if (!SacredHeartCap.tryConsumeUse(player)) {
            ci.cancel();                                // cap reached: no permanent heart
        }
    }
}
