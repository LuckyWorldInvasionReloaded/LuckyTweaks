package com.lwi.luckytweaks.api;

import net.minecraft.server.level.ServerPlayer;

/**
 * Listener notified when the shared-lives system settles a lethal blow. Register via
 * {@link LuckyTweaksApi#registerPlayerFellListener}.
 *
 * <p>Reading {@code LivingDeathEvent} from outside cannot tell these apart: the pack CANCELS a death it
 * saves, so a spent life looks exactly like another mod's totem save. Only the shared-lives handler
 * knows which path it took, so it says so here.
 */
@FunctionalInterface
public interface PlayerFellListener {

    /** How a lethal blow was settled. */
    enum Reason {
        /** Knocked down by PlayerRevive: the life is spent, a teammate can still revive them. */
        DOWNED,
        /** Actually died — solo respawn, gave up, bled out, or the run's last life. */
        DIED
    }

    /** Fired server-side, once per settled blow, after the life has been accounted for. */
    void onPlayerFell(ServerPlayer player, Reason reason);
}
