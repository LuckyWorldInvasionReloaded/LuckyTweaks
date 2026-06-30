package com.lwi.luckytweaks.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Keeps Artifacts' Kitty Slippers working when Enhanced AI is installed.
 *
 * <p>The slippers add a vanilla {@code AvoidEntityGoal} so a creeper flees a wearer within 6 blocks, but
 * Enhanced AI's creeper goals re-engage the wearer and blow up anyway: the swell goal short-circuits on
 * {@code getSwellDir() > 0} (and the target is re-acquired every tick), and the launch goal fires from
 * 8-12 blocks -- outside the slippers' own flee range. The {@code EaiCreeperSwell/LaunchSlippersMixin}s ask
 * this class whether a creeper is "repelled" and, if so, force the Enhanced AI goal's canUse to false, so
 * the creeper just flees as the slippers intend. Enhanced AI stays fully enabled for anyone NOT wearing
 * the slippers.
 *
 * <p>All third-party access is soft: returns false unless BOTH Artifacts and Curios are present, and the
 * only Curios reference lives in {@link KittySlippersCuriosBridge} (touched only when Curios is loaded), so
 * Lucky Tweaks loads fine without either mod.
 */
public final class KittySlippersCompat {
    private KittySlippersCompat() {}

    private static final ResourceLocation KITTY_SLIPPERS = new ResourceLocation("artifacts", "kitty_slippers");
    /** Matches the distance of the slippers' own AvoidEntityGoal (KittySlippersItem uses 6.0). */
    private static final double RANGE = 6.0D;

    private static final boolean ACTIVE =
            ModList.get().isLoaded("artifacts") && ModList.get().isLoaded("curios");

    private static Item slippersItem;
    private static boolean slippersResolved;

    /** True when this creeper should not swell/launch because a Kitty Slippers wearer is its target or nearby. */
    public static boolean isRepelled(Creeper creeper) {
        if (creeper == null || !ACTIVE) {
            return false;
        }
        Item slippers = slippers();
        if (slippers == null) {
            return false;
        }
        // (a) The creeper's target wears the slippers. Covers the launch goal, which is aimed at the target
        //     from up to ~12 blocks -- outside the slippers' own 6-block flee range.
        if (creeper.getTarget() instanceof Player target && KittySlippersCuriosBridge.isWearing(target, slippers)) {
            return true;
        }
        // (b) Any non-spectator player within range wears them. Mirrors the slippers' proximity flee and
        //     also covers the case where the creeper currently targets a different player.
        AABB box = creeper.getBoundingBox().inflate(RANGE);
        List<Player> nearby = creeper.level().getEntitiesOfClass(Player.class, box, p -> !p.isSpectator());
        for (Player p : nearby) {
            if (KittySlippersCuriosBridge.isWearing(p, slippers)) {
                return true;
            }
        }
        return false;
    }

    private static Item slippers() {
        if (!slippersResolved) {
            slippersItem = ForgeRegistries.ITEMS.getValue(KITTY_SLIPPERS);
            slippersResolved = true;
        }
        return slippersItem;
    }
}
