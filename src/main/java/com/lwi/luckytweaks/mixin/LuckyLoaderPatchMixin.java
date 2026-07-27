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
 * <p>{@code LoaderKt.getInputStream(baseDir, path)} is the funnel every lucky block config file goes
 * through when the Lucky Block mod loads an addon -- {@code drops.txt}, {@code natural_gen.txt},
 * {@code properties.txt}, structures... -- and, unlike the downstream {@code parseDrops(List)}, it
 * knows BOTH which addon ({@code baseDir}, folder or zip) and which file ({@code path}) is being
 * read. Injecting at RETURN lets {@link DropPatches#maybePatch} swap the stream for a patched
 * in-memory copy when, and only when, a patch targets that exact (addon, file) pair; everything
 * else -- other files, other addons, binary structures -- keeps the loader's original stream
 * untouched.
 *
 * <p>Runs during the Lucky Block mod's construction, long before world load; the patch engine
 * therefore reads its own files straight from disk instead of Forge config.
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
