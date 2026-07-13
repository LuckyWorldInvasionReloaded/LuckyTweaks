package com.lwi.luckytweaks;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * Lucky Tweaks - fixes and balance tools for the Lucky Block mod.
 *
 * <p><b>1. The luck fix.</b> The Lucky Block mod reads the broken block's entity in
 * {@code playerDestroy}, which Minecraft runs AFTER the block (and its block entity) is removed --
 * so {@code getBlockEntity} returns null and every player break rolls at luck 0, no matter what
 * Luck the block carries (luck crafting, natural structures, custom drops). {@link BreakEvents}
 * captures the real Luck at {@code BlockEvent.BreakEvent} (the entity still exists then) and
 * {@code mixin.DropEvaluatorMixin} injects it into the mod's {@code runRandomDrop}.
 *
 * <p><b>2. Per-block luck caps.</b> Packs (via the {@code luckCaps} COMMON config) and mods (via
 * {@link com.lwi.luckytweaks.api.LuckyTweaksApi#registerLuckCap}) can cap a lucky block's positive
 * luck. The cap applies to BOTH the effective luck of a break roll and the result of the mod's
 * luck-crafting recipe ({@code mixin.LuckCraftingRecipeMixin}) -- so the crafted item's tooltip
 * never promises more than the block will deliver. Negative luck (curses) is never capped.
 *
 * <p><b>3. A tiny API.</b> {@link com.lwi.luckytweaks.api.LuckyTweaksApi#addLuck} lets another mod
 * contribute luck to the break currently being processed (e.g. luck-boosting equipment).
 */
@Mod(LuckyTweaksMod.MODID)
public final class LuckyTweaksMod {
    /** Mod id. Must match {@code mod_id} in gradle.properties. */
    public static final String MODID = "luckytweaks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MODID);
    /** Luck fusion: 2+ same-type lucky blocks fuse into one, summing their Luck. See {@link LuckFusionRecipe}. */
    public static final RegistryObject<RecipeSerializer<LuckFusionRecipe>> LUCK_FUSION =
            RECIPE_SERIALIZERS.register("luck_fusion", () -> new SimpleCraftingRecipeSerializer<>(LuckFusionRecipe::new));

    public LuckyTweaksMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TweaksConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, TweaksClientConfig.CLIENT_SPEC);
        RECIPE_SERIALIZERS.register(FMLJavaModLoadingContext.get().getModEventBus());
        com.lwi.luckytweaks.locator.LocatorNetwork.init();
        com.lwi.luckytweaks.net.SharedLivesNet.init();
        LOGGER.info("Lucky Tweaks loaded.");
    }
}
