package com.lwi.luckytweaks;

/**
 * Per-thread state for the break currently being processed. Written by {@link BreakEvents} (and by
 * other mods through {@link com.lwi.luckytweaks.api.LuckyTweaksApi#addLuck}), consumed one-shot by
 * {@code mixin.DropEvaluatorMixin} when the Lucky Block mod rolls the drop.
 *
 * <p>Thread-locals are safe here because the whole sequence (BreakEvent -> playerDestroy -> drop
 * roll) runs synchronously on the server thread for a given break.
 */
public final class LuckState {
    /** The block's real Luck captured at BreakEvent, already clamped to [-100, 120]. null = no capture. */
    public static final ThreadLocal<Integer> CAPTURED = ThreadLocal.withInitial(() -> null);
    /** Luck contributed by other mods for this break (e.g. equipment bonuses). */
    public static final ThreadLocal<Integer> BONUS = ThreadLocal.withInitial(() -> 0);
    /** The positive-luck cap registered for the broken block, resolved at BreakEvent. null = no cap. */
    public static final ThreadLocal<Integer> CAP = ThreadLocal.withInitial(() -> null);

    private LuckState() {}

    /** Clear all per-thread state. Called at the start of every lucky-block break. */
    public static void reset() {
        CAPTURED.set(null);
        BONUS.set(0);
        CAP.set(null);
    }
}
