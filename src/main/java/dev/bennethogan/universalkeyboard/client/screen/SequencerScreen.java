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
    private static final int DD_VIS    = 3;   // items visible in any dropdown at once

    private static final int COL_NUM  = 0;
    private static final int COL_CH   = 16;
    private static final int COL_TYPE = 34;
    private static final int COL_CTX  = 108;
    private static final int COL_DEL  = 394;

    private static final String[]  MATH_OPS   = {"+", "-", "*", "/", "%", "min", "max", "abs", "neg", "round", "floor", "ceil"};
    private static final boolean[] MATH_UNARY = {false, false, false, false, false, false, false, true, true, true, true, true};
    private static final String[]   IF_OPS    = {">", ">=", "=", "<=", "<", "!="};
    private static final Direction[] RS_DIRS  = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    // ── State ─────────────────────────────────────────────────────────────────
    final BlockPos keyboardPos;
    private final List<SequencerStep> steps;
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

    // Overwrite confirmation
    private String  confirmOverwriteName = null;
    // No-name dialog
    private boolean showNoNameDialog = false;

    // Layout cache (set in init)
    private int panelX, panelY, panelH, rowAreaY;
    private int bottomBtnW, bottomBtnY, loadBtnX;

    private final List<RowWidgets> rows = new ArrayList<>();
    private Button  runStopBtn;
    private EditBox saveName;

    boolean refreshing = false;

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

        // Save-name input centred under title text
        int nameW = 200;
        saveName  = new EditBox(font, panelX + (PANEL_W - nameW) / 2,
                panelY + PAD + 12 + 4, nameW, BTN_H, Component.empty());
        saveName.setMaxLength(40);
        saveName.setHint(Component.literal(I18n.get("gui.universalkeyboard.hint.save_name")));
        addRenderableWidget(saveName);

        for (int r = 0; r < VISIBLE; r++) rows.add(new RowWidgets(r));

        // Five-button bottom row: Run/Stop | Save | Save File | Load File | Close
        bottomBtnW = (PANEL_W - PAD * 2 - 4 * 4) / 5; // ~78 px each
        bottomBtnY = panelY + panelH - PAD - BTN_H;
        int gap = 4;

        runStopBtn = Button.builder(runStopLabel(), b -> onRunStop())
                .pos(panelX + PAD, bottomBtnY).size(bottomBtnW, BTN_H).build();
        addRenderableWidget(runStopBtn);

        addRenderableWidget(Button.builder(Component.translatable("gui.universalkeyboard.btn.save"), b -> onSave())
                .pos(panelX + PAD + (bottomBtnW + gap), bottomBtnY).size(bottomBtnW, BTN_H).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.universalkeyboard.btn.save_file"), b -> onSaveFile())
                .pos(panelX + PAD + (bottomBtnW + gap) * 2, bottomBtnY).size(bottomBtnW, BTN_H).build());

        loadBtnX = panelX + PAD + (bottomBtnW + gap) * 3;
        addRenderableWidget(Button.builder(Component.translatable("gui.universalkeyboard.btn.load_file"), b -> onLoadFile())
                .pos(loadBtnX, bottomBtnY).size(bottomBtnW, BTN_H).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.universalkeyboard.btn.close"), b -> onClose())
                .pos(panelX + PAD + (bottomBtnW + gap) * 4, bottomBtnY).size(bottomBtnW, BTN_H).build());

        refreshAllRows();
        ModPackets.sendSequencerWatch(keyboardPos, true);
    }

    @Override
    public void onClose() {
        ModPackets.sendSequencerWatch(keyboardPos, false);
        super.onClose();
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
        if (typeDropdownRow < 0 && mathDropdownRow < 0 && !loadDropdownOpen
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
        if (confirmOverwriteName != null) renderConfirmDialog(g, mx, my);
        if (showNoNameDialog) renderNoNameDialog(g, mx, my);
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
        int vis   = Math.min(DD_VIS, available.length);
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
        int ddW   = bottomBtnW + 20;
        int ddX   = loadBtnX;
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
            case SET_VALUE              -> 0xFF2299BB;
            case SET_REDSTONE           -> 0xFFBB2222;
            case TYPE_TEXT,
                 TYPE_VARIABLE          -> 0xFFBB22BB;
            case IF                     -> 0xFFBBAA00;
            case CONDITION              -> 0xFFBB7700;
            case DELAY                  -> 0xFF777777;
            case JUMP                   -> 0xFFAAAAAA;
            case MATH                   -> 0xFF22BB22;
            case CYCLE                  -> 0xFF8822BB;
            case END                    -> 0xFF444444;
        };
    }

    private static int stepRowBg(Type type) {
        return switch (type) {
            case SET_VALUE              -> 0x66003344;
            case SET_REDSTONE           -> 0x66330000;
            case TYPE_TEXT,
                 TYPE_VARIABLE          -> 0x66330033;
            case IF                     -> 0x66332200;
            case CONDITION              -> 0x66221100;
            case DELAY                  -> 0x66111111;
            case JUMP                   -> 0x66222222;
            case MATH                   -> 0x66003300;
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
        if (runStopBtn != null) runStopBtn.setMessage(runStopLabel());
    }

    private Component runStopLabel() { return Component.literal(running ? "■ Stop" : "▶ Run"); }

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
        List<Type> list = new ArrayList<>();
        for (Type t : Type.values()) {
            if (t == Type.TYPE_TEXT || t == Type.TYPE_VARIABLE) continue; // added conditionally below
            if (t == Type.SET_VALUE && !ccPresent) continue;
            list.add(t);
        }
        if (ccPresent && hasLinkedComputer()) {
            list.add(Type.TYPE_TEXT);
            list.add(Type.TYPE_VARIABLE);
        }
        return list.toArray(new Type[0]);
    }

    private boolean hasLinkedComputer() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        var be = mc.level.getBlockEntity(keyboardPos);
        if (!(be instanceof dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity kb)) return false;
        for (var positions : kb.getAllChannelTargets().values()) {
            for (BlockPos pos : positions) {
                var target = mc.level.getBlockEntity(pos);
                if (target != null && dev.bennethogan.universalkeyboard.compat.KeyboardMode.isCCComputer(target))
                    return true;
            }
        }
        return false;
    }

    private List<String> gettersFor(int channel) {
        return gettersByChannel.getOrDefault(channel, availableGetters);
    }

    private List<String[]> settersFor(int channel) {
        return settersByChannel.getOrDefault(channel, availableSetters);
    }

    private List<String> buildMathSrcOptions(int channel) {
        List<String> opts = new ArrayList<>();
        opts.add(I18n.get("gui.universalkeyboard.label.manual_input"));
        for (int v = 1; v <= 8; v++) opts.add("V" + v);
        opts.add("RS:N"); opts.add("RS:S"); opts.add("RS:E"); opts.add("RS:W");
        int wc = getWirelessCount();
        for (int w = 1; w <= wc; w++) opts.add("W" + w);
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

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
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

        if (insertHoverGap >= 0 && btn == 0) {
            insertStep(scrollOffset + insertHoverGap);
            return true;
        }

        if (loadDropdownOpen) {
            if (handleLoadDropdownClick(mx, my)) return true;
        }

        if (mathDropdownRow >= 0) {
            if (handleMathDropdownClick(mx, my)) return true;
        }

        if (typeDropdownRow >= 0) {
            Type[] available = buildAvailableTypes();
            int rowY  = rowAreaY + typeDropdownRow * (ROW_H + ROW_GAP);
            int ddX   = panelX + PAD + COL_TYPE;
            int ddW   = 82;
            int itemH = 12;
            int vis   = Math.min(DD_VIS, available.length);
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
                refreshAllRows();
                return true;
            }
            refreshAllRows();
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
            refreshAllRows();
            return true;
        }
        refreshAllRows();
        return false;
    }

    private boolean handleLoadDropdownClick(double mx, double my) {
        int savedScroll = loadDropdownScroll;
        int itemH = 12;
        int ddW   = bottomBtnW + 20;
        int ddX   = loadBtnX;
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
                    // Don't load while running — could be surprising; just set name
                    saveName.setValue(name);
                } else {
                    loadFromFile(name);
                    saveName.setValue(name);
                }
            }
            return true;
        }
        return false;
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
        if (typeDropdownRow >= 0) {
            int max = Math.max(0, buildAvailableTypes().length - DD_VIS);
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
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (showNoNameDialog) { showNoNameDialog = false; return true; }
            if (confirmOverwriteName != null) { confirmOverwriteName = null; return true; }
            if (loadDropdownOpen)  { loadDropdownOpen  = false; return true; }
            if (mathDropdownRow >= 0) { mathDropdownRow = -1; return true; }
            if (typeDropdownRow >= 0) { typeDropdownRow = -1; return true; }
            onClose(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void onSave()    { ModPackets.sendSaveAndRunSequencer(keyboardPos, steps, false); }
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
            ifGetterBtn = DarkButton.make(Component.literal(""), b -> cycleIfGetter(1),
                    ctx, rowY + 1, 80, BTN_H);
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
            sourceBtn = DarkButton.make(Component.literal(""), b -> cycleSource(1),
                    ctx, rowY + 1, 66, BTN_H);
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
            mathDestBtn = DarkButton.make(Component.literal("V1"), b -> cycleMathDest(1),
                    ctx, rowY + 1, 36, BTN_H);
            addRenderableWidget(mathDestBtn);
            mathASourceBtn = DarkButton.make(Component.literal("src A..."), b -> openMathDropdown(true),
                    ctx + 48, rowY + 1, 64, BTN_H);
            addRenderableWidget(mathASourceBtn);
            mathAInput = makeBox(ctx + 48, rowY, 64, "A (# V1 RS:N getter)",
                    str -> { int si = scrollOffset + rowIdx; if (si < steps.size()) steps.get(si).mathA = str; });
            mathAChBtn = DarkButton.make(Component.literal("1"), b -> cycleMathACh(1),
                    ctx + 114, rowY + 1, 22, BTN_H);
            addRenderableWidget(mathAChBtn);
            mathOpBtn = DarkButton.make(Component.literal("+"), b -> cycleMathOp(1),
                    ctx + 140, rowY + 1, 36, BTN_H);
            addRenderableWidget(mathOpBtn);
            mathBSourceBtn = DarkButton.make(Component.literal("src B..."), b -> openMathDropdown(false),
                    ctx + 180, rowY + 1, 64, BTN_H);
            addRenderableWidget(mathBSourceBtn);
            mathBInput = makeBox(ctx + 180, rowY, 64, I18n.get("gui.universalkeyboard.hint.math_src_b"),
                    str -> { int si = scrollOffset + rowIdx; if (si < steps.size()) steps.get(si).mathB = str; });
            mathBChBtn = DarkButton.make(Component.literal("1"), b -> cycleMathBCh(1),
                    ctx + 246, rowY + 1, 22, BTN_H);
            addRenderableWidget(mathBChBtn);
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
                String v = step.setValueStr.matches("V[1-8]") ? step.setValueStr : "V1";
                step.setValueStr = "V" + (wrapIdx(v.charAt(1) - '1', 8, dir) + 1);
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

            // Cycle order: N, S, E, W, W1, W2, ..., W{wirelessCount}, then wrap to N.
            int wirelessCount = getWirelessCount();
            int totalSlots = RS_DIRS.length + wirelessCount;

            // Determine current position in the cycle
            int currentPos;
            if (step.wirelessOutIdx > 0 && step.wirelessOutIdx <= wirelessCount) {
                currentPos = RS_DIRS.length + step.wirelessOutIdx - 1;
            } else {
                currentPos = 0;
                for (int i = 0; i < RS_DIRS.length; i++) if (RS_DIRS[i] == step.redstoneOutDir) { currentPos = i; break; }
            }
            int nextPos = wrapIdx(currentPos, totalSlots, dir);

            if (nextPos < RS_DIRS.length) {
                step.wirelessOutIdx = 0;
                step.redstoneOutDir = RS_DIRS[nextPos];
            } else {
                step.wirelessOutIdx = nextPos - RS_DIRS.length + 1;
            }
            rsDirBtn.setMessage(Component.literal("→ " + redstoneTargetLabel(step)));
        }

        private String redstoneTargetLabel(SequencerStep step) {
            if (step.wirelessOutIdx > 0) return "W" + step.wirelessOutIdx;
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
            ifGetterBtn.setMessage(Component.literal(step.ifGetter));
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

        private List<String> buildIfGetterList(int channel) {
            List<String> list = new ArrayList<>(Arrays.asList(SequencerStep.RS_INPUT_GETTER_NAMES));
            for (int i = 1; i <= 8; i++) list.add("V" + i);
            int wc = getWirelessCount();
            for (int w = 1; w <= wc; w++) list.add("W" + w);
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
            String d = (step.mathDest != null && step.mathDest.matches("V[1-8]")) ? step.mathDest : "V1";
            step.mathDest = "V" + (wrapIdx(d.charAt(1) - '1', 8, dir) + 1);
            mathDestBtn.setMessage(Component.literal(step.mathDest));
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
            int next = wrapIdx(Arrays.asList(MATH_OPS).indexOf(step.mathOp), MATH_OPS.length, dir);
            step.mathOp = MATH_OPS[next];
            mathOpBtn.setMessage(Component.literal(step.mathOp));
            boolean unary = MATH_UNARY[next];
            mathBInput.visible     = !unary && step.mathBManual;
            mathBSourceBtn.visible = !unary && !step.mathBManual;
            mathBChBtn.visible     = !unary;
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
                    ifGetterBtn.setMessage(Component.literal(step.ifGetter.isEmpty() ? I18n.get("gui.universalkeyboard.label.no_getter") : step.ifGetter));
                    ifOpBtn.setMessage(Component.literal(step.ifOp));
                    ifValueInput.setValue(step.ifValueStr);
                    ifModeBtn.setMessage(Component.literal(goTo ? "→step" : "skip"));
                    ifSkipBtn.setMessage(Component.literal("×" + step.ifSkipCount));
                    if (goTo) ifJumpInput.setValue(String.valueOf(step.jumpTarget));
                }
                case CONDITION -> {
                    sourceBtn.visible = opInput.visible = true;
                    sourceBtn.setMessage(Component.literal(step.conditionSource.label));
                    boolean periph = step.conditionSource == ConditionSource.PERIPHERAL;
                    int cx = panelX + PAD;
                    if (periph) {
                        getterBtn.visible = true;
                        List<String> stepGetters = gettersFor(step.channel);
                        if (step.conditionGetter.isEmpty() && !stepGetters.isEmpty())
                            step.conditionGetter = stepGetters.get(0);
                        getterBtn.setMessage(Component.literal(
                                step.conditionGetter.isEmpty() ? I18n.get("gui.universalkeyboard.label.no_getters") : step.conditionGetter));
                        opInput.setX(cx + COL_CTX + 154); opInput.setWidth(124);
                    } else {
                        getterBtn.visible = false;
                        opInput.setX(cx + COL_CTX + 70);  opInput.setWidth(208);
                    }
                    opInput.setValue(step.conditionOp + step.conditionThresholdStr);
                }
                case DELAY -> { delayInput.visible = true; delayInput.setValue(step.delaySecondsStr); }
                case JUMP  -> { jumpInput.visible  = true; jumpInput.setValue(String.valueOf(step.jumpTarget)); }
                case MATH  -> {
                    mathDestBtn.visible = mathOpBtn.visible = mathAChBtn.visible = true;
                    boolean unary   = isUnaryOp(step.mathOp);
                    boolean aManual = step.mathAManual;
                    boolean bManual = step.mathBManual;
                    mathAInput.visible     = aManual;
                    mathASourceBtn.visible = !aManual;
                    mathBInput.visible     = !unary && bManual;
                    mathBSourceBtn.visible = !unary && !bManual;
                    mathBChBtn.visible     = !unary;
                    String dest = (step.mathDest == null || step.mathDest.isEmpty()) ? "V1" : step.mathDest;
                    step.mathDest = dest;
                    mathDestBtn.setMessage(Component.literal(dest));
                    if (aManual) mathAInput.setValue(step.mathA);
                    else mathASourceBtn.setMessage(Component.literal(step.mathA.isEmpty() ? "src A..." : step.mathA));
                    mathAChBtn.setMessage(Component.literal(String.valueOf(step.mathACh)));
                    mathOpBtn.setMessage(Component.literal(step.mathOp));
                    if (!unary) {
                        if (bManual) mathBInput.setValue(step.mathB);
                        else mathBSourceBtn.setMessage(Component.literal(step.mathB.isEmpty() ? "src B..." : step.mathB));
                        mathBChBtn.setMessage(Component.literal(String.valueOf(step.mathBCh)));
                    }
                }
                case TYPE_VARIABLE -> {
                    methodBtn.visible = typeEnterBtn.visible = true;
                    if (!step.setValueStr.matches("V[1-8]")) step.setValueStr = "V1";
                    methodBtn.setMessage(Component.literal(step.setValueStr));
                    typeEnterBtn.setMessage(Component.literal(step.typeTextEnter ? "↵ on" : "↵ off"));
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
        for (int i = 0; i < MATH_OPS.length; i++)
            if (MATH_OPS[i].equals(op)) return MATH_UNARY[i];
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
