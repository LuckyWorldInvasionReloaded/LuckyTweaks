package com.lwi.luckytweaks.mixin;

import com.lwi.luckytweaks.CrocodileSwallow;
import com.lwi.luckytweaks.TweaksConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns the crocodile's destructive item-eat into a recoverable "swallow" (see {@link CrocodileSwallow}).
 *
 * <p>Fuze Relics' {@code CrocodileEntityIsHurtProcedure#execute} deletes one item from the main hand of
 * whoever hit the crocodile (25% per hit). We cancel that whole procedure and reimplement it: same odds,
 * same melee-only reach (the original only acts on an attacker within 1 block, so projectiles stay safe),
 * same burp sound -- but the taken item is stashed on the crocodile via {@link CrocodileSwallow#swallow}
 * and handed back when it dies, instead of being destroyed.
 *
 * <p>Targets the third-party MCreator class by name (remap=false, require=0) so Lucky Tweaks loads fine
 * when Fuze Relics is absent.
 */
@Mixin(targets = "net.mcreator.fuzerelics.procedures.CrocodileEntityIsHurtProcedure", remap = false)
public class CrocodileSwallowMixin {

    @Inject(
            method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void luckytweaks_recoverableSwallow(LevelAccessor world, double x, double y, double z, Entity entity, Entity sourceentity, CallbackInfo ci) {
        if (!TweaksConfig.FIX_CROCODILE.get()) {
            return; // fix disabled in config -> let the mod's original (destructive) item-eat run
        }
        ci.cancel(); // replace the destructive original with a recoverable swallow
        if (entity == null || sourceentity == null) {
            return;
        }
        if (!(world instanceof Level level)) {
            return;
        }
        if (level.isClientSide()) {
            return; // server only: this edits inventories and spawns items
        }
        if (Math.random() >= 0.25) {
            return; // same 1-in-4 per-hit chance as the original
        }
        // The original only steals from an attacker within ~1 block, so ranged hits never lose a weapon. Keep that.
        Vec3 center = new Vec3(entity.getX(), entity.getY(), entity.getZ());
        if (level.getEntitiesOfClass(Entity.class, new AABB(center, center).inflate(1.0), e -> e == sourceentity).isEmpty()) {
            return;
        }
        if (!(sourceentity instanceof LivingEntity victim)) {
            return;
        }
        ItemStack hand = victim.getMainHandItem();
        if (hand.isEmpty()) {
            return;
        }
        // Take exactly one (matches the original's count - 1), but stash it instead of deleting it.
        ItemStack swallowed = hand.copy();
        swallowed.setCount(1);
        hand.shrink(1);
        victim.setItemInHand(InteractionHand.MAIN_HAND, hand);
        if (victim instanceof Player player) {
            player.getInventory().setChanged();
        }
        CrocodileSwallow.swallow(entity, swallowed);
        level.playSound(null, BlockPos.containing(x, y, z),
                ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.player.burp")),
                SoundSource.NEUTRAL, 3.0f, 1.0f);
    }
}
