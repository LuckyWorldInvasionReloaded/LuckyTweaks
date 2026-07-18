package com.lwi.luckytweaks.client;

import com.lwi.luckytweaks.LuckyTweaksMod;
import com.lwi.luckytweaks.TweaksClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The shared-lives HUD: a row of hearts showing the team's remaining lives out of the run's allowance
 * (full coloured heart = a life left, empty container = a life spent). Server-authoritative count (via
 * {@link com.lwi.luckytweaks.net.SharedLivesNet}); colour and screen position are personal CLIENT config
 * ({@link TweaksClientConfig}), edited from the Lucky Tweaks config screen. Default: silver hearts,
 * bottom-right just above Improved Mobs' difficulty text.
 */
public final class SharedLivesHud implements IGuiOverlay {
    public static final SharedLivesHud INSTANCE = new SharedLivesHud();

    /** 81x9 sheet: column 0 = empty container, columns 1.. = the colours in {@link LivesHeartStyles#ORDER}. */
    public static final ResourceLocation SHEET =
            new ResourceLocation(LuckyTweaksMod.MODID, "textures/gui/lives_hearts.png");
    public static final int HEART = 9, SHEET_W = 81, SHEET_H = 9;
    private static final int STEP = 8;      // hearts overlap by 1px, like vanilla

    private static volatile int remaining = -1;
    private static volatile int max = 0;
    private static volatile boolean multiplayer = false;
    private static volatile int peakPlayers = 1;

    private SharedLivesHud() {}

    /** Called on the client thread from the HUD packet. */
    public static void accept(int rem, int mx, boolean mp, int peak) {
        remaining = rem;
        max = mx;
        multiplayer = mp;
        peakPlayers = peak;
    }

    /** Whether the server counts this run as a multiplayer one (two players have been online together). */
    public static boolean isMultiplayerRun() {
        return multiplayer;
    }

    /** Biggest the team has ever been at once: the co-op allowance is one life per head, plus a spare. */
    public static int peakPlayers() {
        return Math.max(1, peakPlayers);
    }

    /**
     * Forget the last run's pool when the world goes away. These are statics that outlive a world, and the
     * config screen's Lives preview reads them from the title screen, where no packet has arrived yet: a
     * co-op run left behind would draw its 3 hearts over someone's next solo run.
     */
    @Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Reset {
        private Reset() {}

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            remaining = -1;
            max = 0;
            multiplayer = false;
            peakPlayers = 1;
        }
    }

    /** Team allowance last synced from the server: >0 means the shared-lives rule is active (synced on
     *  login, so reliable at any time — unlike {@link #remaining}, which lands with the death itself). */
    public static int max() {
        return max;
    }

    /** Team lives left: -1 = unsynced, 0 = out (game over), &gt;0 = lives remain. */
    public static int remaining() {
        return remaining;
    }

    /** Draw {@code count} of {@code total} hearts of {@code style} at (x,y). Shared with the config preview. */
    public static void drawRow(GuiGraphics g, int x, int y, int total, int count, String style) {
        int fullU = LivesHeartStyles.column(style) * HEART;
        RenderSystem.enableBlend();
        for (int i = 0; i < total; i++) {
            int hx = x + i * STEP;
            g.blit(SHEET, hx, y, 0.0F, 0.0F, HEART, HEART, SHEET_W, SHEET_H);
            if (i < count) {
                g.blit(SHEET, hx, y, (float) fullU, 0.0F, HEART, HEART, SHEET_W, SHEET_H);
            }
        }
        RenderSystem.disableBlend();
    }

    public static int rowWidth(int total) {
        return (total - 1) * STEP + HEART;
    }

    /** Draw the row anchored to a corner with a pixel offset — shared by the live HUD and the config
     *  screen preview so both land in exactly the same spot. */
    public static void drawPositioned(GuiGraphics g, HudCorner corner, int offX, int offY,
                                      int screenW, int screenH, int total, int count, String style) {
        int ax = corner.right ? screenW : 0;
        int ay = corner.bottom ? screenH : 0;
        int x0 = corner.right ? ax + offX - rowWidth(total) : ax + offX;
        int y0 = corner.bottom ? ay + offY - HEART : ay + offY;
        drawRow(g, x0, y0, total, count, style);
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenW, int screenH) {
        if (max <= 0 || remaining < 0) {
            return;                                 // not synced yet / feature off
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) {
            return;
        }
        drawPositioned(g,
                HudCorner.byName(TweaksClientConfig.CLIENT.livesHudCorner.get()),
                TweaksClientConfig.CLIENT.livesHudX.get(),
                TweaksClientConfig.CLIENT.livesHudY.get(),
                screenW, screenH, max, remaining, TweaksClientConfig.CLIENT.livesHeartStyle.get());
    }
}
