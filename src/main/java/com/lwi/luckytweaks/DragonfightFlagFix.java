package com.lwi.luckytweaks;

import com.mojang.logging.LogUtils;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.Field;

/**
 * Fixes a Dragonfight-mod (4.7) bug that freezes the Ender Dragon at its low base health
 * (~500-650 HP instead of ~2000-2600) for a whole fight, mainly on dedicated servers.
 *
 * <p>The mod multiplies the dragon's health by ~4 the first time it takes damage, but only while
 * its static {@code DragonFightManagerCustom.isFightRunning} flag is {@code false}. That flag is
 * initialised to {@code true} at class load and only becomes {@code false} once the manager ticks
 * with the dragon at FULL health. So when a server (re)starts while the dragon is already damaged
 * (a restart mid-fight), the stale {@code true} blocks the boost for the rest of that fight — seen
 * on the community server 2026-07-07.
 *
 * <p>The fix: reset the flag to {@code false} at every server start — its only correct value at
 * that moment (nothing can be "running" yet; the mod re-derives the real state on its next tick,
 * and re-applies its own health boost if the fight is indeed underway). Done by reflection on the
 * PUBLIC static field, so this stays a soft dependency: a missing Dragonfight (or a renamed field
 * in a future version) is silently tolerated.
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID)
public final class DragonfightFlagFix {
    private static final Logger LOGGER = LogUtils.getLogger();

    private DragonfightFlagFix() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            Class<?> manager = Class.forName("com.dragonfight.fight.DragonFightManagerCustom");
            Field flag = manager.getField("isFightRunning");
            flag.setBoolean(null, false);
            LOGGER.info("Dragonfight's stale fight flag reset at server start (dragon health-boost fix).");
        } catch (ClassNotFoundException absent) {
            // Dragonfight not installed: nothing to fix.
        } catch (Throwable t) {
            LOGGER.warn("Could not reset Dragonfight's fight flag (mod updated?): {}", t.toString());
        }
    }
}
