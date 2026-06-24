package com.lwi.luckytweaks.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops Enhanced AI's "Open doors" feature from crashing the server.
 *
 * <p>Enhanced AI adds a vanilla {@code OpenDoorGoal} to any mob in the {@code enhancedai:mobs/can_open_doors}
 * tag, but does so WITHOUT first checking the mob has ground navigation. The vanilla {@code DoorInteractGoal}
 * constructor throws {@code IllegalArgumentException("Unsupported mob type for DoorInteractGoal")} when the
 * mob's navigation is not a {@link GroundPathNavigation} -> "Exception ticking world" the moment the mob joins
 * the level. In this pack it triggers when another mod alters a tagged illager's navigation at runtime (so the
 * tag still lists only ground illagers, yet one of them no longer has ground navigation).
 *
 * <p>We add the guard Enhanced AI is missing: a mob without ground navigation is never eligible to open doors,
 * so the goal is never added to it and the constructor never throws. Normal ground illagers keep the feature
 * (the gimmick stays); only the "broken" mob is silently skipped, regardless of which mod altered it.
 *
 * <p>Targets the third-party class by name (remap=false, require=0) so Lucky Tweaks loads fine when Enhanced
 * AI is absent.
 */
@Mixin(targets = "insane96mcp.enhancedai.modules.mobs.OpenDoors", remap = false)
public class EnhancedAiOpenDoorsMixin {

    @Inject(
            method = "shouldBeAbleToOpenDoors(Lnet/minecraft/world/entity/Mob;)Z",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void luckytweaks_requireGroundNavigation(Mob mob, CallbackInfoReturnable<Boolean> cir) {
        // Only override a "yes": if Enhanced AI already said no, leave it. A mob whose navigation is not a
        // GroundPathNavigation would crash the vanilla OpenDoorGoal/DoorInteractGoal constructor -> force "no".
        if (cir.getReturnValueZ() && !(mob.getNavigation() instanceof GroundPathNavigation)) {
            cir.setReturnValue(false);
        }
    }
}
