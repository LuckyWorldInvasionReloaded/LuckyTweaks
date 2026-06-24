package com.lwi.luckytweaks.api;

import com.lwi.luckytweaks.DebugReport;
import com.lwi.luckytweaks.LuckCaps;
import com.lwi.luckytweaks.LuckState;
import com.lwi.luckytweaks.LuckyBlockBreakBus;
import com.lwi.luckytweaks.util.LuckyBlocks;
import com.lwi.luckytweaks.util.WorldGenInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Public API of Lucky Tweaks. Stable entry points for other mods; everything else in this jar is
 * internal and may change between versions.
 */
public final class LuckyTweaksApi {
    private LuckyTweaksApi() {}

    /**
     * Contribute CHANCE to the lucky-block break currently being processed, in PERCENTILE POINTS
     * (ring, belt, event, invasion malus...). Contributions are additive and are added on top of the
     * block's own mean percentile, then translated into the block-specific raw Luck -- so the same
     * "+X% chance" has the same effect on every block, whatever its drop table. Positive raises the
     * player's odds, negative lowers them. Call from a {@code BlockEvent.BreakEvent} handler at
     * priority HIGH or below (Lucky Tweaks resets the break state at HIGHEST).
     */
    public static void addChance(int percentilePoints) {
        LuckState.CHANCE.set(LuckState.CHANCE.get() + percentilePoints);
    }

    /**
     * Grant ONE extra "second chance" at the block's BEST (top) tier for the current break: after the
     * roll, if the chosen drop is NOT the top tier, the picker is re-run once (per granted re-roll). A
     * roll that already hit the top is kept untouched -- never re-rolled, so a jackpot is never lost.
     * Applied AFTER the per-block luck cap, so it lifts the REAL odds past the cap (e.g. the Lucky Belt
     * doubling the Tools Lucky Block jackpot even when its luck is capped). It is purely backend: the
     * discarded pick is never executed, so the player only ever sees the final drop. Additive (call N
     * times for N re-rolls). Call from a {@code BlockEvent.BreakEvent} handler at HIGH or below.
     */
    public static void addTopReroll() {
        LuckState.REROLLS.set(LuckState.REROLLS.get() + 1);
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

    /** Chance (percentile points) contributed so far (via {@link #addChance}) to the current break. */
    public static int getContributedChance() {
        return LuckState.CHANCE.get();
    }

    /**
     * Register a listener notified when a player breaks a (non-disabled) lucky block. Fired
     * server-side at {@code HIGHEST} priority, after the block's stored Luck is captured and before
     * the drop roll. The {@code capturedLuck} handed to the listener is the block's stored Luck
     * (a rarity proxy). Listeners must not throw — exceptions are swallowed so the drop pipeline is
     * never broken. Added for Lucky XP (award XP on lucky-block breaks).
     */
    public static void registerBreakListener(LuckyBlockBreakListener listener) {
        LuckyBlockBreakBus.register(listener);
    }

    /**
     * Whether this block is a lucky block (the {@code lucky} namespace + addons, plus cross-mod
     * lookalikes like {@code fuze_relics:lucky_blockling}; technical {@code *_render} proxies excluded).
     * The single source of truth for "is this a lucky block", shared so consumers never re-implement
     * the heuristic.
     */
    public static boolean isLuckyBlock(BlockState state) {
        return LuckyBlocks.isLuckyBlock(state);
    }

    /**
     * The registry ids of every lucky block currently registered. The block registry is frozen after
     * load, so callers may compute this once and cache it. Added for Lucky XP's event roulette, which
     * picks one lucky block to boost. Order follows registry iteration order.
     */
    public static List<ResourceLocation> getLuckyBlockIds() {
        return LuckyBlocks.allLuckyBlockIds();
    }

    /**
     * Whether this lucky block naturally generates in the given dimension, per the Lucky Block mod's
     * world-gen registry. Degrades to {@code false} if the info can't be read.
     */
    public static boolean spawnsNaturallyIn(ResourceLocation blockId, ResourceLocation dimension) {
        return WorldGenInfo.nativeDims(blockId.toString()).contains(dimension.toString());
    }

    /**
     * The lucky blocks that naturally generate in the given dimension (a subset of
     * {@link #getLuckyBlockIds()}). Added for Lucky XP's event roulette, so it only ever shows and picks
     * blocks relevant to where the player actually is.
     */
    public static List<ResourceLocation> getLuckyBlockIds(ResourceLocation dimension) {
        String dim = dimension.toString();
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation id : LuckyBlocks.allLuckyBlockIds()) {
            if (WorldGenInfo.nativeDims(id.toString()).contains(dim)) {
                out.add(id);
            }
        }
        return out;
    }

    /**
     * The block's natural "1 in N chunks" spawn rate in the given dimension (lower = more common,
     * higher = rarer), or a default (~200) when it can't be read. Added for Lucky XP's roulette to grade
     * blocks by spawn rarity.
     */
    public static int naturalRate(ResourceLocation blockId, ResourceLocation dimension) {
        return WorldGenInfo.nativeRate(blockId.toString(), dimension.toString());
    }

    /**
     * Whether the per-break luck chat debug ({@code /luckychance debug}) is ON for this player. Lets other
     * mods piggy-back on that single toggle (e.g. Lucky XP draws the "Luck +X" block holograms while it's on).
     */
    public static boolean isBreakDebugOn(ServerPlayer player) {
        return DebugReport.isOn(player);
    }
}
