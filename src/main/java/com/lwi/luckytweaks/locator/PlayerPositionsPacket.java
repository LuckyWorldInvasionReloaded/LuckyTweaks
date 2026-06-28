package com.lwi.luckytweaks.locator;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -&gt; client: the OTHER players the receiver may see on its locator bar. For each one we send
 * its UUID (for the player-list lookup that gives the name, head and a stable colour), a normalised
 * direction vector from the receiver to that player, the distance in blocks (0 when {@code sendDistance}
 * is off) and the marker colour. Sent every few ticks as a full snapshot; an empty list clears the bar.
 *
 * <p>The direction + distance model (rather than raw world X/Z) is what lets the client interpolate a
 * tracked player smoothly between updates and draw the up/down height arrow, mirroring Player Locator
 * Plus.
 */
public final class PlayerPositionsPacket {
    /** One tracked player: UUID halves, a normalised direction (x,y,z), distance (blocks) and 0xRRGGBB colour. */
    public record Entry(long uuidMsb, long uuidLsb,
                        float dirX, float dirY, float dirZ,
                        float distance, int color) {}

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
            buf.writeFloat(e.dirX());
            buf.writeFloat(e.dirY());
            buf.writeFloat(e.dirZ());
            buf.writeFloat(e.distance());
            buf.writeInt(e.color());
        }
    }

    public static PlayerPositionsPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readLong(), buf.readLong(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readInt()));
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
