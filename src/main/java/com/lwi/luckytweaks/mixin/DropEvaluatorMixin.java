package com.lwi.luckytweaks.mixin;

import com.lwi.luckytweaks.BreakEvents;
import com.lwi.luckytweaks.LuckState;
import com.lwi.luckytweaks.TweaksConfig;
import mod.lucky.common.drop.DropContext;
import mod.lucky.common.drop.WeightedDrop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The luck fix: inject the block's REAL Luck (captured at {@code BreakEvent} by
 * {@link BreakEvents}, plus any API-contributed bonus, bounded by the block's cap) into the Lucky
 * Block mod's {@code runRandomDrop} luck argument. Without this, every player break rolls at luck
 * 0 -- the mod reads the block entity after Minecraft has already removed it.
 *
 * <p>One-shot: the state is consumed on first use so a nested or follow-up roll within the same
 * break can never re-apply it. The redstone / right-click paths never populate the state (no
 * BreakEvent), so they keep the mod's own (correct) luck reading.
 */
@Mixin(targets = "mod.lucky.common.drop.DropEvaluatorKt", remap = false)
public class DropEvaluatorMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("luckytweaks");

    /** The mod's own (file-private) weighted picker. Shadowed so the weapon filter can re-invoke it
     *  with a thinned pool and reproduce the exact selection the mod would have made. */
    @Shadow(remap = false)
    private static WeightedDrop chooseRandomDrop(List<WeightedDrop> drops, int luck) {
        throw new AssertionError("@Shadow stub -- replaced at load time");
    }

    @ModifyVariable(
            method = "runRandomDrop(Ljava/util/List;ILmod/lucky/common/drop/DropContext;Z)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1,
            remap = false,
            require = 0
    )
    private static int luckytweaks_injectBlockLuck(int luck) {
        Integer captured = LuckState.CAPTURED.get();
        int bonus = LuckState.BONUS.get();
        Integer cap = LuckState.CAP.get();
        if (captured == null && bonus == 0) {
            return luck; // not a tracked player break
        }
        LuckState.reset(); // one-shot per break
        int combined = (captured != null ? captured : luck) + bonus;
        // Per-block cap, positive side only -- curses always apply fully.
        if (cap != null && combined > cap) {
            combined = cap;
        }
        // Global safety clamp, below the weight formula's singularity (~129.87).
        int effective = Math.max(BreakEvents.LUCK_FLOOR, Math.min(BreakEvents.LUCK_CEIL, combined));
        if (effective != luck) {
            LOGGER.info("[luck-fix] injected blockLuck={} (mod passed {}, captured={}, bonus={}, cap={})",
                    effective, luck, captured, bonus, cap);
        }
        return effective;
    }

    /**
     * Weapon-safety filter (config-toggleable via {@link TweaksConfig#FIX_LUCKY_WEAPONS}, default on).
     *
     * <p>The Lucky Sword / Bow / Potion fire a lucky-block drop ON ATTACK at the hit point, and some
     * of those effects hurt the WIELDER (launched TNT, lightning, lava/fire, cobweb, mob spawns). The
     * vanilla weapon pools put every effect at the same luck tier, so raising the weapon's Luck only
     * made them trigger MORE often. Here, for a weapon source only, each harmful entry is dropped from
     * the roll with probability {@code weaponLuck / 100}, capped just below 1 -- so harm fades as Luck
     * climbs and is all-but-gone (a sliver of risk remains) at +100. Good/offensive effects are never
     * touched, and lucky BLOCKS (sourceId not a weapon) are left completely alone.
     */
    @Redirect(
            method = "runRandomDrop(Ljava/util/List;ILmod/lucky/common/drop/DropContext;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lmod/lucky/common/drop/DropEvaluatorKt;chooseRandomDrop(Ljava/util/List;I)Lmod/lucky/common/drop/WeightedDrop;",
                    remap = false),
            remap = false,
            require = 0
    )
    private static WeightedDrop luckytweaks_safenLuckyWeapon(
            List<WeightedDrop> candidates, int rollLuck,
            List<WeightedDrop> srcDrops, int luck, DropContext ctx, boolean showOutput) {
        // @Redirect (not @ModifyArg): only this form hands us the enclosing method's DropContext, which
        // we need for the sourceId. Filter the pool, then make the same pick the mod would have made.
        return chooseRandomDrop(luckytweaks_filterWeaponHarm(candidates, luck, ctx), rollLuck);
    }

    /** The candidate pool fed to {@code chooseRandomDrop}: weapon harm thinned out, everything else as-is. */
    private static List<WeightedDrop> luckytweaks_filterWeaponHarm(
            List<WeightedDrop> candidates, int luck, DropContext ctx) {
        if (!TweaksConfig.FIX_LUCKY_WEAPONS.get()) {
            return candidates;
        }
        String source = ctx.getSourceId();
        if (source == null
                || !(source.endsWith("_sword") || source.endsWith("_bow") || source.endsWith("_potion"))) {
            return candidates; // weapons only; blocks keep their full chaos
        }
        if (luck <= 0 || candidates.size() <= 1) {
            return candidates;
        }
        List<? extends String> markers = TweaksConfig.LUCKY_WEAPON_MARKERS.get();
        if (markers.isEmpty()) {
            return candidates;
        }
        // Replicate the mod's weight formula to find this pool's natural "harmful" share at this luck,
        // then remove harmful drops just enough to bring the per-hit negative chance to the configured
        // target at +100 (scaling smoothly up from the natural share at low luck). This makes the
        // target hold for ANY weapon pool, however many harmful effects it carries.
        double liLuck = Math.min(Math.abs((double) luck), 120.0);
        double levelIncrease = 1.0 / (1.0 - liLuck * 0.0077);
        int minLuck = Integer.MAX_VALUE;
        for (WeightedDrop drop : candidates) {
            minLuck = Math.min(minLuck, drop.getLuck());
        }
        double total = 0.0;
        double harmfulWeight = 0.0;
        for (WeightedDrop drop : candidates) {
            Double chance = drop.getChance();
            double weight = (chance != null ? chance : 1.0) * Math.pow(levelIncrease, drop.getLuck() - minLuck + 1);
            total += weight;
            if (luckytweaks_isHarmful(drop.getDropString(), markers)) {
                harmfulWeight += weight;
            }
        }
        if (harmfulWeight <= 0.0 || total <= 0.0) {
            return candidates;
        }
        double naturalShare = harmfulWeight / total;
        double progress = Math.min(luck, 100) / 100.0; // 0 at luck 0 -> 1 at +100
        double target = naturalShare + (TweaksConfig.LUCKY_WEAPON_NEG_AT_100.get() - naturalShare) * progress;
        if (target >= naturalShare) {
            return candidates; // never make a weapon MORE dangerous than vanilla
        }
        // Per-harmful removal probability that yields the target harmful share after filtering.
        double removal = (naturalShare - target) / (naturalShare * (1.0 - target));
        if (removal <= 0.0) {
            return candidates;
        }
        if (removal > 1.0) {
            removal = 1.0;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        List<WeightedDrop> kept = new ArrayList<>(candidates.size());
        int removed = 0;
        for (WeightedDrop drop : candidates) {
            if (luckytweaks_isHarmful(drop.getDropString(), markers) && rng.nextDouble() < removal) {
                removed++;
                continue;
            }
            kept.add(drop);
        }
        // Never hand chooseRandomDrop an empty list (it throws).
        if (removed == 0 || kept.isEmpty()) {
            return candidates;
        }
        return kept;
    }

    private static boolean luckytweaks_isHarmful(String dropString, List<? extends String> markers) {
        if (dropString == null) {
            return false;
        }
        for (String marker : markers) {
            if (!marker.isEmpty() && dropString.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
