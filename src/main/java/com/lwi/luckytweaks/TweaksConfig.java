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

    /** Make Fuze Relics' crocodile drop swallowed items on death instead of deleting them
     *  (see {@link CrocodileSwallow}). */
    public static final ForgeConfigSpec.BooleanValue FIX_CROCODILE;

    /** Master switch for the optional PlayerRevive co-op revive (see
     *  {@link com.lwi.luckytweaks.mixin.ReviveDisableMixin}). */
    public static final ForgeConfigSpec.BooleanValue ENABLE_PLAYER_REVIVE;

    /** Lucky blocks switched off by the pack (see {@link DisabledBlocks}). */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLED_LUCKY_BLOCKS;

    /**
     * Per-dimension spawn rules, "blockId@dimId=N": N=0 blocks the spawn, N&ge;1 makes the block
     * generate at "1 in N chunks" in that dimension (overriding the natural rate, or forcing it into a
     * dimension it doesn't natively spawn in). No entry = the block's natural behaviour. See
     * {@link DisabledBlocks}.
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SPAWN_RULES;

    /** Player-locator bar (multiplayer): other players' directions shown above the XP bar. */
    public static final ForgeConfigSpec.BooleanValue LOCATOR_ENABLED;
    public static final ForgeConfigSpec.IntValue LOCATOR_MAX_DISTANCE;
    public static final ForgeConfigSpec.IntValue LOCATOR_FADE_DISTANCE;
    public static final ForgeConfigSpec.IntValue LOCATOR_UPDATE_TICKS;

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

        builder.push("playerRevive");
        ENABLE_PLAYER_REVIVE = builder
                .comment(
                        "Let the optional PlayerRevive mod work: a player who would die instead drops into a",
                        "'bleeding' state for a teammate to revive, rather than dying outright. Meant for",
                        "multiplayer co-op. When OFF (default), players die normally even with PlayerRevive",
                        "installed -- Lucky Tweaks forces the death through (only when PlayerRevive actually",
                        "downed the player, so Totems of Undying are left alone), keeping the pack hardcore by",
                        "default. Does nothing when PlayerRevive isn't installed. Default OFF.")
                .define("enablePlayerRevive", false);
        builder.pop();

        builder.push("locator");
        LOCATOR_ENABLED = builder
                .comment(
                        "Show a compass-like strip above the XP bar with the direction of nearby players",
                        "(multiplayer). Centre = ahead, edges = behind; markers fade with distance and are",
                        "coloured per player. Server-controlled (it pushes positions to clients). Default ON.")
                .define("enabled", true);
        LOCATOR_MAX_DISTANCE = builder
                .comment("Maximum horizontal distance (blocks) at which a player shows on the locator bar.",
                        "0 = unlimited: every player in the same dimension is tracked.")
                .defineInRange("maxDistance", 0, 0, 8192);
        LOCATOR_FADE_DISTANCE = builder
                .comment("Distance (blocks) over which a marker fades to its faintest. Independent of",
                        "maxDistance, so fading still works when maxDistance is unlimited (0).")
                .defineInRange("fadeDistance", 256, 16, 8192);
        LOCATOR_UPDATE_TICKS = builder
                .comment("How often the server pushes player positions, in ticks (20 = once per second).")
                .defineInRange("updateTicks", 8, 1, 100);
        builder.pop();

        COMMON_SPEC = builder.build();
    }

    private TweaksConfig() {}
}
