package com.lwi.luckytweaks.achievements;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

/**
 * The single advancement criterion behind every Lucky Tweaks achievement:
 * {@code "trigger": "luckytweaks:lucky_progress"} with {@code {"stat": "<key>", "min": <n>}}.
 *
 * <p><b>Why one generic trigger instead of one per achievement.</b> Every achievement in this mod is the
 * same shape — a counter from {@link AchievementData} reaching a threshold — so a trigger per tier would
 * be dozens of near-identical classes, and worse, adding a tier would mean recompiling the mod. With this
 * one, a new tier is a new JSON file, and a pack can add its own thresholds (or move ours) through a
 * datapack without touching the jar.
 *
 * <p>{@code min} may be NEGATIVE, for the curse side of the ladder: a negative threshold matches when the
 * value is at or below it (crafting a -100 block satisfies {@code min: -100}). A positive one matches at
 * or above, as usual.
 */
public class LuckyProgressTrigger extends SimpleCriterionTrigger<LuckyProgressTrigger.Instance> {
    private final ResourceLocation id;

    public LuckyProgressTrigger(ResourceLocation id) {
        this.id = id;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    protected Instance createInstance(JsonObject json, ContextAwarePredicate player,
                                      DeserializationContext context) {
        String stat = GsonHelper.getAsString(json, "stat");
        int min = GsonHelper.getAsInt(json, "min", 1);
        return new Instance(this.id, player, stat, min);
    }

    /**
     * Offer a player's current value for {@code stat} to every advancement listening on it. Cheap enough
     * to call on every break: vanilla only walks the criteria this player has not completed yet.
     */
    public void fire(ServerPlayer player, String stat, int value) {
        this.trigger(player, instance -> instance.matches(stat, value));
    }

    public static class Instance extends AbstractCriterionTriggerInstance {
        private final String stat;
        private final int min;

        public Instance(ResourceLocation criterion, ContextAwarePredicate player, String stat, int min) {
            super(criterion, player);
            this.stat = stat;
            this.min = min;
        }

        boolean matches(String stat, int value) {
            if (!this.stat.equals(stat)) {
                return false;
            }
            return this.min < 0 ? value <= this.min : value >= this.min;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            JsonObject json = super.serializeToJson(context);
            json.addProperty("stat", this.stat);
            json.addProperty("min", this.min);
            return json;
        }
    }
}
