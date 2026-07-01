package com.lwi.luckytweaks.client;

import com.lwi.luckytweaks.mixin.MinecraftServerStorageAccessor;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
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

        // Leave the current world and stop the integrated server so the save is unlocked.
        if (mc.level != null) {
            mc.level.disconnect();
        }
        mc.clearLevel(new GenericDirtMessageScreen(Component.literal("Creating a new world...")));

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
}
