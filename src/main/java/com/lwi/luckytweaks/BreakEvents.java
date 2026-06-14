package com.lwi.luckytweaks;

import com.lwi.luckytweaks.util.LuckyBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Captures the broken lucky block's REAL Luck while its block entity still exists.
 *
 * <p>The Lucky Block mod reads the block entity in {@code playerDestroy} -- which Minecraft runs
 * AFTER the block is removed -- so its own read always yields luck 0 on player breaks.
 * {@code mixin.DropEvaluatorMixin} later injects the value captured here into the drop roll.
 *
 * <p>Priority HIGHEST so the state is reset+captured before any other mod (e.g. one contributing
 * bonus luck through the API at a lower priority) touches this break. Mods using
 * {@link com.lwi.luckytweaks.api.LuckyTweaksApi#addLuck} should therefore subscribe at HIGH or below.
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID)
public final class BreakEvents {
    /** Hard bounds on any luck value handed to the Lucky Block mod's weight formula. The formula
     *  {@code levelIncrease = 1/(1 - |luck|*0.0077)} has a singularity at ~129.87; 120 stays safely below.
     *  Only TRANSIENT roll values may live in (100, 120] (e.g. a +100 block plus equipment bonuses). */
    public static final int LUCK_FLOOR = -100;
    public static final int LUCK_CEIL = 120;
    /** Bound on luck STORED on an item/block: the Lucky Block mod itself never produces blocks outside
     *  [-100, 100], so anything we write (fusion results...) must stay inside it too. */
    public static final int STORED_LUCK_MAX = 100;

    private BreakEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLuckyBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = event.getState();
        if (!LuckyBlocks.isLuckyBlock(state)) {
            return;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        // Disabled by the pack? Swallow the break entirely. Cancelling stops the effect for every
        // kind of lucky block alike -- the base mod fires its drops from playerDestroy (which a
        // cancelled break never reaches), and a cross-mod block (e.g. fuze_relics:lucky_blockling)
        // never runs its own break logic either. We then remove the block ourselves and drop it
        // straight back, Luck intact, so disabling never costs the player a block. Creative keeps
        // the vanilla "no drop" behaviour.
        if (DisabledBlocks.isDisabled(id)) {
            BlockPos pos = event.getPos();
            int luck = 0;
            BlockEntity disabledBe = level.getBlockEntity(pos);
            if (disabledBe != null) {
                luck = disabledBe.saveWithoutMetadata().getInt("Luck");
            }
            event.setCanceled(true);
            level.removeBlock(pos, false);
            Player player = event.getPlayer();
            if (player == null || !player.getAbilities().instabuild) {
                ItemStack drop = new ItemStack(state.getBlock());
                if (luck != 0) {
                    drop.getOrCreateTag().putInt("Luck", luck);
                }
                Block.popResource(level, pos, drop);
            }
            LuckState.reset();
            return;
        }

        // Reset first: if a previous break threw mid-pipeline, stale state must never leak onto
        // this one. Worst case after reset is "no fix applied", never "wrong luck applied".
        LuckState.reset();
        BlockEntity be = level.getBlockEntity(event.getPos());
        if (be != null) {
            int captured = be.saveWithoutMetadata().getInt("Luck");
            LuckState.CAPTURED.set(Math.max(LUCK_FLOOR, Math.min(LUCK_CEIL, captured)));
        }
        // Resolve the block's cap NOW (we know the id here); the mixin applies it after bonuses.
        if (id != null) {
            LuckState.CAP.set(LuckCaps.capFor(id));
        }
    }
}
