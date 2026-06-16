package com.lwi.luckytweaks;

import com.lwi.luckytweaks.api.LuckyBlockBreakListener;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Internal dispatcher for {@link LuckyBlockBreakListener}. Other mods register through
 * {@link com.lwi.luckytweaks.api.LuckyTweaksApi#registerBreakListener}; {@link BreakEvents} fires.
 */
public final class LuckyBlockBreakBus {
    private static final List<LuckyBlockBreakListener> LISTENERS = new CopyOnWriteArrayList<>();

    private LuckyBlockBreakBus() {}

    public static void register(LuckyBlockBreakListener listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    /** A listener must never break the drop pipeline, so every exception is swallowed. */
    public static void fire(ServerPlayer player, ResourceLocation blockId, BlockPos pos, int capturedLuck) {
        if (LISTENERS.isEmpty()) {
            return;
        }
        for (LuckyBlockBreakListener listener : LISTENERS) {
            try {
                listener.onLuckyBlockBroken(player, blockId, pos, capturedLuck);
            } catch (Throwable ignored) {
                // a misbehaving listener must not abort the break
            }
        }
    }
}
