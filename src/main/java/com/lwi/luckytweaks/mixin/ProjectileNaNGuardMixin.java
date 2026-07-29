package com.lwi.luckytweaks.mixin;

import com.lwi.luckytweaks.LuckyTweaksMod;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops a projectile aimed at a NaN direction instead of firing it.
 *
 * <p>Enhanced AI's ranged goals divide by the HORIZONTAL distance to the target (vanilla multiplies
 * by it, which stays safe at zero). Two mobs at the same X/Z -- what a stack of riding mobs is, and
 * several Lucky Block Pink drops spawn towers of eight -- make that distance zero, so the aim comes
 * out NaN and every tick logs "Invalid entity rotation: NaN, discarding" until the server drowns.
 *
 * <p>Guarding here rather than in each of Enhanced AI's four goals: this is the vanilla funnel every
 * arrow, potion and fireball goes through, so it covers them all and survives their updates.
 *
 * <p>{@code m_6686_} = {@code shoot(DDDFF)V}, per this mod's convention of {@code remap = false} plus
 * SRG names — the build produces no refmap, so a source name would resolve to nothing at runtime.
 */
@Mixin(value = Projectile.class, remap = false)
public class ProjectileNaNGuardMixin {
    private static long luckytweaks_lastLog = -1L;

    @Inject(method = "m_6686_(DDDFF)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void luckytweaks_dropNaNShots(double x, double y, double z, float velocity, float inaccuracy,
                                          CallbackInfo ci) {
        if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) {
            return;
        }
        Projectile self = (Projectile) (Object) this;
        // Logged at most once a minute: this fires every tick while the mobs stay stacked, and the
        // spam is precisely what we are here to stop.
        long now = System.currentTimeMillis();
        if (luckytweaks_lastLog < 0 || now - luckytweaks_lastLog > 60_000L) {
            luckytweaks_lastLog = now;
            LuckyTweaksMod.LOGGER.warn("[nanguard] dropped a {} aimed at NaN (stacked mobs?) near {}",
                    self.getType().getDescriptionId(), self.blockPosition());
        }
        self.discard();
        ci.cancel();
    }
}
