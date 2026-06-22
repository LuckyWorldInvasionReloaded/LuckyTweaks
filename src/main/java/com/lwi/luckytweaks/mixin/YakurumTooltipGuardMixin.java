package com.lwi.luckytweaks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Crash guard for a Yakurum x LootBeams conflict (NPE "Exception ticking world").
 *
 * <p>Yakurum's armor-tooltip handlers (LivingArmorTickEvent#onItemTooltipArmor*, several of them)
 * all funnel through {@code addOnShift(tooltip, lambda)}, which runs the lambda when Shift is held.
 * Those lambdas do {@code event.getEntity().level()}. LootBeams builds an item's tooltip with NO
 * player ({@code ItemStack.getTooltipLines(null, ...)}) the instant an ItemEntity joins the world,
 * so {@code getEntity()} is null and the lambda throws a NullPointerException on the (integrated)
 * server thread -> world-tick crash. It happens frequently in singleplayer while holding Shift as
 * Yakurum armor drops.
 *
 * <p>Fix: wrap the single {@code lambda.run()} inside addOnShift and swallow the NPE. That tooltip
 * is never shown to anyone (it is LootBeams' off-screen colour pre-pass), so skipping it is safe,
 * and a real player hovering the item still gets the tooltip (no null, no exception). Targets the
 * class by name (remap=false, require=0) so Lucky Tweaks degrades cleanly when Yakurum is absent.
 */
@Mixin(targets = "com.sokoly.yakurum.events.LivingArmorTickEvent", remap = false)
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
