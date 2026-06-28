package com.lwi.luckytweaks.client;

import com.lwi.luckytweaks.TweaksConfig;
import com.lwi.luckytweaks.locator.PlayerPositionsPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client side: a strip of markers above the XP bar, one per other player, faithfully reproducing the
 * "Player Locator Plus" mod. A marker sits over where the player is on your screen (their bearing
 * mapped across your horizontal field of view); players outside your view get no marker. Markers fade
 * with distance, carry a small up/down arrow when the player is well above/below you, and -- while the
 * player-list key (Tab) is held -- turn into player heads with name plaques that lift the rest of the
 * HUD out of the way.
 *
 * <p>Reproduction of Player Locator Plus by sit (GPL-3.0, github.com/timas130/PlayerLocatorPlus);
 * the five HUD sprites are taken from that mod.
 */
public final class LocatorOverlay implements IGuiOverlay {
    public static final LocatorOverlay INSTANCE = new LocatorOverlay();

    private static final String NS = "luckytweaks";
    private static final ResourceLocation EMPTY_BAR = tex("empty_bar");
    private static final ResourceLocation MARK = tex("player_mark");
    private static final ResourceLocation MARK_UP = tex("player_mark_up");
    private static final ResourceLocation MARK_DOWN = tex("player_mark_down");
    private static final ResourceLocation MARK_WHITE_OUTLINE = tex("player_mark_white_outline");

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(NS, "textures/gui/sprites/hud/" + name + ".png");
    }

    private static final int BAR_WIDTH = 182;

    private static final int NAME_PLAQUE_PADDING_X = 4;
    private static final int NAME_PLAQUE_PADDING_Y = 2;
    private static final int NAME_PLAQUE_MARGIN = 2;
    private static final int NAME_PLAQUE_OVERLAP_THRESHOLD = 2;

    private static final float HUD_OFFSET_TOTAL = 16f;
    private static final Animatable hudOffset = new Animatable(0f);
    private static long lastFrameNanos = 0L;

    /** When Lucky XP is loaded it owns the XP bar (moves + lifts it) and calls {@link #renderLocator} itself. */
    private static final boolean LUCKYXP_PRESENT = ModList.get().isLoaded("luckyxp");

    /** Replaced wholesale by the network thread (immutable snapshot), read by the render thread. */
    private static volatile List<PlayerPositionsPacket.Entry> entries = List.of();
    /** Our position the moment the last packet arrived, to project tracked players between updates. */
    private static volatile Vec3 lastUpdatePosition = Vec3.ZERO;

    private LocatorOverlay() {}

    /** Called on the client main thread when a positions packet arrives. */
    public static void accept(PlayerPositionsPacket msg) {
        entries = msg.entries();
        Minecraft mc = Minecraft.getInstance();
        lastUpdatePosition = mc.player != null ? mc.player.position() : Vec3.ZERO;
    }

    /** Clear all tracked state -- called when leaving a world so markers from a previous (multiplayer)
     *  session never carry over into the next one (e.g. a solo world, which would otherwise show them). */
    public static void reset() {
        entries = List.of();
        lastUpdatePosition = Vec3.ZERO;
        testMode = false;
        testEntries = List.of();
    }

    /** Lift applied to the rest of the HUD while the name plaques are showing. Read by {@link LocatorHudOffset}. */
    public static float currentHudOffset() {
        return hudOffset.currentValue;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        // Lucky XP re-implements the XP bar (HUD lift + configurable Y) and calls renderLocator itself at
        // the exact spot. Only draw here when it's absent -- then the bar is the plain vanilla one.
        if (!LUCKYXP_PRESENT) renderLocator(g, screenWidth, screenHeight, partialTick);
    }

    private record NamePlaque(int x, String name, double progress) {}

    /** Vanilla path: draw the locator on the vanilla XP bar (top edge at {@code screenHeight - 32 + 3}),
     *  with the name plaques anchored to that same bar. */
    public static void renderLocator(GuiGraphics g, int screenWidth, int screenHeight, float partialTick) {
        int barTopY = screenHeight - 32 + 3;
        renderLocatorOnBar(g, screenWidth, screenHeight, barTopY, barTopY, partialTick);
    }

    /** Draw the markers on a bar whose top edge sits at {@code barTopY}, with the name plaques raised
     *  above {@code plaqueAnchorY}. Public so Lucky XP can drive it from inside its own bar overlay --
     *  passing its green (vanilla) bar Y for the markers and its blue bar Y as the plaque anchor, so the
     *  plaques clear the second bar. When there's a single bar, pass the same Y for both. */
    public static void renderLocatorOnBar(GuiGraphics g, int screenWidth, int screenHeight,
                                          int barTopY, int plaqueAnchorY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        // The dev preview (/lwlocatortest) always renders so the bar can be checked even with the
        // feature disabled server-side; real tracking still respects the enabled switch.
        if (!testMode && !TweaksConfig.LOCATOR_ENABLED.get()) return;
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        List<PlayerPositionsPacket.Entry> list = testMode ? testEntries : entries;
        boolean visibleEmpty = TweaksConfig.LOCATOR_VISIBLE_EMPTY.get();
        boolean show = !list.isEmpty() || visibleEmpty;     // hide the bar entirely when there's nothing to show

        int x = screenWidth / 2 - 91;
        int y = barTopY;

        boolean tabPressed = mc.options.keyPlayerList.isDown();
        boolean showNamesOnTab = TweaksConfig.LOCATOR_SHOW_NAMES_ON_TAB.get();
        List<NamePlaque> plaques = new ArrayList<>();

        if (show) {
            boolean showHeadsOnTab = TweaksConfig.LOCATOR_SHOW_HEADS_ON_TAB.get();
            boolean alwaysShowHeads = TweaksConfig.LOCATOR_ALWAYS_SHOW_HEADS.get();
            boolean showHeight = TweaksConfig.LOCATOR_SHOW_HEIGHT.get();
            boolean fadeMarkers = TweaksConfig.LOCATOR_FADE_MARKERS.get();
            float fadeStart = TweaksConfig.LOCATOR_FADE_START.get();
            float fadeEnd = TweaksConfig.LOCATOR_FADE_END.get();
            float fadeEndOpacity = TweaksConfig.LOCATOR_FADE_END_OPACITY.get().floatValue();

            double horizontalFov = horizontalFov(mc.options.fov().get(), screenWidth, screenHeight);
            Vec3 selfPos = lerpPos(mc.player, partialTick);
            float yaw = mc.player.getYRot();

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            // Background bar when the vanilla XP bar isn't being drawn (e.g. creative: no XP bar), so
            // the markers always have something to sit on -- like the source mod.
            boolean barRendered = mc.gameMode != null && mc.gameMode.hasExperience();
            if (!barRendered) {
                blit(g, EMPTY_BAR, x, y, BAR_WIDTH, 5, 182, 5);
            }

            int idx = -1;
            for (PlayerPositionsPacket.Entry e : list) {
                idx++;
                UUID uuid = new UUID(e.uuidMsb(), e.uuidLsb());

                Player marker = mc.level.getPlayerByUUID(uuid);
                Vec3 dir;
                if (marker != null) {
                    dir = lerpPos(marker, partialTick).subtract(selfPos);
                } else if (e.distance() == 0f) {
                    dir = new Vec3(e.dirX(), e.dirY(), e.dirZ());
                } else {
                    Vec3 projected = lastUpdatePosition.add(new Vec3(e.dirX(), e.dirY(), e.dirZ()).scale(e.distance()));
                    dir = projected.subtract(selfPos);
                }

                double dx = dir.x;
                double dz = dir.z;
                if (dx == 0 && dz == 0) continue;

                double targetYaw = -Mth.atan2(dx, dz) * (180.0 / Math.PI);
                double rel = Mth.wrapDegrees(targetYaw - yaw);            // -180..180, 0 = dead ahead
                double progress = (rel + horizontalFov / 2) / horizontalFov;
                if (progress < 0.0 || progress > 1.0) continue;          // outside your view -> no marker

                int markX = x + (int) Math.round(progress * BAR_WIDTH) - 4;

                PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(uuid) : null;
                boolean showHead = info != null && (alwaysShowHeads || (showHeadsOnTab && tabPressed));

                int opacity = 255;
                if (fadeMarkers && fadeEnd > fadeStart) {
                    float d = Mth.clamp(e.distance(), fadeStart, fadeEnd);
                    float fadeProgress = 1f - (d - fadeStart) / (fadeEnd - fadeStart);
                    opacity = Math.round(((1f - fadeEndOpacity) * fadeProgress + fadeEndOpacity) * 255f);
                }
                int rgb = e.color() & 0xFFFFFF;

                if (showNamesOnTab && (info != null || testMode)) {
                    String name = info != null ? info.getProfile().getName() : "Player" + (idx + 1);
                    plaques.add(new NamePlaque(markX, name, progress));
                }

                if (!showHead) {
                    setColor(g, rgb, opacity);
                    blit(g, MARK, markX, y - 1, 7, 7, 7, 7);
                } else {
                    setColor(g, rgb, opacity);
                    blit(g, MARK_WHITE_OUTLINE, markX, y - 1, 7, 7, 7, 7);
                    g.setColor(1f, 1f, 1f, 1f);
                    PlayerFaceRenderer.draw(g, info.getSkinLocation(), markX + 1, y, 5);
                }
                g.setColor(1f, 1f, 1f, 1f);

                if (showHeight) {
                    double heightN = dir.normalize().y;
                    if (heightN > 0.5) {
                        blit(g, MARK_UP, markX + 1, y - 5, 5, 4, 5, 4);
                    } else if (heightN < -0.5) {
                        blit(g, MARK_DOWN, markX + 1, y + 7, 5, 4, 5, 4);
                    }
                }
            }
            g.setColor(1f, 1f, 1f, 1f);
        }

        // Always ease the HUD lift (target 0 when nothing is shown) so it can never stick lifted if the
        // tracked players vanish while Tab is held.
        hudOffset.targetValue = (show && tabPressed && showNamesOnTab && !plaques.isEmpty()) ? HUD_OFFSET_TOTAL : 0f;
        hudOffset.update(frameDeltaMs());

        float plaqueFade = Math.round(hudOffset.currentValue / HUD_OFFSET_TOTAL * 255f) / 255f;
        if (show && !plaques.isEmpty() && plaqueFade > 0f) {
            renderNamePlaques(g, mc.font, plaques, plaqueAnchorY, plaqueFade);
        }
    }

    private static void renderNamePlaques(GuiGraphics g, Font font, List<NamePlaque> markers, int barY, float fade) {
        // closest to centre wins when two plaques would overlap
        List<NamePlaque> sorted = new ArrayList<>(markers);
        sorted.sort((a, b) -> Double.compare(Math.abs(a.progress - 0.5), Math.abs(b.progress - 0.5)));

        List<int[]> takenRanges = new ArrayList<>();    // {first, last}
        List<NamePlaque> visible = new ArrayList<>();
        for (NamePlaque m : sorted) {
            int plaqueWidth = font.width(m.name) + NAME_PLAQUE_PADDING_X * 2;
            int plaqueX = m.x - plaqueWidth / 2 + 4;
            int first = plaqueX, last = plaqueX + plaqueWidth;
            boolean overlap = false;
            for (int[] r : takenRanges) {
                if (r[0] - NAME_PLAQUE_OVERLAP_THRESHOLD <= last && r[1] + NAME_PLAQUE_OVERLAP_THRESHOLD >= first) {
                    overlap = true;
                    break;
                }
            }
            if (!overlap) {
                takenRanges.add(new int[]{first, last});
                visible.add(m);
            }
        }

        int bgAlpha = Math.round(192 * fade);
        int textAlpha = Math.round(255 * fade);
        for (NamePlaque m : visible) {
            int plaqueWidth = font.width(m.name) + NAME_PLAQUE_PADDING_X * 2;
            int plaqueHeight = font.lineHeight + NAME_PLAQUE_PADDING_Y * 2;
            int plaqueX = m.x - plaqueWidth / 2 + 4;
            int plaqueY = barY - plaqueHeight - NAME_PLAQUE_MARGIN;

            if (bgAlpha > 0) {
                g.fill(plaqueX, plaqueY, plaqueX + plaqueWidth, plaqueY + plaqueHeight, bgAlpha << 24);
            }
            // drawString drops the alpha channel when it's under 4
            if (textAlpha > 3) {
                g.drawString(font, m.name, plaqueX + NAME_PLAQUE_PADDING_X, plaqueY + NAME_PLAQUE_PADDING_Y,
                        (textAlpha << 24) | 0xFFFFFF, false);
            }
        }
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static Vec3 lerpPos(Player p, float partial) {
        double px = Mth.lerp(partial, p.xo, p.getX());
        double py = Mth.lerp(partial, p.yo, p.getY());
        double pz = Mth.lerp(partial, p.zo, p.getZ());
        return new Vec3(px, py, pz);
    }

    /** Horizontal FOV (degrees) from the vertical FOV setting and the window aspect ratio. */
    private static double horizontalFov(int verticalFov, int width, int height) {
        double fovRad = verticalFov / 2.0 * Math.PI / 180.0;
        double d = height / 2.0 / Math.tan(fovRad);
        return Math.atan(width / 2.0 / d) * 2.0 / Math.PI * 180.0;
    }

    private static void setColor(GuiGraphics g, int rgb, int alpha) {
        g.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, alpha / 255f);
    }

    /** Blit a whole standalone PNG (texW x texH) stretched into a {@code w x h} rect at (x,y). */
    private static void blit(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int texW, int texH) {
        g.blit(tex, x, y, w, h, 0f, 0f, texW, texH, texW, texH);
    }

    private static float frameDeltaMs() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return 16f;
        }
        float ms = (now - lastFrameNanos) / 1_000_000f;
        lastFrameNanos = now;
        return Mth.clamp(ms, 0f, 100f);
    }

    // --- singleplayer dev test: a ring of fake players, toggled by /lwlocatortest -----------------

    private static boolean testMode = false;
    private static List<PlayerPositionsPacket.Entry> testEntries = List.of();

    /** Toggle a fixed ring of fake players around where you stand, so the bar can be checked solo:
     *  walking/turning then exercises bearing, distance fade and the up/down height arrows. */
    public static void toggleTest() {
        Minecraft mc = Minecraft.getInstance();
        testMode = !testMode;
        if (testMode && mc.player != null) {
            lastUpdatePosition = mc.player.position();
            int[] dists = {30, 80, 200, 600};
            List<PlayerPositionsPacket.Entry> list = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                double ang = Math.toRadians(i * 45.0);
                double elev = (i % 3 - 1) * 0.6;                 // -0.6 / 0 / +0.6 -> down / flat / up arrows
                double horiz = Math.sqrt(Math.max(0.0, 1.0 - elev * elev));
                float dirX = (float) (Math.sin(ang) * horiz);
                float dirY = (float) elev;
                float dirZ = (float) (Math.cos(ang) * horiz);
                int rgb = Mth.hsvToRgb(i / 8f, 0.85f, 1.0f) & 0xFFFFFF;
                list.add(new PlayerPositionsPacket.Entry(
                        i * 0x9E3779B97F4A7C15L, i * 0x6C8E9CF570932BD5L,
                        dirX, dirY, dirZ, dists[i % dists.length], rgb));
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
}
