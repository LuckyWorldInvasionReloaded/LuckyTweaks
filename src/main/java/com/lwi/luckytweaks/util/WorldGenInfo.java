package com.lwi.luckytweaks.util;

import mod.lucky.common.drop.WeightedDrop;
import mod.lucky.java.JavaLuckyRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the Lucky Block mod's world-gen registry to learn where each lucky block ACTUALLY spawns
 * (its natural dimensions and rate). The registry is keyed by block path ("amongus_lucky_block") and,
 * per block, by full dimension id ("minecraft:the_nether"). Every access is wrapped so the screen and
 * the world-gen mixin degrade gracefully if the Lucky Block mod is somehow absent.
 */
public final class WorldGenInfo {
    /** Fallback "1 in N" rate when a block's native rate for a dimension can't be read. */
    private static final int DEFAULT_RATE = 200;

    private WorldGenInfo() {}

    private static Map<?, ?> dimMapFor(String blockId) {
        try {
            Object all = JavaLuckyRegistry.INSTANCE.getWorldGenDrops();
            if (!(all instanceof Map)) {
                return null;
            }
            Map<?, ?> map = (Map<?, ?>) all;
            // The registry keys by FULL id ("lucky:amongus_lucky_block"); try that first, then a couple
            // of fallbacks, then a path-based scan, so we don't depend on the exact stored form.
            Object dims = map.get(blockId);
            if (dims instanceof Map) {
                return (Map<?, ?>) dims;
            }
            String path = pathOf(blockId);
            dims = map.get("lucky:" + path);
            if (dims instanceof Map) {
                return (Map<?, ?>) dims;
            }
            dims = map.get(path);
            if (dims instanceof Map) {
                return (Map<?, ?>) dims;
            }
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (pathOf(String.valueOf(e.getKey())).equals(path) && e.getValue() instanceof Map) {
                    return (Map<?, ?>) e.getValue();
                }
            }
        } catch (Throwable ignored) {
            // Lucky Block absent or its internals changed -- treat as "no world-gen info".
        }
        return null;
    }

    private static String pathOf(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    /** Full dimension ids this block (by path) natively generates in (dims with a non-empty drop list). */
    public static Set<String> nativeDims(String blockPath) {
        Set<String> out = new HashSet<>();
        Map<?, ?> dims = dimMapFor(blockPath);
        if (dims != null) {
            for (Map.Entry<?, ?> e : dims.entrySet()) {
                Object list = e.getValue();
                if (list instanceof List && !((List<?>) list).isEmpty()) {
                    out.add(String.valueOf(e.getKey()));
                }
            }
        }
        return out;
    }

    /**
     * The block's natural @chance ("1 in N") in this specific dimension, or {@link #DEFAULT_RATE} when
     * it can't be read (no entry for that dimension, an empty list, or a missing/sub-1.0 chance).
     */
    public static int nativeRate(String blockId, String dimId) {
        try {
            Map<?, ?> dims = dimMapFor(blockId);
            if (dims != null) {
                Object list = dims.get(dimId);
                if (list instanceof List && !((List<?>) list).isEmpty()) {
                    Object first = ((List<?>) list).get(0);
                    if (first instanceof WeightedDrop) {
                        Double chance = ((WeightedDrop) first).getChance();
                        if (chance != null && chance >= 1.0) {
                            return chance.intValue();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // Lucky Block absent or its internals changed -- fall through to the default.
        }
        return DEFAULT_RATE;
    }

    /** Resolve a block from a full ("ns:path") or bare ("path", assumed lucky namespace) id. */
    public static Block resolveBlock(String blockId) {
        ResourceLocation rl = blockId.indexOf(':') >= 0
                ? ResourceLocation.tryParse(blockId)
                : new ResourceLocation("lucky", blockId);
        if (rl == null) {
            return null;
        }
        Block block = ForgeRegistries.BLOCKS.getValue(rl);
        return (block == null || block == Blocks.AIR) ? null : block;
    }
}
