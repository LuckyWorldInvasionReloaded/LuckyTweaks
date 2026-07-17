package com.lwi.luckytweaks.net;

import com.lwi.luckytweaks.LuckyTweaksMod;
import com.lwi.luckytweaks.SharedLives;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Server -&gt; client channel for the shared-lives HUD: the pool is server-authoritative
 * ({@link SharedLives} on the overworld's data storage), so the client is told the remaining/maximum
 * count to draw the hearts. Sent to a player on login and broadcast to everyone whenever the pool
 * changes. Registered from the mod constructor, next to the locator channel.
 */
public final class SharedLivesNet {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(LuckyTweaksMod.MODID, "shared_lives"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private SharedLivesNet() {}

    public static void init() {
        CHANNEL.registerMessage(0, SharedLivesHudPacket.class,
                SharedLivesHudPacket::encode, SharedLivesHudPacket::decode, SharedLivesHudPacket::handle);
    }

    /** Send the current pool to one player (on login, so their HUD starts correct). */
    public static void sendTo(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SharedLivesHudPacket(SharedLives.remaining(server), SharedLives.maxLives(server),
                        SharedLives.isMultiplayerRun(server)));
    }

    /** Push the current pool to everyone — the pool is shared, so a change concerns the whole team. */
    public static void broadcast(MinecraftServer server) {
        CHANNEL.send(PacketDistributor.ALL.noArg(),
                new SharedLivesHudPacket(SharedLives.remaining(server), SharedLives.maxLives(server),
                        SharedLives.isMultiplayerRun(server)));
    }
}
