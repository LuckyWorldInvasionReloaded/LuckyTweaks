package com.lwi.luckytweaks;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * The two touch points with PlayerRevive that its events don't cover, kept behind reflection so Lucky
 * Tweaks loads fine without the mod (same reason ReviveDisableMixin targets the class by name).
 *
 * <p><b>Why the persistent-data flag and not the damage source.</b> PlayerRevive's every "downed player
 * actually dies" path (give up, disconnect, bled out) kills with the ORIGINAL lethal damage source —
 * {@code Bleeding.getSource()} returns {@code lastSource} and only falls back to
 * {@code playerrevive:bled_to_death} when that is null, which a real knockdown never leaves null. So the
 * source cannot tell "death of a downed player" from a fresh death; the {@code playerrevive:bleeding}
 * flag that {@code startBleeding} puts on the player's persistent data (and {@code resetPlayer} removes)
 * can — and it is plain NBT, readable without PlayerRevive classes.
 */
public final class PlayerReviveCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FLAG = "playerrevive:bleeding";

    private static Method revive;

    private PlayerReviveCompat() {}

    /** Whether PlayerRevive currently has (or had, when the death fired) this player downed. */
    public static boolean isDowned(Player player) {
        return player.getPersistentData().getBoolean(FLAG);
    }

    /**
     * Fully clear the downed state (bleeding capability, forced pose, flag). Needed on death paths that
     * do NOT go through {@code PlayerReviveServer.kill()} — e.g. a bypassed damage source finishing a
     * downed player — where PlayerRevive's own {@code playerDied} early-returns without cleaning up and
     * the bleeding tick would keep running forever on a player we just saved. Calling it on the
     * kill()-paths too is harmless: kill() re-runs the same resets right after.
     */
    public static void clearDownedState(Player player) {
        if (ModList.get().isLoaded("playerrevive")) {
            try {
                if (revive == null) {
                    revive = Class.forName("team.creative.playerrevive.server.PlayerReviveServer")
                            .getMethod("revive", Player.class);
                }
                revive.invoke(null, player);
            } catch (Throwable t) {
                LOGGER.error("Could not clear PlayerRevive's downed state for {}", player.getScoreboardName(), t);
            }
        }
        player.getPersistentData().remove(FLAG);
    }
}
