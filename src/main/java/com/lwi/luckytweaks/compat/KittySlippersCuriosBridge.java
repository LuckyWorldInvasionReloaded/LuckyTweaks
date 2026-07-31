package com.lwi.luckytweaks.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.List;

/**
 * Isolated Curios access. Only ever reached through {@link KittySlippersCompat} and {@link CuriosWorn},
 * which gate every call on Curios being loaded, so the {@code CuriosApi} reference here is never linked
 * when Curios is absent and Lucky Tweaks loads fine without it.
 */
final class KittySlippersCuriosBridge {
    private KittySlippersCuriosBridge() {}

    /** Whether {@code player} is wearing {@code item} in any Curios slot. */
    static boolean isWearing(Player player, Item item) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .map(handler -> handler.findFirstCurio(item).isPresent())
                .orElse(false);
    }

    /** How many of {@code item} the player is wearing across every Curios slot. */
    static int countWorn(Player player, Item item) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .map(handler -> {
                    int found = 0;
                    List<SlotResult> worn = handler.findCurios(item);
                    for (SlotResult result : worn) {
                        found += result.stack().getCount();
                    }
                    return found;
                })
                .orElse(0);
    }
}
