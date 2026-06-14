package com.lwi.luckytweaks;

import com.lwi.luckytweaks.util.LuckyBlocks;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Luck FUSION: combine two or more lucky blocks of the SAME type (and nothing else) in a crafting
 * grid into one block whose Luck is the sum of all inputs, bounded by the block's registered cap
 * (if any) and the global safety clamp. Two +25 blocks fuse into one +50; with stacks in each slot
 * and shift-click, a whole batch fuses in one click (vanilla repeats the craft per item).
 *
 * <p>Complements the Lucky Block mod's own luck-crafting recipe (1 block + luck items), which never
 * matches a grid holding 2+ lucky blocks -- so the two recipes can't conflict.
 */
public class LuckFusionRecipe extends CustomRecipe {
    public LuckFusionRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        if (!TweaksConfig.ENABLE_LUCK_FUSION.get()) {
            return false; // fusion disabled in config -> recipe never matches
        }
        int blocks = 0;
        ItemStack first = ItemStack.EMPTY;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (!isLuckyBlockItem(s)) {
                return false; // anything else in the grid -> not a fusion
            }
            if (first.isEmpty()) {
                first = s;
            } else if (!s.is(first.getItem())) {
                return false; // different lucky block types can't fuse
            }
            blocks++;
        }
        return blocks >= 2;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        int sum = 0;
        ItemStack first = ItemStack.EMPTY;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (first.isEmpty()) {
                first = s;
            }
            CompoundTag tag = s.getTag();
            sum += (tag != null) ? tag.getInt("Luck") : 0;
        }
        if (first.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = new ItemStack(first.getItem());
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(first.getItem());
        Integer cap = (id == null) ? null : LuckCaps.capFor(id);
        // STORED_LUCK_MAX, not LUCK_CEIL: a fused block is a real item, and no item in the game ever
        // carries more than +/-100 luck (the Lucky Block mod's own bound).
        int upper = (cap != null) ? Math.min(cap, BreakEvents.STORED_LUCK_MAX) : BreakEvents.STORED_LUCK_MAX;
        int luck = Math.max(BreakEvents.LUCK_FLOOR, Math.min(upper, sum));
        if (luck != 0) {
            result.getOrCreateTag().putInt("Luck", luck);
        }
        return result;
    }

    private static boolean isLuckyBlockItem(ItemStack stack) {
        // STRICTLY the "lucky" namespace (base mod + addons): only those blocks actually READ their
        // Luck NBT. Cross-mod lookalikes (e.g. fuze_relics:lucky_blockling, an MCreator block with
        // its own outcome engine) ignore Luck entirely -- fusing them would destroy a block for
        // nothing, so the loose name-based heuristic of LuckyBlocks.isLuckyBlock is wrong here.
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(blockItem.getBlock());
        return id != null && LuckyBlocks.LUCKY_NAMESPACE.equals(id.getNamespace());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return LuckyTweaksMod.LUCK_FUSION.get();
    }
}
