package com.lwi.luckytweaks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The run's shared pool of lives — the pack's difficulty dial, replacing a raw "hardcore on/off" switch
 * (which would let a player uncheck the very thing the pack is about).
 *
 * <p>The pool is <b>shared by the whole team</b> and <b>never refills</b>. It is stored on the overworld's
 * data storage, so it survives a reload and is common to every player, not per-player.
 *
 * <p><b>Why {@code used} and not a countdown.</b> The allowance depends on whether the run is a
 * multiplayer one, and a friend can join a solo run at any point. Storing how many lives have been
 * <i>spent</i> and deriving the remainder from the current allowance means inviting a friend widens the
 * pool instead of stranding the run on the singleplayer one.
 *
 * <p>{@link #isGameOver()} is a transient latch, raised only while a team wipe is being executed: it tells
 * {@link com.lwi.luckytweaks.mixin.ReviveDisableMixin} to let PlayerRevive stand aside, and tells
 * {@link SharedLivesEvents} to let every death through, so killing the team cannot recurse.
 */
public class SharedLives extends SavedData {
    private static final String NAME = "luckytweaks_shared_lives";

    /** Raised only for the duration of a team wipe (see {@link SharedLivesEvents#teamGameOver}). */
    private static volatile boolean gameOver = false;

    private int used;
    private boolean multiplayer;
    private int bought;
    private int peakPlayers;

    public static SharedLives get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(SharedLives::load, SharedLives::new, NAME);
    }

    private static SharedLives load(CompoundTag tag) {
        SharedLives data = new SharedLives();
        data.used = tag.getInt("used");
        data.multiplayer = tag.getBoolean("multiplayer");
        data.bought = tag.getInt("bought");
        data.peakPlayers = tag.getInt("peakPlayers");
        // Saves from before peakPlayers existed: multiplayer was latched but the peak reads 0, which
        // would drop an existing duo to base+1 lives until both happen to be online together again.
        // Multiplayer means at least two were once online at once, so 2 is the honest floor.
        if (data.multiplayer && data.peakPlayers < 2) {
            data.peakPlayers = 2;
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("used", used);
        tag.putBoolean("multiplayer", multiplayer);
        tag.putInt("bought", bought);
        tag.putInt("peakPlayers", peakPlayers);
        return tag;
    }


    /**
     * Latch this run as a multiplayer one, the first time two players are online together.
     *
     * <p>Publishing the world is NOT the test: a solo player opens to LAN just to get commands back, and
     * that must not quietly hand them the co-op allowance. The live head-count is not the test either --
     * it would drop the allowance below what has already been spent the moment a team-mate logs off,
     * stranding the run at zero through no fault of anyone. So the flag only ever goes up, and it is
     * persisted: once you have really played together, the run keeps the co-op allowance for good.
     *
     * <p>Being a dedicated server is not the test either, deliberately: someone alone on a server is
     * playing alone, and gets the singleplayer allowance. PlayerRevive will still knock them down there,
     * but with nobody to revive them that death is just as final as it is in singleplayer, so one life is
     * the honest number. The allowance rises the moment a second player is actually there, and then stays
     * up for good.
     */
    public static void noteHeadcount(MinecraftServer server) {
        // Spectators don't count: they can't revive anyone and aren't playing. A friend hopping in
        // to WATCH a solo run must not latch it as multiplayer (that never comes back down).
        int online = 0;
        for (var p : server.getPlayerList().getPlayers()) {
            if (!p.isSpectator()) {
                online++;
            }
        }
        if (online <= 1) {
            return;
        }
        SharedLives data = get(server);
        boolean dirty = false;
        if (!data.multiplayer) {
            data.multiplayer = true;
            dirty = true;
        }
        // The allowance is one life per player, so it has to remember the biggest the team has ever been,
        // not how big it is right now. A live count would take a life away the moment someone logs off --
        // and if the team had already spent them, the run would be stranded at zero because a friend went
        // to bed. Like the flag above, this only ever goes up.
        if (online > data.peakPlayers) {
            data.peakPlayers = online;
            dirty = true;
        }
        if (dirty) {
            data.setDirty();
        }
    }

    /** The largest the team has ever been at once, i.e. how many lives the co-op allowance is built on. */
    public static int peakPlayers(MinecraftServer server) {
        return Math.max(1, get(server).peakPlayers);
    }

    /** Whether this run has ever had two players online at once (see {@link #noteHeadcount}). */
    public static boolean isMultiplayerRun(MinecraftServer server) {
        return get(server).multiplayer;
    }

    /**
     * The allowance for this run, keyed on {@link #isMultiplayerRun} rather than on being published, plus
     * any lives bought since (see {@link #buyLife}).
     *
     * <p>One base for solo and co-op alike, plus {@code sharedLivesPerPlayer x team size} once the run is
     * co-op — counted off the team's {@link #peakPlayers} rather than who happens to be online: on the
     * defaults (1 and 1) a duo gets 3 and a trio 4, and nobody loses a life because a friend logged off.
     * Per-player at 0 makes team size irrelevant, so a co-op run can be held to a single shared life
     * (user 2026-07-21). Alone, the base stands by itself -- the first death is the run.
     */
    public static int maxLives(MinecraftServer server) {
        int base = TweaksConfig.SHARED_LIVES_BASE.get();
        if (isMultiplayerRun(server)) {
            base += TweaksConfig.SHARED_LIVES_PER_PLAYER.get() * peakPlayers(server);
        }
        return base + get(server).bought;
    }

    /** Whether the run may still buy a life, i.e. it has not hit {@code boughtLivesCap} yet. */
    public static boolean canBuyLife(MinecraftServer server) {
        return get(server).bought < TweaksConfig.BOUGHT_LIVES_CAP.get();
    }

    /**
     * Add one life to the run, paid for elsewhere (the Lucky Labs merchant sells them). Returns the new
     * remaining count, or -1 when the run has already bought all it is allowed to.
     *
     * <p>Raises the ALLOWANCE rather than refunding a spent life, which keeps the pool honest in both
     * directions: buying while still untouched genuinely widens it, the count of deaths already paid for is
     * never rewritten, and the hearts row simply grows by one. The rule that the pool never refills on its
     * own still holds -- this is someone spending for it, once.
     *
     * <p>The cap counts lives BOUGHT across the run, never lives currently held: spending a bought life must
     * not free the slot to buy another, or the ceiling would only ever slow farming down instead of ending it.
     */
    public static int buyLife(MinecraftServer server) {
        if (!canBuyLife(server)) {
            return -1;
        }
        SharedLives data = get(server);
        data.bought++;
        data.setDirty();
        return remaining(server);
    }

    public static int remaining(MinecraftServer server) {
        return Math.max(0, maxLives(server) - get(server).used);
    }

    /** Spend one life; returns how many are left. Never goes below zero. */
    public static int consume(MinecraftServer server) {
        SharedLives data = get(server);
        data.used++;
        data.setDirty();
        // Flush to disk right away: this fires once per death, and a crash before the next autosave
        // would otherwise refund the life (an exploitable "crash the server to undo a death").
        server.overworld().getDataStorage().save();
        return remaining(server);
    }

    /** Testing hook: set the number of lives already spent. */
    public static void setUsed(MinecraftServer server, int value) {
        SharedLives data = get(server);
        data.used = Math.max(0, value);
        data.setDirty();
    }

    public static boolean isGameOver() {
        return gameOver;
    }

    static void setGameOver(boolean value) {
        gameOver = value;
    }
}
