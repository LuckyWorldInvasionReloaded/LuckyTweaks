package com.lwi.luckytweaks;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Stops a broken third-party mixin from crashing players who trade with a villager.
 *
 * <p>Fabric API's {@code TradeOffersTypeAwareBuyForOneEmeraldFactoryMixin} (in fabric-object-builder-api-v1,
 * shipped inside Forgified Fabric API, which txnilib carries) injects into vanilla's "sell an item chosen by
 * the villager's biome" trade — the master fisherman's boat. Against Forge 47.4.x its injection no longer
 * matches the compiled method (it expects an {@code ItemStack} local where there is an {@code Item}), so
 * Mixin replaces the handler with a stub that THROWS on every call. The trade can therefore never produce an
 * offer; all it does is blow up while a villager builds its trade list, which takes the trading player's
 * client down with it. Neruina catches it server-side, which is why the host survives and only the client
 * dies.
 *
 * <p>Any villager reaching that trade triggers it, in a village as much as anywhere else. The Lucky Block
 * Pink villager just makes it near-certain: it spawns at level 2-5 with a random profession, so it builds its
 * high-level trades immediately.
 *
 * <p>This wraps that one trade so a failure becomes "no offer" instead of an exception. Deliberately a
 * wrapper and not a removal: nothing of value is lost either way (the trade cannot work today), but if the
 * mixin is ever fixed upstream the delegate simply starts succeeding again and the trade comes back on its
 * own. Matched by class name because vanilla's listing class is package-private -- class names are readable
 * at runtime under Forge, only members are obfuscated.
 *
 * <p>Upstream is a dead end: Sinytra closed the report as "not planned"/invalid (the bug is Fabric's), and
 * Forge 47.3.12 was the last build where the injection still applied.
 */
@Mod.EventBusSubscriber(modid = LuckyTweaksMod.MODID)
public final class VillagerTradeGuard {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Vanilla's biome-dependent trade, the one Fabric API's mixin breaks. */
    private static final String BROKEN_LISTING =
            "net.minecraft.world.entity.npc.VillagerTrades$EmeraldsForVillagerTypeItem";

    /** Log the underlying failure once per run: it fires per villager, and the cause never changes. */
    private static boolean reported;

    private VillagerTradeGuard() {}

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        final Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();
        // Iterate a COPY of the keys and swap whole lists in, rather than editing each list in place: nothing
        // promises these lists are mutable (a fixed-size or immutable one would throw here and take the
        // server down at startup -- a worse bug than the one being fixed). The replacement is an ArrayList,
        // so later handlers can still add their own trades.
        for (int level : trades.keySet().toIntArray()) {
            final List<VillagerTrades.ItemListing> listings = trades.get(level);
            if (listings == null || listings.isEmpty()) {
                continue;
            }
            List<VillagerTrades.ItemListing> guarded = null;
            for (int i = 0; i < listings.size(); i++) {
                final VillagerTrades.ItemListing listing = listings.get(i);
                if (listing == null || !BROKEN_LISTING.equals(listing.getClass().getName())) {
                    continue;
                }
                if (guarded == null) {
                    guarded = new ArrayList<>(listings);
                }
                guarded.set(i, new Guarded(listing));
            }
            if (guarded != null) {
                trades.put(level, guarded);
            }
        }
    }

    /** Passes the trade through, turning a thrown offer into no offer at all. */
    private record Guarded(VillagerTrades.ItemListing delegate) implements VillagerTrades.ItemListing {
        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            try {
                return delegate.getOffer(trader, random);
            } catch (Throwable t) {
                if (!reported) {
                    reported = true;
                    LOGGER.warn("A villager's biome-dependent trade is broken by another mod's mixin; "
                            + "dropping that one trade so it cannot crash trading players.", t);
                }
                return null;
            }
        }
    }
}
