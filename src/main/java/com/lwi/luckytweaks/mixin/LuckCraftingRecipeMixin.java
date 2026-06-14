package com.lwi.luckytweaks.mixin;

import com.lwi.luckytweaks.LuckCaps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Clamp the RESULT of the Lucky Block mod's luck-crafting recipe to the block's registered cap --
 * including the crafting-table PREVIEW. Without this, a capped block could display e.g. "+70" in
 * the result slot while the cap means it will never roll above its cap: the tooltip overpromised.
 *
 * <p>Target verified against the production jar (javap): the Kotlin class keeps the
 * official-mappings method name {@code assemble(CraftingContainer, RegistryAccess)} alongside the
 * SRG bridge {@code m_5874_(Container, RegistryAccess)}, which type-erases and delegates to it.
 * Injecting at the named method's RETURN therefore covers every vanilla call path.
 */
@Mixin(targets = "mod.lucky.forge.game.LuckModifierCraftingRecipe", remap = false)
public class LuckCraftingRecipeMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("luckytweaks");

    @Inject(
            method = "assemble(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void luckytweaks_clampCraftedLuck(CraftingContainer container, RegistryAccess registryAccess,
                                              CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (result == null || result.isEmpty()) {
            return;
        }
        // The lucky block's ITEM id matches its block id, so the same cap registry serves both.
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(result.getItem());
        Integer cap = (id == null) ? null : LuckCaps.capFor(id);
        if (cap == null) {
            return;
        }
        CompoundTag tag = result.getTag();
        if (tag != null && tag.getInt("Luck") > cap) {
            LOGGER.info("[caps] luck-crafting result {} clamped {} -> {}", id, tag.getInt("Luck"), cap);
            tag.putInt("Luck", cap);
        }
    }
}
