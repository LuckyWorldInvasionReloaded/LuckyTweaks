package com.lwi.luckytweaks;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * COMMON config (one global file, loaded at game start, applied to every world).
 *
 * <p>{@code luckCaps} - per-block positive-luck caps, one {@code "namespace:block_id=cap"} string
 * per entry. Parsed into {@link LuckCaps} on load/reload. Config entries take precedence over caps
 * registered by mods through the API, so packs always have the last word.
 */
public final class TweaksConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> LUCK_CAPS;

    /** Weapon-safety filter (see {@link com.lwi.luckytweaks.mixin.DropEvaluatorMixin}). */
    public static final ForgeConfigSpec.BooleanValue FIX_LUCKY_WEAPONS;
    public static final ForgeConfigSpec.DoubleValue LUCKY_WEAPON_NEG_AT_100;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> LUCKY_WEAPON_MARKERS;

    /** Natural world-gen spawn multiplier (see {@link com.lwi.luckytweaks.mixin.LuckyWorldGenMixin}). */
    public static final ForgeConfigSpec.DoubleValue LUCKY_BLOCK_SPAWN_MULTIPLIER;

    /** Master switch for the fusion recipe (see {@link LuckFusionRecipe}). */
    public static final ForgeConfigSpec.BooleanValue ENABLE_LUCK_FUSION;

    /** Announce legendary and cursed drops in chat (see {@link RareDropAnnouncer}). */
    public static final ForgeConfigSpec.BooleanValue ANNOUNCE_RARE_DROPS;

    /** Make Fuze Relics' crocodile drop swallowed items on death instead of deleting them
     *  (see {@link CrocodileSwallow}). */
    public static final ForgeConfigSpec.BooleanValue FIX_CROCODILE;

    /** How many Yakurum Sacred Hearts a player may ever eat, 0 = uncapped (see {@link SacredHeartCap}). */
    public static final ForgeConfigSpec.IntValue SACRED_HEART_MAX_USES;

    /** The run's shared pool of lives — the pack's difficulty dial (see {@link SharedLives}). */
    public static final ForgeConfigSpec.IntValue SHARED_LIVES_BASE;
    public static final ForgeConfigSpec.IntValue SHARED_LIVES_PER_PLAYER;
    public static final ForgeConfigSpec.IntValue BOUGHT_LIVES_CAP;

    /** Lucky blocks switched off by the pack (see {@link DisabledBlocks}). */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLED_LUCKY_BLOCKS;

    /**
     * Per-dimension spawn rules, "blockId@dimId=N": N=0 blocks the spawn, N&ge;1 makes the block
     * generate at "1 in N chunks" in that dimension (overriding the natural rate, or forcing it into a
     * dimension it doesn't natively spawn in). No entry = the block's natural behaviour. See
     * {@link DisabledBlocks}.
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SPAWN_RULES;

    /** Player-locator bar (multiplayer): other players' directions shown above the XP bar.
     *  Faithful reproduction of the "Player Locator Plus" mod (GPL-3.0, sit &lt;me@sit.sh&gt;,
     *  github.com/timas130/PlayerLocatorPlus). Server pushes each client the relative direction,
     *  distance and per-player colour of the others; the client draws sprite markers on the XP bar,
     *  with player heads + name plaques on the Tab (player-list) key. */
    public static final ForgeConfigSpec.BooleanValue LOCATOR_ENABLED;
    public static final ForgeConfigSpec.IntValue LOCATOR_MAX_DISTANCE;
    public static final ForgeConfigSpec.IntValue LOCATOR_UPDATE_TICKS;
    public static final ForgeConfigSpec.BooleanValue LOCATOR_SEND_DISTANCE;
    public static final ForgeConfigSpec.IntValue LOCATOR_DIRECTION_PRECISION;
    // hiding rules (server)
    public static final ForgeConfigSpec.BooleanValue LOCATOR_SNEAKING_HIDES;
    public static final ForgeConfigSpec.BooleanValue LOCATOR_INVISIBILITY_HIDES;
    // style (client)
    public static final ForgeConfigSpec.BooleanValue LOCATOR_VISIBLE_EMPTY;
    public static final ForgeConfigSpec.BooleanValue LOCATOR_FADE_MARKERS;
    public static final ForgeConfigSpec.IntValue LOCATOR_FADE_START;
    public static final ForgeConfigSpec.IntValue LOCATOR_FADE_END;
    public static final ForgeConfigSpec.DoubleValue LOCATOR_FADE_END_OPACITY;
    public static final ForgeConfigSpec.BooleanValue LOCATOR_SHOW_HEIGHT;
    public static final ForgeConfigSpec.BooleanValue LOCATOR_ALWAYS_SHOW_HEADS;
    public static final ForgeConfigSpec.BooleanValue LOCATOR_SHOW_HEADS_ON_TAB;
    public static final ForgeConfigSpec.BooleanValue LOCATOR_SHOW_NAMES_ON_TAB;

    private static final List<String> DEFAULT_HARMFUL_MARKERS = List.of(
            "ID=tnt", "lightning_bolt", "ID=lava", "flowing_lava", "type=block,ID=fire", "cobweb", "spawn_egg");

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("luck");
        LUCK_CAPS = builder
                .comment(
                        "Per-block positive-luck caps, as \"namespace:block_id=cap\" entries.",
                        "The cap bounds BOTH the result of luck crafting (the tooltip shows the truth)",
                        "and the effective luck when the block is broken. Negative luck is never capped.",
                        "Example: [\"lucky:lucky_block=60\", \"lucky:toolluckyblock=50\"]")
                .defineListAllowEmpty(List.of("luckCaps"), List::of,
                        o -> o instanceof String s && s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+\\s*=\\s*-?\\d+"));
        builder.pop();

        builder.push("luckyWeapons");
        FIX_LUCKY_WEAPONS = builder
                .comment(
                        "Make Lucky Swords/Bows/Potions safer the higher their Luck.",
                        "These weapons fire a lucky-block effect on hit; some effects hurt the wielder.",
                        "When ON, harmful effects are dropped from the roll with a chance that rises with the",
                        "weapon's Luck (see maxRemovalAt100Luck). Good/offensive effects stay, and lucky",
                        "BLOCKS are never touched (their chaos is the point). Default ON.")
                .define("fixLuckyWeapons", true);
        LUCKY_WEAPON_NEG_AT_100 = builder
                .comment(
                        "Target chance of a NEGATIVE effect per hit at +100 weapon Luck. Self-calibrating per",
                        "weapon: the mod measures each pool's own danger and removes just enough to hit this",
                        "number, so it holds whatever a weapon's mix of harmful effects is. Below +100 the",
                        "chance scales smoothly up toward the weapon's natural danger. Keep it above 0 so a",
                        "sliver of risk always remains - it's luck, not a guarantee. 0.01 = about 1% bad hits.")
                .defineInRange("negativeChanceAt100Luck", 0.01, 0.0, 1.0);
        LUCKY_WEAPON_MARKERS = builder
                .comment(
                        "Substrings that mark a weapon drop as 'harmful to the wielder'. A drop is a removal",
                        "candidate if its definition contains any of these. Edit to taste.")
                .defineListAllowEmpty(List.of("harmfulMarkers"), () -> DEFAULT_HARMFUL_MARKERS,
                        o -> o instanceof String);
        builder.pop();

        builder.push("worldgen");
        LUCKY_BLOCK_SPAWN_MULTIPLIER = builder
                .comment(
                        "Multiplier for how often lucky blocks appear in NEWLY generated terrain.",
                        "1.0 = vanilla. Only affects natural world generation -- already-generated chunks",
                        "are never changed. Handy on multiplayer servers where players spread out and want",
                        "denser spawns. Each natural spawn roll is made this many times as likely (and is",
                        "capped at one block per roll), so the increase is approximate, not exact. Max 3.0.")
                .defineInRange("luckyBlockSpawnMultiplier", 1.0, 1.0, 3.0);
        builder.pop();

        builder.push("announcements");
        ANNOUNCE_RARE_DROPS = builder
                .comment(
                        "Announce in chat when a player rolls a legendary (gold) or a cursed (red) drop,",
                        "naming the block it came from. Only with two or more players connected: alone,",
                        "the message would just repeat what you already saw.",
                        "A legendary is announced at once, with the drumroll, so everyone waits out the",
                        "2.2s reveal together. A curse is held back 5s instead, in step with its counter,",
                        "so chat does not out it before the player feels it. Default ON.")
                .define("announceRareDrops", true);
        builder.pop();

        builder.push("fusion");
        ENABLE_LUCK_FUSION = builder
                .comment(
                        "Allow combining two or more lucky blocks of the SAME type in a crafting grid into",
                        "one block whose Luck is the sum of the inputs (bounded by that block's cap). Turn",
                        "this OFF to remove the fusion recipe entirely. Default ON.")
                .define("enableLuckFusion", true);
        builder.pop();

        builder.push("luckyBlocks");
        DISABLED_LUCKY_BLOCKS = builder
                .comment(
                        "Lucky blocks to DISABLE, as full registry IDs (\"namespace:block_id\").",
                        "A disabled block no longer generates in new terrain, and breaking an existing",
                        "one fires no effect -- it simply drops back as an item, keeping its Luck. Covers",
                        "the base block, every addon (the \"lucky\" namespace) and cross-mod lucky blocks",
                        "like fuze_relics:lucky_blockling. Easiest edited in-game via the Lucky Tweaks",
                        "config screen, which lists every lucky block it finds with a checkbox.",
                        "Example: [\"lucky:amongus_lucky_block\", \"fuze_relics:lucky_blockling\"]")
                .defineListAllowEmpty(List.of("disabledLuckyBlocks"), List::of,
                        o -> o instanceof String s && s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"));
        SPAWN_RULES = builder
                .comment(
                        "Per-dimension spawn rules, as \"namespace:block_id@namespace:dimension_id=N\" entries.",
                        "N=0 BLOCKS the spawn in that one dimension (the block still works when broken).",
                        "N>=1 makes the block GENERATE at \"1 in N chunks\" in that dimension -- overriding its",
                        "natural rate there, or forcing it into a dimension it doesn't natively spawn in.",
                        "No entry for a block+dimension = that block's natural behaviour. A block with all of",
                        "its dimensions set to 0 is better listed in disabledLuckyBlocks instead. Easiest",
                        "edited in-game via the Lucky Tweaks config screen.",
                        "Example: [\"lucky:lucky_block@minecraft:the_nether=0\", \"lucky:amongus_lucky_block@minecraft:overworld=120\"]")
                .defineListAllowEmpty(List.of("spawnRules"), List::of,
                        o -> o instanceof String s && s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+@[a-z0-9_.-]+:[a-z0-9_./-]+=\\d+"));
        builder.pop();

        builder.push("crocodile");
        FIX_CROCODILE = builder
                .comment(
                        "Make Fuze Relics' crocodile give items back instead of destroying them.",
                        "The crocodile normally eats (deletes) one item from your main hand on 25% of the",
                        "hits you land on it -- with its 40 HP that usually means losing your weapon before",
                        "it dies. When ON, the eaten item is stashed on the crocodile and dropped when it is",
                        "killed, so you just have to kill it to get your gear back. Does nothing when Fuze",
                        "Relics isn't installed. Default ON.")
                .define("fixCrocodile", true);
        builder.pop();

        builder.push("sacredHeart");
        SACRED_HEART_MAX_USES = builder
                .comment(
                        "How many Yakurum Sacred Hearts a player may ever eat. Each one permanently adds",
                        "2 max-health points (one heart) to the attribute's base value, and Yakurum caps",
                        "nothing -- so the vending machines would let a player farm an unbounded health bar.",
                        "This counts USES OF THE ITEM, not hearts: every other source of extra health works",
                        "through attribute modifiers (Artifacts' Crystal Heart, Health Boost, ...) and is",
                        "left completely alone. Eating one past the cap still grants its Regeneration IV,",
                        "it just no longer grows the bar. The counter resets on death whenever Yakurum's own",
                        "hearts do (i.e. when keepInventory is off). Set to 0 to remove the cap. Default 10.")
                .defineInRange("maxUses", 10, 0, 1000);
        builder.pop();

        // The [playerRevive] section is gone on purpose (1.3): co-op revive is no longer optional. The
        // shared-lives pool hands co-op one life per player BECAUSE a downed team-mate can be picked back
        // up, so a switch that removed the revive while keeping the extra lives no longer composed with
        // anything. It also stopped being a real choice: the toggle's default flipped false -> true, and
        // Forge never rewrites a value a config file already holds, so every world carried over from 1.2.x
        // would silently have kept revive OFF through the release that is built around it. Anyone who truly
        // wants deaths to go straight through still has PlayerRevive's own config, or can drop the mod.
        builder.push("lives");
        SHARED_LIVES_BASE = builder
                .comment(
                        "Lives the run starts with, solo or co-op alike. The pool is SHARED by the team and never",
                        "refills: every death spends one, and the death that empties it ends the run for good.",
                        "At 1 (default) a solo run ends on the very first death -- the pack as it is meant to be",
                        "played. Raise it to make a run more forgiving; this is the difficulty dial, rather than a",
                        "switch that would turn hardcore off and take the pack's whole point with it.",
                        "In co-op the team ALSO gets sharedLivesPerPlayer below for each player, on top of this.")
                .defineInRange("sharedLivesBase", 1, 1, 100);
        SHARED_LIVES_PER_PLAYER = builder
                .comment(
                        "Lives each player adds to a CO-OP run, on top of the base above. Alone this never applies:",
                        "a run only counts as co-op once two players have actually been online together -- opening",
                        "to LAN alone, just to get commands, stays a solo run on the base alone.",
                        "One by default, so a duo runs on 3 and a trio on 4: every player brings their own life,",
                        "and the base is the team's spare.",
                        "0 makes team size irrelevant -- a co-op run then has exactly the base, so base 1 with 0",
                        "here is a whole team on a SINGLE shared life.",
                        "The team size used here is the BIGGEST the team has ever been, not who is online right",
                        "now: otherwise a friend logging off would take a life away, and a team that had already",
                        "spent them would be stranded at zero through no fault of anyone.",
                        "Going down spends a life even if you are revived; giving up costs your items, not a life.")
                .defineInRange("sharedLivesPerPlayer", 1, 0, 100);
        BOUGHT_LIVES_CAP = builder
                .comment(
                        "How many extra lives a run may ever BUY, on top of the allowance above. Other mods can",
                        "sell one through the API (the Lucky Labs merchant does), and this is the ceiling on it.",
                        "One by default: an extra life is meant to be a moment, not an income. Left uncapped, any",
                        "way of farming the currency turns straight into infinite lives and hardcore stops meaning",
                        "anything. Counts lives BOUGHT over the whole run, not lives currently held, so spending",
                        "the bought one does not free the slot. 0 stops them being sold at all.")
                .defineInRange("boughtLivesCap", 1, 0, 100);
        builder.pop();

        builder.push("locator");
        LOCATOR_ENABLED = builder
                .comment(
                        "Show a strip of markers above the XP bar pointing at the other players (multiplayer).",
                        "A marker sits over where that player is on your screen (within your field of view);",
                        "it fades with distance and is coloured per player. Hold the player-list key (Tab) to",
                        "raise name plaques and player-head icons above the bar. Server-controlled: it pushes",
                        "each client the others' relative direction, distance and colour.")
                .define("enabled", true);
        LOCATOR_MAX_DISTANCE = builder
                .comment("Maximum distance (blocks) at which a player shows on the locator bar.",
                        "0 = unlimited: every player in the same dimension is tracked.")
                .defineInRange("maxDistance", 0, 0, 8192);
        LOCATOR_UPDATE_TICKS = builder
                .comment("How often the server pushes player positions, in ticks (20 = once per second).",
                        "Nearby, loaded players are interpolated smoothly client-side regardless of this;",
                        "it mainly governs how often far-away players' projected positions refresh.")
                .defineInRange("updateTicks", 5, 1, 100);
        LOCATOR_SEND_DISTANCE = builder
                .comment("Send each player's exact distance along with the direction. Greatly smooths the",
                        "markers (the client can project a player's position between updates) and enables",
                        "distance fading. Turn OFF to make triangulating another player's coordinates harder.")
                .define("sendDistance", true);
        LOCATOR_DIRECTION_PRECISION = builder
                .comment("How precisely a far player's direction is sent (the direction vector is rounded to",
                        "this many steps). Lower = coarser = harder for others to triangulate your position.")
                .defineInRange("directionPrecision", 300, 2, 100000);
        LOCATOR_SNEAKING_HIDES = builder
                .comment("A sneaking (crouching) player is hidden from everyone's locator bar.")
                .define("sneakingHides", true);
        LOCATOR_INVISIBILITY_HIDES = builder
                .comment("A player under the Invisibility effect is hidden from the locator bar.")
                .define("invisibilityHides", true);
        LOCATOR_VISIBLE_EMPTY = builder
                .comment("Show the empty bar background even when no other players are tracked.")
                .define("visibleEmpty", false);
        LOCATOR_FADE_MARKERS = builder
                .comment("Fade a marker's opacity as the player gets farther away (needs sendDistance).")
                .define("fadeMarkers", true);
        LOCATOR_FADE_START = builder
                .comment("Distance (blocks) at which markers start to fade.")
                .defineInRange("fadeStart", 100, 0, 8192);
        LOCATOR_FADE_END = builder
                .comment("Distance (blocks) at which markers reach their faintest (fadeEndOpacity).")
                .defineInRange("fadeEnd", 1000, 1, 8192);
        LOCATOR_FADE_END_OPACITY = builder
                .comment("Faintest marker opacity, 0 (invisible) to 1 (opaque), reached at fadeEnd.")
                .defineInRange("fadeEndOpacity", 0.3, 0.0, 1.0);
        LOCATOR_SHOW_HEIGHT = builder
                .comment("Draw a small up/down arrow by a marker when that player is well above or below you.")
                .define("showHeight", true);
        LOCATOR_ALWAYS_SHOW_HEADS = builder
                .comment("Always draw player-head icons on the markers instead of plain coloured pins.")
                .define("alwaysShowHeads", false);
        LOCATOR_SHOW_HEADS_ON_TAB = builder
                .comment("Draw player-head icons on the markers while the player-list key (Tab) is held.")
                .define("showHeadsOnTab", true);
        LOCATOR_SHOW_NAMES_ON_TAB = builder
                .comment("Raise name plaques above the bar (lifting the HUD) while the player-list key is held.")
                .define("showNamesOnTab", true);
        builder.pop();

        COMMON_SPEC = builder.build();
    }

    private TweaksConfig() {}
}
