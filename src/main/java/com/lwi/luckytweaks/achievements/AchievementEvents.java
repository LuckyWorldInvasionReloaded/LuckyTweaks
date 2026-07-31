package com.lwi.luckytweaks.achievements;

import com.lwi.luckytweaks.LuckyTweaksMod;
import com.lwi.luckytweaks.TweaksConfig;
import com.lwi.luckytweaks.api.LuckyTweaksApi;
import com.lwi.luckytweaks.util.LuckyBlocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Where the achievement counters actually move, and the only place that talks to
 * {@link LuckyTriggers#PROGRESS}.
 *
 * <p><b>Nothing here changes an existing behaviour.</b> Breaks are observed through the mod's own
 * {@link LuckyTweaksApi#registerBreakListener break listener} and
 * {@link LuckyTweaksApi#registerLegendaryDropListener legendary listener} — the same public extension
 * points Lucky XP uses — so the drop pipeline is untouched, and a listener that threw would be swallowed
 * by the bus rather than costing a player their block. Crafting is observed through Forge's
 * {@code ItemCraftedEvent}, which fires after the craft is settled and cannot cancel it.
 *
 * <p>Every counter is stored in {@link AchievementData}; the advancement side is only ever TOLD the new
 * value. That split is what lets a datapack move a threshold, or a pack add tiers, without the counters
 * being rebuilt.
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID)
public final class AchievementEvents {
    private static boolean listenersRegistered;

    private AchievementEvents() {}

    /**
     * Hook this mod's own break/legendary buses. Called from common setup, once: the buses are static
     * lists that outlive a world, so registering per world load would stack duplicate listeners and
     * count every break twice on the second world.
     */
    public static void registerListeners() {
        if (listenersRegistered) {
            return;
        }
        listenersRegistered = true;
        LuckyTweaksApi.registerBreakListener(AchievementEvents::onLuckyBlockBroken);
        LuckyTweaksApi.registerLegendaryDropListener((player, pos) -> {
            if (enabled() && player != null) {
                AchievementData data = data(player);
                if (data != null) {
                    fire(player, AchievementData.LEGENDARY,
                            data.increment(player, AchievementData.LEGENDARY, 1));
                }
            }
        });
    }

    // ---------------------------------------------------------------- breaking

    private static void onLuckyBlockBroken(ServerPlayer player, ResourceLocation blockId,
                                           net.minecraft.core.BlockPos pos, int capturedLuck) {
        if (!enabled() || player == null || blockId == null) {
            return;
        }
        // Silk touch = the block is picked up, not opened, so there is no roll to be proud of. Counting
        // it would make every break tier farmable by silk-breaking and re-placing the same block for as
        // long as you like -- the infinite pump Lucky XP refuses here for the same reason.
        if (player.getMainHandItem().getEnchantmentLevel(Enchantments.SILK_TOUCH) > 0) {
            return;
        }
        AchievementData data = data(player);
        if (data == null) {
            return;
        }
        fire(player, AchievementData.BROKEN, data.increment(player, AchievementData.BROKEN, 1));
        fire(player, AchievementData.BROKEN_TYPES, data.addBrokenKind(player, blockId.toString()));

        // The block's own Luck at the moment it was broken -- a cursed block knowingly broken, and a
        // block broken at the +100 ceiling, are each their own line of achievements.
        if (capturedLuck < 0) {
            fire(player, AchievementData.NEGATIVE_LUCK_BREAKS,
                    data.increment(player, AchievementData.NEGATIVE_LUCK_BREAKS, 1));
        } else if (capturedLuck >= 100) {
            fire(player, AchievementData.MAX_LUCK_BREAKS,
                    data.increment(player, AchievementData.MAX_LUCK_BREAKS, 1));
        }
    }

    // ---------------------------------------------------------------- crafting

    /**
     * Record the Luck of a lucky block the player just crafted, and whether it came out of a fusion.
     *
     * <p>Covers BOTH ways a lucky block gains Luck: the Lucky Block mod's own luck-crafting recipe
     * (block + luck items) and this mod's {@link com.lwi.luckytweaks.LuckFusionRecipe}. Reading the
     * RESULT rather than either recipe means the per-block luck cap has already been applied, so an
     * achievement can never be earned on a number the block will not actually deliver.
     *
     * <p>Fired before the grid is emptied, so the inputs are still there to tell a fusion (two or more
     * lucky blocks in) from a luck craft (one block plus luck items). That input count is a heuristic,
     * not a recipe check: {@code ItemCraftedEvent} does not carry the recipe in 1.20.1, so any future
     * recipe taking two lucky blocks would also be counted as a fusion.
     */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack result = event.getCrafting();
        if (!isLuckyBlockItem(result)) {
            return;
        }
        AchievementData data = data(player);
        if (data == null) {
            return;
        }

        CompoundTag tag = result.getTag();
        int luck = (tag != null) ? tag.getInt("Luck") : 0;
        if (luck > 0) {
            fire(player, AchievementData.CRAFTED_LUCK_MAX,
                    data.raise(player, AchievementData.CRAFTED_LUCK_MAX, luck));
        } else if (luck < 0) {
            fire(player, AchievementData.CRAFTED_LUCK_MIN,
                    data.lower(player, AchievementData.CRAFTED_LUCK_MIN, luck));
        }

        if (countLuckyBlockInputs(event.getInventory()) >= 2) {
            // One event per craft -- a shift-click repeats the craft and fires this again each time --
            // so count 1, not the result stack. The stack is the size of a SINGLE craft, and counting it
            // would inflate the total on any recipe that ever returns more than one block.
            fire(player, AchievementData.FUSED, data.increment(player, AchievementData.FUSED, 1));
        }
    }

    private static boolean isLuckyBlockItem(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getItem() instanceof BlockItem blockItem
                && LuckyBlocks.isLuckyBlock(blockItem.getBlock());
    }

    private static int countLuckyBlockInputs(Container grid) {
        if (grid == null) {
            return 0;
        }
        int found = 0;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            if (isLuckyBlockItem(grid.getItem(i))) {
                found++;
            }
        }
        return found;
    }

    // ---------------------------------------------------------------- login

    /**
     * Re-offer every stored counter when a player joins.
     *
     * <p>Without this, progress made before an achievement existed would never be awarded: the criterion
     * is only checked when it is offered a value, and a player who already broke 300 lucky blocks would
     * sit on an empty tab until block 301. Re-firing on login also repairs a world where the advancement
     * file was reset, and costs nothing — vanilla ignores an offer for a criterion already satisfied.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        AchievementData data = data(player);
        if (data == null) {
            return;
        }
        for (String stat : new String[]{
                AchievementData.BROKEN, AchievementData.BROKEN_TYPES,
                AchievementData.CRAFTED_LUCK_MAX, AchievementData.CRAFTED_LUCK_MIN,
                AchievementData.FUSED, AchievementData.LEGENDARY,
                AchievementData.NEGATIVE_LUCK_BREAKS, AchievementData.MAX_LUCK_BREAKS}) {
            fire(player, stat, data.count(player, stat));
        }
    }

    // ---------------------------------------------------------------- plumbing

    private static boolean enabled() {
        // The spec is only loaded once the config file is read; before that (very early load) treat the
        // feature as off rather than risking an exception on an unloaded value.
        try {
            return TweaksConfig.ENABLE_ACHIEVEMENTS.get();
        } catch (IllegalStateException notLoadedYet) {
            return false;
        }
    }

    private static AchievementData data(Player player) {
        MinecraftServer server = player.getServer();
        return server == null ? null : AchievementData.get(server);
    }

    /** Offer a value to the advancement system; a value of 0 is worth offering (0 satisfies nothing). */
    private static void fire(ServerPlayer player, String stat, int value) {
        LuckyTriggers.PROGRESS.fire(player, stat, value);
    }
}
