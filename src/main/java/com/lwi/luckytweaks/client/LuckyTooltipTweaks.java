package com.lwi.luckytweaks.client;

import com.lwi.luckytweaks.LuckyTweaksMod;
import com.lwi.luckytweaks.api.LuckyTweaksApi;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Rebuilds the Lucky Block mod's "Luck: +X" item tooltip line (found by its translation key):
 * <ul>
 *   <li>the label is hardcoded to the ENGLISH "Luck" whatever the game language — the mod ships a
 *       machine-translated French line ("la chance") and the pack is English-only;</li>
 *   <li>when the stored Luck has reached the block's effective cap (per-block cap from the config/API,
 *       bounded by the global +100 ceiling), the value renders GOLD with a "(max)" suffix, so a
 *       fully-infused block reads at a glance;</li>
 *   <li>otherwise the mod's own colours are kept (green positive, red negative, gold zero).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID, value = Dist.CLIENT)
public final class LuckyTooltipTweaks {
    private static final String LUCK_KEY = "item.lucky.lucky_block.luck";
    private static final int GLOBAL_CEIL = 100;

    private LuckyTooltipTweaks() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        List<Component> lines = event.getToolTip();
        for (int i = 0; i < lines.size(); i++) {
            if (!(lines.get(i).getContents() instanceof TranslatableContents tc) || !LUCK_KEY.equals(tc.getKey())) {
                continue;
            }
            lines.set(i, rebuild(event.getItemStack()));
            return; // the mod emits a single luck line
        }
    }

    private static Component rebuild(ItemStack stack) {
        int luck = stack.getTag() != null ? stack.getTag().getInt("Luck") : 0;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        Integer cap = id != null ? LuckyTweaksApi.getLuckCap(id) : null;
        int max = Math.min(cap != null ? cap : GLOBAL_CEIL, GLOBAL_CEIL);

        MutableComponent value;
        if (luck > 0 && luck >= max) {
            value = Component.literal("+" + luck + " (max)").withStyle(ChatFormatting.GOLD);
        } else if (luck > 0) {
            value = Component.literal("+" + luck).withStyle(ChatFormatting.GREEN);
        } else if (luck < 0) {
            value = Component.literal(String.valueOf(luck)).withStyle(ChatFormatting.RED);
        } else {
            value = Component.literal("0").withStyle(ChatFormatting.GOLD);
        }
        return Component.literal("Luck").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(value);
    }
}
