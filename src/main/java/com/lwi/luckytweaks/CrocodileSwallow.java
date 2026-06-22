package com.lwi.luckytweaks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Makes Fuze Relics' crocodile "item eat" recoverable instead of destructive.
 *
 * <p>By default {@code CrocodileEntityIsHurtProcedure} deletes one item from the attacker's main hand
 * (count - 1) on a 25% roll per hit. For a one-stack weapon that means losing it outright, and with the
 * crocodile's 40 HP a single kill destroys the player's weapon ~75% of the time -- which is what players
 * were complaining about.
 *
 * <p>{@code mixin.CrocodileSwallowMixin} replaces that deletion with a "swallow": the item is stashed in
 * the crocodile's Forge persistent data (auto-saved/loaded, so it survives chunk unload and relog). This
 * handler spits everything back out when the crocodile dies, so the player only has to kill it to get the
 * gear back. Keyed on the persistent-data tag alone, so it does nothing on any other entity and degrades
 * cleanly when Fuze Relics is absent.
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID)
public final class CrocodileSwallow {

    /** Forge-persistent-data key holding the list of swallowed item stacks on a crocodile. */
    public static final String SWALLOWED_KEY = "LuckyTweaksSwallowed";

    private CrocodileSwallow() {}

    /** Stash one item the crocodile just took, so {@link #onDeath} can give it back later. */
    public static void swallow(Entity crocodile, ItemStack stack) {
        if (crocodile == null || stack == null || stack.isEmpty()) {
            return;
        }
        CompoundTag data = crocodile.getPersistentData();
        ListTag list = data.getList(SWALLOWED_KEY, Tag.TAG_COMPOUND);
        list.add(stack.save(new CompoundTag()));
        data.put(SWALLOWED_KEY, list);
    }

    /**
     * Drop everything a dying entity swallowed. LOWEST priority with the default (no receiveCanceled) so
     * we only run when the death is actually going through -- a death cancelled by another mod never
     * reaches us, which rules out dropping the loot while the crocodile lives on.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(SWALLOWED_KEY, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = data.getList(SWALLOWED_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            if (!stack.isEmpty()) {
                entity.spawnAtLocation(stack);
            }
        }
        data.remove(SWALLOWED_KEY);
    }
}
