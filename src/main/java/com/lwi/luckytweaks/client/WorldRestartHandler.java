package com.lwi.luckytweaks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Adds a "Delete &amp; Restart" button to the hardcore death screen: it deletes the current world and spins up
 * a brand-new one (fresh random seed) without a detour through the main menu. The pack's Difficulty Lock and
 * TerraBlender re-apply the same locked hardcore difficulty and worldgen to the new world automatically, so
 * there is nothing to copy over.
 *
 * <p>The button stays greyed out for a short beat after death (like Old School Hardcore's own buttons and
 * vanilla's respawn/title buttons) so a leftover click from the fight that killed you can't instantly wipe +
 * restart the world before you have even seen the screen. There is no confirmation dialog -- once it lights up
 * it is a single click.
 */
@Mod.EventBusSubscriber(modid = "luckytweaks", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldRestartHandler {
    /** Ticks the button stays disabled after the death screen opens -- matches OSH's and vanilla's ~1s guard. */
    private static final int ENABLE_DELAY_TICKS = 20;

    private static Button restartButton;
    private static int ticksOpen;

    private WorldRestartHandler() {}

    @SubscribeEvent
    public static void onDeathScreenInit(ScreenEvent.Init.Post event) {
        restartButton = null;
        if (!(event.getScreen() instanceof DeathScreen deathScreen)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // Only single-player hardcore: deleting + recreating a world only makes sense for a local hardcore run.
        if (mc.getSingleplayerServer() == null || mc.level == null || !mc.level.getLevelData().isHardcore()) {
            return;
        }
        // With friends connected (LAN/e4mc), the host only reaches this hardcore death screen when the
        // WHOLE team is out of shared lives — the shared-lives game-over kills everyone at once, so no
        // one is still playing and a restart wipes nothing in progress. So the button is fine here as
        // long as the shared-lives rule is actually active (max > 0). If it's off, keep the old guard:
        // hide it in multiplayer so a stray click can't wipe a friend's still-running world.
        boolean sharedLivesActive = SharedLivesHud.max() > 0;
        if (mc.getSingleplayerServer().isPublished() && mc.getSingleplayerServer().getPlayerCount() > 1
                && !sharedLivesActive) {
            return;
        }
        int x = deathScreen.width / 2 - 100;
        int y = deathScreen.height / 4 + 120; // below OSH's spectate (+72) and delete-to-title (+96) buttons
        Button button = Button.builder(Component.literal("Delete & Restart"), b -> WorldRestart.deleteAndRestart(mc))
                .bounds(x, y, 200, 20)
                .build();
        button.active = false; // greyed out until the delay elapses, in step with OSH's buttons
        event.addListener(button);
        restartButton = button;
        ticksOpen = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // Runs while no death screen is open: the fresh world comes up long after the button is gone.
        WorldRestart.tickReshare(Minecraft.getInstance());
        if (restartButton == null) {
            return;
        }
        // Forget the button once the death screen is gone (respawn / spectate / title).
        if (!(Minecraft.getInstance().screen instanceof DeathScreen)) {
            restartButton = null;
            return;
        }
        if (!restartButton.active && ++ticksOpen >= ENABLE_DELAY_TICKS) {
            restartButton.active = true;
        }
    }
}
