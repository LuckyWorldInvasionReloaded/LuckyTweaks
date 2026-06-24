package com.lwi.luckytweaks.client;

import com.lwi.luckytweaks.TweaksConfig;
import com.lwi.luckytweaks.locator.PlayerPositionsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * Client side: a compass-like strip just above the XP bar. Each tracked player is a small coloured
 * marker whose horizontal position is the player's direction relative to where you are looking
 * (centre = dead ahead, edges = directly behind), fading out with distance. Colour derives from UUID.
 */
public final class LocatorOverlay implements IGuiOverlay {
    public static final LocatorOverlay INSTANCE = new LocatorOverlay();

    private static final int BAR_WIDTH = 182;   // vanilla XP bar width
    private static final float FOV = 120f;      // horizontal field of view spread across the bar
    /** When Lucky XP is loaded it owns the XP bar (moves + lifts it) and calls renderOnBar itself. */
    private static final boolean LUCKYXP_PRESENT = ModList.get().isLoaded("luckyxp");

    /** Replaced wholesale by the network thread (immutable snapshot), read by the render thread. */
    private static volatile List<PlayerPositionsPacket.Entry> entries = List.of();

    private LocatorOverlay() {}

    /** Called on the client main thread when a positions packet arrives. */
    public static void accept(PlayerPositionsPacket msg) {
        entries = msg.entries();
    }

    // --- singleplayer dev test: a ring of fake players, toggled by /lwlocatortest ---
    private static boolean testMode = false;
    private static List<PlayerPositionsPacket.Entry> testEntries = List.of();

    /** Toggle a ring of fake players so the bar can be checked without a second player. Positions are
     *  fixed in the world at toggle time, so turning/walking exercises bearing AND distance fade. */
    public static void toggleTest() {
        Minecraft mc = Minecraft.getInstance();
        testMode = !testMode;
        if (testMode && mc.player != null) {
            double px = mc.player.getX();
            double pz = mc.player.getZ();
            int[] dists = {30, 80, 200, 500};
            List<PlayerPositionsPacket.Entry> list = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                double ang = Math.toRadians(i * 360.0 / 8.0);
                int d = dists[i % dists.length];
                list.add(new PlayerPositionsPacket.Entry(
                        i * 0x9E3779B97F4A7C15L, i * 0x6C8E9CF570932BD5L,
                        (float) (px + Math.sin(ang) * d), (float) (pz + Math.cos(ang) * d)));
            }
            testEntries = list;
        } else {
            testEntries = List.of();
        }
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(
                    "[Locator] test markers " + (testMode ? "ON (8 fake players)" : "OFF")), true);
        }
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        // Lucky XP re-implements the XP bar (HUD lift + configurable Y) and calls renderOnBar itself at
        // the exact spot. Only draw here when it's absent -- then the bar is the plain vanilla one.
        if (!LUCKYXP_PRESENT) renderOnBar(g, screenWidth, screenHeight - 32 + 3);
    }

    /** Draw the markers centred on an XP bar whose TOP edge is at {@code barTopY}. Public so Lucky XP can
     *  call it from inside its own bar overlay -- where the HUD lift is already cancelled and the bar's
     *  real Y is known -- keeping the markers glued to the bar wherever that mod places it. */
    public static void renderOnBar(GuiGraphics g, int screenWidth, int barTopY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        List<PlayerPositionsPacket.Entry> list = testMode ? testEntries : entries;
        if (list.isEmpty()) return;

        double fade = TweaksConfig.LOCATOR_FADE_DISTANCE.get();
        float selfX = (float) mc.player.getX();
        float selfZ = (float) mc.player.getZ();
        float yaw = mc.player.getYRot();

        int barLeft = screenWidth / 2 - BAR_WIDTH / 2;
        int barCenterY = barTopY + 2;       // 5px-tall bar -> vertical centre

        for (PlayerPositionsPacket.Entry e : list) {
            float dx = e.x() - selfX;
            float dz = e.z() - selfZ;
            double dist = Math.sqrt(dx * dx + dz * dz);

            // direction to the target as a Minecraft yaw, then relative to where the player looks
            float targetYaw = (float) (-Mth.atan2(dx, dz) * (180.0 / Math.PI));
            float rel = Mth.wrapDegrees(targetYaw - yaw);                  // -180..180, 0 = dead ahead
            // spread over the field of view (like the source mod); players outside it clamp to the edge
            float progress = Mth.clamp((rel + FOV / 2f) / FOV, 0f, 1f);
            int cx = barLeft + Math.round(progress * BAR_WIDTH);

            float t = (float) Math.min(1.0, dist / Math.max(1.0, fade));
            int rgb = colorFor(e.uuidMsb() ^ e.uuidLsb()) & 0xFFFFFF;
            int alpha = (int) (255 * (1f - 0.6f * t));                     // near opaque -> far ~40%
            int r = Math.max(1, Math.round(3 - 1.5f * t));                 // radius shrinks with distance: 3 -> 1

            drawMarker(g, cx, barCenterY, r, rgb, alpha);
        }
    }

    /** A small diamond centred ON the XP bar (covering it, like the source mod's 7x7 marker), with a
     *  1px dark outline so a bright marker stays readable over the green bar. Radius shrinks with distance. */
    private static void drawMarker(GuiGraphics g, int cx, int centerY, int r, int rgb, int alpha) {
        int body = (alpha << 24) | rgb;
        int outline = alpha << 24;          // black, same fade
        for (int dy = -(r + 1); dy <= r + 1; dy++) {        // outline (one pixel larger all round)
            int w = (r + 1) - Math.abs(dy);
            if (w >= 0) g.fill(cx - w, centerY + dy, cx + w + 1, centerY + dy + 1, outline);
        }
        for (int dy = -r; dy <= r; dy++) {                  // coloured body
            int w = r - Math.abs(dy);
            g.fill(cx - w, centerY + dy, cx + w + 1, centerY + dy + 1, body);
        }
    }

    private static int colorFor(long seed) {
        float hue = (((seed % 360L) + 360L) % 360L) / 360f;
        return Mth.hsvToRgb(hue, 0.85f, 1.0f);
    }
}
