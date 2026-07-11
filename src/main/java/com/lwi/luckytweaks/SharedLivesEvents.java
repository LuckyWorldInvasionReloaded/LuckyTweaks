package com.lwi.luckytweaks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Optional;

/**
 * The shared-lives rule, in one sentence: <b>a lethal blow with no life left is final; otherwise it spends
 * a life and you are saved</b> — downed in multiplayer, respawned in singleplayer.
 *
 * <p>Three paths reach {@link LivingDeathEvent}, told apart by whether the event is cancelled and by
 * PlayerRevive's {@code playerrevive:bleeding} persistent-data flag (see {@link PlayerReviveCompat} for
 * why the damage source CANNOT be used — every real give-up dies with the original attack's source):
 *
 * <ul>
 *   <li><b>Cancelled + downed flag</b> — PlayerRevive just knocked the player down (the flag is written
 *       by {@code startBleeding} before the cancel). Costs the life. Cancelled WITHOUT the flag means
 *       some other mod saved this death for its own reasons — we stand aside entirely. Totems never
 *       show up here at all: they act earlier ({@code LivingDamageEvent} for TotemBeforePlayerRevive,
 *       vanilla's {@code checkTotemDeathProtection} for Yakurum's resurrection and pandilla totem), so a
 *       totem or a resurrection <b>never costs a life</b> — the event simply never fires.</li>
 *   <li><b>Uncancelled + downed flag</b> — a downed player actually dying: gave up, disconnected while
 *       down, or was finished by a bypassed source (the void). The life was already spent at the fall,
 *       so this only costs their <b>items</b>; we also clear the bleeding state, which PlayerRevive's own
 *       handler leaves dangling on the paths that don't go through its kill().</li>
 *   <li><b>Anything else</b> — a plain death: singleplayer, or a damage source PlayerRevive bypasses.
 *       Spends a life and respawns, keeping the inventory.</li>
 * </ul>
 *
 * <p>Bleeding out is not one of them: the pack sets PlayerRevive's {@code bleedTime} so high that a downed
 * player waits for rescue indefinitely, in safety (the shipped config also disables player damage on downed
 * players, so nothing can finish them except the void or /kill). Giving up is the only way out of the
 * ground, and it is what keeps a fully-downed team from deadlocking.
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID)
public final class SharedLivesEvents {

    private static final ResourceLocation BLED_TO_DEATH = new ResourceLocation("playerrevive", "bled_to_death");

    private SharedLivesEvents() {}

    // HIGH, not LOWEST: PlayerRevive cancels at HIGHEST (receiveCanceled lets us see that), and OUR
    // cancel must land before every other death listener — Old School Hardcore marks its persistent
    // "hardcore_death" (forced-spectator) flag at LOWEST, grave/stat mods react at NORMAL; none of
    // them may ever process a death this system saved. Found the hard way: a give-up cancelled at
    // LOWEST left OSH's flag set, and the player was forced into spectator at the next gamemode change.
    @SubscribeEvent(priority = EventPriority.HIGH, receiveCanceled = true)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        // While a wipe is being executed every death must go through untouched, or killing the team
        // would recurse straight back into this handler.
        if (server == null || SharedLives.isGameOver() || player.isCreative() || player.isSpectator()) {
            return;
        }

        boolean downed = PlayerReviveCompat.isDowned(player);
        if (event.isCanceled()) {
            if (downed) {
                onDowned(server, player);
            }
            // cancelled without the downed flag: some OTHER mod saved this death — not ours to touch
        } else if (downed || isBledToDeath(event.getSource())) {
            onDownedDeath(event, player);
        } else {
            onPlainDeath(event, server, player);
        }
    }

    /** PlayerRevive downed the player: the fall itself is what costs the team a life. */
    private static void onDowned(MinecraftServer server, ServerPlayer player) {
        int left = SharedLives.consume(server);
        if (left <= 0) {
            broadcast(server, player.getScoreboardName() + " went down with no lives left.", ChatFormatting.DARK_RED);
            teamGameOver(server, null);
            return;
        }
        broadcast(server, player.getScoreboardName() + " went down — " + livesLabel(left) + " left. Revive them!",
                ChatFormatting.GOLD);
    }

    /**
     * A downed player actually dying (gave up, disconnected, finished by a bypassed source): the life is
     * already paid, so this costs the inventory, not a life.
     */
    private static void onDownedDeath(LivingDeathEvent event, ServerPlayer player) {
        event.setCanceled(true);
        // Drop where they fell, before the teleport, so a teammate can still go and fetch the loot.
        player.getInventory().dropAll();
        // Clear the bleeding state BEFORE healing: on the paths that skip PlayerRevive's kill() nothing
        // else ever would, and its tick would keep the swimming pose + effects on a saved player forever.
        PlayerReviveCompat.clearDownedState(player);
        restore(player);
        respawn(player);
        player.displayClientMessage(
                Component.literal("You're out of the fight. Your items are where you fell.")
                        .withStyle(ChatFormatting.RED), false);
    }

    /** No downing happened: singleplayer, or a damage source PlayerRevive bypasses. */
    private static void onPlainDeath(LivingDeathEvent event, MinecraftServer server, ServerPlayer player) {
        int left = SharedLives.consume(server);
        if (left <= 0) {
            // Let this death stand — hardcore turns it into a Game Over — and take the rest down with it.
            broadcast(server, "No lives left. The run is over.", ChatFormatting.DARK_RED);
            teamGameOver(server, player);
            return;
        }
        event.setCanceled(true);
        restore(player);
        respawn(player);
        broadcast(server, player.getScoreboardName() + " died — " + livesLabel(left) + " left.", ChatFormatting.GOLD);
    }

    /**
     * End the run for everyone. The latch makes {@code isReviveActive} return false (so PlayerRevive
     * cannot catch these deaths and down the players instead) and makes {@link #onDeath} stand aside.
     */
    private static void teamGameOver(MinecraftServer server, ServerPlayer alreadyDying) {
        SharedLives.setGameOver(true);
        try {
            List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
            for (ServerPlayer other : players) {
                if (other != alreadyDying && other.isAlive() && !other.isSpectator() && !other.isCreative()) {
                    // die() rather than kill(): kill() deals damage, and a damage event is exactly where
                    // TotemBeforePlayerRevive pops a Totem of Undying -- a player holding one would walk
                    // away from the team's Game Over. Going straight to die() cannot be intercepted.
                    other.setHealth(0.0F);
                    other.die(other.damageSources().genericKill());
                }
            }
        } finally {
            SharedLives.setGameOver(false);
        }
    }

    /**
     * Undo the damage that would have killed them; a cancelled death otherwise leaves the player at 0 HP.
     * This must hand back everything a vanilla respawn would have reset — the death never "happens", so
     * nothing else will.
     */
    private static void restore(ServerPlayer player) {
        if (player.isSleeping()) {
            player.stopSleepInBed(true, true);  // die() would have cleared this; our cancel skipped it
        }
        player.setForcedPose(null);
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        player.fallDistance = 0.0F;
        player.setAirSupply(player.getMaxAirSupply());
        player.removeAllEffects();
        // A vanilla respawn starts on a fresh FoodData; PlayerRevive even pins hunger to 6 while downed.
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.getFoodData().setExhaustion(0.0F);
        // Belt and braces against death listeners that slipped in before our cancel: Old School
        // Hardcore's persistent flag turns every later gamemode change into forced spectator. Our HIGH
        // priority should keep it from ever being written, but a stale one must never survive a save.
        player.getPersistentData().getCompound("PlayerPersisted").remove("hardcore_death");
        // No stopRiding needed: ServerPlayer.teleportTo dismounts first thing, verified in the mapped jar.
    }

    /** Vanilla respawn placement: bed or anchor when it still works, otherwise the world spawn. */
    private static void respawn(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel target = server.getLevel(player.getRespawnDimension());
        BlockPos bed = player.getRespawnPosition();
        if (target != null && bed != null) {
            Optional<Vec3> spot = Player.findRespawnPositionAndUseSpawnBlock(
                    target, bed, player.getRespawnAngle(), player.isRespawnForced(), false);
            if (spot.isPresent()) {
                Vec3 v = spot.get();
                player.teleportTo(target, v.x, v.y, v.z, player.getRespawnAngle(), 0.0F);
                return;
            }
        }
        // No bed, or it burned/was obstructed: the void death above makes a fallback mandatory, never a
        // "leave them where they were".
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                overworld.getSharedSpawnAngle(), 0.0F);
    }

    private static boolean isBledToDeath(DamageSource source) {
        return source.typeHolder().unwrapKey().map(key -> BLED_TO_DEATH.equals(key.location())).orElse(false);
    }

    private static String livesLabel(int lives) {
        return lives == 1 ? "1 shared life" : lives + " shared lives";
    }

    private static void broadcast(MinecraftServer server, String text, ChatFormatting colour) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(text).withStyle(colour), false);
    }
}
