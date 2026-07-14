package com.lwi.luckytweaks.client;

import com.lwi.luckystats.api.LuckyStatsClientApi;
import com.lwi.luckystats.client.ScreenSections;
import com.lwi.luckystats.client.hud.HudStat;

import java.util.List;

/**
 * Registers the combined "Chance" HUD line with Lucky Stats: the player's whole luck modifier as a
 * PERCENTAGE, = the SUM of every contribution in the shared {@code luck_modifiers} sub-compound
 * (ring/gear + invasion malus + event + debug + ...). Since the per-block normalisation every
 * contribution is in percentile points, so the sum is a real, meaningful %.
 *
 * <p>Lives in Lucky Tweaks because the luck system is Lucky Tweaks' (the line used to be registered by
 * Optional Suffering, which now only contributes its malus + keeps its own screen detail). Touches the
 * Lucky Stats client API, so it is only ever called when that mod is present.
 */
public final class LuckChanceHud {
    /** Shared with Optional Suffering's LuckCompat.SUB_KEY and Lucky Tools' GearLuckReporter. */
    private static final String SUB_KEY = "luck_modifiers";

    private LuckChanceHud() {}

    public static void register() {
        LuckyStatsClientApi.registerHudStat("luckytweaks_chance", "Chance",
                data -> "Chance: " + HudStat.signed(clamp(HudStat.sumChildInts(data.getCompound(SUB_KEY)))) + "%");
        // Lucky Tweaks owns the "Luck" section header + the combined total. Other mods (Optional
        // Suffering's malus, Lucky XP's merchant temp/perm) add their own rows to the same section --
        // Lucky Stats merges same-titled sections. Order 100 keeps "Total chance" LAST, below the rows
        // it sums.
        LuckyStatsClientApi.registerScreenSection("Luck", 100, data -> List.of(
                new ScreenSections.Row("Total chance",
                        HudStat.signed(clamp(HudStat.sumChildInts(data.getCompound(SUB_KEY)))) + "%")));
    }

    /** Clamp the shown chance to [-100, +100]: +100% already means "max", anything more is wasted. */
    private static int clamp(int chance) {
        return Math.max(-100, Math.min(100, chance));
    }
}
