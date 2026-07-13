package com.lwi.luckytweaks.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -&gt; client: how many shared lives remain, out of the current allowance. */
public final class SharedLivesHudPacket {
    private final int remaining;
    private final int max;

    public SharedLivesHudPacket(int remaining, int max) {
        this.remaining = remaining;
        this.max = max;
    }

    public static void encode(SharedLivesHudPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.remaining);
        buf.writeVarInt(msg.max);
    }

    public static SharedLivesHudPacket decode(FriendlyByteBuf buf) {
        return new SharedLivesHudPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(SharedLivesHudPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        // The client class is only referenced inside the DIST-guarded supplier, never classloaded on a server.
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lwi.luckytweaks.client.SharedLivesHud.accept(msg.remaining, msg.max)));
        c.setPacketHandled(true);
    }
}
