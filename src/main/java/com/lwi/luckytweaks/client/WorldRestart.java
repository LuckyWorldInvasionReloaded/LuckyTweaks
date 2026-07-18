package com.lwi.luckytweaks.client;

import com.lwi.luckytweaks.mixin.MinecraftServerStorageAccessor;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.function.Function;

/**
 * Deletes the current single-player world and immediately creates + loads a fresh one, mirroring the vanilla
 * "Create New World" flow ({@link net.minecraft.client.gui.screens.worldselection.WorldOpenFlows#createFreshLevel})
 * but headless, so the player never touches the main menu.
 *
 * <p>The new world reuses the current world's {@link LevelSettings} (name, difficulty, hardcore flag,
 * gamerules, datapack config) with a fresh random seed. In this pack, Difficulty Lock re-forces the locked
 * hardcore/hard difficulty and TerraBlender re-applies the biomes, so "same lock settings as now" is automatic.
 */
public final class WorldRestart {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** How long to wait for the fresh world to come up before giving up on re-sharing it. */
    private static final int RESHARE_TIMEOUT_TICKS = 20 * 60;

    // How the wiped run was shared, replayed onto the fresh world by tickReshare(). -1 = nothing pending.
    private static GameType reshareGameType;
    private static boolean reshareCheats;
    private static int resharePort;
    private static String reshareLevelId;
    private static int reshareTicks = -1;

    private WorldRestart() {}

    public static void deleteAndRestart(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }

        // World folder id straight from the storage access (Old School Hardcore's proven source) -- never
        // derived from a file path, which once returned "" and made the delete target the whole saves folder.
        String levelId = ((MinecraftServerStorageAccessor) server).luckytweaks$getStorageSource().getLevelId();

        // HARD SAFETY: only ever a single, specific world subfolder. A blank id or any separator/traversal could
        // resolve to the saves ROOT, so deleteLevel would wipe EVERY world. Refuse outright if it looks off.
        if (levelId == null || levelId.isBlank()
                || levelId.indexOf('/') >= 0 || levelId.indexOf('\\') >= 0 || levelId.contains("..")) {
            LOGGER.error("World restart aborted: refusing to delete with unsafe level id '{}'", levelId);
            return;
        }

        LevelSettings settings = server.getWorldData().getLevelSettings().copy();
        LOGGER.info("World restart: deleting world '{}' and creating a fresh one with a new seed", levelId);

        // A shared run stays shared. A team wipe kills everyone at once, so the friends who just died with
        // you are sitting at their own death screen waiting to come back -- re-publish the fresh world the
        // same way the old one was, instead of making the host walk back through the menu while they wait.
        if (server.isPublished()) {
            reshareGameType = server.getDefaultGameType();
            // isAllowCheatsForAllPlayers() is what publishServer() actually set, and the only honest
            // source: getWorldData().getAllowCommands() is the world-CREATION checkbox, which this pack
            // force-disables (Difficulty Lock) -- reading it would re-share the world with cheats off and
            // strip the host of the very commands they opened to LAN for.
            reshareCheats = server.getPlayerList().isAllowCheatsForAllPlayers();
            resharePort = server.getPort();
            reshareLevelId = levelId;
            reshareTicks = 0;
        } else {
            reshareTicks = -1;
        }

        // Leave the current world and stop the integrated server so the save is unlocked.
        if (mc.level != null) {
            mc.level.disconnect();
        }
        mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("luckytweaks.gui.creating_world")));

        // Delete the old save (same call Old School Hardcore uses).
        try (LevelStorageSource.LevelStorageAccess access = mc.getLevelSource().createAccess(levelId)) {
            access.deleteLevel();
        } catch (IOException e) {
            LOGGER.error("Failed to delete world '{}' before restart", levelId, e);
        }

        // Create + load a fresh world at the same folder with a NEW random seed.
        Function<RegistryAccess, WorldDimensions> dimensions = WorldPresets::createNormalWorldDimensions;
        mc.createWorldOpenFlows().createFreshLevel(levelId, settings, WorldOptions.defaultWithRandomSeed(), dimensions);
    }

    /**
     * Ticked on the client: re-publish the fresh world once it is actually up, if the run we wiped was
     * shared. Driven by a tick rather than a world-load event because the new world comes up asynchronously,
     * and publishing before the server is ready silently does nothing.
     *
     * <p>The old port is reused so a plain LAN address on the local network stays valid; if it is no longer
     * free we take any. This only spares the host the trip back through the menu: e4mc mints a fresh domain
     * for every tunnel, so they still have to pass the new address on to whoever is coming back.
     */
    static void tickReshare(Minecraft mc) {
        if (reshareTicks < 0) {
            return;
        }
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.level == null || !server.isReady()) {
            if (++reshareTicks > RESHARE_TIMEOUT_TICKS) {
                reshareTicks = -1;
                LOGGER.warn("World restart: gave up waiting for the fresh world before re-sharing it");
            }
            return;
        }
        // Only ever re-share the world we just recreated. Quitting to the title mid-load and opening some
        // OTHER world within the timeout would otherwise hand it to LAN behind the player's back.
        String loadedId = ((MinecraftServerStorageAccessor) server).luckytweaks$getStorageSource().getLevelId();
        if (!reshareLevelId.equals(loadedId)) {
            reshareTicks = -1;
            LOGGER.info("World restart: '{}' loaded instead of '{}', not re-sharing it", loadedId, reshareLevelId);
            return;
        }
        reshareTicks = -1;
        if (server.isPublished()) {
            return;
        }
        if (!server.publishServer(reshareGameType, reshareCheats, resharePort)
                && !server.publishServer(reshareGameType, reshareCheats, HttpUtil.getAvailablePort())) {
            LOGGER.warn("World restart: could not re-share the fresh world; the host has to open it manually");
            return;
        }
        LOGGER.info("World restart: fresh world re-shared on port {}", server.getPort());
    }
}
