package com.lwi.luckytweaks;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of per-block positive-luck caps. Two sources, config wins:
 * <ul>
 *   <li>the {@code luckCaps} COMMON config (pack-controlled, reloadable), and</li>
 *   <li>{@link com.lwi.luckytweaks.api.LuckyTweaksApi#registerLuckCap} (mod-controlled, e.g. a mod
 *       capping its own block's infusion).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LuckCaps {
    private static final Map<ResourceLocation, Integer> FROM_API = new ConcurrentHashMap<>();
    private static volatile Map<ResourceLocation, Integer> fromConfig = Map.of();

    private LuckCaps() {}

    /** Cap for this block id, or null if none registered. Config entries override API entries. */
    @Nullable
    public static Integer capFor(ResourceLocation blockId) {
        Integer cap = fromConfig.get(blockId);
        return cap != null ? cap : FROM_API.get(blockId);
    }

    public static void register(ResourceLocation blockId, int cap) {
        // Caps are a POSITIVE-luck bound by contract ("negative luck is never capped"): a negative
        // value would silently turn a lucky block into a cursed one across the whole pipeline
        // (roll, fusion, craft preview). Sanitize to 0 and complain instead. (Audit 2026-06-12.)
        if (cap < 0) {
            LuckyTweaksMod.LOGGER.warn("[caps] negative luck cap {} for {} is invalid -- forcing 0", cap, blockId);
            cap = 0;
        }
        FROM_API.put(blockId, cap);
        LuckyTweaksMod.LOGGER.info("[caps] registered luck cap {} -> {} (API)", blockId, cap);
    }

    /** Re-parse the config list on load AND reload, so packs can tune caps without restarting. */
    @SubscribeEvent
    public static void onConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() != TweaksConfig.COMMON_SPEC) {
            return;
        }
        Map<ResourceLocation, Integer> parsed = new ConcurrentHashMap<>();
        for (String entry : TweaksConfig.LUCK_CAPS.get()) {
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                continue; // validated by the config spec, defensive only
            }
            ResourceLocation id = ResourceLocation.tryParse(entry.substring(0, eq).trim());
            if (id == null) {
                LuckyTweaksMod.LOGGER.warn("[caps] ignoring invalid block id in luckCaps entry: {}", entry);
                continue;
            }
            try {
                int value = Integer.parseInt(entry.substring(eq + 1).trim());
                if (value < 0) {
                    LuckyTweaksMod.LOGGER.warn("[caps] negative luck cap {} for {} is invalid -- forcing 0", value, id);
                    value = 0;
                }
                parsed.put(id, value);
            } catch (NumberFormatException e) {
                LuckyTweaksMod.LOGGER.warn("[caps] ignoring invalid cap value in luckCaps entry: {}", entry);
            }
        }
        fromConfig = parsed;
        if (!parsed.isEmpty()) {
            LuckyTweaksMod.LOGGER.info("[caps] loaded {} luck cap(s) from config", parsed.size());
        }
    }
}
