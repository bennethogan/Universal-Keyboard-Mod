package dev.bennethogan.universalkeyboard.client.screen;

import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import dev.bennethogan.universalkeyboard.sequencer.SequencerStep;
import dev.bennethogan.universalkeyboard.sequencer.SequencerStep.ConditionSource;
import dev.bennethogan.universalkeyboard.sequencer.SequencerStep.Type;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class SequencerScreen extends Screen {

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int PAD       = 6;
    private static final int PANEL_W   = 420;
    private static final int ROW_H     = 16;
    private static final int BTN_H     = 14;
    private static final int ROW_GAP   = 1;
    private static final int VISIBLE   = 10;
    private static final int MAX_STEPS = 100;
    private static final int DD_VIS      = 3;
    private static final int TYPE_DD_VIS = 4;
    private static final int SRC_DD_VIS  = 6;

    private static final int COL_NUM  = 4;
    private static final int COL_CH   = 16;
    private static final int COL_TYPE = 34;
    private static final int COL_CTX  = 108;
    private static final int COL_DEL  = 394;

    private static final String[]   OP_CAT_LABELS = {"Binary", "Unary", "Trig"};
    private static final String[][] OP_CAT_OPS    = {
        {"+", "-", "*", "/", "%", "pow", "min", "max"},
        {"abs", "neg", "round", "floor", "ceil"},
        {"sin", "cos", "tan", "asin", "acos", "atan", "atan2"}
    };
    private static final boolean[][] OP_CAT_UNARY = {
        {false, false, false, false, false, false, false, false},
        {true,  true,  true,  true,  true},
        {true,  true,  true,  true,  true,  true,  false}
    };
    private static final String[]   IF_OPS    = {">", ">=", "=", "<=", "<", "!="};
    private static final Direction[] RS_DIRS  = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};

    private record SrcCat(String label, List<String> items) {}

    // ── State ─────────────────────────────────────────────────────────────────
    final BlockPos keyboardPos;
    private final List<SequencerStep> steps;
    // Autosave/revert state
    private final List<SequencerStep> originalSteps;     // state when menu opened (for Revert)
    private List<CompoundTag>         savedSnapshot;     // last steps pushed to the server
    private List<CompoundTag>         lastTickSnapshot;  // previous tick, to debounce mid-edit autosaves
    private boolean running;
    private int     currentStep;
    private List<String>   availableGetters;
    private List<String[]> availableSetters;
    // per-channel getters/setters sent from server; fall back to channel-1 lists for legacy steps
    private Map<Integer, List<String>>   gettersByChannel = new java.util.HashMap<>();
    private Map<Integer, List<String[]>> settersByChannel = new java.util.HashMap<>();

    private int scrollOffset  = 0;
    private int activeRowIdx  = -1;

    // Insert-between hover: gap index 0 = before first visible row, 1 = between rows 0&1, etc.
    private int insertHoverGap = -1;

    // Type dropdown
    private int typeDropdownRow    = -1;
    private int typeDropdownScroll = 0;

    // Math source dropdown
    private int     mathDropdownRow    = -1;
    private boolean mathDropdownIsA   = true;
    private int     mathDropdownScroll = 0;

    // Load-file dropdown (drop-up)
    private boolean      loadDropdownOpen   = false;
    private List<String> loadDropdownFiles  = new ArrayList<>();
    private int          loadDropdownScroll = 0;

    private int  mathOpDropdownRow  = -1;
    private int  mathOpCatHover     = 0;

    private int     regressMultiRow    = -1;
    private boolean regressMultiIsA    = true;
    private int     regressMultiScroll = 0;

    // Source picker (nested dropdown: categories left, items right)
    private int     srcPickRow        = -1;
    private String  srcPickField      = "";
    private int     srcPickCat        = 0;
    private int     srcPickItemScroll = 0;
    private int     srcPickCh         = 1;
    private boolean srcPickManual     = false;
    private boolean srcPickVarsOnly   = false;
    private int     srcPickBtnX       = 0;

    // Overwrite confirmation
    private String  confirmOverwriteName = null;
    // No-name dialog
    private boolean showNoNameDialog = false;
    // Revert confirmation
    private ConfirmDialog revertDialog;

    // Layout cache (set in init)
    private int panelX, panelY, panelH, rowAreaY;
    private int bottomBtnW, bottomBtnY, loadBtnX;

    private final List<RowWidgets> rows = new ArrayList<>();
    private IconButton runStopBtn;
    private DarkButton starBtn;
    private EditBox saveName;

    boolean refreshing = false;

    // Favorite
    private boolean isFavorited = false;

    public void onFavoriteSync(dev.bennethogan.universalkeyboard.livecontrol.FavoriteScreen fav) {
        isFavorited = fav == dev.bennethogan.universalkeyboard.livecontrol.FavoriteScreen.SEQUENCER;
        if (starBtn != null) {
            starBtn.setAccentBg(isFavorited ? 0xFF3A3000 : -1);
            starBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    net.minecraft.network.chat.Component.translatable(
                            isFavorited ? "gui.universalkeyboard.tooltip.unfavorite"
                                        : "gui.universalkeyboard.tooltip.favorite")));
        }
    }

    // ── Construction ──────────────────────────────────────────────────────────

    public SequencerScreen(BlockPos keyboardPos, List<SequencerStep> steps,
            boolean running, int currentStep,
            List<String> availableGetters, List<String[]> availableSetters,
            Map<Integer, List<String>> gettersByChannel,
            Map<Integer, List<String[]>> settersByChannel) {
        super(Component.empty());
        this.keyboardPos       = keyboardPos;
        this.steps             = new ArrayList<>(steps);
        this.running           = running;
        this.currentStep       = currentStep;
        this.availableGetters  = new ArrayList<>(availableGetters);
        this.availableSetters  = new ArrayList<>(availableSetters);
        this.gettersByChannel  = new java.util.HashMap<>(gettersByChannel);
        this.settersByChannel  = new java.util.HashMap<>(settersByChannel);
        this.originalSteps     = deepCopySteps(this.steps);
        this.savedSnapshot     = snapshot(this.steps);
        this.lastTickSnapshot  = snapshot(this.steps);
    }

    private static SequencerStep copyStep(SequencerStep s) {
        return SequencerStep.load(s.save());
    }

    private static List<SequencerStep> deepCopySteps(List<SequencerStep> src) {
        List<SequencerStep> out = new ArrayList<>(src.size());
        for (SequencerStep s : src) out.add(copyStep(s));
        return out;
    }

    private static List<CompoundTag> snapshot(List<SequencerStep> src) {
        List<CompoundTag> out = new ArrayList<>(src.size());
        for (SequencerStep s : src) out.add(s.save());
        return out;
    }

    public BlockPos getKeyboardPos() { return keyboardPos; }

    public void updateProgress(boolean running, int currentStep) {
        this.running     = running;
        this.currentStep = currentStep;
        updateRunStopLabel();
    }

    public void updateState(List<SequencerStep> newSteps, boolean running, int currentStep,
                            List<String> getters, List<String[]> setters,
                            Map<Integer, List<String>> gettersByChannel,
                            Map<Integer, List<String[]>> settersByChannel) {
        this.running           = running;
        this.currentStep       = currentStep;
        this.availableGetters  = new ArrayList<>(getters);
        this.availableSetters  = new ArrayList<>(setters);
        this.gettersByChannel  = new java.util.HashMap<>(gettersByChannel);
        this.settersByChannel  = new java.util.HashMap<>(settersByChannel);
        updateRunStopLabel();
    }

    // ── init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        rows.clear();

        int titleH  = PAD + 12 + 4 + BTN_H + PAD; // 42 — extra row for save-name input
        int rowsH   = VISIBLE * (ROW_H + ROW_GAP);
        int statusH = PAD + 8;
        int btnRowH = PAD + BTN_H + PAD;
        panelH = titleH + rowsH + statusH + btnRowH;

        panelX   = (width  - PANEL_W) / 2;
        panelY   = (height - panelH)  / 2;
        rowAreaY = panelY + titleH;

        revertDialog = new ConfirmDialog(font);
        revertDialog.setParentBounds(panelX, panelY, PANEL_W, panelH);

        // Top-right: wiki and star buttons
        int IBTN = 16;
        addRenderableWidget(DarkButton.make(net.minecraft.network.chat.Component.literal("?"),
                net.minecraft.network.chat.Component.translatable("gui.universalkeyboard.tooltip.wiki"),
                b -> net.minecraft.client.Minecraft.getInstance().setScreen(new WikiScreen(this)),
                panelX + PANEL_W - PAD - IBTN * 2 - 2, panelY + PAD - 2, IBTN, IBTN));
        isFavorited = MenuNav.currentFavorite == dev.bennethogan.universalkeyboard.livecontrol.FavoriteScreen.SEQUENCER;
        starBtn = DarkButton.make(net.minecraft.network.chat.Component.literal("★"),
                net.minecraft.network.chat.Component.translatable(
                        isFavorited ? "gui.universalkeyboard.tooltip.unfavorite"
                                    : "gui.universalkeyboard.tooltip.favorite"),
                b -> {
                    dev.bennethogan.universalkeyboard.livecontrol.FavoriteScreen next =
                            isFavorited ? dev.bennethogan.universalkeyboard.livecontrol.FavoriteScreen.NONE
                                        : dev.bennethogan.universalkeyboard.livecontrol.FavoriteScreen.SEQUENCER;
                    ModPackets.sendSetFavorite(keyboardPos, next);
                },
                panelX + PANEL_W - PAD - IBTN, panelY + PAD - 2, IBTN, IBTN);
        starBtn.setAccentBg(isFavorited ? 0xFF3A3000 : -1);
        addRenderableWidget(starBtn);

        // Save-name input centred under title text
        int nameW = 200;
        saveName  = new EditBox(font, panelX + (PANEL_W - nameW) / 2,
                panelY + PAD + 12 + 4, nameW, BTN_H, Component.empty());
        saveName.setMaxLength(40);
        saveName.setHint(Component.literal(I18n.get("gui.universalkeyboard.hint.save_name")));
        addRenderableWidget(saveName);

        for (int r = 0; r < VISIBLE; r++) rows.add(new RowWidgets(r));

        // Bottom row: Back page | gap | Play/Stop ; Revert ; Save ; Load
        final int IBTN_W = 48, IBTN_H = 16, gap = 4;
        bottomBtnW = IBTN_W;
        bottomBtnY = panelY + panelH - PAD - IBTN_H;

        // Back, left-positioned; returns to the mode-selection menu
        addRenderableWidget(IconButton.make(ModIcons.PREV_PAGE,
                Component.translatable("gui.universalkeyboard.tooltip.back"),
                b -> goBack(),
                panelX + PAD, bottomBtnY, IBTN_W, IBTN_H));

        // Right group, laid out right-to-left
        int rx = panelX + PANEL_W - PAD - IBTN_W;

        // Load (folder)
        loadBtnX = rx;
        addRenderableWidget(IconButton.make(ModIcons.FOLDER,
                Component.translatable("gui.universalkeyboard.tooltip.load_file"),
                b -> onLoadFile(),
                rx, bottomBtnY, IBTN_W, IBTN_H));
        rx -= IBTN_W + gap;

        // Save (floppy)
        addRenderableWidget(IconButton.make(ModIcons.CONFIG_SAVE,
                Component.translatable("gui.universalkeyboard.tooltip.save_file"),
                b -> onSaveFile(),
                rx, bottomBtnY, IBTN_W, IBTN_H));
        rx -= IBTN_W + gap;

        // Revert, discards to how the menu opened.
        addRenderableWidget(IconButton.make(ModIcons.REVERT,
                Component.translatable("gui.universalkeyboard.tooltip.revert"),
                b -> revertDialog.open(
                        "gui.universalkeyboard.dialog.revert_title",
                        "gui.universalkeyboard.dialog.revert_body",
                        "gui.universalkeyboard.btn.yes_revert",
                        this::onRevert),
                rx, bottomBtnY, IBTN_W, IBTN_H));
        rx -= IBTN_W + gap;

        // Play/Stop, icon toggles with run state
        runStopBtn = IconButton.make(running ? ModIcons.STOP : ModIcons.PLAY,
                Component.translatable(running ? "gui.universalkeyboard.tooltip.stop" : "gui.universalkeyboard.tooltip.play"),
                b -> onRunStop(),
                rx, bottomBtnY, IBTN_W, IBTN_H, running ? -1 : 0xFF1E3A1E, 0);
        addRenderableWidget(runStopBtn);

        refreshAllRows();
        ModPackets.sendSequencerWatch(keyboardPos, true);
    }

    @Override
    public void onClose() {
        ModPackets.sendSequencerWatch(keyboardPos, false);
        super.onClose();
    }

    // Return to the mode-selection menu with breadcrumb
    private void goBack() {
        ModPackets.sendSequencerWatch(keyboardPos, false);
        if (!MenuNav.back()) super.onClose();
    }

    @Override
    public void removed() {
        autosave(); // flush any edit made in the final tick, for every teardown path
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        // Debounced autosave: flush one tick after the last edit, so continuous
        // edits don't spam a packet every tick
        List<CompoundTag> now = snapshot(steps);
        if (now.equals(lastTickSnapshot)) autosave();
        else lastTickSnapshot = now;
    }

    // Push the current steps to the server if they differ from what was last saved.
    private void autosave() {
        List<CompoundTag> now = snapshot(steps);
        if (now.equals(savedSnapshot)) return;
        ModPackets.sendSaveAndRunSequencer(keyboardPos, steps, false);
        savedSnapshot = now;
    }

    // Discard all edits made since the menu was opened
    private void onRevert() {
        steps.clear();
        steps.addAll(deepCopySteps(originalSteps));
        scrollOffset = Math.min(scrollOffset, Math.max(0, steps.size() - VISIBLE));
        refreshAllRows();
        autosave(); // push the reverted state to the server immediately
    }

    // ── render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);

        g.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xFF111111);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + 1,      0xFF666666);
        g.fill(panelX, panelY + panelH - 1, panelX + PANEL_W, panelY + panelH, 0xFF666666);
        g.fill(panelX, panelY, panelX + 1, panelY + panelH,  0xFF666666);
        g.fill(panelX + PANEL_W - 1, panelY, panelX + PANEL_W, panelY + panelH, 0xFF666666);

        g.drawCenteredString(font, I18n.get("gui.universalkeyboard.screen.sequencer.title"),
                panelX + PANEL_W / 2, panelY + PAD, 0xFFFFFF);

        int cx   = panelX + PAD;
        int ctx  = cx + COL_CTX;
        int rowW = PANEL_W - PAD * 2;

        for (int r = 0; r < VISIBLE; r++) {
            int si = scrollOffset + r;
            if (si >= steps.size()) break;
            int rowY = rowAreaY + r * (ROW_H + ROW_GAP);

            SequencerStep step   = steps.get(si);
            int           accent = stepAccentColor(step.type);

            // Colored row background + 2px left accent bar
            g.fill(cx,     rowY, cx + rowW,  rowY + ROW_H, stepRowBg(step.type));
            g.fill(cx,     rowY, cx + 2,     rowY + ROW_H, accent);

            // Faint top/bottom border matching the accent hue
            int borderClr = (accent & 0x00FFFFFF) | 0x44000000;
            g.fill(cx, rowY,              cx + rowW, rowY + 1,          borderClr);
            g.fill(cx, rowY + ROW_H - 1, cx + rowW, rowY + ROW_H,      borderClr);


            g.drawString(font, "§8" + (si + 1), cx + COL_NUM, rowY + (ROW_H - 8) / 2, 0xAAAAAA, false);

            int ly = rowY + (ROW_H - 8) / 2;
            switch (step.type) {
                case DELAY -> g.drawString(font, "§7s",       ctx + 83, ly, 0xAAAAAA, false);
                case JUMP  -> g.drawString(font, "§7→ step",  ctx,      ly, 0x888888, false);
                case MATH  -> g.drawString(font, "§7=",       ctx + 40, ly, 0x888888, false);
                case REGRESS -> {
                    if (step.regressMode.equals("SAMPLE"))
                        g.drawString(font, "§7y", ctx + 170, ly, 0x888888, false);
                    else if (step.regressMode.equals("SOLVE"))
                        g.drawString(font, "§7m,b", ctx + 136, ly, 0x888888, false);
                }
                default    -> {}
            }
        }

        // Empty-list hint
        if (steps.isEmpty()) {
            g.drawCenteredString(font, I18n.get("gui.universalkeyboard.hint.insert_step"),
                    panelX + PANEL_W / 2,
                    rowAreaY + (VISIBLE * (ROW_H + ROW_GAP)) / 2 - 4,
                    0x666666);
        }

        // Compute insert-hover gap (which inter-row gap the mouse is near)
        insertHoverGap = -1;
        if (typeDropdownRow < 0 && mathDropdownRow < 0 && mathOpDropdownRow < 0
                && regressMultiRow < 0 && srcPickRow < 0 && !loadDropdownOpen
                && confirmOverwriteName == null && !showNoNameDialog) {
            int count = Math.min(VISIBLE, steps.size() - scrollOffset);
            if (mx >= cx && mx < cx + rowW) {
                if (count == 0) {
                    // Empty list: any hover in the row area inserts at position 0
                    if (my >= rowAreaY && my < rowAreaY + VISIBLE * (ROW_H + ROW_GAP))
                        insertHoverGap = 0;
                } else {
                    for (int g2 = 0; g2 <= count; g2++) {
                        int gapY = rowAreaY + g2 * (ROW_H + ROW_GAP);
                        if (my >= gapY - 4 && my < gapY + 4) { insertHoverGap = g2; break; }
                    }
                }
            }
        }

        // Draw insert-between indicator
        if (insertHoverGap >= 0) {
            if (steps.isEmpty()) {
                int midY = rowAreaY + (VISIBLE * (ROW_H + ROW_GAP)) / 2;
                g.fill(cx, midY - 1, cx + rowW, midY + 1, 0xFF44BB44);
                g.drawCenteredString(font, "§a+", panelX + PANEL_W / 2, midY - 4, 0x44BB44);
            } else {
                int lineY = rowAreaY + insertHoverGap * (ROW_H + ROW_GAP) - 1;
                g.fill(cx, lineY, cx + rowW, lineY + 2, 0xFF44BB44);
                g.drawString(font, "§a+", cx + 4, lineY - 4, 0x44BB44, false);
            }
        }

        int statusY = rowAreaY + VISIBLE * (ROW_H + ROW_GAP) + PAD;
        if (!running)
            g.drawString(font, I18n.get("gui.universalkeyboard.msg.seq_stopped", steps.size(), steps.size() == 1 ? "" : "s"),
                    cx, statusY, 0xFFFFFF, false);

        for (var w : renderables) w.render(g, mx, my, pt);

        if (running) {
            g.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0x55000000);
            runStopBtn.render(g, mx, my, pt);
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        renderTypeDropdown(g, mx, my);
        renderMathSourceDropdown(g, mx, my);
        renderLoadDropdown(g, mx, my);
        renderMathOpDropdown(g, mx, my);
        renderRegressMultiDropdown(g, mx, my);
        renderSrcPickDropdown(g, mx, my);
        if (confirmOverwriteName != null) renderConfirmDialog(g, mx, my);
        if (showNoNameDialog) renderNoNameDialog(g, mx, my);
        if (revertDialog != null) revertDialog.render(g, mx, my);
        g.pose().popPose();
    }

    // ── Dropdown rendering ────────────────────────────────────────────────────

    private void renderTypeDropdown(GuiGraphics g, int mx, int my) {
        if (typeDropdownRow < 0 || typeDropdownRow >= VISIBLE) return;
        Type[] available = buildAvailableTypes();
        int rowY  = rowAreaY + typeDropdownRow * (ROW_H + ROW_GAP);
        int ddX   = panelX + PAD + COL_TYPE;
        int ddW   = 82;
        int itemH = 12;
        int vis   = Math.min(TYPE_DD_VIS, available.length);
        int ddH   = vis * itemH + 4;
        int ddY   = ddPos(rowY, ddH);

        drawDdBox(g, ddX, ddW, ddY, ddH);
        for (int i = 0; i < vis; i++) {
            int idx = typeDropdownScroll + i;
            if (idx >= available.length) break;
            drawDdItem(g, mx, my, ddX, ddW, ddY, itemH, i, available[idx].coloredLabel());
        }
        drawScrollArrows(g, ddX, ddW, ddY, ddH, typeDropdownScroll, available.length);
    }

    private void renderMathSourceDropdown(GuiGraphics g, int mx, int my) {
        if (mathDropdownRow < 0 || mathDropdownRow >= VISIBLE) return;
        int si = scrollOffset + mathDropdownRow;
        if (si >= steps.size()) return;
        SequencerStep mStep = steps.get(si);
        int mCh = mathDropdownIsA ? mStep.mathACh : mStep.mathBCh;
        List<String> opts = buildMathSrcOptions(mCh);

        int rowY  = rowAreaY + mathDropdownRow * (ROW_H + ROW_GAP);
        int ddX   = mathDropdownIsA ? (panelX + PAD + COL_CTX + 48) : (panelX + PAD + COL_CTX + 180);
        int ddW   = 90;
        int itemH = 12;
        int vis   = Math.min(DD_VIS, opts.size());
        int ddH   = vis * itemH + 4;
        int ddY   = ddPos(rowY, ddH);

        drawDdBox(g, ddX, ddW, ddY, ddH);
        for (int i = 0; i < vis; i++) {
            int idx = mathDropdownScroll + i;
            if (idx >= opts.size()) break;
            drawDdItem(g, mx, my, ddX, ddW, ddY, itemH, i, opts.get(idx));
        }
        drawScrollArrows(g, ddX, ddW, ddY, ddH, mathDropdownScroll, opts.size());
    }

    private void renderLoadDropdown(GuiGraphics g, int mx, int my) {
        if (!loadDropdownOpen || loadDropdownFiles.isEmpty()) return;
        int itemH = 12;
        int ddW   = bottomBtnW + 60;
        int ddX   = loadBtnX + bottomBtnW - ddW; // right-aligned to the Load button
        int vis   = Math.min(DD_VIS, loadDropdownFiles.size());
        int ddH   = vis * itemH + 4;
        int ddY   = bottomBtnY - ddH - 2; // drop UP

        drawDdBox(g, ddX, ddW, ddY, ddH);
        for (int i = 0; i < vis; i++) {
            int idx = loadDropdownScroll + i;
            if (idx >= loadDropdownFiles.size()) break;
            String name = loadDropdownFiles.get(idx);
            if (font.width(name) > ddW - 8) name = font.plainSubstrByWidth(name, ddW - 12) + "…";
            drawDdItem(g, mx, my, ddX, ddW, ddY, itemH, i, name);
        }
        drawScrollArrows(g, ddX, ddW, ddY, ddH, loadDropdownScroll, loadDropdownFiles.size());
    }

    /** {dx, dy, dw, dh, btnY, btnH} for the overwrite confirm dialog. */
    private int[] overwriteDialogLayout() {
        int dw = 240, pad = 8, gap = 4, btnH = 14;
        int textW = dw - pad * 2;
        String label = confirmOverwriteName == null ? "" : confirmOverwriteName;
        if (font.width(label) > dw - 40) label = font.plainSubstrByWidth(label, dw - 44) + "…";
        String title = I18n.get("gui.universalkeyboard.dialog.overwrite", label);
        int dh = pad + GuiText.wrappedHeight(font, title, textW) + gap + 4 + btnH + pad;
        int dx = panelX + (PANEL_W - dw) / 2;
        int dy = panelY + (panelH  - dh) / 2;
        int btnY = dy + dh - pad - btnH;
        return new int[]{dx, dy, dw, dh, btnY, btnH};
    }

    private void renderConfirmDialog(GuiGraphics g, int mx, int my) {
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xAA000000);
        int[] L = overwriteDialogLayout();
        int dx = L[0], dy = L[1], dw = L[2], dh = L[3], btnY = L[4], btnH = L[5];
        int pad = 8, textW = dw - pad * 2;

        g.fill(dx - 1, dy - 1, dx + dw + 1, dy + dh + 1, 0xFF666666);
        g.fill(dx, dy, dx + dw, dy + dh, 0xFF1A1A1A);

        String label = confirmOverwriteName;
        if (font.width(label) > dw - 40) label = font.plainSubstrByWidth(label, dw - 44) + "…";
        GuiText.drawWrappedCentered(g, font, I18n.get("gui.universalkeyboard.dialog.overwrite", label), dx + dw / 2, dy + pad, textW, 0xFFFFFF);

        boolean yh = mx >= dx + 20  && mx < dx + 100 && my >= btnY && my < btnY + btnH;
        boolean nh = mx >= dx + 140 && mx < dx + 220 && my >= btnY && my < btnY + btnH;
        g.fill(dx + 20,  btnY, dx + 100, btnY + btnH, yh ? 0xFF2A4A2A : 0xFF1E3A1E);
        g.fill(dx + 140, btnY, dx + 220, btnY + btnH, nh ? 0xFF4A2A2A : 0xFF3A1E1E);
        g.drawCenteredString(font, I18n.get("gui.universalkeyboard.btn.yes"), dx + 60,  btnY + 3, yh ? 0x88FF88 : 0x66CC66);
        g.drawCenteredString(font, I18n.get("gui.universalkeyboard.btn.no"),  dx + 180, btnY + 3, nh ? 0xFF8888 : 0xCC6666);
    }

    private void renderNoNameDialog(GuiGraphics g, int mx, int my) {
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xAA000000);
        int dw = 240, pad = 8, gap = 4, btnH = 14;
        int textW = dw - pad * 2;
        String msg = I18n.get("gui.universalkeyboard.msg.enter_save_name");
        int dh = pad + GuiText.wrappedHeight(font, msg, textW) + gap + 4 + btnH + pad;
        int dx = panelX + (PANEL_W - dw) / 2;
        int dy = panelY + (panelH  - dh) / 2;

        g.fill(dx - 1, dy - 1, dx + dw + 1, dy + dh + 1, 0xFF666666);
        g.fill(dx, dy, dx + dw, dy + dh, 0xFF1A1A1A);
        GuiText.drawWrappedCentered(g, font, msg, dx + dw / 2, dy + pad, textW, 0xFFFFFF);

        int btnY = dy + dh - pad - btnH;
        boolean hov = mx >= dx + 80 && mx < dx + 160 && my >= btnY && my < btnY + btnH;
        g.fill(dx + 80, btnY, dx + 160, btnY + btnH, hov ? 0xFF3A3A3A : 0xFF2A2A2A);
        g.drawCenteredString(font, I18n.get("gui.universalkeyboard.btn.ok"), dx + 120, btnY + 3, hov ? 0xFFFFFF : 0xAAAAAA);
    }

    // ── Dropdown drawing helpers ───────────────────────────────────────────────

    private static int stepAccentColor(Type type) {
        return switch (type) {
            case SET_VALUE              -> 0xFFBB7700;
            case SET_REDSTONE           -> 0xFFBB2222;
            case TYPE_TEXT,
                 TYPE_VARIABLE          -> 0xFFBB22BB;
            case IF                     -> 0xFFBBAA00;
            case CONDITION              -> 0xFF2299BB;
            case DELAY                  -> 0xFF777777;
            case JUMP                   -> 0xFFAAAAAA;
            case MATH                   -> 0xFF22BB22;
            case REGRESS                -> 0xFF22AABB;
            case CYCLE                  -> 0xFF8822BB;
            case END                    -> 0xFF444444;
        };
    }

    private static int stepRowBg(Type type) {
        return switch (type) {
            case SET_VALUE              -> 0x66221100;
            case SET_REDSTONE           -> 0x66330000;
            case TYPE_TEXT,
                 TYPE_VARIABLE          -> 0x66330033;
            case IF                     -> 0x66332200;
            case CONDITION              -> 0x66003344;
            case DELAY                  -> 0x66111111;
            case JUMP                   -> 0x66222222;
            case MATH                   -> 0x66003300;
            case REGRESS                -> 0x66002233;
            case CYCLE                  -> 0x66110033;
            case END                    -> 0x660D0D0D;
        };
    }

    private int ddPos(int rowY, int ddH) {
        return (rowY + ROW_H + ddH <= panelY + panelH - BTN_H - PAD)
               ? rowY + ROW_H : rowY - ddH;
    }

    private void drawDdBox(GuiGraphics g, int ddX, int ddW, int ddY, int ddH) {
        g.fill(ddX - 1, ddY - 1, ddX + ddW + 1, ddY + ddH + 1, 0xFF555555);
        g.fill(ddX, ddY, ddX + ddW, ddY + ddH, 0xFF1A1A1A);
    }

    private void drawDdItem(GuiGraphics g, int mx, int my, int ddX, int ddW, int ddY,
                            int itemH, int row, String text) {
        int ty = ddY + 2 + row * itemH;
        boolean hov = mx >= ddX && mx < ddX + ddW && my >= ty && my < ty + itemH;
        if (hov) g.fill(ddX + 1, ty, ddX + ddW - 1, ty + itemH, 0xFF2A3A55);
        g.drawString(font, text, ddX + 4, ty + 2, hov ? 0xFFFFFF : 0xAAAAAA, false);
    }

    private void drawScrollArrows(GuiGraphics g, int ddX, int ddW, int ddY, int ddH,
                                  int scroll, int total) {
        if (scroll > 0)
            g.drawString(font, "▲", ddX + ddW - 10, ddY + 2, 0x888888, false);
        if (scroll + DD_VIS < total)
            g.drawString(font, "▼", ddX + ddW - 10, ddY + ddH - 10, 0x888888, false);
    }

    // ── Row management ────────────────────────────────────────────────────────

    private void refreshAllRows() {
        for (RowWidgets row : rows) row.refresh();
        updateRunStopLabel();
    }

    private void updateRunStopLabel() {
        if (runStopBtn == null) return;
        runStopBtn.setIcon(running ? ModIcons.STOP : ModIcons.PLAY);
        runStopBtn.setAccentBg(running ? -1 : 0xFF1E3A1E);
        runStopBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable(running
                        ? "gui.universalkeyboard.tooltip.stop"
                        : "gui.universalkeyboard.tooltip.play")));
    }

    private void insertStep(int beforeIdx) {
        if (steps.size() >= MAX_STEPS) return;
        int idx = Math.max(0, Math.min(beforeIdx, steps.size()));
        steps.add(idx, SequencerStep.ofType(Type.END));
        // Keep the inserted step visible without changing scroll if already in view
        scrollOffset = Math.max(0, Math.min(scrollOffset, idx));
        refreshAllRows();
    }

    private void deleteStep(int stepIdx) {
        if (stepIdx < 0 || stepIdx >= steps.size()) return;
        steps.remove(stepIdx);
        scrollOffset = Math.min(scrollOffset, Math.max(0, steps.size() - VISIBLE));
        refreshAllRows();
    }

    private Type[] buildAvailableTypes() {
        boolean ccPresent = dev.bennethogan.universalkeyboard.compat.PeripheralHelper.isCCPresent();
        boolean typeable  = ccPresent && hasTypeableTarget();
        List<Type> list = new ArrayList<>();
        list.add(Type.MATH);
        if (typeable) { list.add(Type.TYPE_VARIABLE); list.add(Type.TYPE_TEXT); }
        list.add(Type.SET_REDSTONE);
        if (ccPresent) list.add(Type.SET_VALUE);
        list.add(Type.REGRESS);
        list.add(Type.IF);
        list.add(Type.CONDITION);
        list.add(Type.JUMP);
        list.add(Type.DELAY);
        list.add(Type.CYCLE);
        list.add(Type.END);
        return list.toArray(new Type[0]);
    }

    // TYPE steps work against CC computers (key/char events) and monitors (terminal writes).
    private boolean hasTypeableTarget() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        var be = mc.level.getBlockEntity(keyboardPos);
        if (!(be instanceof dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity kb)) return false;
        for (var positions : kb.getAllChannelTargets().values()) {
            for (BlockPos pos : positions) {
                var target = mc.level.getBlockEntity(pos);
                if (target == null) continue;
                if (dev.bennethogan.universalkeyboard.compat.KeyboardMode.isCCComputer(target)
                        || dev.bennethogan.universalkeyboard.compat.MonitorHelper.isMonitor(target))
                    return true;
            }
        }
        return false;
    }

    private List<String> gettersFor(int channel) {
        return gettersByChannel.getOrDefault(channel, availableGetters);
    }

    private String fitLabel(String s, int maxPx) {
        if (font.width(s) <= maxPx) return s;
        return font.plainSubstrByWidth(s, maxPx - font.width("…")) + "…";
    }

    private boolean srcNeedsChannel(String src, boolean isManual) {
        if (isManual || src == null || src.isEmpty()) return false;
        if (SequencerStep.isVar(src)) return false;
        if (src.startsWith("RS:")) return false;
        if (src.matches("L\\d+")) return false;
        if (dev.bennethogan.universalkeyboard.compat.SableCompat.isSableGetter(src)) return false;
        return true;
    }

    private List<String[]> settersFor(int channel) {
        return settersByChannel.getOrDefault(channel, availableSetters);
    }

    private List<String> buildMathSrcOptions(int channel) {
        List<String> opts = new ArrayList<>();
        opts.add(I18n.get("gui.universalkeyboard.label.manual_input"));
        for (int v = 1; v <= SequencerStep.VAR_COUNT; v++) opts.add("V" + v);
        opts.add("RS:N"); opts.add("RS:S"); opts.add("RS:E"); opts.add("RS:W"); opts.add("RS:U"); opts.add("RS:D");
        int wc = getWirelessCount();
        for (int w = 1; w <= wc; w++) opts.add("L" + w);
        opts.addAll(gettersFor(channel));
        return opts;
    }

    int getWirelessCount() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        var be = mc.level.getBlockEntity(keyboardPos);
        if (be instanceof dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity kb)
            return kb.getWirelessCount();
        return 0;
    }

    int getLinkFreqCount() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        var be = mc.level.getBlockEntity(keyboardPos);
        if (be instanceof dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity kb)
            return kb.getLinkFreqCount();
        return 0;
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (revertDialog != null && revertDialog.isOpen())
            return revertDialog.mouseClicked(mx, my, btn);

        if (running) {
            if (runStopBtn.isMouseOver(mx, my)) runStopBtn.onClick(mx, my);
            return true;
        }

        int cx   = panelX + PAD;
        int rowW = PANEL_W - PAD * 2;
        activeRowIdx = -1;
        for (int r = 0; r < VISIBLE; r++) {
            int rowY = rowAreaY + r * (ROW_H + ROW_GAP);
            if (mx >= cx && mx < cx + rowW && my >= rowY && my < rowY + ROW_H) {
                int si = scrollOffset + r;
                if (si < steps.size()) activeRowIdx = r;
                break;
            }
        }

        if (showNoNameDialog) { showNoNameDialog = false; return true; }

        // Confirm dialog blocks everything else
        if (confirmOverwriteName != null) {
            int[] L = overwriteDialogLayout();
            int dx = L[0], btnY = L[4], btnH = L[5];
            if (mx >= dx + 20 && mx < dx + 100 && my >= btnY && my < btnY + btnH) {
                saveToFile(confirmOverwriteName);
            }
            confirmOverwriteName = null;
            return true;
        }

        if (loadDropdownOpen) {
            handleLoadDropdownClick(mx, my);
            return true;
        }

        if (mathDropdownRow >= 0) {
            handleMathDropdownClick(mx, my);
            return true;
        }

        if (mathOpDropdownRow >= 0) {
            handleMathOpDropdownClick(mx, my);
            return true;
        }

        if (regressMultiRow >= 0) {
            handleRegressMultiClick(mx, my);
            return true;
        }

        if (srcPickRow >= 0) {
            handleSrcPickClick(mx, my);
            return true;
        }

        if (typeDropdownRow >= 0) {
            Type[] available = buildAvailableTypes();
            int rowY  = rowAreaY + typeDropdownRow * (ROW_H + ROW_GAP);
            int ddX   = panelX + PAD + COL_TYPE;
            int ddW   = 82;
            int itemH = 12;
            int vis   = Math.min(TYPE_DD_VIS, available.length);
            int ddH   = vis * itemH + 4;
            int ddY   = ddPos(rowY, ddH);
            int savedScroll = typeDropdownScroll;
            int savedRow    = typeDropdownRow;
            typeDropdownRow    = -1;
            typeDropdownScroll = 0;

            if (mx >= ddX && mx < ddX + ddW && my >= ddY && my < ddY + ddH) {
                int rel = ((int) my - ddY - 2) / itemH;
                int idx = savedScroll + rel;
                if (rel >= 0 && rel < vis && idx < available.length) {
                    int si = scrollOffset + savedRow;
                    if (si < steps.size()) steps.get(si).type = available[idx];
                }
            }
            refreshAllRows();
            return true;
        }

        if (insertHoverGap >= 0 && btn == 0) {
            insertStep(scrollOffset + insertHoverGap);
            return true;
        }

        return super.mouseClicked(mx, my, btn);
    }

    private boolean handleMathDropdownClick(double mx, double my) {
        int     savedRow    = mathDropdownRow;
        boolean savedIsA    = mathDropdownIsA;
        int     savedScroll = mathDropdownScroll;
        int     si          = scrollOffset + savedRow;
        int     ch          = (si < steps.size()) ? (savedIsA ? steps.get(si).mathACh : steps.get(si).mathBCh) : 1;
        List<String> opts   = buildMathSrcOptions(ch);

        int ddX   = savedIsA ? (panelX + PAD + COL_CTX + 48) : (panelX + PAD + COL_CTX + 180);
        int ddW   = 90;
        int itemH = 12;
        int vis   = Math.min(DD_VIS, opts.size());
        int ddH   = vis * itemH + 4;
        int rowY  = rowAreaY + savedRow * (ROW_H + ROW_GAP);
        int ddY   = ddPos(rowY, ddH);

        mathDropdownRow    = -1;
        mathDropdownScroll = 0;

        if (si < steps.size() && mx >= ddX && mx < ddX + ddW && my >= ddY && my < ddY + ddH) {
            int rel = ((int) my - ddY - 2) / itemH;
            int idx = savedScroll + rel;
            if (rel >= 0 && rel < vis && idx < opts.size()) {
                String sel  = opts.get(idx);
                SequencerStep step = steps.get(si);
                if (sel.equals(I18n.get("gui.universalkeyboard.label.manual_input"))) {
                    if (savedIsA) step.mathAManual = true; else step.mathBManual = true;
                } else {
                    if (savedIsA) { step.mathA = sel; step.mathAManual = false; }
                    else          { step.mathB = sel; step.mathBManual = false; }
                }
            }
        }
        refreshAllRows();
        return true;
    }

    private boolean handleLoadDropdownClick(double mx, double my) {
        int savedScroll = loadDropdownScroll;
        int itemH = 12;
        int ddW   = bottomBtnW + 60;
        int ddX   = loadBtnX + bottomBtnW - ddW; // right-aligned to the Load button
        int vis   = Math.min(DD_VIS, loadDropdownFiles.size());
        int ddH   = vis * itemH + 4;
        int ddY   = bottomBtnY - ddH - 2;

        loadDropdownOpen   = false;
        loadDropdownScroll = 0;

        if (mx >= ddX && mx < ddX + ddW && my >= ddY && my < ddY + ddH) {
            int rel = ((int) my - ddY - 2) / itemH;
            int idx = savedScroll + rel;
            if (rel >= 0 && rel < vis && idx < loadDropdownFiles.size()) {
                String name = loadDropdownFiles.get(idx);
                if (running) {
                    saveName.setValue(name);
                } else {
                    loadFromFile(name);
                    saveName.setValue(name);
                }
            }
        }
        return true;
    }

    private void renderMathOpDropdown(GuiGraphics g, int mx, int my) {
        if (mathOpDropdownRow < 0 || mathOpDropdownRow >= VISIBLE) return;
        int rowY = rowAreaY + mathOpDropdownRow * (ROW_H + ROW_GAP);
        int catX = panelX + PAD + COL_CTX + 140;
        int catW = 62;
        int itemH = 12;
        int catH  = OP_CAT_LABELS.length * itemH + 4;
        int catY  = ddPos(rowY, catH);

        drawDdBox(g, catX, catW, catY, catH);
        for (int i = 0; i < OP_CAT_LABELS.length; i++) {
            int ty = catY + 2 + i * itemH;
            boolean hov = mx >= catX && mx < catX + catW && my >= ty && my < ty + itemH;
            if (hov) mathOpCatHover = i;
            boolean sel = mathOpCatHover == i;
            if (sel || hov) g.fill(catX + 1, ty, catX + catW - 1, ty + itemH, 0xFF2A3A55);
            g.drawString(font, OP_CAT_LABELS[i], catX + 4, ty + 2, sel ? 0xFFFFFF : 0xAAAAAA, false);
            g.drawString(font, "›", catX + catW - 10, ty + 2, sel ? 0x88AAFF : 0x555555, false);
        }

        if (mathOpCatHover >= 0 && mathOpCatHover < OP_CAT_OPS.length) {
            String[] ops = OP_CAT_OPS[mathOpCatHover];
            int opX = catX + catW + 2;
            int opW = 54;
            int opH = ops.length * itemH + 4;
            int opY = catY;
            if (opY + opH > panelY + panelH - BTN_H - PAD)
                opY = panelY + panelH - BTN_H - PAD - opH;
            drawDdBox(g, opX, opW, opY, opH);
            int si = scrollOffset + mathOpDropdownRow;
            String cur = (si < steps.size()) ? steps.get(si).mathOp : "+";
            for (int i = 0; i < ops.length; i++) {
                int ty = opY + 2 + i * itemH;
                boolean hov = mx >= opX && mx < opX + opW && my >= ty && my < ty + itemH;
                boolean isCur = ops[i].equals(cur);
                if (hov)   g.fill(opX + 1, ty, opX + opW - 1, ty + itemH, 0xFF2A3A55);
                else if (isCur) g.fill(opX + 1, ty, opX + opW - 1, ty + itemH, 0xFF1E3A2A);
                g.drawString(font, ops[i], opX + 4, ty + 2, (hov || isCur) ? 0xFFFFFF : 0xAAAAAA, false);
            }
        }
    }

    private boolean handleMathOpDropdownClick(double mx, double my) {
        int rowY = rowAreaY + mathOpDropdownRow * (ROW_H + ROW_GAP);
        int catX = panelX + PAD + COL_CTX + 140;
        int catW = 62;
        int itemH = 12;
        int catH  = OP_CAT_LABELS.length * itemH + 4;
        int catY  = ddPos(rowY, catH);
        int savedRow = mathOpDropdownRow;

        if (mathOpCatHover >= 0 && mathOpCatHover < OP_CAT_OPS.length) {
            String[] ops = OP_CAT_OPS[mathOpCatHover];
            int opX = catX + catW + 2;
            int opW = 54;
            int opH = ops.length * itemH + 4;
            int opY = catY;
            if (opY + opH > panelY + panelH - BTN_H - PAD)
                opY = panelY + panelH - BTN_H - PAD - opH;
            if (mx >= opX && mx < opX + opW && my >= opY && my < opY + opH) {
                int rel = ((int) my - opY - 2) / itemH;
                if (rel >= 0 && rel < ops.length) {
                    mathOpDropdownRow = -1;
                    int si = scrollOffset + savedRow;
                    if (si < steps.size()) {
                        steps.get(si).mathOp = ops[rel];
                        refreshAllRows();
                    }
                    return true;
                }
            }
        }

        if (mx >= catX && mx < catX + catW && my >= catY && my < catY + catH) {
            int rel = ((int) my - catY - 2) / itemH;
            if (rel >= 0 && rel < OP_CAT_LABELS.length) mathOpCatHover = rel;
            return true;
        }

        mathOpDropdownRow = -1;
        refreshAllRows();
        return true;
    }

    private void renderRegressMultiDropdown(GuiGraphics g, int mx, int my) {
        if (regressMultiRow < 0 || regressMultiRow >= VISIBLE) return;
        int si = scrollOffset + regressMultiRow;
        if (si >= steps.size()) return;
        SequencerStep step = steps.get(si);
        int mCh = regressMultiIsA ? step.mathACh : step.mathBCh;
        List<String> opts = buildMathSrcOptionsNoManual(mCh);
        List<String> selected = parseMultiSrc(regressMultiIsA ? step.mathA : step.mathB);

        int rowY  = rowAreaY + regressMultiRow * (ROW_H + ROW_GAP);
        int ddX   = regressMultiIsA ? (panelX + PAD + COL_CTX + 48) : (panelX + PAD + COL_CTX + 180);
        int ddW   = 90;
        int itemH = 12;
        int vis   = Math.min(DD_VIS + 2, opts.size());
        int ddH   = vis * itemH + 4;
        int ddY   = ddPos(rowY, ddH);

        drawDdBox(g, ddX, ddW, ddY, ddH);
        for (int i = 0; i < vis; i++) {
            int idx = regressMultiScroll + i;
            if (idx >= opts.size()) break;
            String opt = opts.get(idx);
            boolean isSel = selected.contains(opt);
            int ty = ddY + 2 + i * itemH;
            boolean hov = mx >= ddX && mx < ddX + ddW && my >= ty && my < ty + itemH;
            if (isSel)  g.fill(ddX + 1, ty, ddX + ddW - 1, ty + itemH, 0xFF1E3A2A);
            if (hov)    g.fill(ddX + 1, ty, ddX + ddW - 1, ty + itemH, 0xFF2A3A55);
            g.drawString(font, (isSel ? "§a✓ " : "  ") + opt, ddX + 4, ty + 2,
                    hov ? 0xFFFFFF : (isSel ? 0x88FF88 : 0xAAAAAA), false);
        }
        drawScrollArrows(g, ddX, ddW, ddY, ddH, regressMultiScroll, opts.size());
    }

    private boolean handleRegressMultiClick(double mx, double my) {
        int si = scrollOffset + regressMultiRow;
        int mCh = (si < steps.size()) ? (regressMultiIsA ? steps.get(si).mathACh : steps.get(si).mathBCh) : 1;
        List<String> opts = buildMathSrcOptionsNoManual(mCh);

        int rowY  = rowAreaY + regressMultiRow * (ROW_H + ROW_GAP);
        int ddX   = regressMultiIsA ? (panelX + PAD + COL_CTX + 48) : (panelX + PAD + COL_CTX + 180);
        int ddW   = 90;
        int itemH = 12;
        int vis   = Math.min(DD_VIS + 2, opts.size());
        int ddH   = vis * itemH + 4;
        int ddY   = ddPos(rowY, ddH);

        if (mx >= ddX && mx < ddX + ddW && my >= ddY && my < ddY + ddH) {
            int rel = ((int) my - ddY - 2) / itemH;
            int idx = regressMultiScroll + rel;
            if (rel >= 0 && rel < vis && idx < opts.size() && si < steps.size()) {
                String opt = opts.get(idx);
                SequencerStep step = steps.get(si);
                String curSrc = regressMultiIsA ? step.mathA : step.mathB;
                List<String> sel = parseMultiSrc(curSrc);
                if (sel.contains(opt)) sel.remove(opt); else sel.add(opt);
                String joined = String.join(",", sel);
                if (regressMultiIsA) step.mathA = joined; else step.mathB = joined;
                refreshAllRows();
            }
            return true;
        }
        regressMultiRow = -1;
        refreshAllRows();
        return true;
    }

    private List<String> buildMathSrcOptionsNoManual(int channel) {
        List<String> opts = new ArrayList<>();
        for (int v = 1; v <= SequencerStep.VAR_COUNT; v++) opts.add("V" + v);
        opts.add("RS:N"); opts.add("RS:S"); opts.add("RS:E"); opts.add("RS:W"); opts.add("RS:U"); opts.add("RS:D");
        int wc = getWirelessCount();
        for (int w = 1; w <= wc; w++) opts.add("L" + w);
        int lc = getLinkFreqCount();
        for (int w = 1; w <= lc; w++) opts.add("W" + w);
        opts.addAll(gettersFor(channel));
        return opts;
    }

    private List<String> parseMultiSrc(String src) {
        List<String> result = new ArrayList<>();
        if (src == null || src.isBlank()) return result;
        for (String s : src.split(",")) { String t = s.trim(); if (!t.isEmpty()) result.add(t); }
        return result;
    }

    private List<SrcCat> buildSrcCats(int ch, boolean includeManual, boolean varsOnly, boolean ifRsNames) {
        List<SrcCat> cats = new ArrayList<>();
        if (includeManual) {
            cats.add(new SrcCat("Manual",
                List.of(I18n.get("gui.universalkeyboard.label.manual_input"))));
        }
        List<String> vars = new ArrayList<>();
        for (int v = 1; v <= SequencerStep.VAR_COUNT; v++) vars.add("V" + v);
        cats.add(new SrcCat("Vars", vars));
        if (varsOnly) return cats;
        List<String> rsItems = ifRsNames
            ? Arrays.asList(SequencerStep.RS_INPUT_GETTER_NAMES)
            : List.of("RS:N", "RS:S", "RS:E", "RS:W", "RS:U", "RS:D");
        cats.add(new SrcCat("RS", new ArrayList<>(rsItems)));
        int wc = getWirelessCount();
        if (wc > 0) {
            List<String> wl = new ArrayList<>();
            for (int w = 1; w <= wc; w++) wl.add("L" + w);
            cats.add(new SrcCat("Wireless", wl));
        }
        int lc = getLinkFreqCount();
        if (lc > 0) {
            List<String> wl = new ArrayList<>();
            for (int w = 1; w <= lc; w++) wl.add("W" + w);
            cats.add(new SrcCat("W-Channels", wl));
        }
        List<String> allGetters = gettersFor(ch);
        if (!allGetters.isEmpty()) {
            List<String> sable = new ArrayList<>(), periph = new ArrayList<>();
            for (String g : allGetters) {
                if (dev.bennethogan.universalkeyboard.compat.SableCompat.isSableGetter(g)) sable.add(g);
                else periph.add(g);
            }
            if (!sable.isEmpty())  cats.add(new SrcCat("Sable",   sable));
            if (!periph.isEmpty()) cats.add(new SrcCat("Getters", periph));
        }
        return cats;
    }

    private List<SrcCat> buildConditionCats(int ch) {
        List<SrcCat> cats = new ArrayList<>();
        List<String> rsItems = new ArrayList<>();
        for (ConditionSource src : ConditionSource.values())
            if (src.direction != null) rsItems.add(src.label);
        cats.add(new SrcCat("RS", rsItems));
        int wc = getWirelessCount();
        if (wc > 0) {
            List<String> wl = new ArrayList<>();
            for (int w = 1; w <= wc; w++) wl.add("L" + w);
            cats.add(new SrcCat("Wireless", wl));
        }
        int lc = getLinkFreqCount();
        if (lc > 0) {
            List<String> wl = new ArrayList<>();
            for (int w = 1; w <= lc; w++) wl.add("W" + w);
            cats.add(new SrcCat("W-Channels", wl));
        }
        List<String> allGetters = gettersFor(ch);
        if (!allGetters.isEmpty()) {
            List<String> sable = new ArrayList<>(), periph = new ArrayList<>();
            for (String g : allGetters) {
                if (dev.bennethogan.universalkeyboard.compat.SableCompat.isSableGetter(g)) sable.add(g);
                else periph.add(g);
            }
            if (!sable.isEmpty())  cats.add(new SrcCat("Sable",   sable));
            if (!periph.isEmpty()) cats.add(new SrcCat("Getters", periph));
        }
        return cats;
    }

    private List<SrcCat> getSrcCatsForField(String field, int ch, boolean includeManual, boolean varsOnly) {
        if (field.equals("CONDITION")) return buildConditionCats(ch);
        return buildSrcCats(ch, includeManual, varsOnly, field.equals("IF_GETTER"));
    }

    private String getCurrentSrcValue(SequencerStep step, String field) {
        String manualLabel = I18n.get("gui.universalkeyboard.label.manual_input");
        return switch (field) {
            case "IF_GETTER" -> step.ifGetter;
            case "MATH_DEST" -> step.mathDest;
            case "MATH_A"    -> step.mathAManual ? manualLabel : step.mathA;
            case "MATH_B"    -> step.mathBManual ? manualLabel : step.mathB;
            case "CONDITION" -> step.conditionSource == ConditionSource.PERIPHERAL
                    ? step.conditionGetter : step.conditionSource.label;
            default -> "";
        };
    }

    private void renderSrcPickDropdown(GuiGraphics g, int mx, int my) {
        if (srcPickRow < 0 || srcPickRow >= VISIBLE) return;
        int si = scrollOffset + srcPickRow;
        if (si >= steps.size()) return;
        SequencerStep step = steps.get(si);
        List<SrcCat> cats = getSrcCatsForField(srcPickField, srcPickCh, srcPickManual, srcPickVarsOnly);
        if (cats.isEmpty()) return;
        if (srcPickCat >= cats.size()) srcPickCat = 0;

        int catW  = 54;
        int itemH = 12;
        List<String> items = cats.get(srcPickCat).items();
        int maxTextPx = 0;
        for (String item : items) maxTextPx = Math.max(maxTextPx, font.width(item));
        int itemW = Math.max(80, Math.min(maxTextPx + 10, 180));
        int catH  = cats.size() * itemH + 4;
        int rowY  = rowAreaY + srcPickRow * (ROW_H + ROW_GAP);
        int catY  = ddPos(rowY, catH);
        int catX  = Math.max(panelX + itemW + 2, Math.min(srcPickBtnX, panelX + PANEL_W - catW - 2));
        int itemX = catX - 2 - itemW;

        drawDdBox(g, catX, catW, catY, catH);
        for (int i = 0; i < cats.size(); i++) {
            int ty = catY + 2 + i * itemH;
            boolean hov = mx >= catX && mx < catX + catW && my >= ty && my < ty + itemH;
            if (hov) srcPickCat = i;
            boolean sel = srcPickCat == i;
            if (sel || hov) g.fill(catX + 1, ty, catX + catW - 1, ty + itemH, 0xFF2A3A55);
            g.drawString(font, "<", catX + 4, ty + 2, sel ? 0x88AAFF : 0x555555, false);
            g.drawString(font, cats.get(i).label(), catX + 12, ty + 2, sel ? 0xFFFFFF : 0xAAAAAA, false);
        }

        int vis   = Math.min(SRC_DD_VIS, items.size());
        int itemPanelH = vis * itemH + 4;
        int itemY = catY;
        if (itemY + itemPanelH > panelY + panelH - BTN_H - PAD)
            itemY = panelY + panelH - BTN_H - PAD - itemPanelH;

        drawDdBox(g, itemX, itemW, itemY, itemPanelH);
        String curVal = getCurrentSrcValue(step, srcPickField);
        for (int i = 0; i < vis; i++) {
            int idx = srcPickItemScroll + i;
            if (idx >= items.size()) break;
            String item = items.get(idx);
            int ty = itemY + 2 + i * itemH;
            boolean hov = mx >= itemX && mx < itemX + itemW && my >= ty && my < ty + itemH;
            boolean isCur = item.equals(curVal);
            if (hov)       g.fill(itemX + 1, ty, itemX + itemW - 1, ty + itemH, 0xFF2A3A55);
            else if (isCur) g.fill(itemX + 1, ty, itemX + itemW - 1, ty + itemH, 0xFF1E3A2A);
            String display = item;
            if (font.width(display) > itemW - 8) display = font.plainSubstrByWidth(display, itemW - 12) + "…";
            g.drawString(font, display, itemX + 4, ty + 2, (hov || isCur) ? 0xFFFFFF : 0xAAAAAA, false);
        }
        if (items.size() > SRC_DD_VIS)
            drawScrollArrows(g, itemX, itemW, itemY, itemPanelH, srcPickItemScroll, items.size());
    }

    private void handleSrcPickClick(double mx, double my) {
        List<SrcCat> cats = getSrcCatsForField(srcPickField, srcPickCh, srcPickManual, srcPickVarsOnly);
        if (cats.isEmpty() || srcPickCat >= cats.size()) { srcPickRow = -1; refreshAllRows(); return; }

        int catW  = 54;
        int itemH = 12;
        List<String> items = cats.get(srcPickCat).items();
        int maxTextPx = 0;
        for (String item : items) maxTextPx = Math.max(maxTextPx, font.width(item));
        int itemW = Math.max(80, Math.min(maxTextPx + 10, 180));
        int catH  = cats.size() * itemH + 4;
        int rowY  = rowAreaY + srcPickRow * (ROW_H + ROW_GAP);
        int catY  = ddPos(rowY, catH);
        int catX  = Math.max(panelX + itemW + 2, Math.min(srcPickBtnX, panelX + PANEL_W - catW - 2));
        int itemX = catX - 2 - itemW;
        int vis   = Math.min(SRC_DD_VIS, items.size());
        int itemPanelH = vis * itemH + 4;
        int itemY = catY;
        if (itemY + itemPanelH > panelY + panelH - BTN_H - PAD)
            itemY = panelY + panelH - BTN_H - PAD - itemPanelH;

        if (mx >= itemX && mx < itemX + itemW && my >= itemY && my < itemY + itemPanelH) {
            int rel = ((int) my - itemY - 2) / itemH;
            int idx = srcPickItemScroll + rel;
            if (rel >= 0 && rel < vis && idx < items.size()) {
                String catLabel  = cats.get(srcPickCat).label();
                int    savedRow  = srcPickRow;
                String savedField = srcPickField;
                int    savedCh   = srcPickCh;
                srcPickRow = -1;
                applySrcPickSelection(savedRow, savedField, savedCh, catLabel, items.get(idx));
            }
            return;
        }

        if (mx >= catX && mx < catX + catW && my >= catY && my < catY + catH) {
            int rel = ((int) my - catY - 2) / itemH;
            if (rel >= 0 && rel < cats.size()) { srcPickCat = rel; srcPickItemScroll = 0; }
            return;
        }

        srcPickRow = -1;
        refreshAllRows();
    }

    private void applySrcPickSelection(int rowIdx, String field, int ch, String catLabel, String value) {
        int si = scrollOffset + rowIdx;
        if (si >= steps.size()) return;
        SequencerStep step = steps.get(si);
        String manualLabel = I18n.get("gui.universalkeyboard.label.manual_input");
        switch (field) {
            case "IF_GETTER" -> step.ifGetter = value;
            case "MATH_DEST" -> step.mathDest = value;
            case "MATH_A" -> {
                if (value.equals(manualLabel)) step.mathAManual = true;
                else { step.mathA = value; step.mathAManual = false; }
            }
            case "MATH_B" -> {
                if (value.equals(manualLabel)) step.mathBManual = true;
                else { step.mathB = value; step.mathBManual = false; }
            }
            case "CONDITION" -> {
                if (catLabel.equals("RS")) {
                    for (ConditionSource src : ConditionSource.values())
                        if (src.label.equals(value)) { step.conditionSource = src; break; }
                } else {
                    step.conditionSource = ConditionSource.PERIPHERAL;
                    step.conditionGetter = value;
                }
            }
        }
        refreshAllRows();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (running) return true;
        int dir = (int) -Math.signum(dy);

        if (loadDropdownOpen) {
            int max = Math.max(0, loadDropdownFiles.size() - DD_VIS);
            loadDropdownScroll = Math.max(0, Math.min(max, loadDropdownScroll + dir));
            return true;
        }
        if (mathDropdownRow >= 0) {
            int msi = scrollOffset + mathDropdownRow;
            int mch = (msi < steps.size()) ? (mathDropdownIsA ? steps.get(msi).mathACh : steps.get(msi).mathBCh) : 1;
            List<String> opts = buildMathSrcOptions(mch);
            int max = Math.max(0, opts.size() - DD_VIS);
            mathDropdownScroll = Math.max(0, Math.min(max, mathDropdownScroll + dir));
            return true;
        }
        if (regressMultiRow >= 0) {
            int msi = scrollOffset + regressMultiRow;
            int mch = (msi < steps.size()) ? (regressMultiIsA ? steps.get(msi).mathACh : steps.get(msi).mathBCh) : 1;
            List<String> opts = buildMathSrcOptionsNoManual(mch);
            int max = Math.max(0, opts.size() - DD_VIS);
            regressMultiScroll = Math.max(0, Math.min(max, regressMultiScroll + dir));
            return true;
        }
        if (srcPickRow >= 0) {
            List<SrcCat> cats = getSrcCatsForField(srcPickField, srcPickCh, srcPickManual, srcPickVarsOnly);
            if (srcPickCat < cats.size()) {
                int max = Math.max(0, cats.get(srcPickCat).items().size() - SRC_DD_VIS);
                srcPickItemScroll = Math.max(0, Math.min(max, srcPickItemScroll + dir));
            }
            return true;
        }
        if (typeDropdownRow >= 0) {
            int max = Math.max(0, buildAvailableTypes().length - TYPE_DD_VIS);
            typeDropdownScroll = Math.max(0, Math.min(max, typeDropdownScroll + dir));
            return true;
        }

        // Scroll over a row's cycle button to step its value without repeated clicking.
        int cycleDir = dy > 0 ? 1 : -1;
        for (RowWidgets row : rows) {
            if (row.handleScrollCycle(mx, my, cycleDir)) return true;
        }

        int maxOff = Math.max(0, steps.size() - VISIBLE);
        int newOff = Math.max(0, Math.min(maxOff, scrollOffset + dir));
        if (newOff != scrollOffset) { scrollOffset = newOff; activeRowIdx = -1; refreshAllRows(); return true; }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (revertDialog != null && revertDialog.isOpen())
            return revertDialog.keyPressed(keyCode);
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (showNoNameDialog) { showNoNameDialog = false; return true; }
            if (confirmOverwriteName != null) { confirmOverwriteName = null; return true; }
            if (loadDropdownOpen)  { loadDropdownOpen  = false; return true; }
            if (mathDropdownRow >= 0) { mathDropdownRow = -1; return true; }
            if (mathOpDropdownRow >= 0) { mathOpDropdownRow = -1; return true; }
            if (regressMultiRow >= 0) { regressMultiRow = -1; return true; }
            if (srcPickRow >= 0) { srcPickRow = -1; return true; }
            if (typeDropdownRow >= 0) { typeDropdownRow = -1; return true; }
            onClose(); return true;
        }
        if (MenuNav.handleTabBack(this, keyCode, keyboardPos)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void onRunStop() {
        if (running) ModPackets.sendStopSequencer(keyboardPos);
        else         ModPackets.sendSaveAndRunSequencer(keyboardPos, steps, true);
    }

    private void onSaveFile() {
        String name = sanitizeName(saveName.getValue());
        if (name.isEmpty()) { showNoNameDialog = true; return; }
        Path file = getSaveDir().resolve(name + ".seq");
        if (Files.exists(file)) {
            confirmOverwriteName = name;
        } else {
            saveToFile(name);
        }
    }

    private void onLoadFile() {
        loadDropdownFiles  = listSaveFiles();
        loadDropdownScroll = 0;
        loadDropdownOpen   = !loadDropdownFiles.isEmpty();
    }

    // ── File I/O ──────────────────────────────────────────────────────────────

    private Path getSaveDir() {
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("universal_keyboard");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        return dir;
    }

    private List<String> listSaveFiles() {
        try (Stream<Path> s = Files.list(getSaveDir())) {
            return s.filter(p -> p.toString().endsWith(".seq"))
                    .map(p -> p.getFileName().toString().replaceFirst("\\.seq$", ""))
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
        } catch (IOException e) { return new ArrayList<>(); }
    }

    private void saveToFile(String name) {
        ListTag list = new ListTag();
        for (SequencerStep step : steps) list.add(step.save());
        CompoundTag root = new CompoundTag();
        root.put("steps", list);
        try (var out = Files.newOutputStream(getSaveDir().resolve(name + ".seq"))) {
            NbtIo.writeCompressed(root, out);
        } catch (IOException e) {
            UniversalKeyboardMod.LOGGER.error("Failed to save sequencer file: {}", e.getMessage());
        }
    }

    private void loadFromFile(String name) {
        try (var in = Files.newInputStream(getSaveDir().resolve(name + ".seq"))) {
            CompoundTag root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            ListTag list = root.getList("steps", Tag.TAG_COMPOUND);
            steps.clear();
            for (int i = 0; i < list.size(); i++) steps.add(SequencerStep.load(list.getCompound(i)));
            scrollOffset = 0;
            refreshAllRows();
        } catch (IOException e) {
            UniversalKeyboardMod.LOGGER.error("Failed to load sequencer file: {}", e.getMessage());
        }
    }

    private static String sanitizeName(String raw) {
        return raw.trim().replaceAll("[^a-zA-Z0-9 _\\-]", "").trim();
    }

    // ── RowWidgets ────────────────────────────────────────────────────────────

    private class RowWidgets {
        final int rowIdx;

        Button channelBtn, typeBtn, deleteBtn;
        Button  methodBtn;
        EditBox valueInput;
        Button  rsDirBtn;
        EditBox rsSignalInput;
        EditBox typeTextInput;
        Button  typeEnterBtn;
        Button  ifGetterBtn, ifOpBtn, ifSkipBtn, ifModeBtn;
        EditBox ifValueInput, ifJumpInput;
        Button  sourceBtn, getterBtn;
        EditBox opInput;
        EditBox delayInput;
        EditBox jumpInput;
        Button  mathDestBtn, mathASourceBtn, mathAChBtn, mathOpBtn, mathBSourceBtn, mathBChBtn;
        EditBox mathAInput, mathBInput;
        Button  regressModeBtn, regressDest2Btn;

        RowWidgets(int rowIdx) {
            this.rowIdx = rowIdx;
            int rowY = rowAreaY + rowIdx * (ROW_H + ROW_GAP);
            int cx   = panelX + PAD;
            int ctx  = cx + COL_CTX;

            channelBtn = DarkButton.make(Component.literal("-"), b -> cycleChannel(1),
                    cx + COL_CH, rowY + 1, 16, BTN_H);
            addRenderableWidget(channelBtn);

            typeBtn = DarkButton.make(Component.literal(""), b -> {
                mathDropdownRow = -1;
                loadDropdownOpen = false;
                typeDropdownRow    = (typeDropdownRow == rowIdx) ? -1 : rowIdx;
                typeDropdownScroll = 0;
            }, cx + COL_TYPE, rowY + 1, 72, BTN_H);
            addRenderableWidget(typeBtn);

            deleteBtn = DarkButton.make(Component.literal("×"), b -> deleteStep(scrollOffset + rowIdx),
                    cx + COL_DEL, rowY + 1, 14, BTN_H);
            addRenderableWidget(deleteBtn);

            // SET_VALUE
            methodBtn = DarkButton.make(Component.literal(""), b -> cycleMethod(1),
                    ctx, rowY + 1, 120, BTN_H);
            addRenderableWidget(methodBtn);
            valueInput = makeBox(ctx + 124, rowY, 156, I18n.get("gui.universalkeyboard.hint.value_or_variable"),
                    str -> { int si = scrollOffset + rowIdx; if (si < steps.size()) steps.get(si).setValueStr = str; });

            // SET_REDSTONE
            rsDirBtn = DarkButton.make(Component.literal(""), b -> cycleRedstoneDir(1),
                    ctx, rowY + 1, 90, BTN_H);
            addRenderableWidget(rsDirBtn);
            rsSignalInput = makeBox(ctx + 94, rowY, 80, I18n.get("gui.universalkeyboard.hint.signal_or_variable"),
                    str -> { int si = scrollOffset + rowIdx; if (si < steps.size()) steps.get(si).redstoneOutSignalStr = str; });

            // TYPE_TEXT
            typeTextInput = makeBox(ctx, rowY, 210, I18n.get("gui.universalkeyboard.hint.text_to_type"),
                    str -> { int si = scrollOffset + rowIdx; if (si < steps.size()) steps.get(si).typeTextStr = str; });
            typeTextInput.setMaxLength(200);
            typeEnterBtn = DarkButton.make(Component.literal("↵"), b -> toggleTypeEnter(),
                    ctx + 214, rowY + 1, 66, BTN_H);
            addRenderableWidget(typeEnterBtn);

            // IF
            ifGetterBtn = DarkButton.make(Component.literal(""), b -> {
                int si2 = scrollOffset + rowIdx;
                if (si2 < steps.size())
                    openSrcPick("IF_GETTER", steps.get(si2).channel, false, false,
                                panelX + PAD + COL_CTX);
            }, ctx, rowY + 1, 80, BTN_H);
            addRenderableWidget(ifGetterBtn);
            ifOpBtn = DarkButton.make(Component.literal(">"), b -> cycleIfOp(1),
                    ctx + 84, rowY + 1, 28, BTN_H);
            addRenderableWidget(ifOpBtn);
            ifValueInput = makeBox(ctx + 116, rowY, 64, I18n.get("gui.universalkeyboard.hint.cond_value"),
                    str -> { int si = scrollOffset + rowIdx; if (si < steps.size()) steps.get(si).ifValueStr = str; });
            ifModeBtn = DarkButton.make(Component.literal("skip"), b -> toggleIfMode(),
                    ctx + 184, rowY + 1, 34, BTN_H);
            addRenderableWidget(ifModeBtn);
            ifSkipBtn = DarkButton.make(Component.literal("×1"), b -> cycleIfSkip(1),
                    ctx + 222, rowY + 1, 60, BTN_H);
            addRenderableWidget(ifSkipBtn);
            ifJumpInput = makeBox(ctx + 222, rowY, 60, I18n.get("gui.universalkeyboard.hint.step_number"),
                    str -> {
                        int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
                        try { steps.get(si).jumpTarget = Math.max(1, Math.min(MAX_STEPS, Integer.parseInt(str.trim()))); }
                        catch (NumberFormatException ignored) {}
                    });
            ifJumpInput.setMaxLength(3);

            // CONDITION
            sourceBtn = DarkButton.make(Component.literal(""), b -> {
                int si2 = scrollOffset + rowIdx;
                if (si2 < steps.size())
                    openSrcPick("CONDITION", steps.get(si2).channel, false, false,
                                panelX + PAD + COL_CTX);
            }, ctx, rowY + 1, 66, BTN_H);
            addRenderableWidget(sourceBtn);
            getterBtn = DarkButton.make(Component.literal(""), b -> cycleGetter(1),
                    ctx + 70, rowY + 1, 80, BTN_H);
            addRenderableWidget(getterBtn);
            opInput = makeBox(ctx + 154, rowY, 124, ">0",
                    str -> { int si = scrollOffset + rowIdx; if (si < steps.size()) parseOpInput(steps.get(si), str); });

            // DELAY
            delayInput = makeBox(ctx, rowY, 80, "1.0",
                    str -> { int si = scrollOffset + rowIdx; if (si < steps.size()) steps.get(si).delaySecondsStr = str; });

            // JUMP
            jumpInput = makeBox(ctx + 56, rowY, 60, "1",
                    str -> {
                        int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
                        try { steps.get(si).jumpTarget = Math.max(1, Math.min(MAX_STEPS, Integer.parseInt(str.trim()))); }
                        catch (NumberFormatException ignored) {}
                    });
            jumpInput.setMaxLength(3);

            // MATH
            mathDestBtn = DarkButton.make(Component.literal("V1"), b -> {
                int si2 = scrollOffset + rowIdx;
                if (si2 < steps.size())
                    openSrcPick("MATH_DEST", steps.get(si2).mathACh, false, true,
                                panelX + PAD + COL_CTX);
            }, ctx, rowY + 1, 36, BTN_H);
            addRenderableWidget(mathDestBtn);
            mathASourceBtn = DarkButton.make(Component.literal("src A..."), b -> {
                int si2 = scrollOffset + rowIdx;
                if (si2 < steps.size() && steps.get(si2).type == Type.REGRESS
                        && steps.get(si2).regressMode.equals("SAMPLE"))
                    openRegressMultiDropdown(true);
                else if (si2 < steps.size())
                    openSrcPick("MATH_A", steps.get(si2).mathACh, true, false,
                                panelX + PAD + COL_CTX + 48);
            }, ctx + 48, rowY + 1, 64, BTN_H);
            addRenderableWidget(mathASourceBtn);
            mathAInput = makeBox(ctx + 48, rowY, 64, "A (# V1 RS:N getter)",
                    str -> { int si = scrollOffset + rowIdx; if (si < steps.size()) steps.get(si).mathA = str; });
            mathAChBtn = DarkButton.make(Component.literal("1"), b -> cycleMathACh(1),
                    ctx + 114, rowY + 1, 22, BTN_H);
            addRenderableWidget(mathAChBtn);
            mathOpBtn = DarkButton.make(Component.literal("+"), b -> openMathOpDropdown(),
                    ctx + 140, rowY + 1, 36, BTN_H);
            addRenderableWidget(mathOpBtn);
            mathBSourceBtn = DarkButton.make(Component.literal("src B..."), b -> {
                int si2 = scrollOffset + rowIdx;
                if (si2 < steps.size() && steps.get(si2).type == Type.REGRESS
                        && steps.get(si2).regressMode.equals("SAMPLE"))
                    openRegressMultiDropdown(false);
                else if (si2 < steps.size())
                    openSrcPick("MATH_B", steps.get(si2).mathBCh, true, false,
                                panelX + PAD + COL_CTX + 180);
            }, ctx + 180, rowY + 1, 64, BTN_H);
            addRenderableWidget(mathBSourceBtn);
            mathBInput = makeBox(ctx + 180, rowY, 64, I18n.get("gui.universalkeyboard.hint.math_src_b"),
                    str -> { int si = scrollOffset + rowIdx; if (si < steps.size()) steps.get(si).mathB = str; });
            mathBChBtn = DarkButton.make(Component.literal("1"), b -> cycleMathBCh(1),
                    ctx + 246, rowY + 1, 22, BTN_H);
            addRenderableWidget(mathBChBtn);

            // REGRESS
            regressModeBtn = DarkButton.make(Component.literal("SAMPLE"), b -> cycleRegressMode(1),
                    ctx, rowY + 1, 46, BTN_H);
            addRenderableWidget(regressModeBtn);
            regressDest2Btn = DarkButton.make(Component.literal("V2"), b -> cycleRegressDest2(1),
                    ctx + 94, rowY + 1, 36, BTN_H);
            addRenderableWidget(regressDest2Btn);
        }

        private EditBox makeBox(int x, int rowY, int w, String hint,
                                java.util.function.Consumer<String> responder) {
            EditBox box = new EditBox(font, x, rowY + 1, w, BTN_H, Component.literal(hint));
            box.setMaxLength(64);
            box.setHint(Component.literal("§7" + hint));
            box.setResponder(str -> { if (!refreshing) responder.accept(str); });
            addRenderableWidget(box);
            return box;
        }

        // ── Cycling helpers ─────────────────────────────────────────────────

        private void cycleChannel(int dir) {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            if (!stepUsesChannel(step.type)) return;
            step.channel = wrapIdx(step.channel - 1, 16, dir) + 1;
            channelBtn.setMessage(Component.literal(String.valueOf(step.channel)));
        }

        private boolean stepUsesChannel(Type t) {
            return t == Type.SET_VALUE || t == Type.TYPE_TEXT || t == Type.IF || t == Type.CONDITION;
        }

        private void cycleType(int dir) {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            Type[] avail = buildAvailableTypes();
            if (avail.length == 0) return;
            int idx = 0;
            for (int i = 0; i < avail.length; i++) if (avail[i] == step.type) { idx = i; break; }
            step.type = avail[wrapIdx(idx, avail.length, dir)];
            refreshAllRows();
        }

        private void cycleMethod(int dir) {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            if (step.type == Type.TYPE_VARIABLE) {
                String v = SequencerStep.isVar(step.setValueStr) ? step.setValueStr : "V1";
                step.setValueStr = "V" + (wrapIdx(SequencerStep.varIndex(v), SequencerStep.VAR_COUNT, dir) + 1);
                methodBtn.setMessage(Component.literal(step.setValueStr));
                return;
            }
            List<String[]> setters = settersFor(step.channel);
            if (setters.isEmpty()) return;
            int idx = indexOfSetter(step.setMethod, setters);
            if (idx < 0) idx = 0;
            step.setMethod = setters.get(wrapIdx(idx, setters.size(), dir))[0];
            methodBtn.setMessage(Component.literal(step.setMethod));
        }

        private void toggleTypeEnter() {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            step.typeTextEnter = !step.typeTextEnter;
            typeEnterBtn.setMessage(Component.literal(step.typeTextEnter ? "↵ on" : "↵ off"));
        }

        private void cycleRedstoneDir(int dir) {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);

            int wirelessCount = getWirelessCount();
            int linkCount = getLinkFreqCount();
            int totalSlots = RS_DIRS.length + wirelessCount + linkCount;

            int currentPos;
            if (step.linkOutIdx > 0 && step.linkOutIdx <= linkCount) {
                currentPos = RS_DIRS.length + wirelessCount + step.linkOutIdx - 1;
            } else if (step.wirelessOutIdx > 0 && step.wirelessOutIdx <= wirelessCount) {
                currentPos = RS_DIRS.length + step.wirelessOutIdx - 1;
            } else {
                currentPos = 0;
                for (int i = 0; i < RS_DIRS.length; i++) if (RS_DIRS[i] == step.redstoneOutDir) { currentPos = i; break; }
            }
            int nextPos = wrapIdx(currentPos, totalSlots, dir);

            if (nextPos < RS_DIRS.length) {
                step.wirelessOutIdx = 0;
                step.linkOutIdx = 0;
                step.redstoneOutDir = RS_DIRS[nextPos];
            } else if (nextPos < RS_DIRS.length + wirelessCount) {
                step.wirelessOutIdx = nextPos - RS_DIRS.length + 1;
                step.linkOutIdx = 0;
            } else {
                step.linkOutIdx = nextPos - RS_DIRS.length - wirelessCount + 1;
                step.wirelessOutIdx = 0;
            }
            rsDirBtn.setMessage(Component.literal("→ " + redstoneTargetLabel(step)));
        }

        private String redstoneTargetLabel(SequencerStep step) {
            if (step.linkOutIdx > 0) return "W" + step.linkOutIdx;
            if (step.wirelessOutIdx > 0) return "L" + step.wirelessOutIdx;
            return step.redstoneOutDir.getName().toUpperCase();
        }

        private void cycleIfGetter(int dir) {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            List<String> list = buildIfGetterList(step.channel);
            if (list.isEmpty()) return;
            int idx = list.indexOf(step.ifGetter);
            if (idx < 0) idx = 0;
            step.ifGetter = list.get(wrapIdx(idx, list.size(), dir));
            ifGetterBtn.setMessage(Component.literal(fitLabel(step.ifGetter, 72)));
        }

        private void cycleIfOp(int dir) {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            int idx = Arrays.asList(IF_OPS).indexOf(step.ifOp);
            if (idx < 0) idx = 0;
            step.ifOp = IF_OPS[wrapIdx(idx, IF_OPS.length, dir)];
            ifOpBtn.setMessage(Component.literal(step.ifOp));
        }

        private void cycleIfSkip(int dir) {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            step.ifSkipCount = wrapIdx(step.ifSkipCount - 1, 16, dir) + 1;
            ifSkipBtn.setMessage(Component.literal("×" + step.ifSkipCount));
        }

        private void toggleIfMode() {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            step.ifGoTo = !step.ifGoTo;
            refresh();
        }

        private void openMathDropdown(boolean isA) {
            typeDropdownRow  = -1;
            loadDropdownOpen = false;
            mathDropdownRow    = rowIdx;
            mathDropdownIsA    = isA;
            mathDropdownScroll = 0;
        }

        private void openMathOpDropdown() {
            typeDropdownRow   = -1;
            mathDropdownRow   = -1;
            regressMultiRow   = -1;
            loadDropdownOpen  = false;
            mathOpDropdownRow = (mathOpDropdownRow == rowIdx) ? -1 : rowIdx;
            mathOpCatHover    = 0;
        }

        private void openRegressMultiDropdown(boolean isA) {
            typeDropdownRow   = -1;
            mathDropdownRow   = -1;
            mathOpDropdownRow = -1;
            loadDropdownOpen  = false;
            boolean sameBtn = regressMultiRow == rowIdx && regressMultiIsA == isA;
            regressMultiRow    = sameBtn ? -1 : rowIdx;
            regressMultiIsA    = isA;
            regressMultiScroll = 0;
        }

        private void openSrcPick(String field, int ch, boolean includeManual, boolean varsOnly, int btnX) {
            typeDropdownRow   = -1;
            mathDropdownRow   = -1;
            mathOpDropdownRow = -1;
            regressMultiRow   = -1;
            loadDropdownOpen  = false;
            boolean same = srcPickRow == rowIdx && srcPickField.equals(field);
            srcPickRow        = same ? -1 : rowIdx;
            srcPickField      = field;
            srcPickCat        = 0;
            srcPickItemScroll = 0;
            srcPickCh         = ch;
            srcPickManual     = includeManual;
            srcPickVarsOnly   = varsOnly;
            srcPickBtnX       = btnX;
        }

        private List<String> buildIfGetterList(int channel) {
            List<String> list = new ArrayList<>(Arrays.asList(SequencerStep.RS_INPUT_GETTER_NAMES));
            for (int i = 1; i <= SequencerStep.VAR_COUNT; i++) list.add("V" + i);
            int wc = getWirelessCount();
            for (int w = 1; w <= wc; w++) list.add("L" + w);
            list.addAll(gettersFor(channel));
            return list;
        }

        private void cycleSource(int dir) {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            boolean ccPresent = dev.bennethogan.universalkeyboard.compat.PeripheralHelper.isCCPresent();
            ConditionSource[] sources = ConditionSource.values();
            int next = step.conditionSource.ordinal();
            do {
                next = wrapIdx(next, sources.length, dir);
            } while (!ccPresent && sources[next] == ConditionSource.PERIPHERAL);
            step.conditionSource = sources[next];
            refresh();
        }

        private void cycleGetter(int dir) {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            List<String> getters = gettersFor(step.channel);
            if (getters.isEmpty()) return;
            int idx = getters.indexOf(step.conditionGetter);
            if (idx < 0) idx = 0;
            step.conditionGetter = getters.get(wrapIdx(idx, getters.size(), dir));
            getterBtn.setMessage(Component.literal(step.conditionGetter));
        }

        private void cycleMathDest(int dir) {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            String d = SequencerStep.isVar(step.mathDest) ? step.mathDest : "V1";
            step.mathDest = "V" + (wrapIdx(SequencerStep.varIndex(d), SequencerStep.VAR_COUNT, dir) + 1);
            mathDestBtn.setMessage(Component.literal(step.mathDest));
        }

        private void cycleRegressMode(int dir) {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            String[] modes = {"RESET", "SAMPLE", "SOLVE"};
            int idx = 0;
            for (int i = 0; i < modes.length; i++) if (modes[i].equals(step.regressMode)) { idx = i; break; }
            step.regressMode = modes[wrapIdx(idx, modes.length, dir)];
            refresh();
        }

        private void cycleRegressDest2(int dir) {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            String d = SequencerStep.isVar(step.regressDest2) ? step.regressDest2 : "V2";
            step.regressDest2 = "V" + (wrapIdx(SequencerStep.varIndex(d), SequencerStep.VAR_COUNT, dir) + 1);
            regressDest2Btn.setMessage(Component.literal(step.regressDest2));
        }

        private void cycleMathACh(int dir) {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            step.mathACh = wrapIdx(step.mathACh - 1, 16, dir) + 1;
            mathAChBtn.setMessage(Component.literal(String.valueOf(step.mathACh)));
        }

        private void cycleMathBCh(int dir) {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            step.mathBCh = wrapIdx(step.mathBCh - 1, 16, dir) + 1;
            mathBChBtn.setMessage(Component.literal(String.valueOf(step.mathBCh)));
        }

        private void cycleMathOp(int dir) {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            List<String> all = new ArrayList<>();
            for (String[] cat : OP_CAT_OPS) all.addAll(Arrays.asList(cat));
            int idx = all.indexOf(step.mathOp);
            if (idx < 0) idx = 0;
            step.mathOp = all.get(wrapIdx(idx, all.size(), dir));
            mathOpBtn.setMessage(Component.literal(step.mathOp));
            boolean unary = isUnaryOp(step.mathOp);
            mathBInput.visible     = !unary && step.mathBManual;
            mathBSourceBtn.visible = !unary && !step.mathBManual;
            mathBChBtn.visible     = !unary && !step.mathBManual && srcNeedsChannel(step.mathB, false);
        }

        /** Scroll over a math source button to cycle its selected source directly. */
        private void cycleMathSource(boolean isA, int dir) {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            int ch = isA ? step.mathACh : step.mathBCh;
            List<String> opts = buildMathSrcOptions(ch);
            if (opts.isEmpty()) return;
            String manual = I18n.get("gui.universalkeyboard.label.manual_input");
            String cur = isA ? (step.mathAManual ? manual : step.mathA)
                             : (step.mathBManual ? manual : step.mathB);
            int idx = opts.indexOf(cur);
            if (idx < 0) idx = 0;
            String sel = opts.get(wrapIdx(idx, opts.size(), dir));
            if (sel.equals(manual)) {
                if (isA) step.mathAManual = true; else step.mathBManual = true;
            } else {
                if (isA) { step.mathA = sel; step.mathAManual = false; }
                else     { step.mathB = sel; step.mathBManual = false; }
            }
            refresh();
        }

        // ── Scroll-to-cycle ─────────────────────────────────────────────────

        /** If the mouse is over one of this row's cycle buttons, step it by dir. */
        boolean handleScrollCycle(double mx, double my, int dir) {
            if (rowIdx != activeRowIdx) return false;
            int si = scrollOffset + rowIdx;
            if (si >= steps.size()) return false;
            if (hit(channelBtn, mx, my))     { cycleChannel(dir);           return true; }
            if (hit(typeBtn, mx, my))        { cycleType(dir);              return true; }
            if (hit(methodBtn, mx, my))      { cycleMethod(dir);            return true; }
            if (hit(rsDirBtn, mx, my))       { cycleRedstoneDir(dir);       return true; }
            if (hit(ifGetterBtn, mx, my))    { cycleIfGetter(dir);          return true; }
            if (hit(ifOpBtn, mx, my))        { cycleIfOp(dir);              return true; }
            if (hit(ifSkipBtn, mx, my))      { cycleIfSkip(dir);            return true; }
            if (hit(sourceBtn, mx, my))      { cycleSource(dir);            return true; }
            if (hit(getterBtn, mx, my))      { cycleGetter(dir);            return true; }
            if (hit(regressModeBtn, mx, my))  { cycleRegressMode(dir);       return true; }
            if (hit(regressDest2Btn, mx, my)) { cycleRegressDest2(dir);      return true; }
            if (hit(mathDestBtn, mx, my))    { cycleMathDest(dir);          return true; }
            if (hit(mathASourceBtn, mx, my)) { cycleMathSource(true, dir);  return true; }
            if (hit(mathAChBtn, mx, my))     { cycleMathACh(dir);           return true; }
            if (hit(mathOpBtn, mx, my))      { cycleMathOp(dir);            return true; }
            if (hit(mathBSourceBtn, mx, my)) { cycleMathSource(false, dir); return true; }
            if (hit(mathBChBtn, mx, my))     { cycleMathBCh(dir);           return true; }
            return false;
        }

        private boolean hit(net.minecraft.client.gui.components.AbstractWidget w, double mx, double my) {
            return w.visible && w.isMouseOver(mx, my);
        }

        // ── Refresh ─────────────────────────────────────────────────────────

        void refresh() {
            int si  = scrollOffset + rowIdx;
            boolean has = si < steps.size();

            channelBtn.visible = typeBtn.visible = deleteBtn.visible = has;
            methodBtn.visible = valueInput.visible = false;
            rsDirBtn.visible = rsSignalInput.visible = false;
            typeTextInput.visible = typeEnterBtn.visible = false;
            ifGetterBtn.visible = ifOpBtn.visible = ifValueInput.visible = false;
            ifSkipBtn.visible = ifModeBtn.visible = ifJumpInput.visible = false;
            sourceBtn.visible = getterBtn.visible = opInput.visible = false;
            delayInput.visible = jumpInput.visible = false;
            mathDestBtn.visible = mathASourceBtn.visible = mathAInput.visible = false;
            mathAChBtn.visible = mathOpBtn.visible = false;
            mathBSourceBtn.visible = mathBInput.visible = mathBChBtn.visible = false;
            regressModeBtn.visible = regressDest2Btn.visible = false;

            if (!has) return;

            SequencerStep step = steps.get(si);
            typeBtn.setMessage(Component.literal(step.type.coloredLabel()));
            boolean usesChannel = stepUsesChannel(step.type);
            channelBtn.active = usesChannel;
            channelBtn.setMessage(Component.literal(usesChannel ? String.valueOf(step.channel) : "-"));

            refreshing = true;
            switch (step.type) {
                case SET_VALUE -> {
                    methodBtn.visible = valueInput.visible = true;
                    List<String[]> stepSetters = settersFor(step.channel);
                    if (step.setMethod.isEmpty() && !stepSetters.isEmpty())
                        step.setMethod = stepSetters.get(0)[0];
                    methodBtn.setMessage(Component.literal(
                            step.setMethod.isEmpty() ? I18n.get("gui.universalkeyboard.label.no_setters") : step.setMethod));
                    valueInput.setValue(step.setValueStr);
                }
                case SET_REDSTONE -> {
                    rsDirBtn.visible = rsSignalInput.visible = true;
                    rsDirBtn.setMessage(Component.literal("→ " + redstoneTargetLabel(step)));
                    rsSignalInput.setValue(step.redstoneOutSignalStr);
                }
                case TYPE_TEXT -> {
                    typeTextInput.visible = typeEnterBtn.visible = true;
                    typeTextInput.setValue(step.typeTextStr);
                    typeEnterBtn.setMessage(Component.literal(step.typeTextEnter ? "↵ on" : "↵ off"));
                }
                case IF -> {
                    ifGetterBtn.visible = ifOpBtn.visible = ifValueInput.visible = ifModeBtn.visible = true;
                    boolean goTo = step.ifGoTo;
                    ifSkipBtn.visible  = !goTo;
                    ifJumpInput.visible = goTo;
                    List<String> list = buildIfGetterList(step.channel);
                    if (step.ifGetter.isEmpty() && !list.isEmpty()) step.ifGetter = list.get(0);
                    ifGetterBtn.setMessage(Component.literal(step.ifGetter.isEmpty() ? I18n.get("gui.universalkeyboard.label.no_getter") : fitLabel(step.ifGetter, 72)));
                    ifOpBtn.setMessage(Component.literal(step.ifOp));
                    ifValueInput.setValue(step.ifValueStr);
                    ifModeBtn.setMessage(Component.literal(goTo ? "→step" : "skip"));
                    ifSkipBtn.setMessage(Component.literal("×" + step.ifSkipCount));
                    if (goTo) ifJumpInput.setValue(String.valueOf(step.jumpTarget));
                }
                case CONDITION -> {
                    sourceBtn.visible = opInput.visible = true;
                    getterBtn.visible = false;
                    boolean periph = step.conditionSource == ConditionSource.PERIPHERAL;
                    String condLabel = periph
                            ? (step.conditionGetter.isEmpty() ? "source..." : fitLabel(step.conditionGetter, 58))
                            : step.conditionSource.label;
                    sourceBtn.setMessage(Component.literal(condLabel));
                    int cx2 = panelX + PAD;
                    opInput.setX(cx2 + COL_CTX + 70);
                    opInput.setWidth(208);
                    opInput.setValue(step.conditionOp + step.conditionThresholdStr);
                }
                case DELAY -> { delayInput.visible = true; delayInput.setValue(step.delaySecondsStr); }
                case JUMP  -> { jumpInput.visible  = true; jumpInput.setValue(String.valueOf(step.jumpTarget)); }
                case MATH  -> {
                    mathDestBtn.setX(panelX + PAD + COL_CTX);
                    mathDestBtn.visible = mathOpBtn.visible = true;
                    boolean unary   = isUnaryOp(step.mathOp);
                    boolean aManual = step.mathAManual;
                    boolean bManual = step.mathBManual;
                    mathAInput.visible     = aManual;
                    mathASourceBtn.visible = !aManual;
                    mathAChBtn.visible     = !aManual && srcNeedsChannel(step.mathA, false);
                    mathBInput.visible     = !unary && bManual;
                    mathBSourceBtn.visible = !unary && !bManual;
                    mathBChBtn.visible     = !unary && !bManual && srcNeedsChannel(step.mathB, false);
                    String dest = (step.mathDest == null || step.mathDest.isEmpty()) ? "V1" : step.mathDest;
                    step.mathDest = dest;
                    mathDestBtn.setMessage(Component.literal(dest));
                    if (aManual) mathAInput.setValue(step.mathA);
                    else mathASourceBtn.setMessage(Component.literal(step.mathA.isEmpty() ? "src A..." : fitLabel(step.mathA, 56)));
                    mathAChBtn.setMessage(Component.literal(String.valueOf(step.mathACh)));
                    mathOpBtn.setMessage(Component.literal(step.mathOp));
                    if (!unary) {
                        if (bManual) mathBInput.setValue(step.mathB);
                        else mathBSourceBtn.setMessage(Component.literal(step.mathB.isEmpty() ? "src B..." : fitLabel(step.mathB, 56)));
                        mathBChBtn.setMessage(Component.literal(String.valueOf(step.mathBCh)));
                    }
                }
                case TYPE_VARIABLE -> {
                    methodBtn.visible = typeEnterBtn.visible = true;
                    if (!SequencerStep.isVar(step.setValueStr)) step.setValueStr = "V1";
                    methodBtn.setMessage(Component.literal(step.setValueStr));
                    typeEnterBtn.setMessage(Component.literal(step.typeTextEnter ? "↵ on" : "↵ off"));
                }
                case REGRESS -> {
                    int ctx = panelX + PAD + COL_CTX;
                    regressModeBtn.visible = true;
                    regressModeBtn.setMessage(Component.literal(step.regressMode));
                    if (step.regressMode.equals("SAMPLE")) {
                        mathAInput.visible = true;
                        mathBInput.visible = true;
                        mathAInput.setValue(step.mathA);
                        mathBInput.setValue(step.mathB);
                    } else if (step.regressMode.equals("SOLVE")) {
                        mathDestBtn.setX(ctx + 52);
                        mathDestBtn.visible = regressDest2Btn.visible = true;
                        String slope = SequencerStep.isVar(step.mathDest) ? step.mathDest : "V1";
                        step.mathDest = slope;
                        if (!SequencerStep.isVar(step.regressDest2)) step.regressDest2 = "V2";
                        mathDestBtn.setMessage(Component.literal(slope));
                        regressDest2Btn.setMessage(Component.literal(step.regressDest2));
                    }
                }
                case CYCLE, END -> {}
            }
            refreshing = false;
        }

        private int indexOfSetter(String name, List<String[]> setters) {
            for (int i = 0; i < setters.size(); i++)
                if (setters.get(i)[0].equals(name)) return i;
            return -1;
        }
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    private static boolean isUnaryOp(String op) {
        for (int c = 0; c < OP_CAT_OPS.length; c++)
            for (int i = 0; i < OP_CAT_OPS[c].length; i++)
                if (OP_CAT_OPS[c][i].equals(op)) return OP_CAT_UNARY[c][i];
        return false;
    }

    /** Modular step of an index by dir (+1/-1), wrapping at both ends. */
    private static int wrapIdx(int idx, int size, int dir) {
        if (size <= 0) return 0;
        return ((idx + dir) % size + size) % size;
    }

    private static void parseOpInput(SequencerStep step, String raw) {
        raw = raw.trim();
        if (raw.startsWith(">") || raw.startsWith("<") || raw.startsWith("=")) {
            step.conditionOp           = raw.substring(0, 1);
            step.conditionThresholdStr = raw.substring(1).trim();
        } else {
            step.conditionOp           = ">";
            step.conditionThresholdStr = raw;
        }
    }
}
