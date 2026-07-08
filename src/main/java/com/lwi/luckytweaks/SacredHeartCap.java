package com.lwi.luckytweaks;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Caps how many Yakurum Sacred Hearts a player may ever eat.
 *
 * <p>A Sacred Heart permanently raises {@code MAX_HEALTH}'s <i>base value</i> by 2 (one heart), and
 * nothing in Yakurum bounds that: {@code YakurumEntityEvents#applyHealthBoost} just does
 * {@code setBaseValue(getBaseValue() + 2)} every time one is eaten. With the vending machines handing
 * out 2/4/6-7 of them per rarity, a player can farm an unbounded health bar.
 *
 * <p>This counts <b>uses of the item</b>, not hearts. Every other source of extra health is untouched:
 * curios (Artifacts' Crystal Heart, ...), the Health Boost effect and anything else all work through
 * attribute <i>modifiers</i>, never the base value, so they keep scaling freely. Only Yakurum's own
 * permanent bonus is gated -- see {@link com.lwi.luckytweaks.mixin.YakurumSacredHeartCapMixin}, which
 * cancels Yakurum's handler once the cap is reached. Eating past the cap still gives the food's
 * Regeneration IV, it just no longer grows the bar.
 *
 * <p><b>Death.</b> Yakurum drops {@code yakurum_max_health} on respawn unless {@code keepInventory} is
 * on, so without it the extra hearts are lost on death. This class mirrors that decision rather than
 * re-reading the gamerule: after Yakurum's own {@code PlayerEvent.Clone} handler has run, the counter
 * survives exactly when the hearts do. Otherwise a player would lose the hearts <i>and</i> stay capped.
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID)
public final class SacredHeartCap {

    private static final ResourceLocation SACRED_HEART = new ResourceLocation("yakurum", "sacred_heart");

    /** Our counter, on the player's Forge persistent data. */
    private static final String KEY_USES = "luckytweaks_sacred_heart_uses";
    /** Yakurum's own marker -- present on the new player exactly when it chose to keep the hearts. */
    private static final String KEY_YAKURUM_HEALTH = "yakurum_max_health";

    public static boolean isSacredHeart(ItemStack stack) {
        return !stack.isEmpty() && SACRED_HEART.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    public static int usesOf(Player player) {
        return player.getPersistentData().getInt(KEY_USES);
    }

    /**
     * Books one use of a Sacred Heart, server-side.
     *
     * @return {@code true} when the heart may still grant its permanent point (Yakurum's handler is
     *         allowed to run), {@code false} once the cap is reached.
     */
    public static boolean tryConsumeUse(Player player) {
        int max = TweaksConfig.SACRED_HEART_MAX_USES.get();
        if (max <= 0) {
            return true;                                    // 0 = uncapped, vanilla Yakurum behaviour
        }
        int used = usesOf(player);
        if (used >= max) {
            player.displayClientMessage(
                    Component.literal("Your body cannot take another Sacred Heart (" + max + "/" + max + ")")
                            .withStyle(ChatFormatting.RED), true);
            return false;
        }
        used++;
        player.getPersistentData().putInt(KEY_USES, used);
        player.displayClientMessage(
                Component.literal("Sacred Heart " + used + "/" + max)
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    /**
     * Keep the counter in lockstep with the hearts themselves. Runs at LOWEST so Yakurum's own Clone
     * handler has already decided: if it re-applied {@code yakurum_max_health} the hearts survived and
     * so must the counter; if it wiped the tag the player is back to a stock health bar, and eating
     * hearts again has to be allowed.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClone(PlayerEvent.Clone event) {
        Player fresh = event.getEntity();
        if (fresh.getPersistentData().contains(KEY_YAKURUM_HEALTH)) {
            int used = event.getOriginal().getPersistentData().getInt(KEY_USES);
            if (used > 0) {
                fresh.getPersistentData().putInt(KEY_USES, used);
            }
        } else {
            fresh.getPersistentData().remove(KEY_USES);     // hearts gone -> let them be earned again
        }
    }

    private SacredHeartCap() {}
}
