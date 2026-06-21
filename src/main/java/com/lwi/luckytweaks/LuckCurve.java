package com.lwi.luckytweaks;

import mod.lucky.common.drop.WeightedDrop;

import java.util.Arrays;
import java.util.List;

/**
 * Uniform "luck %" &lt;-&gt; Lucky Block raw Luck, computed PER BLOCK.
 *
 * <p>Why this exists: a fixed luck bonus (e.g. +27) has a wildly different effect depending on the
 * block's drop table -- it shifts the weight BETWEEN tiers (drop luck levels), and the shift depends
 * on the spread of those tiers. So "+27" on the binary Tools LB (fail / tool) is not "+27" on a Pink
 * LB with many spread tiers. To balance one uniform "luck %" across all blocks, we work in PERCENTILE.
 *
 * <p>The mod weights each drop:  {@code weight = chance * levelIncrease^exp}, with
 * {@code levelIncrease = 1 / (1 - |luck| * 0.0077)} and
 * {@code exp = (luck >= 0) ? dropLuck : (highestLuck + 1 - dropLuck)} -- negative luck INVERTS the tier
 * order (good tiers get the small exponent, so they become rare; that is why -100 is "lethal").
 * Formula reproduced from the mod source (alexsocha/luckyblock, DropEvaluator.chooseRandomDrop).
 *
 * <p>A drop's PERCENTILE is its midpoint cumulative rank in the block's base (luck-0) distribution.
 * By the midpoint-rank property, the MEAN percentile at luck 0 is exactly 0.5 for EVERY block -- that
 * is what makes "luck %" uniform. {@code luck -> meanPercentile} is monotonic, so we invert it (binary
 * search) to find the raw Luck that delivers a wanted percentile on a SPECIFIC block's table.
 */
public final class LuckCurve {
    private LuckCurve() {}

    /** The mod's level-increase coefficient (0.77 / 100). Singularity at |luck| ~= 129.87. */
    private static final double COEF = 0.0077;

    private static double levelIncrease(int luck) {
        int a = Math.min(Math.abs(luck), 120);
        return 1.0 / (1.0 - a * COEF);
    }

    /**
     * Mean drop percentile (0..1) the block's table yields at the given raw Luck. Exactly 0.5 at
     * luck 0 for every table; rises with positive luck, falls with negative luck.
     */
    public static double meanPercentile(List<WeightedDrop> drops, int luck) {
        int n = (drops == null) ? 0 : drops.size();
        if (n == 0) {
            return 0.5;
        }
        double[] chance = new double[n];
        int[] tier = new int[n];
        Integer[] order = new Integer[n];
        int highest = Integer.MIN_VALUE;
        double base = 0.0;
        for (int i = 0; i < n; i++) {
            WeightedDrop d = drops.get(i);
            Double c = d.getChance();
            chance[i] = (c != null) ? Math.max(0.0, c) : 1.0;
            tier[i] = d.getLuck();
            highest = Math.max(highest, tier[i]);
            base += chance[i];
            order[i] = i;
        }
        if (base <= 0.0) {
            return 0.5;
        }
        // Fixed midpoint-cumulative percentile per drop, from the base (luck-0) distribution.
        Arrays.sort(order, (a, b) -> Integer.compare(tier[a], tier[b]));
        double[] pct = new double[n];
        double cum = 0.0;
        for (int k = 0; k < n; k++) {
            int i = order[k];
            pct[i] = (cum + chance[i] / 2.0) / base;
            cum += chance[i];
        }
        // Weighted mean percentile at this luck (the mod's weighting decides each drop's probability).
        double li = levelIncrease(luck);
        double total = 0.0;
        double acc = 0.0;
        for (int i = 0; i < n; i++) {
            double exp = (luck >= 0) ? tier[i] : (highest + 1 - tier[i]);
            double w = chance[i] * Math.pow(li, exp);
            total += w;
            acc += w * pct[i];
        }
        return (total > 0.0) ? acc / total : 0.5;
    }

    /**
     * Raw Luck in [-100, 100] whose mean percentile is closest to {@code target} (0..1) for this
     * block's table. Returns 0 when the table is luck-insensitive (a single tier -- luck does nothing).
     */
    public static int luckForPercentile(List<WeightedDrop> drops, double target) {
        target = Math.max(0.0, Math.min(1.0, target));
        double atMin = meanPercentile(drops, -100);
        double atMax = meanPercentile(drops, 100);
        if (Math.abs(atMax - atMin) < 1.0e-6) {
            return 0; // luck has no grip on this table
        }
        if (target <= atMin) {
            return -100;
        }
        if (target >= atMax) {
            return 100;
        }
        // meanPercentile is monotonic increasing in luck -> binary search.
        int lo = -100;
        int hi = 100;
        while (lo < hi) {
            int mid = Math.floorDiv(lo + hi, 2);
            if (meanPercentile(drops, mid) < target) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
