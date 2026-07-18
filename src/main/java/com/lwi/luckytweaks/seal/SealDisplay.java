package com.lwi.luckytweaks.seal;

import com.lwi.luckystats.api.LuckyStatsClientApi;
import com.lwi.luckystats.client.ScreenSections;
import com.lwi.luckytweaks.RunSeal;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client-side display of the run seal as a "Run Integrity" section on the Lucky Stats screen. Reads
 * the seal out of the synced stats payload; status is plain text (no colour reliance), so it reads
 * the same for colour-blind players and shows up clearly in a recorded VOD. Touches the Lucky Stats
 * client API, so it is only ever called when that mod is present.
 */
public final class SealDisplay {
    private SealDisplay() {}

    public static void register() {
        // Titles/labels/values are lang KEYS: Lucky Stats resolves them at render (an unknown key —
        // like the audit reason lines below, kept in English on purpose — passes through verbatim).
        LuckyStatsClientApi.registerScreenSection("luckytweaks.section.run_integrity", SealDisplay::rows);
    }

    private static List<ScreenSections.Row> rows(CompoundTag stats) {
        List<ScreenSections.Row> out = new ArrayList<>();
        if (!RunSeal.present(stats)) {
            out.add(new ScreenSections.Row("luckytweaks.seal.status", "luckytweaks.seal.not_recorded"));
            return out;
        }
        int reasons = RunSeal.reasons(stats);
        UUID uuid = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
        if (uuid != null && stats.getLong(RunSeal.KEY_CHECKSUM) != RunSeal.checksum(reasons, uuid)) {
            reasons |= RunSeal.TAMPERED;
        }
        if (reasons == 0) {
            out.add(new ScreenSections.Row("luckytweaks.seal.status", "luckytweaks.seal.valid"));
        } else {
            out.add(new ScreenSections.Row("luckytweaks.seal.status", "luckytweaks.seal.invalid"));
            for (String reason : RunSeal.reasonLabels(reasons)) {
                out.add(new ScreenSections.Row("-", reason));
            }
        }
        return out;
    }
}
