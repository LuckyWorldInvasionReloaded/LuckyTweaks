package com.lwi.luckytweaks;

import com.lwi.luckystats.api.LuckyStatsApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/**
 * The only place that touches Lucky Stats for the cursed-drop counter -- the twin of
 * {@link LegendaryStatsBridge}. Gated on {@code luckystats} being present so Lucky Tweaks runs standalone
 * (the counter is simply not recorded when it is absent).
 */
final class CursedStatsBridge {
    /** Per-player stats key incremented once per cursed drop. Equals luckystats' {@code K_CURSED_BREAKS}
     *  ("cursed_breaks"), which the existing "Cursed drops: N" HUD line and stats screen already read --
     *  so no client-side change is needed, only the SOURCE of the count moves (negative-tier auto-count ->
     *  curated LWCurse marker, and bumped 5 s after the draw so the HUD never spoils the drop). */
    static final String COUNTER_KEY = "cursed_breaks";

    private static Boolean loaded;

    private CursedStatsBridge() {}

    private static boolean statsLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("luckystats");
        }
        return loaded;
    }

    /** Bump the player's cursed-drop counter by one, no-op if Lucky Stats is absent. */
    static void increment(ServerPlayer player) {
        if (player == null || !statsLoaded()) {
            return;
        }
        LuckyStatsApi.incrementCounter(player, COUNTER_KEY, 1);
    }
}
