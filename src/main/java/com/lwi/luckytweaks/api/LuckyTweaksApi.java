package com.lwi.luckytweaks.api;

import com.lwi.luckytweaks.LuckCaps;
import com.lwi.luckytweaks.LuckState;
import net.minecraft.resources.ResourceLocation;

/**
 * Public API of Lucky Tweaks. Stable entry points for other mods; everything else in this jar is
 * internal and may change between versions.
 */
public final class LuckyTweaksApi {
    private LuckyTweaksApi() {}

    /**
     * Contribute luck to the lucky-block break currently being processed on this thread.
     *
     * <p>Call from a {@code BlockEvent.BreakEvent} handler at priority HIGH or below (Lucky Tweaks
     * resets the break state at HIGHEST). Contributions accumulate, are added on top of the block's
     * captured Luck, then bounded by the block's cap (if any) and the global safety clamp before
     * being handed to the Lucky Block mod's drop roll. Negative contributions are allowed.
     */
    public static void addLuck(int bonus) {
        LuckState.BONUS.set(LuckState.BONUS.get() + bonus);
    }

    /**
     * Cap a lucky block's POSITIVE luck. Applies to both the result of luck crafting (so the item
     * tooltip never overpromises) and the effective luck of a break roll. Negative luck (curses)
     * is never capped. Entries from the {@code luckCaps} config override API registrations, so
     * packs keep the last word over mods.
     */
    public static void registerLuckCap(ResourceLocation blockId, int maxLuck) {
        LuckCaps.register(blockId, maxLuck);
    }

    /**
     * The positive-luck cap currently in effect for this block (config first, then API
     * registrations), or null if the block is uncapped. Mods whose features WRITE Luck onto a
     * block (gambles, blessings...) should bound what they write with this, so an item tooltip
     * can never promise more than the block will roll.
     */
    @javax.annotation.Nullable
    public static Integer getLuckCap(ResourceLocation blockId) {
        return LuckCaps.capFor(blockId);
    }

    /**
     * The broken block's stored Luck captured for the break currently being processed on this
     * thread (already clamped to the safety bounds), or null when no capture happened (not a
     * player break / no block entity). For consumers that re-implement an outcome engine for a
     * lucky-block lookalike (e.g. a pack script biasing a third-party block's roll by its Luck).
     */
    @javax.annotation.Nullable
    public static Integer getCapturedLuck() {
        return LuckState.CAPTURED.get();
    }

    /** Luck contributed so far (via {@link #addLuck}) to the break currently being processed. */
    public static int getContributedLuck() {
        return LuckState.BONUS.get();
    }
}
