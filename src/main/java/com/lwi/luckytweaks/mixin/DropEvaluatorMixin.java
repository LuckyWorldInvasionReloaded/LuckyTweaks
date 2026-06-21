package com.lwi.luckytweaks.mixin;

import com.lwi.luckytweaks.BreakEvents;
import com.lwi.luckytweaks.LuckCurve;
import com.lwi.luckytweaks.LuckState;
import com.lwi.luckytweaks.TweaksConfig;
import mod.lucky.common.drop.DropContext;
import mod.lucky.common.drop.WeightedDrop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The luck fix + per-block chance normalisation, applied at the mod's own weighted picker.
 *
 * <p>Everything is done in {@link #luckytweaks_pickDrop}, the redirect on {@code chooseRandomDrop},
 * because that is the one site that reliably receives BOTH the block's full drop list AND the
 * enclosing {@code DropContext}. A plain {@code @ModifyVariable} on the luck argument does NOT get
 * the drop list handed to it -- it comes through {@code null} -- so the chance cannot be normalised
 * there (confirmed in-game: the modifier ran with {@code drops=null}).
 *
 * <p>For every tracked block break we recompute the luck passed to the picker:
 * <ul>
 *   <li><b>Luck fix</b>: start from the block's REAL Luck, captured at {@code BreakEvent} by
 *       {@link BreakEvents} (otherwise the mod reads the already-removed block entity and rolls at 0).</li>
 *   <li><b>Chance</b>: any API-contributed CHANCE (ring / belt / event / invasion malus, in
 *       percentile points) is added to the block's own mean drop percentile, then converted back --
 *       via {@link LuckCurve} -- to the raw Luck that yields that percentile on THIS table. So "+X%"
 *       means the same thing on a binary Tools LB and on a spread Pink LB.</li>
 * </ul>
 *
 * <p>One-shot: the captured state is consumed on first use, so a follow-up roll in the same break
 * never re-applies it. Redstone / right-click paths never populate the state, so they keep the mod's
 * own (correct) reading. Weapons never go through {@code BreakEvent}, so they keep the harm filter
 * only (no chance normalisation).
 */
@Mixin(targets = "mod.lucky.common.drop.DropEvaluatorKt", remap = false)
public class DropEvaluatorMixin {

    /** The mod's own (file-private) weighted picker, shadowed so we can re-invoke it with our luck. */
    @Shadow(remap = false)
    private static WeightedDrop chooseRandomDrop(List<WeightedDrop> drops, int luck) {
        throw new AssertionError("@Shadow stub -- replaced at load time");
    }

    /**
     * Redirect the mod's {@code chooseRandomDrop(drops, luck)} call inside {@code runRandomDrop} so we
     * can (1) substitute the effective Luck for this break (luck fix + chance normalisation) and
     * (2) thin out weapon self-harm. The enclosing args (the source drop list + DropContext) ARE
     * delivered here reliably -- which is exactly why the whole thing lives in the redirect.
     */
    @Redirect(
            method = "runRandomDrop(Ljava/util/List;ILmod/lucky/common/drop/DropContext;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lmod/lucky/common/drop/DropEvaluatorKt;chooseRandomDrop(Ljava/util/List;I)Lmod/lucky/common/drop/WeightedDrop;",
                    remap = false),
            remap = false,
            require = 0
    )
    private static WeightedDrop luckytweaks_pickDrop(
            List<WeightedDrop> candidates, int rollLuck,
            List<WeightedDrop> srcDrops, int luck, DropContext ctx, boolean showOutput) {
        // Snapshot the debug inputs + re-roll grants BEFORE computeEffectiveLuck consumes (resets) the state.
        net.minecraft.server.level.ServerPlayer dbgPlayer = LuckState.PLAYER.get();
        Integer dbgCaptured = LuckState.CAPTURED.get();
        int dbgChance = LuckState.CHANCE.get();
        net.minecraft.resources.ResourceLocation dbgBlock = LuckState.BLOCK_ID.get();
        int rerolls = LuckState.REROLLS.get();
        int effectiveLuck = luckytweaks_computeEffectiveLuck(rollLuck, candidates);
        List<WeightedDrop> pool = luckytweaks_filterWeaponHarm(candidates, effectiveLuck, ctx);
        WeightedDrop chosen = chooseRandomDrop(pool, effectiveLuck);
        // Re-roll grants (e.g. Lucky Belt): a SECOND chance at the top tier, ONLY while this pick missed
        // it. A pick that already hit the top is kept (a jackpot is never lost). Applied AFTER the luck
        // cap, so it lifts the REAL odds past the cap. Purely backend: the discarded pick is never run.
        boolean rerolled = false;
        if (rerolls > 0 && chosen != null) {
            int topTier = luckytweaks_topTier(pool);
            for (int i = 0; i < rerolls && chosen.getLuck() < topTier; i++) {
                chosen = chooseRandomDrop(pool, effectiveLuck);
                rerolled = true;
            }
        }
        if (dbgPlayer != null && com.lwi.luckytweaks.DebugReport.isOn(dbgPlayer)) {
            com.lwi.luckytweaks.DebugReport.send(dbgPlayer, dbgBlock, candidates, dbgCaptured, dbgChance, effectiveLuck, chosen, rerolls, rerolled);
        }
        return chosen;
    }

    /** Highest {@code @luck} tier present in the pool (its "top"/jackpot tier). */
    private static int luckytweaks_topTier(List<WeightedDrop> pool) {
        int top = Integer.MIN_VALUE;
        for (WeightedDrop d : pool) {
            if (d.getLuck() > top) {
                top = d.getLuck();
            }
        }
        return top;
    }

    /**
     * Translate the captured block Luck + the player's contributed CHANCE into the raw Luck to roll on
     * {@code drops}. CHANCE is read as a % of the way toward THIS block's best (frac &gt; 0) or worst
     * (frac &lt; 0) possible outcome, so +100% always lands the block's max and -100% its min, uniformly
     * across tables (readable: 100% = "as good as it gets here"). Returns the mod's own luck untouched
     * when this break was not tracked (no capture, no chance).
     */
    private static int luckytweaks_computeEffectiveLuck(int modLuck, List<WeightedDrop> drops) {
        Integer captured = LuckState.CAPTURED.get();
        int chance = LuckState.CHANCE.get();
        Integer cap = LuckState.CAP.get();
        if (captured == null && chance == 0) {
            return modLuck; // not a tracked player break
        }
        LuckState.reset(); // one-shot per break
        int blockLuck = (captured != null) ? captured : modLuck;
        if (chance != 0 && drops != null && !drops.isEmpty()) {
            // CHANCE = % of the way toward the block's BEST outcome (frac>0) or its WORST (frac<0):
            // +100% targets max (pct 1.0), -100% the min (pct 0), on ANY table. Clamp: >100% is wasted.
            double frac = Math.max(-100, Math.min(100, chance)) / 100.0;
            double basePct = LuckCurve.meanPercentile(drops, blockLuck);
            double targetPct = (frac >= 0.0)
                    ? basePct + frac * (1.0 - basePct)
                    : basePct + frac * basePct;
            blockLuck = LuckCurve.luckForPercentile(drops, targetPct);
        }
        // Per-block positive cap (e.g. Tools LB = +50), then the global safety clamp [-100, +100].
        if (cap != null && blockLuck > cap) {
            blockLuck = cap;
        }
        return Math.max(BreakEvents.LUCK_FLOOR, Math.min(BreakEvents.LUCK_CEIL, blockLuck));
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
