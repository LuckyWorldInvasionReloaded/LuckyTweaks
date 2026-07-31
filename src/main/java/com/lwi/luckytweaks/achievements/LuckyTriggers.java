package com.lwi.luckytweaks.achievements;

import com.lwi.luckytweaks.LuckyTweaksMod;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.resources.ResourceLocation;

/**
 * Holder and registration point for this mod's advancement criteria.
 *
 * <p>Criteria are not a Forge registry in 1.20.1 — they live in vanilla's own static
 * {@link CriteriaTriggers} table, which is read the first time an advancement JSON is parsed. So
 * {@link #register()} must run during common setup (before any datapack load), and exactly once.
 */
public final class LuckyTriggers {
    /** The one criterion behind every Lucky Tweaks achievement. See {@link LuckyProgressTrigger}. */
    public static final LuckyProgressTrigger PROGRESS =
            new LuckyProgressTrigger(new ResourceLocation(LuckyTweaksMod.MODID, "lucky_progress"));

    private static boolean registered;

    private LuckyTriggers() {}

    /** Called from {@code FMLCommonSetupEvent}'s work queue (see {@link LuckyTweaksMod}). */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        CriteriaTriggers.register(PROGRESS);
    }
}
