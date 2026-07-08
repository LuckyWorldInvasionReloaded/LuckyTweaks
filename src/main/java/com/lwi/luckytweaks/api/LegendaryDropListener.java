package com.lwi.luckytweaks.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Listener notified when a player's lucky block rolls a LEGENDARY drop. Register via
 * {@link LuckyTweaksApi#registerLegendaryDropListener}.
 *
 * <p>Companion to {@link LuckyBlockBreakListener}, which fires <i>before</i> the roll and therefore
 * cannot know the outcome. This one fires from the drop evaluation itself, on the same server tick as
 * the break, the first time a chosen sub-drop carries the {@code LWLeg} marker — so it survives the
 * legendary suspense delay (the marked item only materialises ~44 ticks later).
 */
@FunctionalInterface
public interface LegendaryDropListener {
    /**
     * Fired server-side at most once per break, right after the legendary sub-drop is resolved.
     *
     * @param player the breaking player
     * @param pos    the broken lucky block's position, or {@code null} if it could not be captured
     */
    void onLegendaryDrop(ServerPlayer player, BlockPos pos);
}
