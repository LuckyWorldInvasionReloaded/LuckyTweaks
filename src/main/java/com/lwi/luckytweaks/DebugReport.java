package com.lwi.luckytweaks;

import mod.lucky.common.drop.WeightedDrop;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DEV/TEST: per-break luck debug. Toggled per player via {@code /luckychance debug}; while ON, every
 * lucky-block break that player makes prints a chat recap of the luck math + the drop that was rolled,
 * handy for calibrating the chance values. Pure Lucky Tweaks (no Lucky Stats dependency). Remove or
 * keep op-gated before ship. (The section sign is a legacy chat-formatting code, parsed by the chat
 * renderer; the build compiles with UTF-8 so the accents and arrows are preserved.)
 */
public final class DebugReport {
    private static final Set<UUID> ENABLED = ConcurrentHashMap.newKeySet();

    private DebugReport() {}

    /** Flip the debug flag for this player; returns the new state. */
    public static boolean toggle(ServerPlayer player) {
        UUID id = player.getUUID();
        if (ENABLED.contains(id)) {
            ENABLED.remove(id);
            return false;
        }
        ENABLED.add(id);
        return true;
    }

    public static boolean isOn(ServerPlayer player) {
        return ENABLED.contains(player.getUUID());
    }

    /** Send the per-break recap to the player. {@code captured} may be null (treated as 0). */
    public static void send(ServerPlayer player, ResourceLocation blockId, List<WeightedDrop> drops,
                            Integer captured, int chance, int effLuck, WeightedDrop chosen,
                            int rerollGrants, boolean rerolled) {
        int base0 = (captured != null) ? captured : 0;
        double base = LuckCurve.meanPercentile(drops, base0);
        int clamped = Math.max(-100, Math.min(100, chance));
        double frac = clamped / 100.0;
        double target = (frac >= 0.0) ? base + frac * (1.0 - base) : base + frac * base;
        double real = LuckCurve.meanPercentile(drops, effLuck);
        String blk = (blockId != null) ? blockId.getPath() : "?";
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§b[luck] §f%s §7| base §f%.2f §7→ cible §f%.2f §7(chance §f%+d%%§7) → eff §f%d §7→ réel §f%.2f",
                blk, base, target, clamped, effLuck, real)));
        if (chosen != null) {
            double dc = (chosen.getChance() != null) ? chosen.getChance() : 1.0;
            String ds = chosen.getDropString();
            if (ds == null) {
                ds = "?";
            } else if (ds.length() > 50) {
                ds = ds.substring(0, 50) + "…";
            }
            String belt = (rerollGrants > 0) ? (rerolled ? "  §7[belt §aRE-ROLL§7]" : "  §7[belt: 1st=top, kept]") : "";
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§7   drop §ftier %+d §7(poids §f%.0f§7) → §f%s%s",
                    chosen.getLuck(), dc, ds, belt)));
        }
    }
}
