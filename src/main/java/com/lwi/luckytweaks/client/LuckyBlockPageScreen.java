package com.lwi.luckytweaks.client;

import com.lwi.luckytweaks.util.WorldGenInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.Set;

/**
 * Per-block detail page reached by clicking a row on the Lucky Tweaks "Lucky Blocks" tab. Lets the
 * player switch this one lucky block off entirely, or tune its spawn per dimension: a "spawn here"
 * tick plus a "1 in N" frequency (Frequent / Medium / Rare presets, or a free number) for each of
 * Overworld / Nether / End.
 *
 * <p>The {@link LuckyTweaksConfigScreen.State} is SHARED BY REFERENCE with the list screen, so every
 * edit here lands straight in that screen's model and is written out when it saves. We capture the
 * live widget values back into the state on close.
 *
 * <p>The screen never relies on colour to convey state (the user is colour-blind): meaning is carried
 * by the checkbox ticks, the "(forced)" text note on non-native dimensions, and greyed-out (inactive)
 * controls, all of which read on light-on-dark contrast alone.
 */
public final class LuckyBlockPageScreen extends Screen {
    private static final String[] DIM_IDS = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};
    private static final String[] DIM_LABELS = {"Overworld", "Nether", "End"};
    private static final int FREQUENT = 80;
    private static final int MEDIUM = 200;
    private static final int RARE = 500;

    private static final int CONTENT_W = 320;
    private static final int ROW_H = 48;     // vertical space a single dimension block occupies

    private final Screen parent;
    private final LuckyTweaksConfigScreen.State state;
    private final ItemStack icon;
    private final Set<String> nativeDims;

    private Checkbox disableBox;
    private final Checkbox[] dimBoxes = new Checkbox[3];
    private final EditBox[] dimRates = new EditBox[3];
    private final Button[][] dimPresets = new Button[3][3]; // [dim][Frequent, Medium, Rare]

    public LuckyBlockPageScreen(Screen parent, Block block, LuckyTweaksConfigScreen.State state) {
        super(block.getName());
        this.parent = parent;
        this.state = state;
        this.icon = new ItemStack(block); // the block itself is only needed here, for the title + icon
        this.nativeDims = WorldGenInfo.nativeDims(state.id);
    }

    @Override
    protected void init() {
        int left = this.width / 2 - CONTENT_W / 2;
        int y = 40;

        // Master "off" switch. While ticked, every per-dimension control below is greyed and ignored.
        this.disableBox = new Checkbox(left, y, CONTENT_W, 20,
                Component.literal("Disable entirely (no spawn, breaks inert)"), this.state.disabled, true);
        this.addRenderableWidget(this.disableBox);
        y += 28;

        for (int i = 0; i < 3; i++) {
            final int dim = i;
            boolean isNative = this.nativeDims.contains(DIM_IDS[i]);
            // A non-native dimension is forced on when ticked; flag it so the player knows ticking it
            // makes the block spawn somewhere it normally wouldn't.
            String label = isNative ? DIM_LABELS[i] : DIM_LABELS[i] + " (forced)";
            this.dimBoxes[i] = new Checkbox(left, y, CONTENT_W, 20,
                    Component.literal(label + " - spawn here"), this.state.dimEnabled[i], true);
            this.addRenderableWidget(this.dimBoxes[i]);

            int by = y + 22;
            // Three frequency presets feeding the same rate field (and the field stays editable for any N).
            this.dimPresets[i][0] = preset(left, by, "Frequent", dim, FREQUENT);
            this.dimPresets[i][1] = preset(left + 66, by, "Medium", dim, MEDIUM);
            this.dimPresets[i][2] = preset(left + 132, by, "Rare", dim, RARE);

            EditBox rate = new EditBox(this.font, left + 200, by, 44, 20, Component.empty());
            rate.setFilter(s -> s.matches("\\d*"));
            rate.setValue(Integer.toString(Math.max(1, this.state.dimRate[dim])));
            rate.setResponder(s -> {
                int n = 1;
                try {
                    n = Integer.parseInt(s);
                } catch (NumberFormatException ignored) {
                    // empty or mid-edit -- keep the floor of 1 until a real number is typed
                }
                this.state.dimRate[dim] = Math.max(1, n);
            });
            this.dimRates[i] = rate;
            this.addRenderableWidget(rate);

            y += ROW_H;
        }

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(this.width / 2 - 75, this.height - 28, 150, 20).build());

        refreshEnabled();
    }

    /** A frequency preset button: sets the row's rate (state + EditBox) to a fixed "1 in N". */
    private Button preset(int x, int y, String text, int dim, int rate) {
        Button b = Button.builder(Component.literal(text), btn -> {
            this.state.dimRate[dim] = rate;
            this.dimRates[dim].setValue(Integer.toString(rate)); // responder stores it back, kept in sync
        }).bounds(x, y, 62, 20).build();
        this.addRenderableWidget(b);
        return b;
    }

    /** Grey out per-dimension controls when the block is disabled or that dimension is unticked. */
    private void refreshEnabled() {
        boolean off = this.disableBox.selected();
        for (int i = 0; i < 3; i++) {
            this.dimBoxes[i].active = !off;
            boolean rowOn = !off && this.dimBoxes[i].selected();
            this.dimRates[i].active = rowOn;
            this.dimRates[i].setEditable(rowOn);
            for (Button preset : this.dimPresets[i]) {
                preset.active = rowOn;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        // A tick on the master switch or a dimension box flips what's editable -- re-grey live.
        refreshEnabled();
        return handled;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partial) {
        this.renderBackground(gg);
        super.render(gg, mouseX, mouseY, partial);

        // Title centred, with the block's icon just to its left. A broken third-party item renderer
        // must never break the page (it would leak the screen's render state), so guard the icon draw.
        gg.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        int titleW = this.font.width(this.title);
        try {
            gg.renderItem(this.icon, this.width / 2 - titleW / 2 - 22, 8);
        } catch (Throwable ignored) {
            // no icon, title still shows
        }

        // The small "1 in N (lower = more common)" hint sits to the right of each rate field.
        boolean off = this.disableBox.selected();
        for (int i = 0; i < 3; i++) {
            boolean rowOn = !off && this.dimBoxes[i].selected();
            int hintColor = rowOn ? 0xA0A0A0 : 0x707070; // dimmer when the row is inactive
            gg.drawString(this.font, Component.literal("1 in N (lower = more common)"),
                    this.dimRates[i].getX() + this.dimRates[i].getWidth() + 6, this.dimRates[i].getY() + 6,
                    hintColor, false);
        }
    }

    @Override
    public void onClose() {
        // Capture the live widget values into the shared state before leaving. The rate EditBoxes
        // already push through their responder; copy the checkboxes here too.
        this.state.disabled = this.disableBox.selected();
        for (int i = 0; i < 3; i++) {
            this.state.dimEnabled[i] = this.dimBoxes[i].selected();
            this.state.dimRate[i] = Math.max(1, this.state.dimRate[i]);
        }
        // Returning to our OWN list screen (not the external mods list) lays out fine inline -- the
        // deferred hand-off the list screen uses is only needed for the Catalogue/Configured parent.
        this.minecraft.setScreen(this.parent);
    }
}
