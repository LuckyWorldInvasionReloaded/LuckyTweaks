package com.lwi.luckytweaks.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -&gt; client: how many shared lives remain, out of the current allowance, and whether this run
 * counts as a multiplayer one (so the config screen can preview the right allowance).
 */
public final class SharedLivesHudPacket {
    private final int remaining;
    private final int max;
    private final boolean multiplayer;

    public SharedLivesHudPacket(int remaining, int max, boolean multiplayer) {
        this.remaining = remaining;
        this.max = max;
        this.multiplayer = multiplayer;
    }

    public static void encode(SharedLivesHudPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.remaining);
        buf.writeVarInt(msg.max);
        buf.writeBoolean(msg.multiplayer);
    }

    public static SharedLivesHudPacket decode(FriendlyByteBuf buf) {
        return new SharedLivesHudPacket(buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(SharedLivesHudPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        // The client class is only referenced inside the DIST-guarded supplier, never classloaded on a server.
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lwi.luckytweaks.client.SharedLivesHud.accept(msg.remaining, msg.max, msg.multiplayer)));
        c.setPacketHandled(true);
    }
}
