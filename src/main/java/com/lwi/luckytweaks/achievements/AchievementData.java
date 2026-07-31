package com.lwi.luckytweaks.achievements;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/**
 * The run's per-player achievement counters -- how many lucky blocks a player has broken, how many
 * distinct kinds, the best and worst Luck they ever crafted, and so on.
 *
 * <p><b>Why a SavedData and not the player's NBT.</b> Same reasoning as
 * {@link com.lwi.luckytweaks.SharedLives}: the world's data storage is a place this mod fully owns, it
 * survives a reload, and — unlike a player's own tag — it is untouched by death, respawn and
 * {@code keepInventory}. An achievement counter that reset on death would be worthless in a pack whose
 * whole point is that you die.
 *
 * <p>Counters are keyed by player UUID, so a player who leaves and comes back keeps their progress, and
 * an offline player's numbers are still readable (the {@code /luckyachievements} listing).
 *
 * <p>This class only STORES. Deciding when a number goes up, and telling the advancement system about
 * it, is {@link AchievementEvents}' job.
 */
public final class AchievementData extends SavedData {
    private static final String NAME = "luckytweaks_achievements";

    // ---- counter keys. Also the "stat" strings used by the advancement JSONs. ----

    /** Lucky blocks broken, all kinds counted together. */
    public static final String BROKEN = "broken";
    /** How many DISTINCT kinds of lucky block this player has broken (size of the {@link #BROKEN_KINDS} set). */
    public static final String BROKEN_TYPES = "broken_types";
    /** The set backing {@link #BROKEN_TYPES}: one entry per block id ever broken. */
    private static final String BROKEN_KINDS = "broken_kinds";
    /** Highest POSITIVE Luck this player ever crafted onto a lucky block (luck crafting or fusion). */
    public static final String CRAFTED_LUCK_MAX = "crafted_luck_max";
    /** Lowest NEGATIVE Luck ever crafted. Stored as the negative number itself, so it only ever goes down. */
    public static final String CRAFTED_LUCK_MIN = "crafted_luck_min";
    /** Lucky blocks fused together through {@link com.lwi.luckytweaks.LuckFusionRecipe}. */
    public static final String FUSED = "fused";
    /** Legendary drops rolled, as counted by {@link com.lwi.luckytweaks.LegendaryDropBus}. */
    public static final String LEGENDARY = "legendary";
    /** Lucky blocks broken while the block itself carried negative Luck (a knowingly cursed break).
     *  Deliberately NOT named {@code cursed_breaks}: Lucky Stats already uses that string for a different
     *  thing (a drop that rolled the {@code LWCurse} marker), and two counters answering different
     *  questions under one name is how a pack author ends up debugging the wrong number. */
    public static final String NEGATIVE_LUCK_BREAKS = "negative_luck_breaks";
    /** Lucky blocks broken while the block carried +100 Luck -- the ceiling the pack allows. */
    public static final String MAX_LUCK_BREAKS = "max_luck_breaks";

    // ---- the pack's own ladder: what the OTHER mods in Lucky World Invasion put in a player's way ----

    /** How many DISTINCT Lucky Tools the player has ever held (radar, wand, shield, spawner, idol,
     *  hammer, ring, belt). Backed by the {@link #TOOL_KINDS} set. */
    public static final String TOOLS_FOUND = "tools_found";
    /** The set backing {@link #TOOLS_FOUND}: one entry per tool id ever picked up. */
    private static final String TOOL_KINDS = "tool_kinds";
    /** How many DISTINCT Lucky Tools the player has ever held two of at once. Not cool.
     *  Distinct kinds, not raw pickups: a player who drops one of their two rings and picks it back up has
     *  not found a second one, and a counter that said otherwise would climb for as long as they kept
     *  dropping it. Backed by {@link #TOOL_DUPE_KINDS}. */
    public static final String TOOL_DUPES = "tool_dupes";
    /** The set backing {@link #TOOL_DUPES}: one entry per tool id ever held in double. */
    private static final String TOOL_DUPE_KINDS = "tool_dupe_kinds";
    /** Best total Chance (percentile points) the player ever carried into a single lucky-block break --
     *  ring + belt + event + invasion malus, as the drop roll saw it. */
    public static final String CHANCE_MAX = "chance_max";
    /** Lucky XP world events that landed on a MEGA jackpot while the player was there. Reported by Lucky XP
     *  through {@link com.lwi.luckytweaks.api.LuckyTweaksApi#reportMegaJackpot}. */
    public static final String MEGA_JACKPOTS = "mega_jackpots";
    /** Items bought from a shop -- a Lucky XP vending machine or the Lucky Merchant. Reported through
     *  {@link com.lwi.luckytweaks.api.LuckyTweaksApi#reportShopPurchase}. */
    public static final String SHOP_BUYS = "shop_buys";
    /** Of those purchases, the ones taken from a LEGENDARY-rarity slot. */
    public static final String LEGENDARY_BUYS = "legendary_buys";
    /** Swings/shots/throws of a lucky weapon carrying no Luck at all -- pure kamikaze. */
    public static final String RAW_WEAPON_USES = "raw_weapon_uses";
    /** How many DISTINCT water bosses the player has stood face to face with. Backed by
     *  {@link #WATER_BOSS_KINDS}. */
    public static final String WATER_BOSSES = "water_bosses";
    /** The set backing {@link #WATER_BOSSES}: one entry per boss entity id ever met. */
    private static final String WATER_BOSS_KINDS = "water_boss_kinds";
    /** Invasions the player saw through to the end. Reported by Optional Suffering through
     *  {@link com.lwi.luckytweaks.api.LuckyTweaksApi#reportInvasionCompleted}. */
    public static final String INVASIONS = "invasions";
    /** 1 once the player has worn the Extendo Grip. Extendooooo. */
    public static final String EXTENDO = "extendo";
    /** Nights spent awake from dusk to dawn, no bed. */
    public static final String ALL_NIGHTERS = "all_nighters";
    /** Cheesecakes à la merde eaten. Bravery, or a very short memory. */
    public static final String CHEESECAKE = "cheesecake";
    /** Cursed drops rolled -- the curse twin of {@link #LEGENDARY}, counted at the roll. */
    public static final String CURSED_DROPS = "cursed_drops";
    /** Yakurum Sacred Hearts eaten (the ones the pack caps at ten). */
    public static final String SACRED_HEARTS = "sacred_hearts";
    /** The highest world day this player was alive for. A hardcore run's real score. */
    public static final String DAYS_SURVIVED = "days_survived";
    /** Times this player was knocked down and got back up -- a team-mate came for them. */
    public static final String REVIVED = "revived";
    /** 1 once the player has played on with the run's LAST shared life on the line. */
    public static final String LAST_STAND = "last_stand";
    /** Crocodiles killed while they still had somebody's gear in their stomach. */
    public static final String CROC_RECOVERED = "croc_recovered";
    /** 1 once the Ender Trigon has fallen -- the pack's stated goal. */
    public static final String DRAGON_SLAIN = "dragon_slain";
    /** Chaos Lucky Block gauntlets escaped through the door. Reported by the pack's own script. */
    public static final String GAUNTLET_ESCAPES = "gauntlet_escapes";

    /**
     * Every counter, in reading order. The single list the login re-offer, the {@code /luckyachievements}
     * report and its {@code set}/{@code grant} completion all read, so a new stat is added in ONE place.
     */
    public static final String[] ALL_STATS = {
            BROKEN, BROKEN_TYPES, MAX_LUCK_BREAKS, NEGATIVE_LUCK_BREAKS, LEGENDARY, FUSED,
            CRAFTED_LUCK_MAX, CRAFTED_LUCK_MIN,
            TOOLS_FOUND, TOOL_DUPES, CHANCE_MAX, MEGA_JACKPOTS, SHOP_BUYS, LEGENDARY_BUYS,
            RAW_WEAPON_USES, WATER_BOSSES, INVASIONS, EXTENDO, ALL_NIGHTERS, CHEESECAKE,
            CURSED_DROPS, SACRED_HEARTS, DAYS_SURVIVED, REVIVED, LAST_STAND, CROC_RECOVERED,
            DRAGON_SLAIN, GAUNTLET_ESCAPES,
    };

    /** uuid string -> that player's counters. One flat compound per player. */
    private final CompoundTag players = new CompoundTag();

    public static AchievementData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(AchievementData::load, AchievementData::new, NAME);
    }

    private static AchievementData load(CompoundTag tag) {
        AchievementData data = new AchievementData();
        CompoundTag stored = tag.getCompound("players");
        for (String key : stored.getAllKeys()) {
            data.players.put(key, stored.getCompound(key).copy());
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("players", players.copy());
        return tag;
    }

    private CompoundTag of(UUID id) {
        String key = id.toString();
        if (!players.contains(key, Tag.TAG_COMPOUND)) {
            players.put(key, new CompoundTag());
        }
        return players.getCompound(key);
    }

    /** This player's value for {@code stat}, 0 when they have never scored on it. */
    public int count(ServerPlayer player, String stat) {
        return of(player.getUUID()).getInt(stat);
    }

    /** Add {@code delta} to a counter; returns the value AFTER the write. */
    public int increment(ServerPlayer player, String stat, int delta) {
        CompoundTag tag = of(player.getUUID());
        int value = tag.getInt(stat) + delta;
        tag.putInt(stat, value);
        setDirty();
        return value;
    }

    /**
     * Keep the HIGHEST value ever seen for this stat (a personal best, e.g. the best Luck ever crafted).
     * Returns the stored value afterwards, which may be the one already there.
     */
    public int raise(ServerPlayer player, String stat, int value) {
        CompoundTag tag = of(player.getUUID());
        int best = Math.max(tag.getInt(stat), value);
        if (best != tag.getInt(stat)) {
            tag.putInt(stat, best);
            setDirty();
        }
        return best;
    }

    /** The mirror of {@link #raise} for stats that count DOWN (the worst curse ever crafted). */
    public int lower(ServerPlayer player, String stat, int value) {
        CompoundTag tag = of(player.getUUID());
        int worst = Math.min(tag.getInt(stat), value);
        if (worst != tag.getInt(stat)) {
            tag.putInt(stat, worst);
            setDirty();
        }
        return worst;
    }

    /**
     * Record that this player broke a lucky block of kind {@code blockId}, and return how many DISTINCT
     * kinds they have now broken. The set of kinds is stored alongside its own size so the size can be
     * read (and matched by an advancement) without walking the list every time.
     */
    public int addBrokenKind(ServerPlayer player, String blockId) {
        return addKind(player, BROKEN_KINDS, BROKEN_TYPES, blockId);
    }

    /** Record a Lucky Tool this player has held; returns how many DISTINCT tools that makes. */
    public int addToolKind(ServerPlayer player, String itemId) {
        return addKind(player, TOOL_KINDS, TOOLS_FOUND, itemId);
    }

    /** Record a Lucky Tool this player has held two of; returns how many DISTINCT tools that makes. */
    public int addToolDupeKind(ServerPlayer player, String itemId) {
        return addKind(player, TOOL_DUPE_KINDS, TOOL_DUPES, itemId);
    }

    /** Record a water boss this player has met; returns how many DISTINCT bosses that makes. */
    public int addWaterBossKind(ServerPlayer player, String entityId) {
        return addKind(player, WATER_BOSS_KINDS, WATER_BOSSES, entityId);
    }

    /** Whether {@code value} is already in one of the sets above (e.g. "is this tool a duplicate?"). */
    public boolean hasToolKind(ServerPlayer player, String itemId) {
        return hasKind(player, TOOL_KINDS, itemId);
    }

    /**
     * Add {@code value} to a string set and keep {@code sizeStat} equal to the set's size. The size is
     * stored alongside the list so it can be read (and matched by an advancement) without walking the
     * list every time. Returns the size afterwards, whether or not anything was added.
     */
    private int addKind(ServerPlayer player, String listKey, String sizeStat, String value) {
        CompoundTag tag = of(player.getUUID());
        ListTag kinds = tag.getList(listKey, Tag.TAG_STRING);
        for (int i = 0; i < kinds.size(); i++) {
            if (kinds.getString(i).equals(value)) {
                return kinds.size(); // already known, nothing to write
            }
        }
        kinds.add(StringTag.valueOf(value));
        tag.put(listKey, kinds);
        tag.putInt(sizeStat, kinds.size());
        setDirty();
        return kinds.size();
    }

    private boolean hasKind(ServerPlayer player, String listKey, String value) {
        ListTag kinds = of(player.getUUID()).getList(listKey, Tag.TAG_STRING);
        for (int i = 0; i < kinds.size(); i++) {
            if (kinds.getString(i).equals(value)) {
                return true;
            }
        }
        return false;
    }
}
