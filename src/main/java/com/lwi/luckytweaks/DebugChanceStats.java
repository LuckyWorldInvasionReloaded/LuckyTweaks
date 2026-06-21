package com.lwi.luckytweaks;

import com.lwi.luckystats.api.LuckyStatsApi;
import com.lwi.luckytweaks.api.LuckyTweaksApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * All Lucky Stats API access for the {@code /luckychance} DEBUG command, isolated here so the
 * {@code luckystats} classes are only loaded when that mod is present (callers gate every entry point
 * behind {@code ModList.isLoaded("luckystats")} -- same pattern as {@link com.lwi.luckytweaks.seal.SealService}).
 *
 * <p>The debug chance is one contribution in the shared {@code luck_modifiers} sub-compound (the same
 * one Optional Suffering and Lucky Tools write to), so it shows up in the "Luck modifier" HUD line
 * (which sums every child key) right next to the ring and the invasion malus. It is in PERCENTILE
 * points, like every other contribution since the per-block normalisation.
 */
final class DebugChanceStats {
    /** Shared with Optional Suffering's LuckCompat.SUB_KEY and Lucky Tools' GearLuckReporter. */
    private static final String SUB_KEY = "luck_modifiers";
    private static final String SRC_DEBUG = "debug";

    private DebugChanceStats() {}

    /** Add {@code delta} percentile points to the debug contribution; returns the new value. */
    static int add(ServerPlayer player, int delta) {
        CompoundTag stats = LuckyStatsApi.getStats(player);
        CompoundTag mods = stats.getCompound(SUB_KEY);
        int next = mods.getInt(SRC_DEBUG) + delta;
        mods.putInt(SRC_DEBUG, next);
        stats.put(SUB_KEY, mods); // re-store (getCompound returns a fresh tag when absent)
        return next;
    }

    /** Clear the debug contribution. */
    static void reset(ServerPlayer player) {
        CompoundTag stats = LuckyStatsApi.getStats(player);
        CompoundTag mods = stats.getCompound(SUB_KEY);
        mods.putInt(SRC_DEBUG, 0);
        stats.put(SUB_KEY, mods);
    }

    /** The current debug contribution (percentile points). */
    static int get(ServerPlayer player) {
        return LuckyStatsApi.getStats(player).getCompound(SUB_KEY).getInt(SRC_DEBUG);
    }

    /** The full "Luck modifier" total = sum of every contribution (gear + invasion + debug + ...). */
    static int total(ServerPlayer player) {
        CompoundTag mods = LuckyStatsApi.getStats(player).getCompound(SUB_KEY);
        int sum = 0;
        for (String key : mods.getAllKeys()) {
            sum += mods.getInt(key);
        }
        return sum;
    }

    /** Apply the stored debug chance to the break currently being rolled (called at HIGH). */
    static void apply(ServerPlayer player) {
        int debug = get(player);
        if (debug != 0) {
            LuckyTweaksApi.addChance(debug);
        }
    }
}
