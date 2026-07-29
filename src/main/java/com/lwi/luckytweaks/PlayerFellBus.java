package com.lwi.luckytweaks;

import com.lwi.luckytweaks.api.PlayerFellListener;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Internal dispatcher for {@link PlayerFellListener}, fired by {@link SharedLivesEvents} once it has
 * decided what a lethal blow costs. Other mods register through
 * {@link com.lwi.luckytweaks.api.LuckyTweaksApi#registerPlayerFellListener}.
 */
public final class PlayerFellBus {
    private static final List<PlayerFellListener> LISTENERS = new CopyOnWriteArrayList<>();

    private PlayerFellBus() {}

    public static void register(PlayerFellListener listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    /** A listener must never derail the death handling, so every exception is swallowed. */
    public static void fire(ServerPlayer player, PlayerFellListener.Reason reason) {
        for (PlayerFellListener listener : LISTENERS) {
            try {
                listener.onPlayerFell(player, reason);
            } catch (Throwable ignored) {
                // a misbehaving listener must not cost a life twice or skip a respawn
            }
        }
    }
}
