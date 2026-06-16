package com.lwi.luckytweaks.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Listener notified when a player breaks a (non-disabled) lucky block. Register via
 * {@link LuckyTweaksApi#registerBreakListener}.
 */
@FunctionalInterface
public interface LuckyBlockBreakListener {
    /**
     * Fired server-side at {@code HIGHEST} priority, right after Lucky Tweaks captures the block's
     * real stored Luck and before the drop is rolled.
     *
     * @param player       the breaking player
     * @param blockId      the lucky block's registry id
     * @param pos          the broken block's position
     * @param capturedLuck the block's stored Luck (clamped), or 0 if none — a proxy for drop rarity
     */
    void onLuckyBlockBroken(ServerPlayer player, ResourceLocation blockId, BlockPos pos, int capturedLuck);
}
