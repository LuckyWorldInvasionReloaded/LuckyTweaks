package com.lwi.luckytweaks.locator;

import com.lwi.luckytweaks.LuckyTweaksMod;
import com.lwi.luckytweaks.TweaksConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Server side: every {@code updateTicks}, send each player the in-dimension, in-radius positions of the
 * other players as a relative direction + distance + colour (see {@link PlayerPositionsPacket}). When a
 * player's last trackable target disappears (the other player leaves, changes dimension, goes out of
 * range or hides), one empty packet is sent to clear that player's bar -- so a lone player (solo world,
 * or everyone else gone) shows nothing at all, and no packets are sent to players who never had any.
 * Players who are sneaking / invisible / spectating are filtered out per config.
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID)
public final class LocatorService {
    private static int tickCounter;
    /** UUIDs of players who currently have at least one marker, so we can clear a bar exactly once. */
    private static Set<UUID> hadMarkers = new HashSet<>();

    private LocatorService() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!TweaksConfig.LOCATOR_ENABLED.get()) return;
        if (++tickCounter % TweaksConfig.LOCATOR_UPDATE_TICKS.get() != 0) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        int max = TweaksConfig.LOCATOR_MAX_DISTANCE.get();
        boolean sendDistance = TweaksConfig.LOCATOR_SEND_DISTANCE.get();
        float precision = TweaksConfig.LOCATOR_DIRECTION_PRECISION.get();

        Set<UUID> withMarkers = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            List<ServerPlayer> players = level.players();
            for (ServerPlayer self : players) {
                List<PlayerPositionsPacket.Entry> entries = new ArrayList<>();
                for (ServerPlayer other : players) {
                    if (other == self || isHidden(other)) continue;

                    Vec3 delta = other.position().subtract(self.position());
                    double dist = delta.length();
                    if (max > 0 && dist > max) continue;

                    Vec3 dir = dist > 1.0e-4 ? delta.scale(1.0 / dist) : new Vec3(0, 0, 1);
                    float dx = Math.round(dir.x * precision) / precision;
                    float dy = Math.round(dir.y * precision) / precision;
                    float dz = Math.round(dir.z * precision) / precision;

                    float distance = 0f;
                    if (sendDistance) {
                        float d = (float) dist;
                        distance = d < 200f ? d : Math.round(d / 50f) * 50f;
                    }

                    UUID id = other.getUUID();
                    entries.add(new PlayerPositionsPacket.Entry(
                            id.getMostSignificantBits(), id.getLeastSignificantBits(),
                            dx, dy, dz, distance, colorFor(id)));
                }
                UUID selfId = self.getUUID();
                if (!entries.isEmpty()) {
                    LocatorNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> self),
                            new PlayerPositionsPacket(entries));
                    withMarkers.add(selfId);
                } else if (hadMarkers.contains(selfId)) {
                    // just lost the last trackable target -> clear this player's bar exactly once
                    LocatorNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> self),
                            new PlayerPositionsPacket(List.of()));
                }
            }
        }
        hadMarkers = withMarkers;
    }

    /** A player is kept off everyone's bar when sneaking / invisible / spectating. */
    private static boolean isHidden(ServerPlayer p) {
        if (p.isSpectator()) return true;
        if (TweaksConfig.LOCATOR_SNEAKING_HIDES.get() && p.isCrouching()) return true;
        if (TweaksConfig.LOCATOR_INVISIBILITY_HIDES.get() && p.hasEffect(MobEffects.INVISIBILITY)) return true;
        return false;
    }

    // --- per-player colour (random but stable from the UUID), HSL like the source mod -------------

    private static int colorFor(UUID uuid) {
        Random random = new Random(uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits());
        float h = random.nextFloat() * 360f;
        float s = random.nextFloat() / 4f + 0.75f;
        float l = random.nextFloat() / 2f + 0.5f;
        return hslToColor(h, s, l);
    }

    private static int hslToColor(float h, float s, float l) {
        float c = (1f - Math.abs(2f * l - 1f)) * s;
        float m = l - 0.5f * c;
        float x = c * (1f - Math.abs((h / 60f % 2f) - 1f));
        int seg = (int) (h / 60f);

        float r, g, b;
        switch (seg) {
            case 0 -> { r = c + m; g = x + m; b = m; }
            case 1 -> { r = x + m; g = c + m; b = m; }
            case 2 -> { r = m; g = c + m; b = x + m; }
            case 3 -> { r = m; g = x + m; b = c + m; }
            case 4 -> { r = x + m; g = m; b = c + m; }
            default -> { r = c + m; g = m; b = x + m; }
        }
        return (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    private static int clamp255(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }
}
