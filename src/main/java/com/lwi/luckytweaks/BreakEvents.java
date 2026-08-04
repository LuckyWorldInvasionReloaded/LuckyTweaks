package com.lwi.luckytweaks;

import com.lwi.luckytweaks.util.LuckyBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
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
     *  {@code levelIncrease = 1/(1 - |luck|*0.0077)} has a singularity at ~129.87. Effective luck is
     *  capped at +100 (user rule 2026-06-20: ring/belt/event ADD onto the block's luck, but the total
     *  never exceeds +100; per-block caps like the Tools LB's +50 still apply below that). */
    public static final int LUCK_FLOOR = -100;
    public static final int LUCK_CEIL = 100;
    /** Bound on luck STORED on an item/block: the Lucky Block mod itself never produces blocks outside
     *  [-100, 100], so anything we write (fusion results...) must stay inside it too. */
    public static final int STORED_LUCK_MAX = 100;

    /**
     * One-shot guard for the legendary-drop COUNTER, driven from the drop-roll side
     * ({@code mixin.LegendaryCounterMixin}, which hooks the Lucky Block mod's {@code runEvaluatedDrop}).
     *
     * <p>Tri-state, per server thread:
     * <ul>
     *   <li>{@code null} — not armed: no real player break is "in flight" this tick, so a legendary
     *       evaluation must NOT be counted. This is the default and the state restored at the end of
     *       every server tick, so a DELAYED legendary reveal (the suspense wrap fires the actual drop
     *       ~44 ticks later, in a LATER tick) is never double-counted.</li>
     *   <li>{@code FALSE} — armed, not yet counted: a real player just broke a (non-disabled) lucky
     *       block on THIS tick; the first legendary evaluation seen may count.</li>
     *   <li>{@code TRUE} — already counted: consumed by the first increment, so any further legendary
     *       sub-drop in the SAME break (e.g. a multi-legendary roll) does not count again.</li>
     * </ul>
     *
     * <p>Why count at the ROLL and not by scanning spawned entities afterwards: the suspense rework
     * wraps each legendary sub-drop in {@code group(<sounds+particles>; <item>, delay=2.2)}, so the
     * item/entity only spawns ~44 ticks after the break — a post-spawn AABB scan would miss it. The
     * roll, by contrast, happens synchronously on the break tick, before the delay is applied, so the
     * legendary marker is already visible there regardless of the reveal delay or its position.
     */
    public static final ThreadLocal<Boolean> LEGENDARY_COUNTED_THIS_BREAK =
            ThreadLocal.withInitial(() -> null);

    /** Position of the lucky block broken on THIS tick, so the drop roll can tell listeners where it
     *  happened. Armed alongside the guards above, cleared with them at end-of-tick. */
    public static final ThreadLocal<BlockPos> BREAK_POS = ThreadLocal.withInitial(() -> null);

    /** Which lucky block was broken on THIS tick. The roll knows the player, not the block, and the
     *  block is already gone by the time a drop is announced. Armed and cleared with {@link #BREAK_POS}. */
    public static final ThreadLocal<ResourceLocation> BREAK_ID = ThreadLocal.withInitial(() -> null);

    /** How long a curse is kept quiet, counter and announcement alike. */
    private static final int CURSED_REVEAL_TICKS = 100;

    /**
     * The curse twin of {@link #LEGENDARY_COUNTED_THIS_BREAK}: the same tri-state one-shot guard, for the
     * CURSED counter ({@code mixin.CursedCounterMixin}, which keys on the Elder Guardian curse sound at
     * {@code runEvaluatedDrop}). Armed and reset on exactly the same ticks; a curse and a legendary never
     * coexist in one drop, so the two guards are fully independent.
     */
    public static final ThreadLocal<Boolean> CURSED_COUNTED_THIS_BREAK =
            ThreadLocal.withInitial(() -> null);

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
        // Capture player + block id for the /luckychance debug report (real players only).
        LuckState.BLOCK_ID.set(id);
        if (event.getPlayer() instanceof ServerPlayer dbgPlayer
                && !(dbgPlayer instanceof net.minecraftforge.common.util.FakePlayer)) {
            LuckState.PLAYER.set(dbgPlayer);
        }

        // Listener notification + counter arming happen at LOWEST, see notifyListeners below.
    }

    /**
     * Notify external listeners (e.g. Lucky XP's experience award) that a player broke a lucky block,
     * and arm the per-break counters.
     *
     * <p><b>LOWEST, and only when the break SURVIVED.</b> The capture above must run at HIGHEST (the
     * block entity still exists there), but the notification must not: anything cancelling the break at
     * a lower priority — stand protection, a claim mod, spawn protection — would otherwise leave the
     * block standing while listeners had already been told it broke. That is a free-XP farm: break the
     * same protected block forever, get paid every time (reported in a Lucky Lab, 2026-07-19). LOWEST is
     * the last priority tier, so by the time this runs every cancel has had its say.
     *
     * <p>The ThreadLocals set at HIGHEST are still live here (same tick, cleared at end-of-tick), and the
     * drop roll runs later still, in {@code playerDestroy}, so the counters are armed in time.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void notifyListeners(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel)) {
            return;
        }
        BlockState state = event.getState();
        if (!LuckyBlocks.isLuckyBlock(state)) {
            return;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null || DisabledBlocks.isDisabled(id)) {
            return;
        }
        if (event.getPlayer() instanceof ServerPlayer serverPlayer
                && !(serverPlayer instanceof net.minecraftforge.common.util.FakePlayer)) {
            Integer cl = LuckState.CAPTURED.get();
            LuckyBlockBreakBus.fire(serverPlayer, id, event.getPos(), cl != null ? cl : 0);

            // Arm the legendary counter for THIS break: the drop-roll mixin (LegendaryCounterMixin)
            // bumps the stat the first time the Lucky Block mod evaluates a drop carrying the LWLeg
            // marker. FALSE = "armed, not yet counted". Counting at the roll (not by scanning spawned
            // entities) is what makes it survive the suspense reveal delay -- the marked item/entity
            // spawns ~44 ticks later, but the roll happens on the break tick.
            LEGENDARY_COUNTED_THIS_BREAK.set(Boolean.FALSE);
            // Arm the cursed counter for THIS break too (its mixin keys on the curse sound at the roll).
            CURSED_COUNTED_THIS_BREAK.set(Boolean.FALSE);
            // Remember where, so LegendaryDropBus listeners get a position: the roll knows the player
            // but not the block. Cleared with the guards at end-of-tick.
            BREAK_POS.set(event.getPos());
            BREAK_ID.set(id);
        }
    }

    /**
     * Count a legendary drop for {@code player}, at most once per break. Called by
     * {@code mixin.LegendaryCounterMixin} from the Lucky Block mod's drop evaluation when a chosen
     * sub-drop carries the {@code LWLeg} marker. Guarded by {@link #LEGENDARY_COUNTED_THIS_BREAK}:
     * <ul>
     *   <li>only fires while the flag is {@code FALSE} (a real player broke a lucky block on this tick
     *       and nothing has been counted yet), so a delayed reveal in a later tick -- where the flag
     *       has been reset to {@code null} at end-of-tick -- never counts;</li>
     *   <li>consumes the flag to {@code TRUE} on the first hit, so a second legendary sub-drop in the
     *       same break (multi-legendary roll) is not counted twice.</li>
     * </ul>
     */
    public static void countLegendaryAtRoll(ServerPlayer player) {
        if (player == null || player instanceof net.minecraftforge.common.util.FakePlayer) {
            return;
        }
        if (!Boolean.FALSE.equals(LEGENDARY_COUNTED_THIS_BREAK.get())) {
            return; // not armed (null), or already counted this break (TRUE)
        }
        LEGENDARY_COUNTED_THIS_BREAK.set(Boolean.TRUE);
        LegendaryStatsBridge.increment(player);
        // Same one-shot guard, so external listeners (Lucky XP's legendary bonus) also see exactly one
        // event per break. Fired after the stat, still synchronously on the break tick.
        LegendaryDropBus.fire(player, BREAK_POS.get());

        // Announce a legendary RIGHT AWAY, with the drumroll rather than after it: the suspense wrap
        // takes 2.2 s to reveal the drop, and the whole point is that everyone spends them waiting with
        // the player. Nothing is spoiled -- the chat says a legendary is coming, not what it is.
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server != null) {
            RareDropAnnouncer.announce(server, player.getUUID(), BREAK_ID.get(), false);
        }
    }

    /**
     * Count a cursed drop for {@code player}, at most once per break. Called by {@code mixin.CursedCounterMixin}
     * when the Lucky Block mod evaluates our curse-sound sub-drop. Same guard contract as
     * {@link #countLegendaryAtRoll}: only fires while {@link #CURSED_COUNTED_THIS_BREAK} is {@code FALSE}
     * (a real player broke a lucky block this tick, nothing counted yet) and consumes it to {@code TRUE},
     * so a curse with several content sub-drops -- or the sound matched more than once -- counts only once.
     */
    public static void countCursedAtRoll(ServerPlayer player) {
        if (player == null || player instanceof net.minecraftforge.common.util.FakePlayer) {
            return;
        }
        if (!Boolean.FALSE.equals(CURSED_COUNTED_THIS_BREAK.get())) {
            return; // not armed (null), or already counted this break (TRUE)
        }
        CURSED_COUNTED_THIS_BREAK.set(Boolean.TRUE);
        // Anti-spoiler: DELAY the counter bump by 5 s (100 ticks). The "Cursed drops: N" HUD must NOT tick
        // up in sync with the bad drop, or the player would know instantly the drop was cursed. Re-resolve
        // the player by UUID at fire time (if they log off in the interval we simply skip).
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        java.util.UUID uuid = player.getUUID();
        ResourceLocation blockId = BREAK_ID.get();   // cleared at end-of-tick, long before this fires
        com.lwi.luckytweaks.util.ServerScheduler.schedule(CURSED_REVEAL_TICKS, () -> {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                CursedStatsBridge.increment(p);
            }
            // Same delay as the counter: announcing sooner would out the curse before the player feels it.
            RareDropAnnouncer.announce(server, uuid, blockId, true);
        });
    }

    /**
     * Clear the legendary-counter arming at the end of every server tick. The whole break -> roll
     * sequence is synchronous within a single tick, so by END it is fully done; resetting to the
     * unarmed state ({@code null}) here means a DELAYED legendary reveal (fired ~44 ticks later by the
     * suspense {@code group(...,delay=2.2)} wrap) lands on an unarmed flag and is correctly NOT
     * counted -- and the arming can never leak onto a later, unrelated tick's break.
     */
    @SubscribeEvent
    public static void onServerTickEnd(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            LEGENDARY_COUNTED_THIS_BREAK.set(null);
            CURSED_COUNTED_THIS_BREAK.set(null);
            BREAK_POS.set(null);
            BREAK_ID.set(null);
        }
    }
}
