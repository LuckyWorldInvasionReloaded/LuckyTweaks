package com.lwi.luckytweaks.integration;

import com.lwi.luckytweaks.BreakEvents;
import com.lwi.luckytweaks.LuckCaps;
import com.lwi.luckytweaks.LuckyTweaksMod;
import com.lwi.luckytweaks.util.LuckyBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Optional JEI integration. The Lucky Block mod's luck crafting and our luck fusion are "special"
 * (dynamically computed) recipes, which JEI hides by default -- so players cannot discover how to
 * infuse a lucky block. This plugin generates EXAMPLE recipes in the vanilla crafting category:
 * <ul>
 *   <li>one infusion recipe per (lucky block x luck item), result luck = the item's modifier
 *       bounded by the block's cap -- enumerated live from the Lucky Block mod's registry
 *       ({@code JavaLuckyRegistry.craftingLuckModifiers}, i.e. every addon's luck_crafting.txt),
 *       so any pack's blocks and values show up automatically;</li>
 *   <li>one fusion recipe per lucky block (two +25 blocks -> one fused block).</li>
 * </ul>
 * Only loaded by JEI itself (via {@link JeiPlugin} scanning), so JEI stays a soft dependency.
 */
@JeiPlugin
public class LuckyTweaksJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = new ResourceLocation(LuckyTweaksMod.MODID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        try {
            List<CraftingRecipe> recipes = new ArrayList<>();
            // blockId -> (itemId -> luck value); "*" = items that apply to every lucky block.
            Map<String, Map<String, Integer>> table = readLuckCraftingTable();
            int n = 0;
            for (Item item : ForgeRegistries.ITEMS) {
                ResourceLocation blockId = ForgeRegistries.ITEMS.getKey(item);
                if (blockId == null || !LuckyBlocks.LUCKY_NAMESPACE.equals(blockId.getNamespace())
                        || !(item instanceof BlockItem)) {
                    continue;
                }
                Integer cap = LuckCaps.capFor(blockId);
                int upper = (cap != null) ? Math.min(cap, BreakEvents.STORED_LUCK_MAX) : BreakEvents.STORED_LUCK_MAX;

                // --- Infusion examples: block + 1 luck item -> block at the item's modifier ---
                Map<String, Integer> luckItems = new java.util.HashMap<>(table.getOrDefault("*", Map.of()));
                luckItems.putAll(table.getOrDefault(blockId.toString(), Map.of()));
                for (Map.Entry<String, Integer> e : luckItems.entrySet()) {
                    ResourceLocation ingId = ResourceLocation.tryParse(e.getKey());
                    Item ing = (ingId == null) ? null : ForgeRegistries.ITEMS.getValue(ingId);
                    if (ing == null || ing == net.minecraft.world.item.Items.AIR) {
                        continue;
                    }
                    int luck = Math.max(BreakEvents.LUCK_FLOOR, Math.min(upper, e.getValue()));
                    if (luck == 0) {
                        continue;
                    }
                    recipes.add(new ShapelessRecipe(
                            new ResourceLocation(LuckyTweaksMod.MODID, "jei_infusion_" + (n++)),
                            "luckytweaks.infusion", CraftingBookCategory.MISC,
                            withLuck(item, luck),
                            NonNullList.of(Ingredient.EMPTY, Ingredient.of(item), Ingredient.of(ing))));
                }

                // --- Fusion example: two +25 blocks -> one fused block (sum, bounded by the cap) ---
                ItemStack half = withLuck(item, 25);
                recipes.add(new ShapelessRecipe(
                        new ResourceLocation(LuckyTweaksMod.MODID, "jei_fusion_" + (n++)),
                        "luckytweaks.fusion", CraftingBookCategory.MISC,
                        withLuck(item, Math.min(upper, 50)),
                        NonNullList.of(Ingredient.EMPTY, Ingredient.of(half), Ingredient.of(half.copy()))));
            }
            if (!recipes.isEmpty()) {
                registration.addRecipes(RecipeTypes.CRAFTING, recipes);
                LuckyTweaksMod.LOGGER.info("[jei] registered {} example luck recipes", recipes.size());
            }
        } catch (Throwable t) {
            // Never let the integration take JEI down with it: degrade to "no examples".
            LuckyTweaksMod.LOGGER.warn("[jei] failed to build example recipes: {}", t.toString());
        }
    }

    private static ItemStack withLuck(Item item, int luck) {
        ItemStack stack = new ItemStack(item);
        if (luck != 0) {
            stack.getOrCreateTag().putInt("Luck", luck);
        }
        return stack;
    }

    /**
     * The Lucky Block mod's luck-crafting registry, normalized to blockId -> (itemId -> value),
     * with "*" holding flat entries that apply to every lucky block. The real structure is a
     * NESTED map (each addon's luck_crafting.txt is per-block; values come back as LinkedHashMap,
     * which crashed the first naive flat read). Handles both nestings (block->item and
     * item->block, disambiguated by the "lucky:" namespace) plus a flat fallback. Read by
     * reflection so there is no compile dependency; an empty result just means no infusion
     * examples, never a crash.
     */
    private static Map<String, Map<String, Integer>> readLuckCraftingTable() {
        Map<String, Map<String, Integer>> table = new java.util.HashMap<>();
        try {
            Class<?> reg = Class.forName("mod.lucky.java.JavaLuckyRegistry");
            Object instance = reg.getField("INSTANCE").get(null);
            Object raw = reg.getMethod("getCraftingLuckModifiers").invoke(instance);
            if (!(raw instanceof Map<?, ?> outer)) {
                return table;
            }
            for (Map.Entry<?, ?> e : outer.entrySet()) {
                String keyA = String.valueOf(e.getKey());
                Object val = e.getValue();
                if (val instanceof Number num) {
                    // Flat form: ingredient -> value, applies to every lucky block.
                    table.computeIfAbsent("*", k -> new java.util.HashMap<>()).put(keyA, num.intValue());
                } else if (val instanceof Map<?, ?> inner) {
                    for (Map.Entry<?, ?> ie : inner.entrySet()) {
                        if (!(ie.getValue() instanceof Number num)) {
                            continue;
                        }
                        String keyB = String.valueOf(ie.getKey());
                        // One key is the lucky block (always in the "lucky" namespace), the other
                        // the ingredient -- accept either nesting order.
                        String blockId = keyA.startsWith(LuckyBlocks.LUCKY_NAMESPACE + ":") ? keyA : keyB;
                        String itemId = blockId.equals(keyA) ? keyB : keyA;
                        table.computeIfAbsent(blockId, k -> new java.util.HashMap<>()).put(itemId, num.intValue());
                    }
                }
            }
        } catch (Throwable t) {
            LuckyTweaksMod.LOGGER.warn("[jei] could not read the Lucky Block luck-crafting registry: {}", t.toString());
        }
        return table;
    }
}
