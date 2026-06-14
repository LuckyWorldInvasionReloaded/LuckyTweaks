package com.lwi.luckytweaks.mixin;

import com.lwi.luckytweaks.DisabledBlocks;
import com.lwi.luckytweaks.TweaksConfig;
import com.lwi.luckytweaks.util.WorldGenInfo;
import mod.lucky.common.Random;
import mod.lucky.common.Vec3;
import mod.lucky.kotlin.ranges.IntRange;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Natural-spawn multiplier (config-toggleable via {@link TweaksConfig#LUCKY_BLOCK_SPAWN_MULTIPLIER},
 * default 1.0 = off).
 *
 * <p>{@code generateLuckyFeature} walks the block's world-gen drops and, for each one, rolls
 * {@code randInt(IntRange(0, N)) == 0} -- a 1-in-(N+1) chance, the "1 in N chunks" of natural_gen.
 * It places the first entry that passes. Here we intercept that single roll: results in {@code [0,
 * multiplier-1]} are all reported as {@code 0} (a hit), so a spawn becomes exactly {@code multiplier}
 * times as likely over the uniform range -- and self-caps at certainty once the band covers the whole
 * range. A fractional multiplier promotes its last band slot probabilistically. Misses pass through
 * untouched, and the redirect is scoped to this one method, so nothing else that rolls is affected.
 */
@Mixin(targets = "mod.lucky.java.game.LuckyWorldGenUtilsKt", remap = false)
public class LuckyWorldGenMixin {

    /**
     * Apply the per-block spawn rules before any natural roll. The block id being placed is the third
     * argument, the target dimension the fourth. A pack-disabled block, or one with a {@code 0} rule in
     * this dimension, reports "nothing placed" (false). A {@code N>=1} rule overrides the natural rate
     * (and forces the block into a dimension it doesn't natively spawn in): we roll it ourselves and
     * place on a hit. No rule at all falls through to the mod's own native behaviour.
     */
    @Inject(method = "generateLuckyFeature", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void luckytweaks_skipDisabledSpawn(
            Object world, Vec3 pos, String blockId, String dimKey, Random random,
            CallbackInfoReturnable<Boolean> cir) {
        if (DisabledBlocks.isDisabledWorldGen(blockId)) {
            cir.setReturnValue(false);
            return;
        }
        Integer rule = DisabledBlocks.spawnRule(blockId, dimKey);
        if (rule == null) {
            return; // default native behaviour
        }
        if (rule <= 0) {
            cir.setReturnValue(false); // blocked
            return;
        }
        luckytweaks_placeAtRate(world, pos, blockId, random, rule);
        cir.setReturnValue(true);
    }

    /**
     * Place a block at an explicit "1 in N" rate (the {@code N>=1} of a spawn rule): roll the rate,
     * apply the same multiplier promotion as natural spawns, and on a hit place at the surface position
     * the mod already located for this call. A plain placement is enough -- the block forms its own
     * block entity and runs its drops when broken.
     */
    private static void luckytweaks_placeAtRate(Object world, Vec3 pos, String blockId, Random random, int rate) {
        if (!(world instanceof WorldGenLevel level)) {
            return;
        }
        double mult = TweaksConfig.LUCKY_BLOCK_SPAWN_MULTIPLIER.get();
        int result = random.randInt(new IntRange(0, rate));
        boolean hit;
        if (mult <= 1.0) {
            hit = (result == 0);
        } else {
            int whole = (int) Math.floor(mult);
            double frac = mult - whole;
            hit = (result >= 0 && result < whole)
                    || (result == whole && frac > 0.0 && ThreadLocalRandom.current().nextDouble() < frac);
        }
        if (!hit) {
            return;
        }
        Block block = WorldGenInfo.resolveBlock(blockId);
        if (block == null) {
            return;
        }
        level.setBlock(new BlockPos(pos.getX().intValue(), pos.getY().intValue(), pos.getZ().intValue()),
                block.defaultBlockState(), 2);
    }

    @Redirect(
            method = "generateLuckyFeature",
            at = @At(value = "INVOKE",
                    target = "Lmod/lucky/common/Random;randInt(Lmod/lucky/kotlin/ranges/IntRange;)I",
                    remap = false),
            remap = false,
            require = 0
    )
    private static int luckytweaks_multiplySpawn(Random random, IntRange range) {
        int result = random.randInt(range);
        double mult = TweaksConfig.LUCKY_BLOCK_SPAWN_MULTIPLIER.get();
        if (mult <= 1.0 || result < 0) {
            return result; // vanilla behaviour, or a non-spawn slot we never touch
        }
        // result == 0 is the (sole) "spawn" outcome over a uniform range. Promote the first
        // `floor(mult)` slots to a spawn, then the next slot with the fractional remainder.
        int whole = (int) Math.floor(mult);
        if (result < whole) {
            return 0;
        }
        double frac = mult - whole;
        if (frac > 0.0 && result == whole && ThreadLocalRandom.current().nextDouble() < frac) {
            return 0;
        }
        return result;
    }
}
