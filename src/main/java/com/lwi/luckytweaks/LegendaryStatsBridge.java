package com.lwi.luckytweaks;

import com.lwi.luckystats.api.LuckyStatsApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/**
 * The only place that touches Lucky Stats for the legendary-drop counter, isolated so the
 * {@code luckystats} classes only ever resolve when that mod is present -- {@link #increment} is gated
 * on {@code ModList.isLoaded("luckystats")}, same pattern as {@link DebugChanceStats} /
 * {@link com.lwi.luckytweaks.seal.SealService}. Lucky Tweaks therefore runs fine standalone (the
 * counter is simply not recorded).
 */
final class LegendaryStatsBridge {
    /** Per-player stats key incremented once per legendary drop. Paired with a Lucky Stats client-side
     *  HUD/screen registration ("Legendary drops: N") in the luckystats mod. */
    static final String COUNTER_KEY = "legendary";

    private static Boolean loaded;

    private LegendaryStatsBridge() {}

    private static boolean statsLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("luckystats");
        }
        return loaded;
    }

    /** Bump the player's legendary-drop counter by one, no-op if Lucky Stats is absent. */
    static void increment(ServerPlayer player) {
        if (player == null || !statsLoaded()) {
            return;
        }
        LuckyStatsApi.incrementCounter(player, COUNTER_KEY, 1);
    }
}
