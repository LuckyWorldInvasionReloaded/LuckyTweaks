package com.lwi.luckytweaks.locator;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> client: the OTHER players the receiver may see on its locator bar, each as their UUID
 * (for a stable colour) and world X/Z. Sent every few ticks; an empty list clears the bar.
 */
public final class PlayerPositionsPacket {
    /** One tracked player: UUID halves + horizontal world position. */
    public record Entry(long uuidMsb, long uuidLsb, float x, float z) {}

    private final List<Entry> entries;

    public PlayerPositionsPacket(List<Entry> entries) {
        this.entries = entries;
    }

    public List<Entry> entries() {
        return entries;
    }

    public static void encode(PlayerPositionsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entries.size());
        for (Entry e : msg.entries) {
            buf.writeLong(e.uuidMsb());
            buf.writeLong(e.uuidLsb());
            buf.writeFloat(e.x());
            buf.writeFloat(e.z());
        }
    }

    public static PlayerPositionsPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readLong(), buf.readLong(), buf.readFloat(), buf.readFloat()));
        }
        return new PlayerPositionsPacket(list);
    }

    public static void handle(PlayerPositionsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        // Hand off to the client overlay on the main thread. The client class is only referenced inside
        // the DIST-guarded supplier, so it is never classloaded on a dedicated server.
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lwi.luckytweaks.client.LocatorOverlay.accept(msg)));
        c.setPacketHandled(true);
    }
}
