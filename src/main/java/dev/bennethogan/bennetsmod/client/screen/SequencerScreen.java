package dev.bennethogan.bennetsmod.client.screen;

import dev.bennethogan.bennetsmod.UniversalKeyboardMod;
import dev.bennethogan.bennetsmod.network.ModPackets;
import dev.bennethogan.bennetsmod.sequencer.SequencerStep;
import dev.bennethogan.bennetsmod.sequencer.SequencerStep.ConditionSource;
import dev.bennethogan.bennetsmod.sequencer.SequencerStep.Type;
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
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    private static final Type[]      ALL_TYPES = Type.values();

    // ── State ─────────────────────────────────────────────────────────────────
    final BlockPos keyboardPos;
    private final List<SequencerStep> steps;
    private boolean running;
    private int     currentStep;
    private List<String>   availableGetters;
    private List<String[]> availableSetters;

    private int scrollOffset = 0;

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
    private String confirmOverwriteName = null;

    // Layout cache (set in init)
    private int panelX, panelY, panelH, rowAreaY;
    private int bottomBtnW, bottomBtnY, loadBtnX;

    private final List<RowWidgets> rows = new ArrayList<>();
    private Button  runStopBtn, addBtn;
    private EditBox saveName;

    boolean refreshing = false;

    // ── Construction ──────────────────────────────────────────────────────────

    public SequencerScreen(BlockPos keyboardPos, List<SequencerStep> steps,
            boolean running, int currentStep, String peripheralType,
            List<String> availableGetters, List<String[]> availableSetters) {
        super(Component.empty());
        this.keyboardPos      = keyboardPos;
        this.steps            = new ArrayList<>(steps);
        this.running          = running;
        this.currentStep      = currentStep;
        this.availableGetters = new ArrayList<>(availableGetters);
        this.availableSetters = new ArrayList<>(availableSetters);
    }

    public BlockPos getKeyboardPos() { return keyboardPos; }

    public void updateState(List<SequencerStep> newSteps, boolean running, int currentStep,
                            List<String> getters, List<String[]> setters) {
        this.running          = running;
        this.currentStep      = currentStep;
        this.availableGetters = new ArrayList<>(getters);
        this.availableSetters = new ArrayList<>(setters);
        updateRunStopLabel();
    }

    // ── init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        rows.clear();

        int titleH  = PAD + 12 + 4 + BTN_H + PAD; // 42 — extra row for save-name input
        int rowsH   = VISIBLE * (ROW_H + ROW_GAP);
        int addH    = PAD + BTN_H;
        int statusH = PAD + 8;
        int btnRowH = PAD + BTN_H + PAD;
        panelH = titleH + rowsH + addH + statusH + btnRowH;

        panelX   = (width  - PANEL_W) / 2;
        panelY   = (height - panelH)  / 2;
        rowAreaY = panelY + titleH;

        // Save-name input centred under title text
        int nameW = 200;
        saveName  = new EditBox(font, panelX + (PANEL_W - nameW) / 2,
                panelY + PAD + 12 + 4, nameW, BTN_H, Component.empty());
        saveName.setMaxLength(40);
        saveName.setHint(Component.literal("§7save name..."));
        addRenderableWidget(saveName);

        for (int r = 0; r < VISIBLE; r++) rows.add(new RowWidgets(r));

        int addY = rowAreaY + VISIBLE * (ROW_H + ROW_GAP) + PAD;
        addBtn = Button.builder(Component.literal("+ Add Step"), b -> addStep())
                .pos(panelX + PAD + COL_TYPE, addY).size(80, BTN_H).build();
        addRenderableWidget(addBtn);

        // Five-button bottom row: Run/Stop | Save | Save File | Load File | Close
        bottomBtnW = (PANEL_W - PAD * 2 - 4 * 4) / 5; // ~78 px each
        bottomBtnY = panelY + panelH - PAD - BTN_H;
        int gap = 4;

        runStopBtn = Button.builder(runStopLabel(), b -> onRunStop())
                .pos(panelX + PAD, bottomBtnY).size(bottomBtnW, BTN_H).build();
        addRenderableWidget(runStopBtn);

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> onSave())
                .pos(panelX + PAD + (bottomBtnW + gap), bottomBtnY).size(bottomBtnW, BTN_H).build());

        addRenderableWidget(Button.builder(Component.literal("Save File"), b -> onSaveFile())
                .pos(panelX + PAD + (bottomBtnW + gap) * 2, bottomBtnY).size(bottomBtnW, BTN_H).build());

        loadBtnX = panelX + PAD + (bottomBtnW + gap) * 3;
        addRenderableWidget(Button.builder(Component.literal("Load File"), b -> onLoadFile())
                .pos(loadBtnX, bottomBtnY).size(bottomBtnW, BTN_H).build());

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .pos(panelX + PAD + (bottomBtnW + gap) * 4, bottomBtnY).size(bottomBtnW, BTN_H).build());

        refreshAllRows();
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

        g.drawCenteredString(font, "§bPeripheral Sequencer",
                panelX + PANEL_W / 2, panelY + PAD, 0xFFFFFF);

        int cx  = panelX + PAD;
        int ctx = cx + COL_CTX;

        for (int r = 0; r < VISIBLE; r++) {
            int si = scrollOffset + r;
            if (si >= steps.size()) break;
            int rowY = rowAreaY + r * (ROW_H + ROW_GAP);

            if (running && si == currentStep)
                g.fill(cx, rowY, cx + PANEL_W - PAD * 2, rowY + ROW_H, 0x331E6040);

            g.drawString(font, "§8" + (si + 1), cx + COL_NUM, rowY + (ROW_H - 8) / 2, 0xAAAAAA, false);

            SequencerStep step = steps.get(si);
            int ly = rowY + (ROW_H - 8) / 2;
            switch (step.type) {
                case DELAY -> g.drawString(font, "§7s",       ctx + 83, ly, 0xAAAAAA, false);
                case JUMP  -> g.drawString(font, "§7→ step",  ctx,      ly, 0x888888, false);
                case MATH  -> g.drawString(font, "§7=",       ctx + 40, ly, 0x888888, false);
                default    -> {}
            }
        }

        int statusY = rowAreaY + VISIBLE * (ROW_H + ROW_GAP) + PAD + BTN_H + PAD;
        if (running)
            g.drawString(font, "§a● Running  §7step §f" + (currentStep + 1) + "§7/§f" + steps.size(),
                    cx, statusY, 0xFFFFFF, false);
        else
            g.drawString(font, "§8● Stopped  §7(" + steps.size() + " step" + (steps.size() == 1 ? "" : "s") + ")",
                    cx, statusY, 0xFFFFFF, false);

        for (var w : renderables) w.render(g, mx, my, pt);

        // Dropdowns must render above all buttons. Button text is font-batched and can
        // bleed through a plain fill drawn afterwards; elevating Z forces the dropdown
        // geometry above the batched text layer.
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        renderTypeDropdown(g, mx, my);
        renderMathSourceDropdown(g, mx, my);
        renderLoadDropdown(g, mx, my);
        if (confirmOverwriteName != null) renderConfirmDialog(g, mx, my);
        g.pose().popPose();
    }

    // ── Dropdown rendering ────────────────────────────────────────────────────

    private void renderTypeDropdown(GuiGraphics g, int mx, int my) {
        if (typeDropdownRow < 0 || typeDropdownRow >= VISIBLE) return;
        int rowY  = rowAreaY + typeDropdownRow * (ROW_H + ROW_GAP);
        int ddX   = panelX + PAD + COL_TYPE;
        int ddW   = 82;
        int itemH = 12;
        int vis   = Math.min(DD_VIS, ALL_TYPES.length);
        int ddH   = vis * itemH + 4;
        int ddY   = ddPos(rowY, ddH);

        drawDdBox(g, ddX, ddW, ddY, ddH);
        for (int i = 0; i < vis; i++) {
            int idx = typeDropdownScroll + i;
            if (idx >= ALL_TYPES.length) break;
            drawDdItem(g, mx, my, ddX, ddW, ddY, itemH, i, ALL_TYPES[idx].label);
        }
        drawScrollArrows(g, ddX, ddW, ddY, ddH, typeDropdownScroll, ALL_TYPES.length);
    }

    private void renderMathSourceDropdown(GuiGraphics g, int mx, int my) {
        if (mathDropdownRow < 0 || mathDropdownRow >= VISIBLE) return;
        int si = scrollOffset + mathDropdownRow;
        if (si >= steps.size()) return;
        List<String> opts = buildMathSrcOptions();

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

    private void renderConfirmDialog(GuiGraphics g, int mx, int my) {
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xAA000000);
        int dw = 240, dh = 58;
        int dx = panelX + (PANEL_W - dw) / 2;
        int dy = panelY + (panelH  - dh) / 2;

        g.fill(dx - 1, dy - 1, dx + dw + 1, dy + dh + 1, 0xFF666666);
        g.fill(dx, dy, dx + dw, dy + dh, 0xFF1A1A1A);

        String label = confirmOverwriteName;
        if (font.width(label) > dw - 40) label = font.plainSubstrByWidth(label, dw - 44) + "…";
        g.drawCenteredString(font, "Overwrite §e'" + label + "'§r?", dx + dw / 2, dy + 10, 0xFFFFFF);

        boolean yh = mx >= dx + 20  && mx < dx + 100 && my >= dy + 30 && my < dy + 44;
        boolean nh = mx >= dx + 140 && mx < dx + 220 && my >= dy + 30 && my < dy + 44;
        g.fill(dx + 20,  dy + 30, dx + 100, dy + 44, yh ? 0xFF2A4A2A : 0xFF1E3A1E);
        g.fill(dx + 140, dy + 30, dx + 220, dy + 44, nh ? 0xFF4A2A2A : 0xFF3A1E1E);
        g.drawCenteredString(font, "Yes", dx + 60,  dy + 33, yh ? 0x88FF88 : 0x66CC66);
        g.drawCenteredString(font, "No",  dx + 180, dy + 33, nh ? 0xFF8888 : 0xCC6666);
    }

    // ── Dropdown drawing helpers ───────────────────────────────────────────────

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
        if (addBtn != null) addBtn.visible = steps.size() < MAX_STEPS;
        updateRunStopLabel();
    }

    private void updateRunStopLabel() {
        if (runStopBtn != null) runStopBtn.setMessage(runStopLabel());
    }

    private Component runStopLabel() { return Component.literal(running ? "■ Stop" : "▶ Run"); }

    private void addStep() {
        if (steps.size() >= MAX_STEPS) return;
        steps.add(SequencerStep.ofType(Type.END));
        scrollOffset = Math.max(0, steps.size() - VISIBLE);
        refreshAllRows();
    }

    private void deleteStep(int stepIdx) {
        if (stepIdx < 0 || stepIdx >= steps.size()) return;
        steps.remove(stepIdx);
        scrollOffset = Math.min(scrollOffset, Math.max(0, steps.size() - VISIBLE));
        refreshAllRows();
    }

    private List<String> buildMathSrcOptions() {
        List<String> opts = new ArrayList<>();
        opts.add("[Manual Input]");
        for (int v = 1; v <= 8; v++) opts.add("V" + v);
        opts.add("RS:N"); opts.add("RS:S"); opts.add("RS:E"); opts.add("RS:W");
        opts.addAll(availableGetters);
        return opts;
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Confirm dialog blocks everything else
        if (confirmOverwriteName != null) {
            int dw = 240, dh = 58;
            int dx = panelX + (PANEL_W - dw) / 2;
            int dy = panelY + (panelH  - dh) / 2;
            if (mx >= dx + 20 && mx < dx + 100 && my >= dy + 30 && my < dy + 44) {
                saveToFile(confirmOverwriteName);
            }
            confirmOverwriteName = null;
            return true;
        }

        if (loadDropdownOpen) {
            if (handleLoadDropdownClick(mx, my)) return true;
        }

        if (mathDropdownRow >= 0) {
            if (handleMathDropdownClick(mx, my)) return true;
        }

        if (typeDropdownRow >= 0) {
            int rowY  = rowAreaY + typeDropdownRow * (ROW_H + ROW_GAP);
            int ddX   = panelX + PAD + COL_TYPE;
            int ddW   = 82;
            int itemH = 12;
            int vis   = Math.min(DD_VIS, ALL_TYPES.length);
            int ddH   = vis * itemH + 4;
            int ddY   = ddPos(rowY, ddH);
            int savedScroll = typeDropdownScroll;
            int savedRow    = typeDropdownRow;
            typeDropdownRow    = -1;
            typeDropdownScroll = 0;

            if (mx >= ddX && mx < ddX + ddW && my >= ddY && my < ddY + ddH) {
                int rel = ((int) my - ddY - 2) / itemH;
                int idx = savedScroll + rel;
                if (rel >= 0 && rel < vis && idx < ALL_TYPES.length) {
                    int si = scrollOffset + savedRow;
                    if (si < steps.size()) steps.get(si).type = ALL_TYPES[idx];
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
        List<String> opts   = buildMathSrcOptions();

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
                if (sel.equals("[Manual Input]")) {
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
        int dir = (int) -Math.signum(dy);

        if (loadDropdownOpen) {
            int max = Math.max(0, loadDropdownFiles.size() - DD_VIS);
            loadDropdownScroll = Math.max(0, Math.min(max, loadDropdownScroll + dir));
            return true;
        }
        if (mathDropdownRow >= 0) {
            List<String> opts = buildMathSrcOptions();
            int max = Math.max(0, opts.size() - DD_VIS);
            mathDropdownScroll = Math.max(0, Math.min(max, mathDropdownScroll + dir));
            return true;
        }
        if (typeDropdownRow >= 0) {
            int max = Math.max(0, ALL_TYPES.length - DD_VIS);
            typeDropdownScroll = Math.max(0, Math.min(max, typeDropdownScroll + dir));
            return true;
        }

        int maxOff = Math.max(0, steps.size() - VISIBLE);
        int newOff = Math.max(0, Math.min(maxOff, scrollOffset + dir));
        if (newOff != scrollOffset) { scrollOffset = newOff; refreshAllRows(); return true; }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
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
        if (name.isEmpty()) return;
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

            channelBtn = Button.builder(Component.literal("-"), b -> cycleChannel())
                    .pos(cx + COL_CH, rowY + 1).size(16, BTN_H).build();
            addRenderableWidget(channelBtn);

            typeBtn = Button.builder(Component.literal(""), b -> {
                mathDropdownRow = -1;
                loadDropdownOpen = false;
                typeDropdownRow    = (typeDropdownRow == rowIdx) ? -1 : rowIdx;
                typeDropdownScroll = 0;
            }).pos(cx + COL_TYPE, rowY + 1).size(72, BTN_H).build();
            addRenderableWidget(typeBtn);

            deleteBtn = Button.builder(Component.literal("×"), b -> deleteStep(scrollOffset + rowIdx))
                    .pos(cx + COL_DEL, rowY + 1).size(14, BTN_H).build();
            addRenderableWidget(deleteBtn);

            // SET_VALUE
            methodBtn = Button.builder(Component.literal(""), b -> cycleMethod())
                    .pos(ctx, rowY + 1).size(120, BTN_H).build();
            addRenderableWidget(methodBtn);
            valueInput = makeBox(ctx + 124, rowY, 156, "value or V1-V8",
                    str -> { int si = scrollOffset + rowIdx; if (si < steps.size()) steps.get(si).setValueStr = str; });

            // SET_REDSTONE
            rsDirBtn = Button.builder(Component.literal(""), b -> cycleRedstoneDir())
                    .pos(ctx, rowY + 1).size(90, BTN_H).build();
            addRenderableWidget(rsDirBtn);
            rsSignalInput = makeBox(ctx + 94, rowY, 80, "0-15 or V1-V8",
                    str -> { int si = scrollOffset + rowIdx; if (si < steps.size()) steps.get(si).redstoneOutSignalStr = str; });

            // TYPE_TEXT
            typeTextInput = makeBox(ctx, rowY, 210, "text to type...",
                    str -> { int si = scrollOffset + rowIdx; if (si < steps.size()) steps.get(si).typeTextStr = str; });
            typeTextInput.setMaxLength(200);
            typeEnterBtn = Button.builder(Component.literal("↵"), b -> toggleTypeEnter())
                    .pos(ctx + 214, rowY + 1).size(66, BTN_H).build();
            addRenderableWidget(typeEnterBtn);

            // IF
            ifGetterBtn = Button.builder(Component.literal(""), b -> cycleIfGetter())
                    .pos(ctx, rowY + 1).size(80, BTN_H).build();
            addRenderableWidget(ifGetterBtn);
            ifOpBtn = Button.builder(Component.literal(">"), b -> cycleIfOp())
                    .pos(ctx + 84, rowY + 1).size(28, BTN_H).build();
            addRenderableWidget(ifOpBtn);
            ifValueInput = makeBox(ctx + 116, rowY, 64, "# or V1-V8 or RS:N",
                    str -> { int si = scrollOffset + rowIdx; if (si < steps.size()) steps.get(si).ifValueStr = str; });
            ifModeBtn = Button.builder(Component.literal("skip"), b -> toggleIfMode())
                    .pos(ctx + 184, rowY + 1).size(34, BTN_H).build();
            addRenderableWidget(ifModeBtn);
            ifSkipBtn = Button.builder(Component.literal("×1"), b -> cycleIfSkip())
                    .pos(ctx + 222, rowY + 1).size(60, BTN_H).build();
            addRenderableWidget(ifSkipBtn);
            ifJumpInput = makeBox(ctx + 222, rowY, 60, "step#",
                    str -> {
                        int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
                        try { steps.get(si).jumpTarget = Math.max(1, Math.min(MAX_STEPS, Integer.parseInt(str.trim()))); }
                        catch (NumberFormatException ignored) {}
                    });
            ifJumpInput.setMaxLength(3);

            // CONDITION
            sourceBtn = Button.builder(Component.literal(""), b -> cycleSource())
                    .pos(ctx, rowY + 1).size(66, BTN_H).build();
            addRenderableWidget(sourceBtn);
            getterBtn = Button.builder(Component.literal(""), b -> cycleGetter())
                    .pos(ctx + 70, rowY + 1).size(80, BTN_H).build();
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
            mathDestBtn = Button.builder(Component.literal("V1"), b -> cycleMathDest())
                    .pos(ctx, rowY + 1).size(36, BTN_H).build();
            addRenderableWidget(mathDestBtn);
            mathASourceBtn = Button.builder(Component.literal("src A..."), b -> openMathDropdown(true))
                    .pos(ctx + 48, rowY + 1).size(64, BTN_H).build();
            addRenderableWidget(mathASourceBtn);
            mathAInput = makeBox(ctx + 48, rowY, 64, "A (# V1 RS:N getter)",
                    str -> { int si = scrollOffset + rowIdx; if (si < steps.size()) steps.get(si).mathA = str; });
            mathAChBtn = Button.builder(Component.literal("1"), b -> cycleMathACh())
                    .pos(ctx + 114, rowY + 1).size(22, BTN_H).build();
            addRenderableWidget(mathAChBtn);
            mathOpBtn = Button.builder(Component.literal("+"), b -> cycleMathOp())
                    .pos(ctx + 140, rowY + 1).size(36, BTN_H).build();
            addRenderableWidget(mathOpBtn);
            mathBSourceBtn = Button.builder(Component.literal("src B..."), b -> openMathDropdown(false))
                    .pos(ctx + 180, rowY + 1).size(64, BTN_H).build();
            addRenderableWidget(mathBSourceBtn);
            mathBInput = makeBox(ctx + 180, rowY, 64, "B",
                    str -> { int si = scrollOffset + rowIdx; if (si < steps.size()) steps.get(si).mathB = str; });
            mathBChBtn = Button.builder(Component.literal("1"), b -> cycleMathBCh())
                    .pos(ctx + 246, rowY + 1).size(22, BTN_H).build();
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

        private void cycleChannel() {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            if (!stepUsesChannel(step.type)) return;
            step.channel = (step.channel % 16) + 1;
            channelBtn.setMessage(Component.literal(String.valueOf(step.channel)));
        }

        private boolean stepUsesChannel(Type t) {
            return t == Type.SET_VALUE || t == Type.TYPE_TEXT || t == Type.IF || t == Type.CONDITION;
        }

        private void cycleMethod() {
            int si = scrollOffset + rowIdx; if (si >= steps.size() || availableSetters.isEmpty()) return;
            SequencerStep step = steps.get(si);
            int idx = indexOfSetter(step.setMethod);
            step.setMethod = availableSetters.get((idx + 1) % availableSetters.size())[0];
            methodBtn.setMessage(Component.literal(step.setMethod));
        }

        private void toggleTypeEnter() {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            step.typeTextEnter = !step.typeTextEnter;
            typeEnterBtn.setMessage(Component.literal(step.typeTextEnter ? "↵ on" : "↵ off"));
        }

        private void cycleRedstoneDir() {
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
            int nextPos = (currentPos + 1) % totalSlots;

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

        private int getWirelessCount() {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return 0;
            net.minecraft.world.level.block.entity.BlockEntity be = mc.level.getBlockEntity(keyboardPos);
            if (be instanceof dev.bennethogan.bennetsmod.blockentity.LinkedKeyboardBlockEntity kb)
                return kb.getWirelessCount();
            return 0;
        }

        private void cycleIfGetter() {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            List<String> list = buildIfGetterList();
            if (list.isEmpty()) return;
            int idx = list.indexOf(step.ifGetter);
            step.ifGetter = list.get((idx + 1) % list.size());
            ifGetterBtn.setMessage(Component.literal(step.ifGetter));
        }

        private void cycleIfOp() {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            int idx = Arrays.asList(IF_OPS).indexOf(step.ifOp);
            step.ifOp = IF_OPS[(idx + 1) % IF_OPS.length];
            ifOpBtn.setMessage(Component.literal(step.ifOp));
        }

        private void cycleIfSkip() {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            step.ifSkipCount = (step.ifSkipCount % 16) + 1;
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

        private List<String> buildIfGetterList() {
            List<String> list = new ArrayList<>(Arrays.asList(SequencerStep.RS_INPUT_GETTER_NAMES));
            list.addAll(availableGetters);
            return list;
        }

        private void cycleSource() {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            ConditionSource[] sources = ConditionSource.values();
            step.conditionSource = sources[(step.conditionSource.ordinal() + 1) % sources.length];
            refresh();
        }

        private void cycleGetter() {
            int si = scrollOffset + rowIdx; if (si >= steps.size() || availableGetters.isEmpty()) return;
            SequencerStep step = steps.get(si);
            int idx = availableGetters.indexOf(step.conditionGetter);
            step.conditionGetter = availableGetters.get((idx + 1) % availableGetters.size());
            getterBtn.setMessage(Component.literal(step.conditionGetter));
        }

        private void cycleMathDest() {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            String d = (step.mathDest != null && step.mathDest.matches("V[1-8]")) ? step.mathDest : "V1";
            step.mathDest = "V" + ((d.charAt(1) - '0') % 8 + 1);
            mathDestBtn.setMessage(Component.literal(step.mathDest));
        }

        private void cycleMathACh() {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            step.mathACh = (step.mathACh % 16) + 1;
            mathAChBtn.setMessage(Component.literal(String.valueOf(step.mathACh)));
        }

        private void cycleMathBCh() {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            step.mathBCh = (step.mathBCh % 16) + 1;
            mathBChBtn.setMessage(Component.literal(String.valueOf(step.mathBCh)));
        }

        private void cycleMathOp() {
            int si = scrollOffset + rowIdx; if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            int next = (Arrays.asList(MATH_OPS).indexOf(step.mathOp) + 1) % MATH_OPS.length;
            step.mathOp = MATH_OPS[next];
            mathOpBtn.setMessage(Component.literal(step.mathOp));
            boolean unary = MATH_UNARY[next];
            mathBInput.visible     = !unary && step.mathBManual;
            mathBSourceBtn.visible = !unary && !step.mathBManual;
            mathBChBtn.visible     = !unary;
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
            typeBtn.setMessage(Component.literal(step.type.label));
            boolean usesChannel = stepUsesChannel(step.type);
            channelBtn.active = usesChannel;
            channelBtn.setMessage(Component.literal(usesChannel ? String.valueOf(step.channel) : "-"));

            refreshing = true;
            switch (step.type) {
                case SET_VALUE -> {
                    methodBtn.visible = valueInput.visible = true;
                    if (step.setMethod.isEmpty() && !availableSetters.isEmpty())
                        step.setMethod = availableSetters.get(0)[0];
                    methodBtn.setMessage(Component.literal(
                            step.setMethod.isEmpty() ? "(no setters)" : step.setMethod));
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
                    List<String> list = buildIfGetterList();
                    if (step.ifGetter.isEmpty() && !list.isEmpty()) step.ifGetter = list.get(0);
                    ifGetterBtn.setMessage(Component.literal(step.ifGetter.isEmpty() ? "(none)" : step.ifGetter));
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
                        if (step.conditionGetter.isEmpty() && !availableGetters.isEmpty())
                            step.conditionGetter = availableGetters.get(0);
                        getterBtn.setMessage(Component.literal(
                                step.conditionGetter.isEmpty() ? "(no getters)" : step.conditionGetter));
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
                case CYCLE, END -> {}
            }
            refreshing = false;
        }

        private int indexOfSetter(String name) {
            for (int i = 0; i < availableSetters.size(); i++)
                if (availableSetters.get(i)[0].equals(name)) return i;
            return -1;
        }
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    private static boolean isUnaryOp(String op) {
        for (int i = 0; i < MATH_OPS.length; i++)
            if (MATH_OPS[i].equals(op)) return MATH_UNARY[i];
        return false;
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
