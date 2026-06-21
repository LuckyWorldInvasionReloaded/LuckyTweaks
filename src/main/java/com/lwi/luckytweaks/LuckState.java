package com.lwi.luckytweaks;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Per-thread state for the break currently being processed. Written by {@link BreakEvents} (and by
 * other mods through {@link com.lwi.luckytweaks.api.LuckyTweaksApi}), consumed one-shot by
 * {@code mixin.DropEvaluatorMixin} when the Lucky Block mod rolls the drop.
 *
 * <p>Thread-locals are safe here because the whole sequence (BreakEvent -> playerDestroy -> drop
 * roll) runs synchronously on the server thread for a given break.
 */
public final class LuckState {
    /** The block's real Luck captured at BreakEvent, already clamped to safety bounds. null = no capture. */
    public static final ThreadLocal<Integer> CAPTURED = ThreadLocal.withInitial(() -> null);
    /** The player's CHANCE contribution for this break, in PERCENTILE POINTS (additive: ring + event +
     *  invasion malus + debug). Read as a % of the way toward the block's best/worst outcome, then
     *  converted to the block-specific raw Luck by {@link LuckCurve}. */
    public static final ThreadLocal<Integer> CHANCE = ThreadLocal.withInitial(() -> 0);
    /** The positive-luck cap registered for the broken block, resolved at BreakEvent. null = no cap. */
    public static final ThreadLocal<Integer> CAP = ThreadLocal.withInitial(() -> null);
    /** Extra "second chances" at the block's TOP tier for this break (e.g. the Lucky Belt). The picker
     *  re-runs once per re-roll, but ONLY while the current pick missed the top tier -- applied AFTER
     *  the cap, so it lifts the real odds past the cap without ever discarding a winning pick. */
    public static final ThreadLocal<Integer> REROLLS = ThreadLocal.withInitial(() -> 0);
    /** The breaking player, captured at BreakEvent. Used ONLY by the {@code /luckychance debug} report.
     *  null = no real player (or not a tracked break). */
    public static final ThreadLocal<ServerPlayer> PLAYER = ThreadLocal.withInitial(() -> null);
    /** The broken block's id, captured at BreakEvent. Used ONLY by the debug report. */
    public static final ThreadLocal<ResourceLocation> BLOCK_ID = ThreadLocal.withInitial(() -> null);

    private LuckState() {}

    /** Clear all per-thread state. Called at the start of every lucky-block break. */
    public static void reset() {
        CAPTURED.set(null);
        CHANCE.set(0);
        CAP.set(null);
        REROLLS.set(0);
        PLAYER.set(null);
        BLOCK_ID.set(null);
    }
}
