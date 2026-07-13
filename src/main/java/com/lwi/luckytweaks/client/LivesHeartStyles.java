package com.lwi.luckytweaks.client;

/**
 * The selectable heart colours for the shared-lives HUD, in display/cycle order. Each name maps to a
 * column in {@code textures/gui/lives_hearts.png} (column 0 is the empty container, colours start at 1).
 * Adding a colour = add a 9px column to the sheet and a name here.
 */
public final class LivesHeartStyles {
    public static final String[] ORDER =
            {"silver", "gold", "amethyst", "emerald", "sapphire", "ruby", "rose", "onyx"};

    private LivesHeartStyles() {}

    /** The sheet column (in 9px units) of a style's full heart; falls back to silver for an unknown name. */
    public static int column(String style) {
        return indexOf(style) + 1;      // +1: column 0 is the empty container
    }

    public static String next(String style) {
        return ORDER[(indexOf(style) + 1) % ORDER.length];
    }

    private static int indexOf(String style) {
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i].equals(style)) {
                return i;
            }
        }
        return 0;                       // unknown -> silver
    }
}
