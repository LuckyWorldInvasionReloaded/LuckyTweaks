package com.lwi.luckytweaks.util;

import com.lwi.luckytweaks.LuckyTweaksMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal server-tick task queue: run a {@link Runnable} after N server ticks. Used by the CURSED counter
 * to delay the "Cursed drops" HUD bump by 5 s (anti-spoiler -- the counter must not move in sync with the
 * bad drop). Tasks are drained at the END of every server tick, on the server thread.
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID)
public final class ServerScheduler {
    private record Task(long dueTick, Runnable run) {}

    private static final List<Task> TASKS = new ArrayList<>();
    private static long tick = 0L;

    private ServerScheduler() {}

    /** Run {@code task} after {@code delayTicks} server ticks (clamped to {@code >= 0}). Call on the server thread. */
    public static void schedule(int delayTicks, Runnable task) {
        if (task == null) {
            return;
        }
        synchronized (ServerScheduler.class) {
            TASKS.add(new Task(tick + Math.max(0, delayTicks), task));
        }
    }

    @SubscribeEvent
    public static void onServerTickEnd(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        List<Runnable> due = null;
        synchronized (ServerScheduler.class) {
            tick++;
            if (TASKS.isEmpty()) {
                return;
            }
            for (int i = TASKS.size() - 1; i >= 0; i--) {
                if (TASKS.get(i).dueTick() <= tick) {
                    if (due == null) {
                        due = new ArrayList<>();
                    }
                    due.add(TASKS.remove(i).run());
                }
            }
        }
        if (due != null) {
            for (Runnable r : due) {
                try {
                    r.run();
                } catch (Throwable ignored) {
                    // a scheduled task must never break the server tick loop
                }
            }
        }
    }
}
