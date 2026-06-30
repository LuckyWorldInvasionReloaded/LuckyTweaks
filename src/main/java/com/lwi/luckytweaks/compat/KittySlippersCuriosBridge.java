package com.lwi.luckytweaks.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Isolated Curios access. Only ever reached through {@link KittySlippersCompat}, which gates every call on
 * Curios being loaded, so the {@code CuriosApi} reference here is never linked when Curios is absent and
 * Lucky Tweaks loads fine without it.
 */
final class KittySlippersCuriosBridge {
    private KittySlippersCuriosBridge() {}

    /** Whether {@code player} is wearing {@code item} in any Curios slot. */
    static boolean isWearing(Player player, Item item) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .map(handler -> handler.findFirstCurio(item).isPresent())
                .orElse(false);
    }
}
