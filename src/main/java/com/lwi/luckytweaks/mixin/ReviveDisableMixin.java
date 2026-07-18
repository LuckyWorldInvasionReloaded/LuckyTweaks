package com.lwi.luckytweaks.mixin;

import com.lwi.luckytweaks.PlayerReviveCompat;
import com.lwi.luckytweaks.SharedLives;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hard-disables PlayerRevive when it has no business running: a team wipe is under way, or the player is
 * the only one online (see the two cases below). Co-op revive itself is not optional — the shared-lives
 * pool is built on it — so there is no longer a config switch here. PlayerRevive
 * gates its WHOLE bleeding/revive system behind the static {@code ReviveEventServer.isReviveActive(Entity)},
 * which its {@code playerDied(LivingDeathEvent)} handler (priority HIGHEST) checks BEFORE it cancels the
 * death. Forcing it to {@code false} here means PlayerRevive never cancels the death, never starts bleeding,
 * sends no chat messages and attaches no bleeding capability — the player just dies the vanilla way (no
 * "lying down", no "is bleeding out", no second death after the bleed timer).
 *
 * <p>Replaces the earlier {@code LivingDeathEvent} un-cancel approach, which let PlayerRevive run first and
 * only undid it afterwards — leaving the bleeding side effects + a delayed re-death. Targets the class by
 * name ({@code remap=false}, {@code require=0}) so Lucky Tweaks is inert when PlayerRevive is absent, exactly
 * like {@code YakurumTooltipGuardMixin}.
 */
@Mixin(targets = "team.creative.playerrevive.server.ReviveEventServer", remap = false)
public class ReviveDisableMixin {

    @Inject(
            method = "isReviveActive(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void luckytweaks$disableRevive(Entity player, CallbackInfoReturnable<Boolean> cir) {
        // First case: a team wipe is being executed (the shared pool just ran dry). PlayerRevive must not
        // catch those deaths and knock everyone down instead of letting the run end.
        if (SharedLives.isGameOver()) {
            cir.setReturnValue(false);
            return;
        }
        // Second case: nobody else is online. Being downed only means something when a team-mate can come and
        // undo it; alone -- on a server just as much as on a LAN world -- it is a plain death wearing a
        // costume, so let it through as one and keep singleplayer and "server, but alone" identical.
        //
        // Only the KNOCK-DOWN is gated, never a player already on the ground: this same method also drives
        // PlayerRevive's bleeding tick (its PlayerTickEvent handler checks it), so switching it off under a
        // downed player -- exactly what happens when their last team-mate logs off -- would freeze them there
        // instead of letting them bleed out or give up.
        if (player instanceof ServerPlayer sp && !PlayerReviveCompat.isDowned(sp) && isAlone(sp)) {
            cir.setReturnValue(false);
        }
    }

    private static boolean isAlone(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return true;
        }
        // Spectators can't revive anyone: a survivor whose only company is a spectator IS alone, and
        // downing them would leave give-up (items dropped) as their only way off the ground.
        int playing = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.isSpectator()) {
                playing++;
            }
        }
        return playing <= 1;
    }
}
