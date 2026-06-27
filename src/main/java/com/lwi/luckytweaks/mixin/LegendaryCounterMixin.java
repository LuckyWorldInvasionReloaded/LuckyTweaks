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
 * The legendary-drop COUNTER, detected AT THE ROLL (see {@code LEGENDARY_SUSPENSE_PLAN.md} point 2).
 *
 * <p>The legendary celebration is now native + delayed inline in the lucky blocks' {@code drops.txt}
 * (each legendary sub-drop is wrapped in {@code group(<sounds+particles>; <item>, delay=2.2)}), so the
 * marked item/entity only materialises ~44 ticks after the break. The old post-spawn AABB scan went
 * blind against that delay; the counter therefore moves here, to the point where the Lucky Block mod
 * EVALUATES a chosen sub-drop -- which happens synchronously on the break tick, before the delay is
 * applied, and on the actually-resolved sub-drop (so a {@code #randList}/group whose only one branch
 * is legendary is counted only when that branch is the one rolled).
 *
 * <p>Hooks {@code DropEvaluatorKt.runEvaluatedDrop(SingleDrop, DropContext)} at HEAD: when the
 * SingleDrop's source carries the {@code LWLeg} marker and the break is a real player's,
 * {@link BreakEvents#countLegendaryAtRoll} bumps the stat at most once per break (its
 * {@code LEGENDARY_COUNTED_THIS_BREAK} guard handles one-shot de-dup AND ignores the second,
 * delayed evaluation that fires in a later tick). Unconditional injection: it never cancels, mutates,
 * or reorders anything -- it only observes -- so it composes with the Tools boost (same class) and the
 * Tweaks luck fix without coordination.
 */
@Mixin(targets = "mod.lucky.common.drop.DropEvaluatorKt", remap = false)
public class LegendaryCounterMixin {

    @Inject(
            method = "runEvaluatedDrop(Lmod/lucky/common/drop/SingleDrop;Lmod/lucky/common/drop/DropContext;)V",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private static void luckytweaks_countLegendary(SingleDrop drop, DropContext context, CallbackInfo ci) {
        if (drop == null || context == null) {
            return;
        }
        if (!luckytweaks_isLegendary(drop)) {
            return;
        }
        // DropContext.player is Kotlin Any? (the common code is multi-platform); on Forge it is the
        // breaking ServerPlayer, or null/something-else for non-player sources (weapons, etc.).
        Object playerObj = context.getPlayer();
        if (playerObj instanceof ServerPlayer player) {
            BreakEvents.countLegendaryAtRoll(player);
        }
    }

    /** Whether the evaluated sub-drop's source text carries the {@code LWLeg} legendary marker. */
    private static boolean luckytweaks_isLegendary(SingleDrop drop) {
        try {
            String props = drop.getPropsString();
            if (props != null && props.contains(LuckyTweaksApi.LEG_TAG)) {
                return true;
            }
        } catch (Throwable ignored) {
            // fall through to toString()
        }
        // Belt-and-suspenders: the marker may surface in the resolved props rather than the raw
        // propsString, so also scan the full string form (type + props + propsString).
        return drop.toString().contains(LuckyTweaksApi.LEG_TAG);
    }
}
