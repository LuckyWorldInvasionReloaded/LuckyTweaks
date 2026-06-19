package com.lwi.luckytweaks.mixin;

import com.google.common.collect.ImmutableList;
import net.minecraftforge.internal.BrandingControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Adds a custom line to the Forge main-menu branding (bottom-left), read verbatim from
 * {@code config/luckytweaks_branding.txt} when that file exists and is non-empty. Lets a modpack
 * show e.g. its name/version there without rebuilding a mod (just edit the file). No file -> no
 * line, so a standalone install is unaffected. Mirrors ModernFix's approach: capture the
 * ImmutableList.Builder local mid-{@code computeBranding} and add to it.
 */
@Mixin(value = BrandingControl.class, remap = false, priority = 1100)
public class BrandingMixin {
    @Inject(
            method = "computeBranding",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/ModList;get()Lnet/minecraftforge/fml/ModList;"),
            locals = LocalCapture.CAPTURE_FAILHARD,
            require = 0
    )
    private static void luckytweaks_addBrandingLine(CallbackInfo ci, ImmutableList.Builder<String> builder) {
        try {
            Path p = Paths.get("config", "luckytweaks_branding.txt");
            if (Files.exists(p)) {
                String line = Files.readString(p).trim();
                if (!line.isEmpty()) {
                    builder.add(line);
                }
            }
        } catch (Exception ignored) {
            // Best-effort cosmetic line; never let it break branding.
        }
    }
}
