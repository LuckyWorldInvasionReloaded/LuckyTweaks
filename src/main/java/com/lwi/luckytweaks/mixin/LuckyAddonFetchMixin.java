package com.lwi.luckytweaks.mixin;

import com.lwi.luckytweaks.AddonDownloads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.util.List;

/**
 * Hook of the addon auto-download (see {@link AddonDownloads}).
 *
 * <p>{@code LoaderKt.findAddonsOrMakeDir} is the Lucky Block mod's addon-folder scan; injecting at
 * its HEAD is the only placement guaranteed to run BEFORE addon discovery regardless of Forge's
 * parallel mod-construction order. A missing configured addon (Pink, not redistributable) is
 * downloaded right there, so the scan that follows finds it like any other addon.
 */
@Mixin(targets = "mod.lucky.java.loader.LoaderKt", remap = false)
public class LuckyAddonFetchMixin {

    @Inject(
            method = "findAddonsOrMakeDir(Ljava/io/File;)Ljava/util/List;",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private static void luckytweaks_fetchMissingAddons(File gameDir,
                                                       CallbackInfoReturnable<List<File>> cir) {
        try {
            AddonDownloads.ensureAddons();
        } catch (Throwable t) {
            // Never let the fetcher take the addon loader down -- worst case the addon is absent.
            com.lwi.luckytweaks.LuckyTweaksMod.LOGGER.error("[addonfetch] unexpected error", t);
        }
    }
}
