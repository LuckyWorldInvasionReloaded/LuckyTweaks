package com.lwi.luckytweaks;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Tells the server when someone rolls a legendary or a cursed drop, so a co-op run has a shared feed of
 * who is getting what -- the sort of thing that is invisible when everyone is off breaking their own
 * blocks. Gold for legendary, red for cursed.
 *
 * <p><b>Singleplayer stays quiet.</b> With nobody else connected the message would only tell the player
 * what they already saw, so it is skipped below two players.
 *
 * <p><b>The two are timed in opposite ways, on purpose.</b> A legendary is announced immediately, with
 * the drumroll: the suspense wrap takes 2.2 s to reveal the drop, and everyone gets to spend them
 * waiting alongside the breaker. Nothing is given away -- the line says a legendary is coming, not what
 * it is. A curse is the reverse: it is held back 5 s, in step with its stat counter, which
 * {@code BreakEvents.countCursedAtRoll} already delays so the HUD does not out the curse before the
 * player feels it. Announcing that one early would spoil it through the chat window instead.
 */
public final class RareDropAnnouncer {
    private RareDropAnnouncer() {}

    /**
     * Broadcast the drop. The player is re-resolved by UUID because this runs after a delay and they may
     * have logged off in between; the block is passed by id since the block itself is long gone by then.
     */
    public static void announce(MinecraftServer server, UUID playerId, @Nullable ResourceLocation blockId,
                                boolean cursed) {
        if (server == null || playerId == null || !TweaksConfig.ANNOUNCE_RARE_DROPS.get()) {
            return;
        }
        if (server.getPlayerList().getPlayerCount() < 2) {
            return; // nobody to tell
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        server.getPlayerList().broadcastSystemMessage(line(player, blockId, cursed), false);
    }

    /**
     * The chat line: plain white, with only the rarity word coloured -- gold for legendary, red for
     * cursed -- and the block named the way the game names it, keeping whatever colour it carries.
     *
     * <p>Styling the whole line, as this first did, both drowned the one word worth spotting and
     * repainted the block's own name.
     */
    private static Component line(ServerPlayer player, @Nullable ResourceLocation blockId, boolean cursed) {
        Component word = Component.translatable(
                        cursed ? "luckytweaks.announce.word.cursed" : "luckytweaks.announce.word.legendary")
                .copy().withStyle(cursed ? ChatFormatting.RED : ChatFormatting.GOLD);
        return Component.translatable("luckytweaks.announce.drop",
                player.getDisplayName(), word, blockName(blockId)).withStyle(ChatFormatting.WHITE);
    }

    /**
     * Build the line without broadcasting it, for {@code /lwannouncetest}: the real thing needs two
     * players connected, which makes the wording, the colours and the block name untestable solo.
     */
    public static Component preview(ServerPlayer player, @Nullable ResourceLocation blockId, boolean cursed) {
        return line(player, blockId, cursed);
    }

    /**
     * The block named as the game names it. Goes through an ItemStack rather than
     * {@code Block.getName()}, because that is what carries the display name a pack or a rarity gives
     * it -- the name as seen in an inventory, colour included. Falls back to the bare block name for a
     * block with no item, and to a generic word if the id was never captured.
     */
    private static Component blockName(@Nullable ResourceLocation blockId) {
        if (blockId != null) {
            Block block = ForgeRegistries.BLOCKS.getValue(blockId);
            if (block != null) {
                ItemStack stack = new ItemStack(block);
                return stack.isEmpty() ? block.getName() : stack.getHoverName();
            }
        }
        return Component.translatable("luckytweaks.announce.unknown_block");
    }
}
