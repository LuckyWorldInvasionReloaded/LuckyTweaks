package com.lwi.luckytweaks;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Set of lucky blocks the pack has switched OFF (the {@code disabledLuckyBlocks} COMMON config).
 *
 * <p>A disabled block no longer generates in new terrain (the world-gen mixin skips it) and, when an
 * already-placed one is broken, fires no effect -- it just drops back as an item ({@link BreakEvents}).
 * Entries are full registry IDs ({@code "namespace:block_id"}); the in-game config screen writes them
 * for you, but they can also be edited straight in the config file (handy on a server).
 *
 * <p>Cached as a {@code volatile Set} re-parsed on every config load/reload, because the world-gen
 * check runs on the (hot) chunk-generation path.
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DisabledBlocks {
    private static volatile Set<String> disabled = Set.of();
    private static volatile java.util.Map<String, Integer> rules = Map.of();

    private DisabledBlocks() {}

    /** Exact match on a fully-qualified block id (the form {@link BreakEvents} has on hand). */
    public static boolean isDisabled(ResourceLocation blockId) {
        return blockId != null && !disabled.isEmpty() && disabled.contains(blockId.toString());
    }

    /**
     * Match for the world-gen path, where the Lucky Block mod hands us a block id whose exact form
     * (full {@code "lucky:foo"} vs bare {@code "foo"}) we don't want to depend on. Compares both the
     * whole string and the path portion, so any of those forms resolves the same disabled block.
     */
    public static boolean isDisabledWorldGen(String blockId) {
        if (blockId == null || disabled.isEmpty()) {
            return false;
        }
        if (disabled.contains(blockId) || disabled.contains("lucky:" + blockId)) {
            return true;
        }
        String path = pathOf(blockId);
        for (String entry : disabled) {
            if (pathOf(entry).equals(path)) {
                return true;
            }
        }
        return false;
    }

    /** A snapshot of the disabled ids, for the config screen to pre-check its boxes. */
    public static Set<String> view() {
        return disabled;
    }

    /**
     * The spawn rule for this block in this dimension, or {@code null} if none is set (use the block's
     * natural behaviour). {@code 0} blocks the spawn; {@code N>=1} means "1 in N chunks". {@code dimKey}
     * is the full dimension id (e.g. {@code "minecraft:the_nether"}); {@code blockId} may be full or
     * path-only (the world-gen caller's exact form is not relied upon) -- rules are keyed by the block's
     * path joined to the full dimension id, like {@link #isDisabledWorldGen}.
     */
    public static Integer spawnRule(String blockId, String dimKey) {
        if (blockId == null || dimKey == null || rules.isEmpty()) {
            return null;
        }
        return rules.get(pathOf(blockId) + "@" + dimKey);
    }

    private static String pathOf(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    /** Re-read the config lists. Called on load/reload, and directly after the screen saves changes. */
    public static void refresh() {
        Set<String> parsed = new HashSet<>();
        for (String entry : TweaksConfig.DISABLED_LUCKY_BLOCKS.get()) {
            if (entry != null && !entry.isBlank()) {
                parsed.add(entry.trim());
            }
        }
        disabled = parsed.isEmpty() ? Set.of() : Set.copyOf(parsed);

        Map<String, Integer> parsedRules = new HashMap<>();
        for (String entry : TweaksConfig.SPAWN_RULES.get()) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String e = entry.trim();
            int at = e.indexOf('@');
            int eq = e.indexOf('=', at + 1);
            if (at < 0 || eq < 0) {
                continue; // malformed -- skip
            }
            String block = e.substring(0, at);
            String dim = e.substring(at + 1, eq);
            try {
                parsedRules.put(pathOf(block) + "@" + dim, Integer.parseInt(e.substring(eq + 1).trim()));
            } catch (NumberFormatException ignored) {
                // malformed N -- skip
            }
        }
        rules = parsedRules.isEmpty() ? Map.of() : Map.copyOf(parsedRules);

        if (!disabled.isEmpty() || !rules.isEmpty()) {
            LuckyTweaksMod.LOGGER.info("[disable] {} off, {} spawn rules", disabled.size(), rules.size());
        }
    }

    @SubscribeEvent
    public static void onConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() == TweaksConfig.COMMON_SPEC) {
            refresh();
        }
    }
}
