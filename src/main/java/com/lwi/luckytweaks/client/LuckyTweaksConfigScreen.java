package com.lwi.luckytweaks.client;

import com.lwi.luckytweaks.DisabledBlocks;
import com.lwi.luckytweaks.TweaksClientConfig;
import com.lwi.luckytweaks.TweaksConfig;
import com.lwi.luckytweaks.seal.SealService;
import com.lwi.luckytweaks.util.LuckyBlocks;
import com.lwi.luckytweaks.util.WorldGenInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lucky Tweaks' config screen. Tab "Settings" holds the simple toggles + spawn-rate slider; tab
 * "Lucky Blocks" is an auto-detected, clickable LIST of every lucky block in the game.
 *
 * <p>Clicking a forceable block opens a {@link LuckyBlockPageScreen} where you can switch it off
 * entirely or tune its spawn per dimension (a "spawn here" tick plus a "1 in N" frequency for each of
 * Overworld / Nether / End). A block is FORCEABLE iff its registry namespace is {@code "lucky"} -- the
 * base mod and its addons, whose world-gen feature can run in every dimension. Lucky-likes from other
 * mods (e.g. {@code fuze_relics:lucky_blockling}) have no controllable natural spawn, so they show
 * greyed and non-interactive.
 *
 * <p>Each block's editable state lives in a small {@link State} object built once in the constructor
 * and SHARED BY REFERENCE with the page screen, so edits survive navigating in and out. {@link #save()}
 * walks those states and writes the {@code disabledLuckyBlocks} + {@code spawnRules} config lists.
 */
public class LuckyTweaksConfigScreen extends Screen {
    private static final int TAB_Y = 22;
    private static final int LEGEND_Y = 44;
    private static final int LIST_TOP = 56;
    private static final int ROW_W = 300;
    private static final int NAME_W = 250;

    static final String[] DIM_IDS = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};
    private static final String[] DIM_SHORT_KEYS = {
            "luckytweaks.gui.dim_short_overworld", "luckytweaks.gui.dim_short_nether", "luckytweaks.gui.dim_short_end"};
    private static final int MEDIUM = 200; // default "1 in N" for a dimension a block doesn't natively use

    private final Screen parent;

    private boolean weaponFix;
    private boolean fusion;
    private boolean crocodile;
    private double multiplier;
    // Lives tab (base/per-player = COMMON; heart style + HUD position = CLIENT).
    private int livesBase;
    private int livesPerPlayer;
    private String heartStyle;
    private String hudCorner;
    private int hudX;
    private int hudY;
    private final List<String> blockIds = new ArrayList<>();
    private final Map<String, Block> blockById = new HashMap<>();
    private final Map<String, Set<String>> nativeById = new HashMap<>(); // id -> native dimension ids (cached for save/reset)
    private final Map<String, State> states = new HashMap<>();           // forceable blocks only ("lucky" namespace)

    private int activeTab = 0;

    private Checkbox weaponBox;
    private Checkbox fusionBox;
    private Checkbox crocodileBox;
    private MultiplierSlider slider;
    private IntSlider baseSlider;
    private IntSlider perPlayerSlider;
    private IntSlider xSlider;
    private IntSlider ySlider;
    private BlockList list;

    public LuckyTweaksConfigScreen(Screen parent) {
        super(Component.translatable("luckytweaks.gui.title"));
        this.parent = parent;
        this.weaponFix = TweaksConfig.FIX_LUCKY_WEAPONS.get();
        this.fusion = TweaksConfig.ENABLE_LUCK_FUSION.get();
        this.crocodile = TweaksConfig.FIX_CROCODILE.get();
        this.multiplier = TweaksConfig.LUCKY_BLOCK_SPAWN_MULTIPLIER.get();
        this.livesBase = TweaksConfig.SHARED_LIVES_BASE.get();
        this.livesPerPlayer = TweaksConfig.SHARED_LIVES_PER_PLAYER.get();
        this.heartStyle = TweaksClientConfig.CLIENT.livesHeartStyle.get();
        this.hudCorner = TweaksClientConfig.CLIENT.livesHudCorner.get();
        this.hudX = TweaksClientConfig.CLIENT.livesHudX.get();
        this.hudY = TweaksClientConfig.CLIENT.livesHudY.get();

        List<Block> blocks = new ArrayList<>();
        for (Block block : ForgeRegistries.BLOCKS) {
            if (LuckyBlocks.isLuckyBlock(block) && ForgeRegistries.BLOCKS.getKey(block) != null) {
                blocks.add(block);
            }
        }
        blocks.sort(Comparator.comparing(b -> String.valueOf(ForgeRegistries.BLOCKS.getKey(b))));
        for (Block block : blocks) {
            String id = ForgeRegistries.BLOCKS.getKey(block).toString();
            this.blockIds.add(id);
            this.blockById.put(id, block);
            Set<String> nat = WorldGenInfo.nativeDims(id);
            this.nativeById.put(id, nat);
            // Only "lucky"-namespace blocks are forceable: their world-gen feature runs in every
            // dimension, so we can both block and force per dimension. Others get no editable state.
            if (forceable(id)) {
                this.states.put(id, buildState(id, nat));
            }
        }
    }

    /** A block is configurable iff its registry namespace is exactly "lucky" (base mod + addons). */
    private static boolean forceable(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 && id.substring(0, colon).equals(LuckyBlocks.LUCKY_NAMESPACE);
    }

    /** Seed a block's editable state from the live config + its native world-gen footprint. */
    private static State buildState(String id, Set<String> nat) {
        State s = new State(id);
        s.disabled = DisabledBlocks.view().contains(id);
        for (int i = 0; i < 3; i++) {
            Integer rule = DisabledBlocks.spawnRule(id, DIM_IDS[i]); // null=default, 0=blocked, >=1="1 in N"
            boolean isNat = nat.contains(DIM_IDS[i]);
            if (rule != null) {
                s.dimEnabled[i] = rule > 0;
                s.dimRate[i] = rule > 0 ? rule : (isNat ? WorldGenInfo.nativeRate(id, DIM_IDS[i]) : MEDIUM);
            } else {
                s.dimEnabled[i] = isNat;
                s.dimRate[i] = isNat ? WorldGenInfo.nativeRate(id, DIM_IDS[i]) : MEDIUM;
            }
        }
        return s;
    }

    @Override
    protected void init() {
        int tabW = 100;
        int gap = 4;
        int startX = this.width / 2 - (3 * tabW + 2 * gap) / 2;
        String[] tabKeys = {"luckytweaks.gui.tab_settings", "luckytweaks.gui.tab_lucky_blocks", "luckytweaks.gui.tab_lives"};
        for (int t = 0; t < tabKeys.length; t++) {
            final int tab = t;
            Button tabBtn = Button.builder(Component.translatable(tabKeys[t]), b -> switchTab(tab))
                    .bounds(startX + t * (tabW + gap), TAB_Y, tabW, 20).build();
            tabBtn.active = this.activeTab != t;
            this.addRenderableWidget(tabBtn);
        }

        this.weaponBox = null;
        this.fusionBox = null;
        this.crocodileBox = null;
        this.slider = null;
        this.baseSlider = null;
        this.perPlayerSlider = null;
        this.xSlider = null;
        this.ySlider = null;
        this.list = null;

        if (this.activeTab == 0) {
            int x = this.width / 2 - 150;
            this.weaponBox = new Checkbox(x, 58, 300, 20,
                    Component.translatable("luckytweaks.gui.safer_weapons"), this.weaponFix, true);
            this.fusionBox = new Checkbox(x, 84, 300, 20,
                    Component.translatable("luckytweaks.gui.lucky_block_fusion"), this.fusion, true);
            this.crocodileBox = new Checkbox(x, 110, 300, 20,
                    Component.translatable("luckytweaks.gui.crocodile_items"), this.crocodile, true);
            // Row 136 used to hold the Player Revive switch; co-op revive is no longer optional (1.3), so
            // the slider moves up into its place rather than leaving a hole in the column.
            this.slider = new MultiplierSlider(x, 136, 300, 20, this.multiplier);
            this.addRenderableWidget(this.weaponBox);
            this.addRenderableWidget(this.fusionBox);
            this.addRenderableWidget(this.crocodileBox);
            this.addRenderableWidget(this.slider);
        } else if (this.activeTab == 1) {
            this.list = new BlockList(this.minecraft, this.width, this.height, LIST_TOP, this.height - 36, 24);
            for (String id : this.blockIds) {
                this.list.addEntryPublic(new BlockEntry(this.blockById.get(id), this.states.get(id)));
            }
            this.addWidget(this.list);
        } else {
            int x = this.width / 2 - 150;
            this.baseSlider = new IntSlider(x, 64, 300, 20, "luckytweaks.gui.lives_base", 1, 10, this.livesBase);
            this.perPlayerSlider = new IntSlider(x, 88, 300, 20, "luckytweaks.gui.lives_per_player", 0, 10, this.livesPerPlayer);
            Button heartBtn = Button.builder(heartMsg(), b -> {
                this.heartStyle = LivesHeartStyles.next(this.heartStyle);
                b.setMessage(heartMsg());
            }).bounds(x, 112, 300, 20).build();
            Button cornerBtn = Button.builder(cornerMsg(), b -> {
                this.hudCorner = HudCorner.byName(this.hudCorner).next().name();
                b.setMessage(cornerMsg());
            }).bounds(x, 136, 300, 20).build();
            this.xSlider = new IntSlider(x, 160, 148, 20, "luckytweaks.gui.hud_x", -200, 200, this.hudX);
            this.ySlider = new IntSlider(x + 152, 160, 148, 20, "luckytweaks.gui.hud_y", -200, 200, this.hudY);
            this.addRenderableWidget(this.baseSlider);
            this.addRenderableWidget(this.perPlayerSlider);
            this.addRenderableWidget(heartBtn);
            this.addRenderableWidget(cornerBtn);
            this.addRenderableWidget(this.xSlider);
            this.addRenderableWidget(this.ySlider);
        }

        this.addRenderableWidget(Button.builder(Component.translatable("luckytweaks.gui.reset_defaults"), b -> resetDefaults())
                .bounds(this.width / 2 - 154, this.height - 28, 150, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(this.width / 2 + 4, this.height - 28, 150, 20).build());
    }

    private void switchTab(int tab) {
        captureCurrentTab();
        this.activeTab = tab;
        this.rebuildWidgets();
    }

    /**
     * Capture the simple-tab widgets back into our fields. The Lucky Blocks list needs no capture: its
     * rows hold no editable widget of their own (each block's edits live in its shared {@link State},
     * mutated by the page screen).
     */
    private void captureCurrentTab() {
        if (this.weaponBox != null) {
            this.weaponFix = this.weaponBox.selected();
        }
        if (this.fusionBox != null) {
            this.fusion = this.fusionBox.selected();
        }
        if (this.crocodileBox != null) {
            this.crocodile = this.crocodileBox.selected();
        }
        if (this.slider != null) {
            this.multiplier = this.slider.multiplier();
        }
        if (this.baseSlider != null) {
            this.livesBase = this.baseSlider.value();
        }
        if (this.perPlayerSlider != null) {
            this.livesPerPlayer = this.perPlayerSlider.value();
        }
        if (this.xSlider != null) {
            this.hudX = this.xSlider.value();
        }
        if (this.ySlider != null) {
            this.hudY = this.ySlider.value();
        }
    }

    private Component heartMsg() {
        return Component.translatable("luckytweaks.gui.heart_colour", capitalize(this.heartStyle));
    }

    private Component cornerMsg() {
        return Component.translatable("luckytweaks.gui.corner", HudCorner.byName(this.hudCorner).label());
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void resetDefaults() {
        this.weaponFix = true;
        this.fusion = true;
        this.crocodile = true;
        this.multiplier = 1.0;
        this.livesBase = 1;         // co-op = this PLUS livesPerPlayer per player
        this.livesPerPlayer = 1;
        this.heartStyle = "emerald";
        this.hudCorner = "BOT_RIGHT";
        this.hudX = -6;
        this.hudY = -24;
        for (Map.Entry<String, State> e : this.states.entrySet()) {
            Set<String> nat = this.nativeById.get(e.getKey());
            State s = e.getValue();
            s.disabled = false;
            for (int i = 0; i < 3; i++) {
                boolean isNat = nat.contains(DIM_IDS[i]);
                s.dimEnabled[i] = isNat;
                s.dimRate[i] = isNat ? WorldGenInfo.nativeRate(e.getKey(), DIM_IDS[i]) : MEDIUM;
            }
        }
        this.rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partial) {
        this.renderBackground(gg);
        if (this.list != null) {
            this.list.render(gg, mouseX, mouseY, partial);
        }
        super.render(gg, mouseX, mouseY, partial);
        gg.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        if (this.activeTab == 1) {
            gg.drawCenteredString(this.font,
                    Component.translatable("luckytweaks.gui.blocks_hint"),
                    this.width / 2, LEGEND_Y, 0xA0A0A0);
        } else if (this.activeTab == 2) {
            // Live preview at the REAL on-screen position, from the current (unsaved) widget values:
            // dragging X/Y moves the hearts, cycling the corner jumps them, the colour recolours -- all
            // before Done. Reads the live widgets (not the captured fields) so it tracks a drag in progress.
            // The COUNT matches the situation the player is actually in, so it mirrors the real HUD: alone,
            // the flat solo allowance; in co-op, the slider's base lives PLUS the per-player slider times
            // the team's high-water mark (which the server syncs with the hearts). Bought lives are left out
            // on purpose -- this previews the settings, not the run's current luck.
            int base = this.baseSlider != null ? this.baseSlider.value() : this.livesBase;
            int per = this.perPlayerSlider != null ? this.perPlayerSlider.value() : this.livesPerPlayer;
            int total = Math.max(1, base + (isMultiplayerNow() ? per * SharedLivesHud.peakPlayers() : 0));
            int offX = this.xSlider != null ? this.xSlider.value() : this.hudX;
            int offY = this.ySlider != null ? this.ySlider.value() : this.hudY;
            SharedLivesHud.drawPositioned(gg, HudCorner.byName(this.hudCorner), offX, offY,
                    this.width, this.height, total, total, this.heartStyle);
        }
    }

    /**
     * Whether this run counts as a multiplayer one. Read from the server's own verdict (synced with the
     * hearts), never re-derived here: opening a solo world to LAN just for the commands is still a solo
     * run, so a client-side {@code isPublished()} check would preview the wrong allowance.
     */
    private boolean isMultiplayerNow() {
        return SharedLivesHud.isMultiplayerRun();
    }

    @Override
    public void onClose() {
        captureCurrentTab();
        save();
        // Return to the parent (the Catalogue/Configured mods screen) exactly the way every other Forge
        // config screen does -- a plain synchronous setScreen. Our sibling OSConfigScreen returns this
        // way to the same parent with no ill effect. The earlier deferred mc.execute(...) hand-off was
        // what left the mods list half-rendered (only its scroll column drawn) on the way back, so the
        // deferral is gone.
        this.minecraft.setScreen(this.parent);
    }

    private void save() {
        TweaksConfig.FIX_LUCKY_WEAPONS.set(this.weaponFix);
        TweaksConfig.ENABLE_LUCK_FUSION.set(this.fusion);
        TweaksConfig.FIX_CROCODILE.set(this.crocodile);
        TweaksConfig.LUCKY_BLOCK_SPAWN_MULTIPLIER.set(this.multiplier);
        TweaksConfig.SHARED_LIVES_BASE.set(this.livesBase);
        TweaksConfig.SHARED_LIVES_PER_PLAYER.set(this.livesPerPlayer);

        // Heart colour + HUD position are CLIENT config (personal), saved to the client spec.
        TweaksClientConfig.CLIENT.livesHeartStyle.set(this.heartStyle);
        TweaksClientConfig.CLIENT.livesHudCorner.set(this.hudCorner);
        TweaksClientConfig.CLIENT.livesHudX.set(this.hudX);
        TweaksClientConfig.CLIENT.livesHudY.set(this.hudY);
        TweaksClientConfig.CLIENT_SPEC.save();

        List<String> disabledList = new ArrayList<>();
        List<String> rulesList = new ArrayList<>();
        for (String id : this.blockIds) {
            State s = this.states.get(id);
            if (s == null) {
                continue; // non-forceable block: nothing we can write for it
            }
            Set<String> nat = this.nativeById.get(id);
            if (s.disabled) {
                disabledList.add(id);
                continue;
            }
            for (int i = 0; i < 3; i++) {
                boolean isNat = nat.contains(DIM_IDS[i]);
                int nr = isNat ? WorldGenInfo.nativeRate(id, DIM_IDS[i]) : -1;
                int rate = Math.max(1, s.dimRate[i]);
                if (!s.dimEnabled[i]) {
                    // Blocking a native dimension needs an explicit "=0"; a non-native dim that's off is
                    // already its default (no spawn), so it needs no entry.
                    if (isNat) {
                        rulesList.add(id + "@" + DIM_IDS[i] + "=0");
                    }
                } else {
                    // Write a rule only when it differs from nature: forcing a non-native dim, or
                    // overriding a native dim's rate. Native-at-native-rate is the default -- skip it.
                    if (!isNat || rate != nr) {
                        rulesList.add(id + "@" + DIM_IDS[i] + "=" + rate);
                    }
                }
            }
        }
        TweaksConfig.DISABLED_LUCKY_BLOCKS.set(disabledList);
        TweaksConfig.SPAWN_RULES.set(rulesList);
        TweaksConfig.COMMON_SPEC.save();
        DisabledBlocks.refresh();
        // Forge doesn't reliably refire ModConfigEvent on a programmatic save, so update the run seal now.
        if (SealService.statsLoaded()) {
            SealService.onConfigChanged();
        }
    }

    /** Spawn-rate slider mapping the config's 1.0-3.0 range onto the widget's 0-1 value. */
    private static final class MultiplierSlider extends AbstractSliderButton {
        MultiplierSlider(int x, int y, int w, int h, double multiplier) {
            super(x, y, w, h, Component.empty(), (Math.max(1.0, Math.min(3.0, multiplier)) - 1.0) / 2.0);
            updateMessage();
        }

        double multiplier() {
            return Math.round((1.0 + this.value * 2.0) * 20.0) / 20.0; // 0.05 steps
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("luckytweaks.gui.spawn_rate", String.format("%.2f", multiplier())));
        }

        @Override
        protected void applyValue() {
            // Read from the slider on save; nothing to push live.
        }
    }

    /** A labelled integer slider over {@code [min, max]} (lives counts and HUD pixel offsets). */
    private static final class IntSlider extends AbstractSliderButton {
        private final String labelKey; // translation key whose single %s argument is the current value
        private final int min;
        private final int max;

        IntSlider(int x, int y, int w, int h, String labelKey, int min, int max, int val) {
            super(x, y, w, h, Component.empty(),
                    (double) (Math.max(min, Math.min(max, val)) - min) / (max - min));
            this.labelKey = labelKey;
            this.min = min;
            this.max = max;
            updateMessage();
        }

        int value() {
            return this.min + (int) Math.round(this.value * (this.max - this.min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(this.labelKey, value()));
        }

        @Override
        protected void applyValue() {
            // value() derives from the slider position on demand; nothing to push live.
        }
    }

    /**
     * One lucky block's editable state, SHARED BY REFERENCE with {@link LuckyBlockPageScreen} so the
     * page's edits land straight in this screen's model. {@code disabled} switches the block off
     * entirely; otherwise {@code dimEnabled[i]} / {@code dimRate[i]} carry the per-dimension spawn and
     * its "1 in N" frequency, indexed by {@link #DIM_IDS}.
     */
    public static final class State {
        public final String id;
        public boolean disabled;
        public boolean[] dimEnabled = new boolean[3];
        public int[] dimRate = new int[3];

        State(String id) {
            this.id = id;
        }
    }

    /**
     * A clickable list row. A forceable block shows its icon + name (plus a tiny spawn summary) and
     * opens its {@link LuckyBlockPageScreen} when clicked anywhere on the row; a non-forceable
     * lucky-like is greyed and inert (it has no controllable natural spawn). We handle the whole-row
     * click in {@link #mouseClicked} rather than nesting a button, so there's no button background
     * drawn behind every row.
     */
    private final class BlockEntry extends ContainerObjectSelectionList.Entry<BlockEntry> {
        private final Block block;
        private final ItemStack icon;
        private final String name;
        private final State state;        // null => non-forceable, non-interactive

        BlockEntry(Block block, State state) {
            this.block = block;
            this.icon = new ItemStack(block);
            this.state = state;
            String raw = block.getName().getString();
            // Non-forceable lucky-likes (cross-mod, no controllable world-gen) get the suffix; the
            // suffix AND the dimmed colour below both say "not editable" so meaning never rests on hue.
            this.name = state != null ? fit(raw, NAME_W) : fit(I18n.get("luckytweaks.gui.unsupported", raw), NAME_W);
        }

        /** "spawns: O N E" with the dims this block is currently set to generate in (or off/disabled). */
        private String spawnSummary(State s) {
            if (s.disabled) {
                return I18n.get("luckytweaks.gui.summary_disabled");
            }
            StringBuilder sb = new StringBuilder();
            boolean any = false;
            for (int i = 0; i < 3; i++) {
                if (s.dimEnabled[i]) {
                    if (any) {
                        sb.append(' ');
                    }
                    sb.append(I18n.get(DIM_SHORT_KEYS[i]));
                    any = true;
                }
            }
            return any ? I18n.get("luckytweaks.gui.summary_spawns", sb.toString())
                    : I18n.get("luckytweaks.gui.summary_off");
        }

        private String fit(String s, int max) {
            return LuckyTweaksConfigScreen.this.font.width(s) <= max
                    ? s
                    : LuckyTweaksConfigScreen.this.font.plainSubstrByWidth(s, max - 6) + "…";
        }

        @Override
        public void render(GuiGraphics gg, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partial) {
            // A faint highlight on hover marks the row as a click target (forceable rows only). Hover
            // is light-on-dark, never colour-coded, so it reads for a colour-blind player.
            if (this.state != null && hovered) {
                gg.fill(left, top, left + width, top + height - 2, 0x33FFFFFF);
            }
            // Forceable lucky blocks have a normal item model, so we render the item. Cross-mod
            // lucky-likes drawn by a block-entity renderer (e.g. fuze_relics:lucky_blockling) have an
            // INVISIBLE block/item model -- renderItem would draw nothing -- so for those we blit the
            // block's particle sprite instead (the blockling's is its own 16x16 face texture). Either
            // way a broken model/renderer must never break the list render: that would leak the scroll
            // list's scissor and corrupt the screen we return to (the mods list), so the draw is guarded.
            try {
                if (this.state != null) {
                    gg.renderItem(this.icon, left, top + 2);
                } else {
                    TextureAtlasSprite sprite = LuckyTweaksConfigScreen.this.minecraft.getBlockRenderer()
                            .getBlockModel(this.block.defaultBlockState()).getParticleIcon(ModelData.EMPTY);
                    gg.blit(left, top + 2, 0, 16, 16, sprite);
                }
            } catch (Throwable ignored) {
                // no icon for this row, but the row (and everything after) still renders
            }
            if (this.state != null) {
                gg.drawString(LuckyTweaksConfigScreen.this.font, this.name, left + 22, top + 2, 0xFFFFFF, false);
                gg.drawString(LuckyTweaksConfigScreen.this.font,
                        Component.literal(spawnSummary(this.state)), left + 22, top + 12, 0x909090, false);
            } else {
                gg.drawString(LuckyTweaksConfigScreen.this.font, this.name, left + 22, top + 7, 0x808080, false);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.state != null && button == 0) {
                LuckyTweaksConfigScreen.this.minecraft.setScreen(
                        new LuckyBlockPageScreen(LuckyTweaksConfigScreen.this, this.block, this.state));
                return true;
            }
            return false;
        }

        // No child widgets: the row IS the control. Empty lists keep the list's focus/narration happy.
        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    /** Scrollable container for the {@link BlockEntry} rows. */
    private static final class BlockList extends ContainerObjectSelectionList<BlockEntry> {
        BlockList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
            super(mc, width, height, top, bottom, itemHeight);
        }

        void addEntryPublic(BlockEntry entry) {
            this.addEntry(entry);
        }

        @Override
        public int getRowWidth() {
            return ROW_W;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.width / 2 + 158;
        }
    }
}
