package com.lwi.luckytweaks.locator;

import com.lwi.luckytweaks.LuckyTweaksMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Player-locator networking: a tiny server -> client channel that ships the horizontal positions of
 * the players a given client may track on its locator bar. Registered from the mod constructor.
 */
public final class LocatorNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(LuckyTweaksMod.MODID, "locator"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private LocatorNetwork() {}

    public static void init() {
        CHANNEL.registerMessage(0, PlayerPositionsPacket.class,
                PlayerPositionsPacket::encode, PlayerPositionsPacket::decode, PlayerPositionsPacket::handle);
    }
}
