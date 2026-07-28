package com.lwi.luckytweaks.mixin;

import com.lwi.luckytweaks.DropPatches;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.InputStream;

/**
 * The single hook of the drop-patch system (see {@link DropPatches}).
 *
 * <p>{@code getInputStream(baseDir, path)} is the funnel every addon config file goes through, and
 * unlike the downstream {@code parseDrops} it knows both which addon and which file is being read.
 * The stream is swapped only when a patch targets that exact pair; everything else, binaries
 * included, passes through untouched.
 */
@Mixin(targets = "mod.lucky.java.loader.LoaderKt", remap = false)
public class LuckyLoaderPatchMixin {

    @Inject(
            method = "getInputStream(Ljava/io/File;Ljava/lang/String;)Ljava/io/InputStream;",
            at = @At("RETURN"),
            remap = false,
            require = 0,
            cancellable = true
    )
    private static void luckytweaks_patchStream(File baseDir, String path,
                                                CallbackInfoReturnable<InputStream> cir) {
        try {
            InputStream patched = DropPatches.maybePatch(baseDir, path, cir.getReturnValue());
            if (patched != null) {
                cir.setReturnValue(patched);
            }
        } catch (Throwable t) {
            // Never let a patching problem take the whole addon loader down -- worst case the addon
            // loads exactly as shipped.
            com.lwi.luckytweaks.LuckyTweaksMod.LOGGER.error(
                    "[droppatch] unexpected error while patching {}/{}", baseDir, path, t);
        }
    }
}
