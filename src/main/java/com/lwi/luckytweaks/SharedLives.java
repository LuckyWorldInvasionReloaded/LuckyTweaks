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
 * <p><b>Why {@code used} and not a countdown.</b> The allowance depends on whether the world is a
 * multiplayer one, and a solo world can be opened to LAN mid-run (e4mc, "Open to LAN"). Storing how many
 * lives have been <i>spent</i> and deriving the remainder from the current allowance means inviting a
 * friend grants the multiplayer allowance instead of stranding the run on the solo one.
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

    public static SharedLives get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(SharedLives::load, SharedLives::new, NAME);
    }

    private static SharedLives load(CompoundTag tag) {
        SharedLives data = new SharedLives();
        data.used = tag.getInt("used");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("used", used);
        return tag;
    }

    /**
     * The allowance for how this world is being played right now. {@code isPublished()} is true on a
     * dedicated server AND on a singleplayer world opened to LAN — the same condition PlayerRevive itself
     * uses to decide whether bleeding applies, so the two systems can never disagree.
     */
    public static int maxLives(MinecraftServer server) {
        return server.isPublished()
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
