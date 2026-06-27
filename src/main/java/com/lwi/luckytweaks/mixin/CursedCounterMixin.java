package com.lwi.luckytweaks.mixin;

import com.lwi.luckytweaks.BreakEvents;
import com.lwi.luckytweaks.api.LuckyTweaksApi;
import mod.lucky.common.drop.DropContext;
import mod.lucky.common.drop.SingleDrop;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The CURSED-drop COUNTER, detected AT THE ROLL -- the exact twin of {@link LegendaryCounterMixin}.
 *
 * <p>A cursed sub-drop carries the {@code LWCurse} NBT marker (injected offline into the lucky blocks'
 * {@code drops.txt}, never at runtime), exactly like a legendary carries {@code LWLeg}. There is
 * deliberately NO sound, particle or other tell on a curse: a curse must not put the player on alert.
 *
 * <p>Hooks {@code runEvaluatedDrop(SingleDrop, DropContext)} at HEAD: when the chosen sub-drop's source
 * carries {@code LWCurse} and the break is a real player's, {@link BreakEvents#countCursedAtRoll} records
 * it -- but the actual {@code cursed_breaks} HUD bump is DELAYED 5 s (see that method), so the counter
 * never ticks up in sync with the bad drop (anti-spoiler). The break-side {@code CURSED_COUNTED_THIS_BREAK}
 * guard de-dups, so a curse with several marked sub-drops is recorded once. Unconditional, non-cancelling
 * observer: composes with the Tools boost and the legendary counter on the same target class.
 */
@Mixin(targets = "mod.lucky.common.drop.DropEvaluatorKt", remap = false)
public class CursedCounterMixin {

    @Inject(
            method = "runEvaluatedDrop(Lmod/lucky/common/drop/SingleDrop;Lmod/lucky/common/drop/DropContext;)V",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private static void luckytweaks_countCursed(SingleDrop drop, DropContext context, CallbackInfo ci) {
        if (drop == null || context == null) {
            return;
        }
        if (!luckytweaks_isCursed(drop)) {
            return;
        }
        // DropContext.player is Kotlin Any? (multi-platform); on Forge it is the breaking ServerPlayer,
        // or null/something-else for non-player sources.
        Object playerObj = context.getPlayer();
        if (playerObj instanceof ServerPlayer player) {
            BreakEvents.countCursedAtRoll(player);
        }
    }

    /** Whether the evaluated sub-drop's source text carries the {@code LWCurse} cursed marker. */
    private static boolean luckytweaks_isCursed(SingleDrop drop) {
        try {
            String props = drop.getPropsString();
            if (props != null && props.contains(LuckyTweaksApi.CURSE_TAG)) {
                return true;
            }
        } catch (Throwable ignored) {
            // fall through to toString()
        }
        // Belt-and-suspenders: the marker may surface in the full string form rather than propsString.
        return drop.toString().contains(LuckyTweaksApi.CURSE_TAG);
    }
}
