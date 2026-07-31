package com.lwi.luckytweaks.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;

/**
 * "Is this player wearing that item in a Curios slot?", asked safely.
 *
 * <p>The only Curios reference lives in {@link KittySlippersCuriosBridge}, which this class never touches
 * unless Curios is actually loaded — so every caller gets a plain {@code false} on a game without it,
 * instead of a {@code NoClassDefFoundError}. Added for the achievement ladder (the Extendo Grip), which
 * asks about an item the pack ships but the mod does not depend on.
 */
public final class CuriosWorn {
    private static final boolean ACTIVE = ModList.get().isLoaded("curios");

    private CuriosWorn() {}

    /** Whether Curios is present at all — callers can skip the whole question when it isn't. */
    public static boolean available() {
        return ACTIVE;
    }

    public static boolean isWearing(Player player, Item item) {
        return ACTIVE && player != null && item != null && KittySlippersCuriosBridge.isWearing(player, item);
    }

    /**
     * How many of {@code item} the player is WEARING (0 without Curios). An accessory in its slot is not in
     * the inventory, so anything counting what a player owns has to add this to the inventory count or it
     * will miss the one they are actually using.
     */
    public static int countWorn(Player player, Item item) {
        return (ACTIVE && player != null && item != null)
                ? KittySlippersCuriosBridge.countWorn(player, item) : 0;
    }
}
