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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.lwi.luckytweaks.net.SharedLivesNet;

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
 *       some other mod saved this death for its own reasons — we stand aside entirely. Almost every totem
 *       acts earlier and never shows up here at all ({@code LivingDamageEvent} for TotemBeforePlayerRevive,
 *       vanilla's {@code checkTotemDeathProtection} for Yakurum's resurrection and pandilla totem, and
 *       Artifacts' Chorus Totem injects at its head), so a totem or a resurrection <b>never costs a life</b>
 *       — the event simply never fires. The one exception is Born in Chaos' Death Totem, which saves from a
 *       NORMAL listener, i.e. after us: see {@link #mayBeSavedByDeathTotem} and
 *       {@link #onDeathAfterDeathTotem}.</li>
 *   <li><b>Uncancelled + downed flag</b> — a downed player actually dying: gave up, disconnected while
 *       down, or was finished by a bypassed source (the void). The life was already spent at the fall,
 *       so this only costs their <b>items</b>; we also clear the bleeding state, which PlayerRevive's own
 *       handler leaves dangling on the paths that don't go through its kill().</li>
 *   <li><b>Anything else</b> — a plain death: singleplayer, or a damage source PlayerRevive bypasses.
 *       Spends a life and respawns, keeping the inventory.</li>
 * </ul>
 *
 * <p>Bleeding out lands in the second path: the pack ships {@code bleedTime} = 5 minutes ON PURPOSE
 * (designer decision 2026-07-18) — rescue has a deadline, which keeps the team playing close together
 * instead of scattering across the map. A player nobody reaches in time bleeds out exactly like a
 * give-up: the life was already spent at the fall, the items drop where they fell. Downed players are
 * otherwise safe meanwhile (the shipped config disables damage on them), and giving up early is what
 * keeps a fully-downed team from deadlocking.
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID)
public final class SharedLivesEvents {

    private static final ResourceLocation BLED_TO_DEATH = new ResourceLocation("playerrevive", "bled_to_death");

    // Last pool state pushed to clients, so a periodic tick can re-broadcast only on a real change.
    private static int lastMax = -1;
    private static int lastRemaining = -1;

    private SharedLivesEvents() {}

    /**
     * Latch the run as multiplayer once two players are online together, and re-sync the hearts HUD when
     * the pool changes for a reason that isn't a death: that very latch (the allowance jumps
     * solo->multiplayer) or editing the lives count in the config. Both change {@code maxLives}/
     * {@code remaining} with no packet otherwise; this cheap once-a-second check pushes an update only
     * when the value actually moved.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null || server.getTickCount() % 20 != 0) {
            return;
        }
        SharedLives.noteHeadcount(server);
        int max = SharedLives.maxLives(server);
        int remaining = SharedLives.remaining(server);
        if (max != lastMax || remaining != lastRemaining) {
            lastMax = max;
            lastRemaining = remaining;
            SharedLivesNet.broadcast(server);
        }
    }

    /** Sync the pool to a joining player, and show the one-time "designed for hardcore, adjustable" line. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        // Latch multiplayer/peak NOW, not at the next once-a-second tick: in that window a downing
        // would consume against the SOLO allowance (1 life) and wipe the team on the spot.
        SharedLives.noteHeadcount(server);
        // Push the pool so the heart HUD is correct from the first frame. There used to be a one-time
        // "you have a single life..." chat line here too; it is gone now that the hearts show the count
        // on screen, which says it more plainly than a message you read once and forget.
        SharedLivesNet.sendTo(player);
    }

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

        deferredToDeathTotem = null;                 // this death gets its own verdict
        boolean downed = PlayerReviveCompat.isDowned(player);
        if (event.isCanceled()) {
            if (downed) {
                onDowned(server, player);
            }
            // cancelled without the downed flag: some OTHER mod saved this death — not ours to touch
        } else if (downed || isBledToDeath(event.getSource())) {
            onDownedDeath(event, player);
        } else if (mayBeSavedByDeathTotem(player)) {
            deferredToDeathTotem = player;           // let Born in Chaos try at NORMAL; settled at LOW
        } else {
            onPlainDeath(event, server, player);
        }
    }

    /**
     * The player whose death {@link #onDeath} handed over to Born in Chaos' Death Totem, consumed by
     * {@link #onDeathAfterDeathTotem} in the same synchronous dispatch.
     *
     * <p>Explicit state rather than re-checking the inventory at LOW: the totem's own listener SHRINKS the
     * stack when it fires, so a second look would report "no totem" and skip a death that still needs
     * settling -- no life spent, no respawn, no game over. Deaths are dispatched one at a time on the
     * server thread, and LOW receives cancelled events too, so this is always set and cleared in pairs.
     */
    private static ServerPlayer deferredToDeathTotem;

    /** Born in Chaos' Death Totem, the one death-protection item that saves from a LivingDeathEvent listener. */
    private static final ResourceLocation DEATH_TOTEM = new ResourceLocation("born_in_chaos_v1", "death_totem");

    /**
     * Whether Born in Chaos' Death Totem could still save this death.
     *
     * <p>Every other totem in the pack (vanilla, Artifacts' Chorus Totem, Yakurum's pandilla totem and its
     * resurrection) acts inside {@code checkTotemDeathProtection}, so a death they save never reaches this
     * class at all. This one instead cancels the death from a NORMAL-priority listener, i.e. AFTER us: if we
     * settled it at HIGH we would spend a life and cancel the event, the totem's listener would never run,
     * and it would silently fail to save anyone.
     *
     * <p>Checks the whole inventory rather than just the hands on purpose: being broader than Born in Chaos
     * only costs a deferral to LOW, while being narrower would spend a life on a death it was about to save.
     */
    private static boolean mayBeSavedByDeathTotem(ServerPlayer player) {
        Item totem = ForgeRegistries.ITEMS.getValue(DEATH_TOTEM);
        if (totem == null) {
            return false;                            // Born in Chaos isn't installed
        }
        // By ITEM, not Inventory#contains(ItemStack) — that compares NBT too, so a totem renamed at
        // an anvil would be missed, we would settle the death at HIGH, and the totem could never fire.
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(totem)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Second pass, once Born in Chaos' Death Totem has had its say at NORMAL, and still ahead of Corpse and
     * Old School Hardcore at LOWEST. Only the deaths {@link #onDeath} deliberately stood back from get here.
     */
    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    public static void onDeathAfterDeathTotem(LivingDeathEvent event) {
        ServerPlayer player = deferredToDeathTotem;
        deferredToDeathTotem = null;                 // consume it whatever happens next
        if (player == null || event.getEntity() != player) {
            return;                                  // this death was settled at HIGH
        }
        if (event.isCanceled()) {
            return;                                  // the totem saved them: free, exactly like every other totem
        }
        MinecraftServer server = player.getServer();
        if (server == null || SharedLives.isGameOver()) {
            return;
        }
        onPlainDeath(event, server, player);         // the totem passed: the run pays for this death
    }

    /** PlayerRevive downed the player: the fall itself is what costs the team a life. */
    private static void onDowned(MinecraftServer server, ServerPlayer player) {
        int left = SharedLives.consume(server);
        SharedLivesNet.broadcast(server);               // refresh the hearts HUD for everyone
        if (left <= 0) {
            broadcast(server, Component.translatable("luckytweaks.msg.down_no_lives",
                    player.getScoreboardName()), ChatFormatting.DARK_RED);
            teamGameOver(server, null);
            return;
        }
        broadcast(server, Component.translatable("luckytweaks.msg.down",
                player.getScoreboardName(), livesLabel(left)), ChatFormatting.GOLD);
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
                Component.translatable("luckytweaks.msg.out_of_fight")
                        .withStyle(ChatFormatting.RED), false);
    }

    /** No downing happened: singleplayer, or a damage source PlayerRevive bypasses. */
    private static void onPlainDeath(LivingDeathEvent event, MinecraftServer server, ServerPlayer player) {
        int left = SharedLives.consume(server);
        SharedLivesNet.broadcast(server);               // refresh the hearts HUD for everyone
        if (left <= 0) {
            // Let this death stand — hardcore turns it into a Game Over — and take the rest down with it.
            broadcast(server, Component.translatable("luckytweaks.msg.game_over"), ChatFormatting.DARK_RED);
            teamGameOver(server, player);
            return;
        }
        event.setCanceled(true);
        restore(player);
        respawn(player);
        broadcast(server, Component.translatable("luckytweaks.msg.died",
                player.getScoreboardName(), livesLabel(left)), ChatFormatting.GOLD);
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

    private static Component livesLabel(int lives) {
        return lives == 1 ? Component.translatable("luckytweaks.msg.lives_one")
                : Component.translatable("luckytweaks.msg.lives_many", lives);
    }

    /** Translatable components resolve on each CLIENT, so every player reads these in their own language. */
    private static void broadcast(MinecraftServer server, Component text, ChatFormatting colour) {
        server.getPlayerList().broadcastSystemMessage(text.copy().withStyle(colour), false);
    }
}
