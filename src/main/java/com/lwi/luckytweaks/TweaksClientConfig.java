package com.lwi.luckytweaks;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * CLIENT config ({@code config/luckytweaks-client.toml}): purely personal HUD preferences that must
 * NOT be server-synced. Right now just the shared-lives hearts — their colour and where on the screen
 * they sit (a corner + a pixel offset, edited from the Lucky Tweaks config screen). Client, not common,
 * because two players on a server each want their own placement.
 */
public final class TweaksClientConfig {
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        Pair<Client, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = pair.getLeft();
        CLIENT_SPEC = pair.getRight();
    }

    private TweaksClientConfig() {}

    public static final class Client {
        public final ForgeConfigSpec.ConfigValue<String> livesHeartStyle;
        public final ForgeConfigSpec.ConfigValue<String> livesHudCorner;
        public final ForgeConfigSpec.IntValue livesHudX;
        public final ForgeConfigSpec.IntValue livesHudY;

        Client(ForgeConfigSpec.Builder b) {
            b.comment("Shared-lives HUD: the row of hearts showing the team's remaining lives.")
                    .push("livesHud");
            livesHeartStyle = b.comment(
                            "Heart colour. One of: silver, gold, amethyst, emerald, sapphire, ruby, rose, onyx.")
                    .define("heartStyle", "emerald");
            livesHudCorner = b.comment(
                            "Which screen corner the hearts anchor to: TOP_LEFT, TOP_RIGHT, BOT_LEFT, BOT_RIGHT.")
                    .define("corner", "BOT_RIGHT");
            livesHudX = b.comment(
                            "Horizontal pixel offset from the corner (negative pulls left, away from a right edge).")
                    .defineInRange("offsetX", -6, -10000, 10000);
            livesHudY = b.comment(
                            "Vertical pixel offset from the corner (negative lifts up, away from a bottom edge).",
                            "The default -24 sits the hearts just above Improved Mobs' bottom-right difficulty text.")
                    .defineInRange("offsetY", -24, -10000, 10000);
            b.pop();
        }
    }
}
