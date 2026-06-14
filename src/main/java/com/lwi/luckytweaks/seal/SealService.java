package com.lwi.luckytweaks.seal;

import com.lwi.luckystats.api.LuckyStatsApi;
import com.lwi.luckytweaks.RunSeal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Server-side seal logic that touches the Lucky Stats API. Isolated here so the {@code luckystats}
 * classes only resolve when the mod is actually present -- callers gate every entry point on
 * {@link #statsLoaded()}, so Lucky Tweaks runs fine standalone (the seal feature is simply inert).
 */
public final class SealService {
    private static Boolean loaded;

    private SealService() {}

    public static boolean statsLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("luckystats");
        }
        return loaded;
    }

    /** Full re-evaluation (on join): tamper-check the stored seal, then OR in every current violation. */
    public static void evaluateOnJoin(Player player) {
        CompoundTag stats = LuckyStatsApi.getStats(player);
        int reasons = RunSeal.present(stats) ? RunSeal.reasons(stats) : 0;
        if (RunSeal.present(stats) && !RunSeal.verify(stats, player.getUUID())) {
            reasons |= RunSeal.TAMPERED; // stored seal was edited outside the game
        }
        if (player.isCreative() || player.isSpectator()) {
            reasons |= RunSeal.CREATIVE;
        }
        if (!player.level().getLevelData().isHardcore()) {
            reasons |= RunSeal.NOT_HARDCORE;
        }
        MinecraftServer server = player.getServer();
        if (server != null && server.getWorldData().getAllowCommands()) {
            reasons |= RunSeal.CHEATS;
        }
        if (RunSeal.isConfigNonDefault()) {
            reasons |= RunSeal.MODIFIED_CONFIG;
        }
        RunSeal.write(stats, reasons, player.getUUID());
    }

    /** Add a single reason bit (sticky), preserving prior reasons and re-checking for tampering. */
    public static void addReason(Player player, int bit) {
        CompoundTag stats = LuckyStatsApi.getStats(player);
        int reasons = RunSeal.present(stats) ? RunSeal.reasons(stats) : 0;
        if (RunSeal.present(stats) && !RunSeal.verify(stats, player.getUUID())) {
            reasons |= RunSeal.TAMPERED;
        }
        reasons |= bit;
        RunSeal.write(stats, reasons, player.getUUID());
    }

    /**
     * Re-evaluate config taint right now (e.g. just after the in-game screen changes settings). Forge
     * doesn't reliably refire ModConfigEvent on a programmatic save, so the screen calls this directly.
     * Hops onto the server thread before touching player data.
     */
    public static void onConfigChanged() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(SealService::taintConfigOnline);
        }
    }

    /** Mark every online player's run as config-modified (mid-run config change). */
    public static void taintConfigOnline() {
        if (!RunSeal.isConfigNonDefault()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return; // no world running yet -- the join check will catch it
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            addReason(player, RunSeal.MODIFIED_CONFIG);
        }
    }
}
