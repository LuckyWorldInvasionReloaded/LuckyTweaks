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
        CompoundTag tag = of(player.getUUID());
        ListTag kinds = tag.getList(BROKEN_KINDS, Tag.TAG_STRING);
        for (int i = 0; i < kinds.size(); i++) {
            if (kinds.getString(i).equals(blockId)) {
                return kinds.size(); // already known, nothing to write
            }
        }
        kinds.add(StringTag.valueOf(blockId));
        tag.put(BROKEN_KINDS, kinds);
        tag.putInt(BROKEN_TYPES, kinds.size());
        setDirty();
        return kinds.size();
    }
}
