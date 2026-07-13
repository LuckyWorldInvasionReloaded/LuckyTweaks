package com.lwi.luckytweaks.client;

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
        return switch (this) {
            case TOP_LEFT -> "Top-Left";
            case TOP_RIGHT -> "Top-Right";
            case BOT_LEFT -> "Bottom-Left";
            case BOT_RIGHT -> "Bottom-Right";
        };
    }

    public static HudCorner byName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return BOT_RIGHT;
        }
    }
}
