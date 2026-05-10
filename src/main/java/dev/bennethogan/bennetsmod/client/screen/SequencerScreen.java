package dev.bennethogan.bennetsmod.client.screen;

import dev.bennethogan.bennetsmod.network.ModPackets;
import dev.bennethogan.bennetsmod.sequencer.SequencerStep;
import dev.bennethogan.bennetsmod.sequencer.SequencerStep.ConditionSource;
import dev.bennethogan.bennetsmod.sequencer.SequencerStep.Type;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Sequencer screen — scrollable list of up to 16 steps.
 * 6 rows visible at a time; scroll via mouse wheel.
 *
 * Row layout (panel content = 304 px):
 *   [#  ] [TypeBtn 66] [Context 204] [×  16]
 *
 * Context per type:
 *   SET_VALUE  : [MethodBtn 104] [ValueInput 96]
 *   CONDITION  : [SourceBtn 66] [GetterBtn 78 | ─] [OpInput 50]
 *   DELAY      : [DelayInput 80]  + "s" text
 *   CYCLE / END: empty
 */
public class SequencerScreen extends Screen {

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int PAD      = 8;
    private static final int PANEL_W  = 320;
    private static final int ROW_H    = 20;
    private static final int ROW_GAP  = 2;
    private static final int VISIBLE  = 6;
    private static final int BTN_H    = 18;
    private static final int MAX_STEPS = 16;

    // Column X offsets inside the panel content area (from panelX + PAD)
    private static final int COL_NUM    = 0;   // width 16 (drawn as text)
    private static final int COL_TYPE   = 18;  // width 66
    private static final int COL_CTX    = 88;  // width 204 (= 18+66+4)
    private static final int COL_DEL    = 296; // width 12 (= 88+204+4)

    // Context sub-columns
    private static final int CTX_METHOD = 0;   // width 104
    private static final int CTX_VALUE  = 108; // width 96 (= 104+4)
    private static final int CTX_SRC    = 0;   // width 66
    private static final int CTX_GETTER = 70;  // width 78 (= 66+4)
    private static final int CTX_OP     = 152; // width 52 (= 70+78+4), or 152 for redstone-only (= 66+4+82 remaining)
    private static final int CTX_OP_RS  = 70;  // for redstone: op starts earlier since no getter
    private static final int CTX_OP_RS_W  = 130;
    private static final int CTX_DELAY  = 0;   // width 80

    // ── State ─────────────────────────────────────────────────────────────────
    final BlockPos keyboardPos;
    private final List<SequencerStep> steps;
    private boolean running;
    private int     currentStep;
    private List<String>   availableGetters;
    private List<String[]> availableSetters;

    private int scrollOffset = 0;

    private int panelX, panelY, panelH;
    private int rowAreaY;  // y of first visible row

    // Widgets for the 6 visible rows
    private final List<RowWidgets> rows = new ArrayList<>();

    // Bottom button refs (need to update label for run/stop)
    private Button runStopBtn;
    private Button addBtn;

    // Suppress responder callbacks during row refresh
    boolean refreshing = false;

    // ── Construction ──────────────────────────────────────────────────────────

    public SequencerScreen(BlockPos keyboardPos, List<SequencerStep> steps,
            boolean running, int currentStep,
            String peripheralType,
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
        this.running       = running;
        this.currentStep   = currentStep;
        this.availableGetters = new ArrayList<>(getters);
        this.availableSetters = new ArrayList<>(setters);
        // Replace step list without losing edits that haven't been saved yet
        // (just update running metadata; don't overwrite step data since user might be mid-edit)
        updateRunStopLabel();
    }

    // ── Initialization ────────────────────────────────────────────────────────

    @Override
    protected void init() {
        rows.clear();

        int titleH    = PAD + 12 + PAD;
        int rowsH     = VISIBLE * (ROW_H + ROW_GAP);
        int addH      = PAD + BTN_H;
        int statusH   = PAD + 10;
        int btnRowH   = PAD + BTN_H + PAD;
        panelH = titleH + rowsH + addH + statusH + btnRowH;

        panelX = (width  - PANEL_W) / 2;
        panelY = (height - panelH)  / 2;
        rowAreaY = panelY + titleH;

        // Create 6 row widget groups
        for (int r = 0; r < VISIBLE; r++) {
            rows.add(new RowWidgets(r));
        }

        // + Add Step button
        int addY = rowAreaY + VISIBLE * (ROW_H + ROW_GAP) + PAD;
        addBtn = Button.builder(Component.literal("+ Add Step"), b -> addStep())
                .pos(panelX + PAD + COL_NUM + COL_TYPE + 18 + 4, addY)
                .size(80, BTN_H)
                .build();
        addRenderableWidget(addBtn);

        // Bottom buttons
        int btnY = panelY + panelH - PAD - BTN_H;
        int bw   = (PANEL_W - PAD * 2 - 8) / 3;

        runStopBtn = Button.builder(runStopLabel(), b -> onRunStop())
                .pos(panelX + PAD, btnY)
                .size(bw, BTN_H)
                .build();
        addRenderableWidget(runStopBtn);

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> onSave())
                .pos(panelX + PAD + bw + 4, btnY)
                .size(bw, BTN_H)
                .build());

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .pos(panelX + PAD + (bw + 4) * 2, btnY)
                .size(bw, BTN_H)
                .build());

        refreshAllRows();
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);

        // Panel
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xFF111111);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + 1,      0xFF666666);
        g.fill(panelX, panelY + panelH - 1, panelX + PANEL_W, panelY + panelH, 0xFF666666);
        g.fill(panelX, panelY, panelX + 1, panelY + panelH, 0xFF666666);
        g.fill(panelX + PANEL_W - 1, panelY, panelX + PANEL_W, panelY + panelH, 0xFF666666);

        // Title
        g.drawCenteredString(font, "§bPeripheral Sequencer",
                panelX + PANEL_W / 2, panelY + PAD, 0xFFFFFF);

        // Row number labels and highlight current step
        int cx = panelX + PAD;
        for (int r = 0; r < VISIBLE; r++) {
            int si = scrollOffset + r;
            if (si >= steps.size()) break;
            int rowY = rowAreaY + r * (ROW_H + ROW_GAP);

            // Highlight active step while running
            if (running && si == currentStep) {
                g.fill(cx, rowY, cx + PANEL_W - PAD * 2, rowY + ROW_H, 0x331E6040);
            }

            g.drawString(font, "§8" + (si + 1), cx, rowY + (ROW_H - 8) / 2, 0xAAAAAA, false);
        }

        // Status line
        int statusY = rowAreaY + VISIBLE * (ROW_H + ROW_GAP) + PAD + BTN_H + PAD;
        if (running) {
            g.drawString(font, "§a● Running  §7— step §f" + (currentStep + 1) + "§7/§f" + steps.size(),
                    panelX + PAD, statusY, 0xFFFFFF, false);
        } else {
            g.drawString(font, "§8● Stopped  §7(" + steps.size() + " step" + (steps.size() == 1 ? "" : "s") + ")",
                    panelX + PAD, statusY, 0xFFFFFF, false);
        }

        // Inline labels beside certain step inputs
        for (int r = 0; r < VISIBLE; r++) {
            int si = scrollOffset + r;
            if (si >= steps.size()) break;
            SequencerStep step = steps.get(si);
            int rowY = rowAreaY + r * (ROW_H + ROW_GAP);
            if (step.type == Type.DELAY) {
                g.drawString(font, "§7 s",
                        cx + COL_CTX + CTX_DELAY + 82, rowY + (ROW_H - 8) / 2, 0xAAAAAA, false);
            } else if (step.type == Type.SET_REDSTONE) {
                g.drawString(font, "§7sig",
                        cx + COL_CTX + 94 + 62, rowY + (ROW_H - 8) / 2, 0xAAAAAA, false);
            }
        }

        for (var w : renderables) w.render(g, mx, my, pt);
    }

    // ── Row management ────────────────────────────────────────────────────────

    private void refreshAllRows() {
        for (RowWidgets row : rows) row.refresh();
        updateAddButton();
        updateRunStopLabel();
    }

    private void updateAddButton() {
        if (addBtn != null) addBtn.visible = steps.size() < MAX_STEPS;
    }

    private void updateRunStopLabel() {
        if (runStopBtn != null) runStopBtn.setMessage(runStopLabel());
    }

    private Component runStopLabel() {
        return Component.literal(running ? "■ Stop" : "▶ Run");
    }

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

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int maxOff = Math.max(0, steps.size() - VISIBLE);
        int newOff = (int) Math.max(0, Math.min(maxOff, scrollOffset - Math.signum(dy)));
        if (newOff != scrollOffset) {
            scrollOffset = newOff;
            refreshAllRows();
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void onSave() {
        ModPackets.sendSaveAndRunSequencer(keyboardPos, steps, false);
    }

    private void onRunStop() {
        if (running) {
            ModPackets.sendStopSequencer(keyboardPos);
        } else {
            ModPackets.sendSaveAndRunSequencer(keyboardPos, steps, true);
        }
    }

    // ── RowWidgets inner class ────────────────────────────────────────────────

    // Horizontal directions available for redstone output (no UP/DOWN — keyboard is flat)
    private static final Direction[] RS_DIRS = {
        Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private static final String[] IF_OPS = {">", ">=", "=", "<=", "<", "!="};

    private class RowWidgets {
        final int rowIdx;

        // Always-present
        Button typeBtn;
        Button deleteBtn;

        // SET_VALUE
        Button  methodBtn;
        EditBox valueInput;

        // SET_REDSTONE
        Button  rsDirBtn;
        EditBox rsSignalInput;

        // TYPE_TEXT
        EditBox typeTextInput;
        Button  typeEnterBtn;

        // IF
        Button  ifGetterBtn;
        Button  ifOpBtn;
        EditBox ifValueInput;
        Button  ifSkipBtn;

        // CONDITION
        Button  sourceBtn;
        Button  getterBtn;
        EditBox opInput;

        // DELAY
        EditBox delayInput;

        RowWidgets(int rowIdx) {
            this.rowIdx = rowIdx;
            int rowY = rowAreaY + rowIdx * (ROW_H + ROW_GAP);
            int cx   = panelX + PAD;

            // Type cycling button
            typeBtn = Button.builder(Component.literal(""), b -> cycleType())
                    .pos(cx + COL_TYPE, rowY + 1)
                    .size(66, BTN_H)
                    .build();
            addRenderableWidget(typeBtn);

            // Delete button
            deleteBtn = Button.builder(Component.literal("×"), b -> deleteStep(scrollOffset + rowIdx))
                    .pos(cx + COL_DEL, rowY + 1)
                    .size(12, BTN_H)
                    .build();
            addRenderableWidget(deleteBtn);

            // ── SET_VALUE ──────────────────────────────────────────────────

            methodBtn = Button.builder(Component.literal(""), b -> cycleMethod())
                    .pos(cx + COL_CTX + CTX_METHOD, rowY + 1)
                    .size(104, BTN_H)
                    .build();
            addRenderableWidget(methodBtn);

            valueInput = new EditBox(font,
                    cx + COL_CTX + CTX_VALUE, rowY + 1, 96, BTN_H,
                    Component.literal("value"));
            valueInput.setMaxLength(32);
            valueInput.setResponder(str -> {
                if (!refreshing) {
                    int si = scrollOffset + rowIdx;
                    if (si < steps.size()) steps.get(si).setValueStr = str;
                }
            });
            addRenderableWidget(valueInput);

            // ── SET_REDSTONE ───────────────────────────────────────────────

            rsDirBtn = Button.builder(Component.literal(""), b -> cycleRedstoneDir())
                    .pos(cx + COL_CTX, rowY + 1)
                    .size(90, BTN_H)
                    .build();
            addRenderableWidget(rsDirBtn);

            rsSignalInput = new EditBox(font,
                    cx + COL_CTX + 94, rowY + 1, 60, BTN_H,
                    Component.literal("0"));
            rsSignalInput.setMaxLength(4);
            rsSignalInput.setHint(Component.literal("§70-15"));
            rsSignalInput.setResponder(str -> {
                if (!refreshing) {
                    int si = scrollOffset + rowIdx;
                    if (si < steps.size()) steps.get(si).redstoneOutSignalStr = str;
                }
            });
            addRenderableWidget(rsSignalInput);

            // ── TYPE_TEXT ──────────────────────────────────────────────────

            typeTextInput = new EditBox(font,
                    cx + COL_CTX, rowY + 1, 158, BTN_H,
                    Component.literal("text"));
            typeTextInput.setMaxLength(200);
            typeTextInput.setHint(Component.literal("§7text to type..."));
            typeTextInput.setResponder(str -> {
                if (!refreshing) {
                    int si = scrollOffset + rowIdx;
                    if (si < steps.size()) steps.get(si).typeTextStr = str;
                }
            });
            addRenderableWidget(typeTextInput);

            typeEnterBtn = Button.builder(Component.literal("↵"), b -> toggleTypeEnter())
                    .pos(cx + COL_CTX + 162, rowY + 1)
                    .size(38, BTN_H)
                    .build();
            addRenderableWidget(typeEnterBtn);

            // ── IF ─────────────────────────────────────────────────────────

            // getter: 0-70, op: 74-100, value: 104-154, skip btn: 158-204
            ifGetterBtn = Button.builder(Component.literal(""), b -> cycleIfGetter())
                    .pos(cx + COL_CTX, rowY + 1)
                    .size(70, BTN_H)
                    .build();
            addRenderableWidget(ifGetterBtn);

            ifOpBtn = Button.builder(Component.literal(">"), b -> cycleIfOp())
                    .pos(cx + COL_CTX + 74, rowY + 1)
                    .size(26, BTN_H)
                    .build();
            addRenderableWidget(ifOpBtn);

            ifValueInput = new EditBox(font,
                    cx + COL_CTX + 104, rowY + 1, 50, BTN_H,
                    Component.literal("0"));
            ifValueInput.setMaxLength(16);
            ifValueInput.setHint(Component.literal("§7value"));
            ifValueInput.setResponder(str -> {
                if (!refreshing) {
                    int si = scrollOffset + rowIdx;
                    if (si < steps.size()) steps.get(si).ifValueStr = str;
                }
            });
            addRenderableWidget(ifValueInput);

            ifSkipBtn = Button.builder(Component.literal("skip: 1"), b -> cycleIfSkip())
                    .pos(cx + COL_CTX + 158, rowY + 1)
                    .size(46, BTN_H)
                    .build();
            addRenderableWidget(ifSkipBtn);

            // ── CONDITION ──────────────────────────────────────────────────

            sourceBtn = Button.builder(Component.literal(""), b -> cycleSource())
                    .pos(cx + COL_CTX + CTX_SRC, rowY + 1)
                    .size(66, BTN_H)
                    .build();
            addRenderableWidget(sourceBtn);

            getterBtn = Button.builder(Component.literal(""), b -> cycleGetter())
                    .pos(cx + COL_CTX + CTX_GETTER, rowY + 1)
                    .size(78, BTN_H)
                    .build();
            addRenderableWidget(getterBtn);

            // opInput is used for both PERIPHERAL and redstone conditions
            // width/position differs — we place it at the PERIPHERAL position;
            // for redstone we reposition via setX in refresh()
            opInput = new EditBox(font,
                    cx + COL_CTX + CTX_OP, rowY + 1, 52, BTN_H,
                    Component.literal(">0"));
            opInput.setMaxLength(24);
            opInput.setHint(Component.literal("§7>0"));
            opInput.setResponder(str -> {
                if (!refreshing) {
                    int si = scrollOffset + rowIdx;
                    if (si < steps.size()) parseOpInput(steps.get(si), str);
                }
            });
            addRenderableWidget(opInput);

            // ── DELAY ──────────────────────────────────────────────────────

            delayInput = new EditBox(font,
                    cx + COL_CTX + CTX_DELAY, rowY + 1, 80, BTN_H,
                    Component.literal("1.0"));
            delayInput.setMaxLength(12);
            delayInput.setResponder(str -> {
                if (!refreshing) {
                    int si = scrollOffset + rowIdx;
                    if (si < steps.size()) steps.get(si).delaySecondsStr = str;
                }
            });
            addRenderableWidget(delayInput);
        }

        // ── Cycling helpers ─────────────────────────────────────────────────

        private void cycleType() {
            int si = scrollOffset + rowIdx;
            if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            Type[] types = Type.values();
            step.type = types[(step.type.ordinal() + 1) % types.length];
            refresh();
        }

        private void cycleMethod() {
            int si = scrollOffset + rowIdx;
            if (si >= steps.size() || availableSetters.isEmpty()) return;
            SequencerStep step = steps.get(si);
            int idx = indexOfSetter(step.setMethod);
            idx = (idx + 1) % availableSetters.size();
            step.setMethod = availableSetters.get(idx)[0];
            methodBtn.setMessage(Component.literal(step.setMethod));
        }

        private void toggleTypeEnter() {
            int si = scrollOffset + rowIdx;
            if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            step.typeTextEnter = !step.typeTextEnter;
            typeEnterBtn.setMessage(Component.literal(step.typeTextEnter ? "↵ on" : "↵ off"));
        }

        private void cycleRedstoneDir() {
            int si = scrollOffset + rowIdx;
            if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            int idx = 0;
            for (int i = 0; i < RS_DIRS.length; i++) {
                if (RS_DIRS[i] == step.redstoneOutDir) { idx = i; break; }
            }
            step.redstoneOutDir = RS_DIRS[(idx + 1) % RS_DIRS.length];
            rsDirBtn.setMessage(Component.literal("→ " + step.redstoneOutDir.getName().toUpperCase()));
        }

        private void cycleIfGetter() {
            int si = scrollOffset + rowIdx;
            if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            List<String> combined = buildIfGetterList();
            if (combined.isEmpty()) return;
            int idx = combined.indexOf(step.ifGetter);
            step.ifGetter = combined.get((idx + 1) % combined.size());
            ifGetterBtn.setMessage(Component.literal(step.ifGetter));
        }

        private void cycleIfOp() {
            int si = scrollOffset + rowIdx;
            if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            int idx = java.util.Arrays.asList(IF_OPS).indexOf(step.ifOp);
            step.ifOp = IF_OPS[(idx + 1) % IF_OPS.length];
            ifOpBtn.setMessage(Component.literal(step.ifOp));
        }

        private void cycleIfSkip() {
            int si = scrollOffset + rowIdx;
            if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            step.ifSkipCount = (step.ifSkipCount % 9) + 1; // cycles 1–9
            ifSkipBtn.setMessage(Component.literal("skip: " + step.ifSkipCount));
        }

        private List<String> buildIfGetterList() {
            List<String> list = new ArrayList<>();
            for (String name : SequencerStep.RS_INPUT_GETTER_NAMES) list.add(name);
            list.addAll(availableGetters);
            return list;
        }

        private void cycleSource() {
            int si = scrollOffset + rowIdx;
            if (si >= steps.size()) return;
            SequencerStep step = steps.get(si);
            ConditionSource[] sources = ConditionSource.values();
            step.conditionSource = sources[(step.conditionSource.ordinal() + 1) % sources.length];
            refresh();
        }

        private void cycleGetter() {
            int si = scrollOffset + rowIdx;
            if (si >= steps.size() || availableGetters.isEmpty()) return;
            SequencerStep step = steps.get(si);
            int idx = availableGetters.indexOf(step.conditionGetter);
            idx = (idx + 1) % availableGetters.size();
            step.conditionGetter = availableGetters.get(idx);
            getterBtn.setMessage(Component.literal(step.conditionGetter));
        }

        // ── Refresh (updates all visibility and labels) ─────────────────────

        void refresh() {
            int si = scrollOffset + rowIdx;
            boolean hasStep = si < steps.size();

            typeBtn.visible      = hasStep;
            deleteBtn.visible    = hasStep;
            methodBtn.visible     = false;
            valueInput.visible    = false;
            rsDirBtn.visible      = false;
            rsSignalInput.visible = false;
            typeTextInput.visible = false;
            typeEnterBtn.visible  = false;
            ifGetterBtn.visible   = false;
            ifOpBtn.visible       = false;
            ifValueInput.visible  = false;
            ifSkipBtn.visible     = false;
            sourceBtn.visible     = false;
            getterBtn.visible     = false;
            opInput.visible       = false;
            delayInput.visible    = false;

            if (!hasStep) return;

            SequencerStep step = steps.get(si);
            typeBtn.setMessage(Component.literal(step.type.label));

            refreshing = true;
            switch (step.type) {
                case SET_VALUE -> {
                    methodBtn.visible  = true;
                    valueInput.visible = true;
                    String mLabel = step.setMethod.isEmpty()
                            ? (availableSetters.isEmpty() ? "(no setters)" : availableSetters.get(0)[0])
                            : step.setMethod;
                    if (step.setMethod.isEmpty() && !availableSetters.isEmpty())
                        step.setMethod = availableSetters.get(0)[0];
                    methodBtn.setMessage(Component.literal(mLabel));
                    valueInput.setValue(step.setValueStr);
                }
                case IF -> {
                    ifGetterBtn.visible  = true;
                    ifOpBtn.visible      = true;
                    ifValueInput.visible = true;
                    ifSkipBtn.visible    = true;
                    List<String> combined = buildIfGetterList();
                    if (step.ifGetter.isEmpty() && !combined.isEmpty())
                        step.ifGetter = combined.get(0);
                    ifGetterBtn.setMessage(Component.literal(
                            step.ifGetter.isEmpty() ? "(none)" : step.ifGetter));
                    ifOpBtn.setMessage(Component.literal(step.ifOp));
                    ifValueInput.setValue(step.ifValueStr);
                    ifSkipBtn.setMessage(Component.literal("skip: " + step.ifSkipCount));
                }
                case CONDITION -> {
                    sourceBtn.visible = true;
                    opInput.visible   = true;
                    sourceBtn.setMessage(Component.literal(step.conditionSource.label));

                    boolean isPeripheral = step.conditionSource == ConditionSource.PERIPHERAL;
                    int cx = panelX + PAD;

                    if (isPeripheral) {
                        getterBtn.visible = true;
                        String gLabel = step.conditionGetter.isEmpty()
                                ? (availableGetters.isEmpty() ? "(no getters)" : availableGetters.get(0))
                                : step.conditionGetter;
                        if (step.conditionGetter.isEmpty() && !availableGetters.isEmpty())
                            step.conditionGetter = availableGetters.get(0);
                        getterBtn.setMessage(Component.literal(gLabel));
                        // opInput at peripheral position
                        opInput.setX(cx + COL_CTX + CTX_OP);
                        opInput.setWidth(52);
                    } else {
                        getterBtn.visible = false;
                        // opInput at redstone position (wider, no getter)
                        opInput.setX(cx + COL_CTX + CTX_OP_RS);
                        opInput.setWidth(CTX_OP_RS_W);
                    }
                    opInput.setValue(step.conditionOp + step.conditionThresholdStr);
                }
                case DELAY -> {
                    delayInput.visible = true;
                    delayInput.setValue(step.delaySecondsStr);
                }
                case SET_REDSTONE -> {
                    rsDirBtn.visible      = true;
                    rsSignalInput.visible = true;
                    rsDirBtn.setMessage(Component.literal("→ " + step.redstoneOutDir.getName().toUpperCase()));
                    rsSignalInput.setValue(step.redstoneOutSignalStr);
                }
                case TYPE_TEXT -> {
                    typeTextInput.visible = true;
                    typeEnterBtn.visible  = true;
                    typeTextInput.setValue(step.typeTextStr);
                    typeEnterBtn.setMessage(Component.literal(step.typeTextEnter ? "↵ on" : "↵ off"));
                }
                case CYCLE, END -> { /* no extra widgets */ }
            }
            refreshing = false;
        }

        // ── Helpers ─────────────────────────────────────────────────────────

        private int indexOfSetter(String name) {
            for (int i = 0; i < availableSetters.size(); i++)
                if (availableSetters.get(i)[0].equals(name)) return i;
            return -1;
        }
    }

    // ── Static helpers ────────────────────────────────────────────────────────

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
