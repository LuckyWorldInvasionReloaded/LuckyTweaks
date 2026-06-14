package com.lwi.luckytweaks.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/** Detects lucky blocks: the "lucky" namespace (base mod + addons), plus cross-mod naming conventions. */
public final class LuckyBlocks {
    public static final String LUCKY_NAMESPACE = "lucky";

    private LuckyBlocks() {}

    public static boolean isLuckyBlock(BlockState state) {
        return isLuckyBlock(state.getBlock());
    }

    public static boolean isLuckyBlock(Block block) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id == null) {
            return false;
        }
        String path = id.getPath();
        // Skip technical render-proxy companions. MCreator registers a "<name>_render" block (drawn by
        // a block-entity renderer) alongside the real one; it shares the real block's display name but
        // has an empty model, so it showed up as a second, icon-less "Lucky Blockling" row. It is not
        // a real lucky block -- exclude it everywhere (the detection list AND break handling).
        if (path.endsWith("_render")) {
            return false;
        }
        if (LUCKY_NAMESPACE.equals(id.getNamespace())) {
            return true;
        }
        // Lucky blocks living in other mods' namespaces (e.g. fuze_relics:lucky_blockling).
        return path.contains("lucky_block") || path.contains("luckyblock");
    }
}
