package com.lwi.luckytweaks.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link MinecraftServer}'s protected {@code storageSource} field so the world-restart feature can read
 * the real world folder id via {@code getLevelId()} -- the reliable source, instead of deriving it from a file
 * path (which once returned an empty string and made a delete target the whole saves folder).
 *
 * <p>Follows the Lucky Tweaks mixin convention: {@code remap = false} with the SRG field name. In production
 * the field is {@code f_129744_} (confirmed by Old School Hardcore's own {@code server.f_129744_.m_78277_()}).
 */
@Mixin(value = MinecraftServer.class, remap = false)
public interface MinecraftServerStorageAccessor {
    @Accessor(value = "f_129744_", remap = false)
    LevelStorageSource.LevelStorageAccess luckytweaks$getStorageSource();
}
