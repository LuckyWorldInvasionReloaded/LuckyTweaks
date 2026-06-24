package com.lwi.luckytweaks.locator;

import com.lwi.luckytweaks.LuckyTweaksMod;
import com.lwi.luckytweaks.TweaksConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;

/**
 * Server side: every {@code updateTicks}, send each player the in-dimension, in-radius positions of the
 * other players. An empty packet is still sent so a client whose targets all left clears its bar.
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID)
public final class LocatorService {
    private static int tickCounter;

    private LocatorService() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!TweaksConfig.LOCATOR_ENABLED.get()) return;
        if (++tickCounter % TweaksConfig.LOCATOR_UPDATE_TICKS.get() != 0) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        double max = TweaksConfig.LOCATOR_MAX_DISTANCE.get();
        double maxSq = max * max;       // max == 0 -> unlimited (no distance filter)

        for (ServerLevel level : server.getAllLevels()) {
            List<ServerPlayer> players = level.players();
            if (players.size() < 2) continue;
            for (ServerPlayer self : players) {
                List<PlayerPositionsPacket.Entry> entries = new ArrayList<>();
                for (ServerPlayer other : players) {
                    if (other == self) continue;
                    double dx = other.getX() - self.getX();
                    double dz = other.getZ() - self.getZ();
                    if (max > 0 && dx * dx + dz * dz > maxSq) continue;
                    entries.add(new PlayerPositionsPacket.Entry(
                            other.getUUID().getMostSignificantBits(),
                            other.getUUID().getLeastSignificantBits(),
                            (float) other.getX(), (float) other.getZ()));
                }
                LocatorNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> self),
                        new PlayerPositionsPacket(entries));
            }
        }
    }
}
