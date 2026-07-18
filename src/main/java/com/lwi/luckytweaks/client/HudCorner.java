package com.lwi.luckytweaks.client;

import net.minecraft.client.resources.language.I18n;

/** The four screen corners a HUD element can anchor to (kept simple: the user asked for corners). */
public enum HudCorner {
    TOP_LEFT(false, false),
    TOP_RIGHT(true, false),
    BOT_LEFT(false, true),
    BOT_RIGHT(true, true);

    public final boolean right;
    public final boolean bottom;

    HudCorner(boolean right, boolean bottom) {
        this.right = right;
        this.bottom = bottom;
    }

    public HudCorner next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public String label() {
        return I18n.get(switch (this) {
            case TOP_LEFT -> "luckytweaks.gui.corner_top_left";
            case TOP_RIGHT -> "luckytweaks.gui.corner_top_right";
            case BOT_LEFT -> "luckytweaks.gui.corner_bot_left";
            case BOT_RIGHT -> "luckytweaks.gui.corner_bot_right";
        });
    }

    public static HudCorner byName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return BOT_RIGHT;
        }
    }
}
