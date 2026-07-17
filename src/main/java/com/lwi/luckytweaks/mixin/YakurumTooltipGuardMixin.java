package com.lwi.luckytweaks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Crash guard for a Yakurum x LootBeams conflict (NPE "Exception ticking world", and silently lost drops).
 *
 * <p>Yakurum's tooltip handlers all funnel through {@code addOnShift(tooltip, lambda)}, which runs the
 * lambda when Shift is held. Those lambdas do {@code event.getEntity().level()}. LootBeams builds an
 * item's tooltip with NO player ({@code ItemStack.getTooltipLines(null, ...)}) the instant an ItemEntity
 * joins the world, so {@code getEntity()} is null and the lambda throws a NullPointerException on the
 * (integrated) server thread. It fires whenever a Yakurum item drops while someone holds Shift.
 *
 * <p>Yakurum funnels through that helper from TWO classes, and they must BOTH be guarded: the armor
 * handlers live in {@code LivingArmorTickEvent}, the weapon/tool ones in {@code LivingWeaponToolTickEvent}.
 * Only the first was covered at first, and the gap was not a mere crash: the NPE unwinds through
 * {@code ServerLevel.addFreshEntity}, so the drop is destroyed mid-spawn and the player silently gets
 * NOTHING. That is how the Water Lucky Block's legendary (yakurum:big_water_blade) vanished.
 *
 * <p>Fix: wrap the single {@code lambda.run()} inside addOnShift and swallow the NPE. That tooltip is
 * never shown to anyone (it is LootBeams' off-screen colour pre-pass), so skipping it is safe, and a real
 * player hovering the item still gets the tooltip (no null, no exception). Targets the classes by name
 * (remap=false, require=0) so Lucky Tweaks degrades cleanly when Yakurum is absent.
 */
@Mixin(targets = {
        "com.sokoly.yakurum.events.LivingArmorTickEvent",
        "com.sokoly.yakurum.events.LivingWeaponToolTickEvent"
}, remap = false)
public class YakurumTooltipGuardMixin {

    @Redirect(
            method = "addOnShift(Ljava/util/List;Ljava/lang/Runnable;)V",
            at = @At(value = "INVOKE", target = "Ljava/lang/Runnable;run()V"),
            remap = false,
            require = 0
    )
    private static void luckytweaks_safeTooltipRun(Runnable lambda) {
        try {
            lambda.run();
        } catch (NullPointerException ignored) {
            // Tooltip built without a player (LootBeams colour pre-pass) -> nothing to display; skip.
        }
    }
}
