package com.lwi.luckytweaks.compat;

import io.github.gaming32.worldhost.plugin.InfoTextsCategory;
import io.github.gaming32.worldhost.plugin.WorldHostPlugin;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.List;

/**
 * Puts your own username on World Host's Friends screen.
 *
 * <p>Adding someone asks for <i>their</i> username, and the screen never tells you yours -- so the one
 * thing you have to give a friend ("add me, I'm X") is the one thing it doesn't show. This adds it, with
 * a line saying what to do with it. The exchange only ever happens once: once two players have friended
 * each other it survives deaths, world re-creations and restarts.
 *
 * <p>Registered by World Host itself, which scans mod files for {@link WorldHostPlugin.Entrypoint}. That
 * makes this a true soft dependency: without World Host nothing ever loads this class, so no guard is
 * needed anywhere else.
 */
@WorldHostPlugin.Entrypoint
public final class WorldHostFriendsInfo implements WorldHostPlugin {
    @Override
    public List<Component> getInfoTexts(InfoTextsCategory category) {
        // World Host declares side=BOTH, so this plugin is also built on a dedicated server, where the
        // screens (and Minecraft.getInstance()) do not exist. The name is only ever asked for by a screen.
        if (category != InfoTextsCategory.FRIENDS_SCREEN || FMLEnvironment.dist != Dist.CLIENT) {
            return List.of();
        }
        return clientInfoTexts();
    }

    private static List<Component> clientInfoTexts() {
        String name = Minecraft.getInstance().getUser().getName();
        return List.of(
                Component.translatable("luckytweaks.worldhost.your_username",
                                Component.literal(name).withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("luckytweaks.worldhost.your_username.hint")
                        .withStyle(ChatFormatting.DARK_GRAY)
        );
    }
}
