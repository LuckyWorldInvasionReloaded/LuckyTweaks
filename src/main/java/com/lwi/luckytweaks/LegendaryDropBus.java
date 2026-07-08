package com.lwi.luckytweaks;

import com.lwi.luckytweaks.api.LegendaryDropListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Internal dispatcher for {@link LegendaryDropListener}. Other mods register through
 * {@link com.lwi.luckytweaks.api.LuckyTweaksApi#registerLegendaryDropListener};
 * {@link BreakEvents#countLegendaryAtRoll} fires, once per break.
 */
public final class LegendaryDropBus {
    private static final List<LegendaryDropListener> LISTENERS = new CopyOnWriteArrayList<>();

    private LegendaryDropBus() {}

    public static void register(LegendaryDropListener listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    /** A listener must never break the drop pipeline, so every exception is swallowed. */
    public static void fire(ServerPlayer player, BlockPos pos) {
        if (LISTENERS.isEmpty()) {
            return;
        }
        for (LegendaryDropListener listener : LISTENERS) {
            try {
                listener.onLegendaryDrop(player, pos);
            } catch (Throwable ignored) {
                // a misbehaving listener must not abort the drop
            }
        }
    }
}
