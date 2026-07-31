package com.lwi.luckytweaks.achievements;

import com.lwi.luckytweaks.CrocodileSwallow;
import com.lwi.luckytweaks.LuckyTweaksMod;
import com.lwi.luckytweaks.PlayerReviveCompat;
import com.lwi.luckytweaks.SacredHeartCap;
import com.lwi.luckytweaks.SharedLives;
import com.lwi.luckytweaks.TweaksConfig;
import com.lwi.luckytweaks.api.LuckyTweaksApi;
import com.lwi.luckytweaks.api.PlayerFellListener;
import com.lwi.luckytweaks.compat.CuriosWorn;
import com.lwi.luckytweaks.util.LuckyBlocks;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The half of the ladder that is about the PACK rather than about lucky blocks: the Lucky Tools you
 * collect (and the one that drops twice), the Chance you stack up, the water bosses a block throws at
 * you, the night you spend awake, the cheesecake nobody should eat.
 *
 * <p><b>Why the targets are config lists and not constants.</b> Everything here names content owned by
 * OTHER mods — Lucky Tools' eight items, Yakurum's bosses, Confluence's Extendo Grip, the pack's own
 * KubeJS cheesecake. Hard-coding those ids would mean a pack that swaps a mod out waits for a new jar;
 * with {@link TweaksConfig#ACHIEVEMENT_TOOLS} and friends it edits a list. An id no installed mod
 * provides is simply never matched, so a shorter pack quietly gets a shorter ladder instead of an error.
 *
 * <p><b>What this class cannot see for itself.</b> Three of the achievements sit inside other mods'
 * logic — a Lucky XP mega-jackpot event, a vending-machine purchase, an invasion carried to its end.
 * There is no way to observe those from here without reaching into their internals, so they arrive
 * through {@link LuckyTweaksApi#reportMegaJackpot}, {@link LuckyTweaksApi#reportShopPurchase} and
 * {@link LuckyTweaksApi#reportInvasionCompleted} (or, until a sibling mod adopts them, through
 * {@code /luckyachievements grant} from a KubeJS script or a datapack's invasion end command).
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID)
public final class PackAchievements {

    /** A night counts as pulled once the player has been awake this long inside it. The dark half of a
     *  Minecraft day is 10000 ticks; the slack covers a login mid-dusk and the odd server hiccup. */
    private static final int AWAKE_TICKS_FOR_NIGHT = 9000;
    /** Dusk and dawn, in a day's 24000 ticks: the window a night is counted over. */
    private static final long DUSK = 13000L;
    private static final long DAWN = 23000L;
    /** How often the per-player watches run. Bosses are scanned half as often as the rest. */
    private static final int WATCH_INTERVAL = 20;
    private static final int BOSS_INTERVAL = 40;

    private static final ResourceLocation BLOCK_REACH = new ResourceLocation("forge", "block_reach");

    // ---- config caches, re-read on every config load/reload (see ConfigWatch) ----
    private static volatile Set<String> toolItems = Set.of();
    private static volatile Set<String> weaponItems = Set.of();
    private static volatile Set<String> waterBosses = Set.of();
    private static volatile String extendoGripId = "";
    private static volatile String cheesecakeId = "";
    private static volatile String finalBossId = "";

    /** Per-player progress through the CURRENT night. Deliberately not saved: a night interrupted by a
     *  server restart is not a night anyone stayed up through. */
    private static final Map<UUID, NightWatch> NIGHTS = new HashMap<>();
    /** Players the shared-lives handler reported as DOWNED, until they get up or die for good. */
    private static final Set<UUID> DOWNED = new HashSet<>();

    private PackAchievements() {}

    // ---------------------------------------------------------------- lucky tools

    /**
     * The Lucky Tool that just dropped — and whether it is the SECOND of its kind, which is the joke the
     * pack asked for: a duplicate is not progress, and it gets its own (unflattering) achievement.
     *
     * <p>A duplicate is judged on the inventory, not on the "ever seen" set: a player who drops a radar
     * and picks it straight back up has one radar, not two, and telling them otherwise would make the
     * achievement a lie. Two of the same tool actually in hand is the real thing.
     */
    @SubscribeEvent
    public static void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        if (!AchievementEvents.enabled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getStack();
        String id = idOf(stack);
        if (id == null || !toolItems.contains(id)) {
            return;
        }
        AchievementData data = AchievementEvents.data(player);
        if (data == null) {
            return;
        }
        // Fired after the stack has been taken in, so a genuine duplicate is already the second one here.
        // Counted per KIND, not per pickup: dropping one of two rings and picking it up again is the same
        // duplicate, and a per-pickup counter would climb for as long as the player kept throwing it.
        if (data.hasToolKind(player, id) && held(player, stack.getItem()) >= 2) {
            AchievementEvents.fire(player, AchievementData.TOOL_DUPES, data.addToolDupeKind(player, id));
        }
        AchievementEvents.fire(player, AchievementData.TOOLS_FOUND, data.addToolKind(player, id));
    }

    /**
     * The same count, for tools that never went through a pickup: dispensed by a vending machine, handed
     * over by the merchant, given by a command. Sweeps the inventory on the slow watch — 41 slots once a
     * second is nothing, and it means "found" holds however the tool arrived.
     */
    private static void trackTools(ServerPlayer player, AchievementData data) {
        if (toolItems.isEmpty() || data.count(player, AchievementData.TOOLS_FOUND) >= toolItems.size()) {
            return;
        }
        for (ItemStack stack : player.getInventory().items) {
            String id = idOf(stack);
            if (id != null && toolItems.contains(id) && !data.hasToolKind(player, id)) {
                AchievementEvents.fire(player, AchievementData.TOOLS_FOUND, data.addToolKind(player, id));
            }
        }
        if (!CuriosWorn.available()) {
            return;
        }
        // The ring and the belt are worn, not carried: they are in no inventory slot at all.
        for (String id : toolItems) {
            if (data.hasToolKind(player, id)) {
                continue;
            }
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            if (item != null && CuriosWorn.isWearing(player, item)) {
                AchievementEvents.fire(player, AchievementData.TOOLS_FOUND, data.addToolKind(player, id));
            }
        }
    }

    /**
     * How many of {@code item} the player has on them right now: inventory, off hand, and the Curios slots
     * — the Lucky Ring and the Lucky Belt are WORN, so they leave the inventory entirely, and a count that
     * skipped them would call a second ring "the only one".
     */
    private static int held(ServerPlayer player, Item item) {
        int found = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                found += stack.getCount();
            }
        }
        if (player.getOffhandItem().is(item)) {
            found += player.getOffhandItem().getCount();
        }
        return found + CuriosWorn.countWorn(player, item);
    }

    // ---------------------------------------------------------------- chance

    /**
     * Record the best Chance a player ever carried into a break.
     *
     * <p>Priority LOWEST on purpose: Chance is contributed by other mods (the ring, the belt, a Lucky XP
     * event, the invasion malus) from their own {@code BreakEvent} handlers at HIGH or below, so this is
     * the first moment the total is final. It is the same number the drop roll is about to use.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSettled(BlockEvent.BreakEvent event) {
        if (!AchievementEvents.enabled() || event.isCanceled()
                || !(event.getPlayer() instanceof ServerPlayer player)
                || !LuckyBlocks.isLuckyBlock(event.getState())) {
            return;
        }
        // Silk touch picks the block up instead of opening it: no roll happens, so the Chance stacked up
        // for it was never actually spent. Recording it would also let a player bank their best Chance by
        // silk-breaking one block, the same infinite pump the break counters refuse.
        if (player.getMainHandItem().getEnchantmentLevel(Enchantments.SILK_TOUCH) > 0) {
            return;
        }
        int chance = LuckyTweaksApi.getContributedChance();
        if (chance > 0) {
            AchievementEvents.raise(player, AchievementData.CHANCE_MAX, chance);
        }
    }

    // ---------------------------------------------------------------- kamikaze weapons

    /** A lucky sword swung with no Luck on it. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            countRawWeapon(player, player.getMainHandItem());
        }
    }

    /** A lucky bow released with no Luck on it. */
    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            countRawWeapon(player, event.getBow());
        }
    }

    /**
     * Everything else in the weapon list — the lucky potion above all — counted where it is USED.
     * Swords and bows are skipped here: their real use is the swing and the release, and a right-click
     * would count a second time (and count a sword nobody swung).
     */
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof BowItem) {
            return;
        }
        countRawWeapon(player, stack);
    }

    /**
     * A lucky weapon fires a lucky-block drop table at whatever it hits. Infusing it with Luck is what
     * bends that table toward the good half — so using one at zero (or negative) Luck is a coin flip
     * between a legendary and a crater under your own feet. That is the achievement.
     */
    private static void countRawWeapon(ServerPlayer player, ItemStack stack) {
        if (!AchievementEvents.enabled() || stack == null || stack.isEmpty()) {
            return;
        }
        String id = idOf(stack);
        if (id == null || !weaponItems.contains(id)) {
            return;
        }
        int luck = stack.getTag() != null ? stack.getTag().getInt("Luck") : 0;
        if (luck <= 0) {
            AchievementEvents.bump(player, AchievementData.RAW_WEAPON_USES, 1);
        }
    }

    // ---------------------------------------------------------------- cheesecake

    /**
     * Two things worth eating: the pack's Cheesecake à la merde (whether it was the good half is not our
     * problem) and Yakurum's Sacred Heart, the only permanent max-health source the pack allows — and one
     * it caps, which makes eating them a ladder rather than a grind.
     */
    @SubscribeEvent
    public static void onFinishEating(LivingEntityUseItemEvent.Finish event) {
        if (!AchievementEvents.enabled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack eaten = event.getItem();
        if (!cheesecakeId.isEmpty() && cheesecakeId.equals(idOf(eaten))) {
            AchievementEvents.bump(player, AchievementData.CHEESECAKE, 1);
        }
        if (SacredHeartCap.isSacredHeart(eaten)) {
            AchievementEvents.bump(player, AchievementData.SACRED_HEARTS, 1);
        }
    }

    // ---------------------------------------------------------------- deaths worth remembering

    /**
     * Two deaths this mod already has a stake in.
     *
     * <p><b>The crocodile</b> — Fuze Relics' crocodile eats an item off you on a quarter of your hits, and
     * this mod is what makes it give the item back when it dies ({@link CrocodileSwallow}). Killing one
     * that still has somebody's gear in its stomach is the whole point of that fix, so it is worth a line.
     * Priority LOW, deliberately: {@link CrocodileSwallow} clears the stash at LOWEST, and two subscribers
     * on the same priority have no defined order — this one has to read the tag while it is still there.
     *
     * <p><b>The Ender Trigon</b> — the pack's stated goal ("slay the Ender Dra.. Trigon"). Credited to
     * everyone in the fight's dimension, because nobody kills that thing alone in a shared-lives run.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (!AchievementEvents.enabled() || event.getEntity().level().isClientSide()) {
            return;
        }
        LivingEntity dead = event.getEntity();
        if (dead.getPersistentData().contains(CrocodileSwallow.SWALLOWED_KEY, Tag.TAG_LIST)
                && event.getSource().getEntity() instanceof ServerPlayer killer) {
            AchievementEvents.bump(killer, AchievementData.CROC_RECOVERED, 1);
        }
        if (!finalBossId.isEmpty() && finalBossId.equals(idOf(dead))
                && dead.level() instanceof ServerLevel level) {
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator()) {
                    AchievementEvents.bump(player, AchievementData.DRAGON_SLAIN, 1);
                }
            }
        }
    }

    /**
     * The shared-lives side: being knocked down and getting back up is a team-mate's doing, and it is the
     * one thing in this pack that a player cannot achieve alone.
     *
     * <p>Watching {@code LivingDeathEvent} could not tell a revive from a respawn — the pack cancels the
     * death it saves — so the DOWNED/DIED split comes from the shared-lives handler itself, and the
     * "…and got back up" half is read off PlayerRevive's own downed flag on the slow watch.
     */
    private static void onPlayerFell(ServerPlayer player, PlayerFellListener.Reason reason) {
        if (reason == PlayerFellListener.Reason.DOWNED) {
            DOWNED.add(player.getUUID());
        } else {
            DOWNED.remove(player.getUUID()); // died for real: nobody got there in time
        }
    }

    /** Hook the shared-lives bus. Called once from {@link AchievementEvents#registerListeners()}. */
    static void registerListeners() {
        LuckyTweaksApi.registerPlayerFellListener(PackAchievements::onPlayerFell);
    }

    // ---------------------------------------------------------------- per-tick watches

    /**
     * The three things that are a STATE rather than an event: standing near a boss, wearing the Extendo
     * Grip, and being awake at 3am. Polled rather than hooked, because none of them fires anything this
     * mod can subscribe to.
     *
     * <p>Each watch pays for itself: the boss scan stops for a player who has met them all, the grip
     * check stops once earned, and the night watch is 20 ticks of arithmetic per player.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !AchievementEvents.enabled()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        long tick = server.getTickCount();
        boolean watch = tick % WATCH_INTERVAL == 0;
        boolean bosses = tick % BOSS_INTERVAL == 0;
        if (!watch && !bosses) {
            return;
        }
        AchievementData data = AchievementData.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            if (watch) {
                trackNight(player, data);
                trackTools(player, data);
                trackExtendoGrip(player, data);
                trackRun(server, player, data);
            }
            if (bosses) {
                trackWaterBosses(player, data);
            }
        }
    }

    /** Forget a player's half-finished night when they log out; it restarts from scratch next dusk. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        NIGHTS.remove(event.getEntity().getUUID());
        DOWNED.remove(event.getEntity().getUUID());
    }

    /**
     * An all-nighter: from dusk to dawn without touching a bed. Sleeping at any point during the night
     * disqualifies it (this pack has one-player-sleep, so one player in a bed ends everyone's night —
     * only the ones who stayed up get the achievement).
     */
    private static void trackNight(ServerPlayer player, AchievementData data) {
        Level level = player.level();
        long dayTime = level.getDayTime();
        long day = Math.floorDiv(dayTime, 24000L);
        long time = Math.floorMod(dayTime, 24000L);
        NightWatch watch = NIGHTS.computeIfAbsent(player.getUUID(), id -> new NightWatch());

        if (time >= DUSK && time < DAWN) {
            if (watch.day != day) { // a new night: start counting again
                watch.day = day;
                watch.awakeTicks = 0;
                watch.slept = false;
            }
            if (player.isSleeping()) {
                watch.slept = true;
            } else {
                watch.awakeTicks += WATCH_INTERVAL;
            }
            return;
        }
        // Daylight. Settle the night that just ended (same day number: dawn has not rolled the count).
        if (watch.day == day && watch.awakeTicks > 0) {
            if (!watch.slept && watch.awakeTicks >= AWAKE_TICKS_FOR_NIGHT) {
                AchievementEvents.fire(player, AchievementData.ALL_NIGHTERS,
                        data.increment(player, AchievementData.ALL_NIGHTERS, 1));
            }
            watch.day = -1;
            watch.awakeTicks = 0;
            watch.slept = false;
        }
    }

    /**
     * How far the RUN has got, from this player's seat: the day count they are alive for, whether they
     * ever played on with the pool down to its last life, and whether they got back up after going down.
     *
     * <p>The day is read off the overworld, not off the player's own dimension — a run's age is the run's,
     * and the End does not have a day count worth the name. It is a personal best, so a player who joins on
     * day 40 starts at 40: the counter says "this run was that old while I was in it", which is what a
     * hardcore pack means by surviving.
     */
    private static void trackRun(MinecraftServer server, ServerPlayer player, AchievementData data) {
        if (player.isAlive()) {
            long day = Math.floorDiv(server.overworld().getDayTime(), 24000L);
            if (day > 0) {
                AchievementEvents.fire(player, AchievementData.DAYS_SURVIVED,
                        data.raise(player, AchievementData.DAYS_SURVIVED, (int) Math.min(day, Integer.MAX_VALUE)));
            }
            // Down to one life and still playing: the next mistake ends the run for everyone.
            //
            // The run must have STARTED with more than one, or this says nothing: a solo run begins on a
            // single life by design, so "one life left" would be true from the first second and every solo
            // player would own this before they broke a block. What is worth an achievement is a pool that
            // was spent down to its last one.
            if (SharedLives.remaining(server) == 1 && SharedLives.maxLives(server) > 1
                    && data.count(player, AchievementData.LAST_STAND) == 0) {
                AchievementEvents.fire(player, AchievementData.LAST_STAND,
                        data.increment(player, AchievementData.LAST_STAND, 1));
            }
        }
        // Down a moment ago, up and alive now: somebody came and got them.
        if (DOWNED.contains(player.getUUID()) && player.isAlive() && !PlayerReviveCompat.isDowned(player)) {
            DOWNED.remove(player.getUUID());
            AchievementEvents.fire(player, AchievementData.REVIVED,
                    data.increment(player, AchievementData.REVIVED, 1));
        }
    }

    /**
     * The Extendo Grip, once. Read through Curios when it is there; otherwise fall back to "the player
     * carries it AND their block reach is above vanilla", which is what an accessory system of a mod's
     * own making looks like from the outside.
     */
    private static void trackExtendoGrip(ServerPlayer player, AchievementData data) {
        if (extendoGripId.isEmpty() || data.count(player, AchievementData.EXTENDO) > 0) {
            return;
        }
        Item grip = ForgeRegistries.ITEMS.getValue(new ResourceLocation(extendoGripId));
        if (grip == null) {
            return; // no such item in this pack -- the achievement simply never comes up
        }
        if (CuriosWorn.isWearing(player, grip) || (carries(player, grip) && hasExtraBlockReach(player))) {
            AchievementEvents.fire(player, AchievementData.EXTENDO,
                    data.increment(player, AchievementData.EXTENDO, 1));
        }
    }

    private static boolean carries(ServerPlayer player, Item item) {
        return player.getInventory().contains(new ItemStack(item));
    }

    private static boolean hasExtraBlockReach(ServerPlayer player) {
        Attribute reach = ForgeRegistries.ATTRIBUTES.getValue(BLOCK_REACH);
        if (reach == null || player.getAttribute(reach) == null) {
            return false;
        }
        return player.getAttributeValue(reach) > player.getAttributeBaseValue(reach) + 1.0E-4D;
    }

    /** Every configured water boss within range counts as met, one entry per kind. */
    private static void trackWaterBosses(ServerPlayer player, AchievementData data) {
        if (waterBosses.isEmpty()
                || data.count(player, AchievementData.WATER_BOSSES) >= waterBosses.size()
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        double range = TweaksConfig.ACHIEVEMENT_WATER_BOSS_RANGE.get();
        AABB box = player.getBoundingBox().inflate(range);
        List<Entity> near = level.getEntities(player, box, entity -> waterBosses.contains(idOf(entity)));
        for (Entity entity : near) {
            String id = idOf(entity);
            if (id != null) {
                AchievementEvents.fire(player, AchievementData.WATER_BOSSES,
                        data.addWaterBossKind(player, id));
            }
        }
    }

    // ---------------------------------------------------------------- reported from outside

    /** A Lucky XP world event that landed on a MEGA jackpot. See {@link LuckyTweaksApi#reportMegaJackpot}. */
    public static void reportMegaJackpot(ServerPlayer player) {
        AchievementEvents.bump(player, AchievementData.MEGA_JACKPOTS, 1);
    }

    /** One item bought from a shop; {@code legendary} marks the legendary-rarity slots. */
    public static void reportShopPurchase(ServerPlayer player, boolean legendary) {
        AchievementEvents.bump(player, AchievementData.SHOP_BUYS, 1);
        if (legendary) {
            AchievementEvents.bump(player, AchievementData.LEGENDARY_BUYS, 1);
        }
    }

    /** An invasion this player saw through to its end. */
    public static void reportInvasionCompleted(ServerPlayer player) {
        AchievementEvents.bump(player, AchievementData.INVASIONS, 1);
    }

    /**
     * A cursed drop, counted at the roll. Called from {@link com.lwi.luckytweaks.BreakEvents} on the same
     * delayed schedule as the Lucky Stats counter: the bump is held back five seconds so a toast cannot
     * tell the player the drop was cursed before the drop does.
     */
    public static void reportCursedDrop(ServerPlayer player) {
        AchievementEvents.bump(player, AchievementData.CURSED_DROPS, 1);
    }

    // ---------------------------------------------------------------- plumbing

    private static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? null : id.toString();
    }

    private static String idOf(Entity entity) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id == null ? null : id.toString();
    }

    /** Re-read every target list. Called on config load and reload. */
    public static void refresh() {
        toolItems = copyOf(TweaksConfig.ACHIEVEMENT_TOOLS.get());
        weaponItems = copyOf(TweaksConfig.ACHIEVEMENT_WEAPONS.get());
        waterBosses = copyOf(TweaksConfig.ACHIEVEMENT_WATER_BOSSES.get());
        extendoGripId = TweaksConfig.ACHIEVEMENT_EXTENDO_GRIP.get().trim();
        cheesecakeId = TweaksConfig.ACHIEVEMENT_CHEESECAKE.get().trim();
        finalBossId = TweaksConfig.ACHIEVEMENT_FINAL_BOSS.get().trim();
    }

    private static Set<String> copyOf(List<? extends String> entries) {
        Set<String> parsed = new LinkedHashSet<>();
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                parsed.add(trimmed);
            }
        }
        return parsed.isEmpty() ? Set.of() : Set.copyOf(parsed);
    }

    /** How far into the current night one player is. */
    private static final class NightWatch {
        private long day = -1;
        private int awakeTicks;
        private boolean slept;
    }

    /** The config lists are read per tick and per pickup, so they are cached rather than re-parsed. */
    @Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ConfigWatch {
        private ConfigWatch() {}

        @SubscribeEvent
        public static void onConfig(ModConfigEvent event) {
            if (event.getConfig().getSpec() == TweaksConfig.COMMON_SPEC) {
                refresh();
            }
        }
    }
}
