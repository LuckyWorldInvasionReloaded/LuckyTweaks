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
    private boolean welcomed;
    private boolean multiplayer;

    public static SharedLives get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(SharedLives::load, SharedLives::new, NAME);
    }

    private static SharedLives load(CompoundTag tag) {
        SharedLives data = new SharedLives();
        data.used = tag.getInt("used");
        data.welcomed = tag.getBoolean("welcomed");
        data.multiplayer = tag.getBoolean("multiplayer");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("used", used);
        tag.putBoolean("welcomed", welcomed);
        tag.putBoolean("multiplayer", multiplayer);
        return tag;
    }

    /** Whether the one-time "designed for hardcore, adjustable" welcome line has already been shown. */
    public boolean hasWelcomed() {
        return welcomed;
    }

    public void setWelcomed() {
        this.welcomed = true;
        setDirty();
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
        if (server.getPlayerList().getPlayerCount() <= 1) {
            return;
        }
        SharedLives data = get(server);
        if (!data.multiplayer) {
            data.multiplayer = true;
            data.setDirty();
        }
    }

    /** Whether this run has ever had two players online at once (see {@link #noteHeadcount}). */
    public static boolean isMultiplayerRun(MinecraftServer server) {
        return get(server).multiplayer;
    }

    /** The allowance for this run, keyed on {@link #isMultiplayerRun} rather than on being published. */
    public static int maxLives(MinecraftServer server) {
        return isMultiplayerRun(server)
                ? TweaksConfig.SHARED_LIVES_MULTIPLAYER.get()
                : TweaksConfig.SHARED_LIVES_SOLO.get();
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
