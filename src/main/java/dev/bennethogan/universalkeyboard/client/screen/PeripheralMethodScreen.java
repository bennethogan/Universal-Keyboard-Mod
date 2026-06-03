package dev.bennethogan.universalkeyboard.client.screen;

import dev.bennethogan.universalkeyboard.compat.KeyboardMode;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;

public class PeripheralMethodScreen extends Screen {

    private static final int PANEL_W     = 250;
    private static final int PAD         = 8;
    private static final int ROW_H       = 9;   // getter row height
    private static final int BTN_H       = 18;  // setter button height
    private static final int MAX_GETTERS = 6;  // max visible getter rows
    private static final int MAX_SETTERS = 4;  // max visible setter button slots

    private final BlockPos       keyboardPos;
    private final String         peripheralType;
    private final List<String[]> getters; // [name, value]
    private final List<String[]> setters; // [name, argType]
    private int                  currentChannel;

    private int panelX, panelY, panelH;
    private int selectedIdx = -1;

    // Scroll state
    private int getterScroll = 0;
    private int setterScroll = 0;

    // Y bounds for hover-based scroll routing
    private int getterAreaY1, getterAreaY2;
    private int setterAreaY1, setterAreaY2;

    // Fixed setter button slots (updated on scroll)
    private final List<Button> setterBtns = new ArrayList<>();
    private int setterLabelY;

    private int getterLabelY, getterStartY;
    private int inputY;

    private EditBox inputBox;

    public PeripheralMethodScreen(BlockPos keyboardPos, String peripheralType,
                                   List<String[]> getters, List<String[]> setters, int channel) {
        super(Component.empty());
        this.keyboardPos    = keyboardPos;
        this.peripheralType = peripheralType;
        this.getters        = getters;
        this.setters        = setters;
        this.currentChannel = channel;
    }

    @Override
    protected void init() {
        setterBtns.clear();

        int visG = Math.min(getters.size(), MAX_GETTERS);
        int visS = Math.min(setters.size(), MAX_SETTERS);

        int getterH = getters.isEmpty() ? 0 : 10 + visG * ROW_H + 4;
        int setterH = setters.isEmpty() ? 0 : 10 + visS * BTN_H + 4;
        int inputH  = BTN_H + 4;
        int closeH  = BTN_H + PAD;

        panelH = PAD + 12 + 4 + getterH + setterH + inputH + closeH;
        panelH = Math.min(panelH, height - 16);
        panelX = (width  - PANEL_W) / 2;
        panelY = (height - panelH)  / 2;

        addRenderableWidget(DarkButton.make(Component.literal("?"),
                Component.translatable("gui.universalkeyboard.tooltip.wiki"),
                b -> net.minecraft.client.Minecraft.getInstance().setScreen(new WikiScreen(this)),
                panelX + PANEL_W - PAD - 16, panelY + 2, 16, 16));

        int y = panelY + PAD + 12 + 4;

        // Getter section
        if (!getters.isEmpty()) {
            getterLabelY = y;
            y += 10;
            getterStartY = y;
            getterAreaY1 = y;
            getterAreaY2 = y + visG * ROW_H;
            y += visG * ROW_H + 4;
        }

        // Setter section
        if (!setters.isEmpty()) {
            setterLabelY = y;
            y += 10;
            setterAreaY1 = y;
            for (int i = 0; i < visS; i++) {
                int slot = i;
                Button btn = DarkButton.make(Component.literal(""), b -> selectSetterSlot(slot),
                        panelX + PAD, y, PANEL_W - PAD * 2, BTN_H);
                addRenderableWidget(btn);
                setterBtns.add(btn);
                y += BTN_H;
            }
            setterAreaY2 = y;
            y += 4;
        }

        // Input row
        inputY = y;
        int editW = PANEL_W - PAD * 2 - 44;
        inputBox = new EditBox(font, panelX + PAD, y, editW, BTN_H,
                Component.literal(I18n.get("gui.universalkeyboard.label.value")));
        inputBox.setMaxLength(256);
        inputBox.setHint(Component.literal(I18n.get("gui.universalkeyboard.hint.select_control")));
        addRenderableWidget(inputBox);

        addRenderableWidget(Button.builder(Component.translatable("gui.universalkeyboard.btn.set"), b -> submitCall())
                .pos(panelX + PAD + editW + 4, y)
                .size(36, BTN_H)
                .build());
        y += BTN_H + 4;

        // Bottom row: [Channel N] [Refresh] [Close]
        int rowW   = PANEL_W - PAD * 2;
        int thirdW = (rowW - 8) / 3;
        addRenderableWidget(Button.builder(
                Component.literal(I18n.get("gui.universalkeyboard.btn.channel", currentChannel)),
                b -> ModPackets.sendCycleChannelAndReopen(keyboardPos, KeyboardMode.CC_PERIPHERAL))
                .pos(panelX + PAD, y)
                .size(thirdW, BTN_H)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.universalkeyboard.btn.refresh"), b -> requestRefresh())
                .pos(panelX + PAD + thirdW + 4, y)
                .size(thirdW, BTN_H)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.universalkeyboard.btn.close"), b -> onClose())
                .pos(panelX + PAD + (thirdW + 4) * 2, y)
                .size(rowW - (thirdW + 4) * 2, BTN_H)
                .build());

        refreshSetterButtons();
    }

    // ── Scroll ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int delta = (int) -Math.signum(dy);
        if (my >= getterAreaY1 && my <= getterAreaY2 && getters.size() > MAX_GETTERS) {
            getterScroll = Math.max(0, Math.min(getters.size() - MAX_GETTERS, getterScroll + delta));
            return true;
        }
        if (my >= setterAreaY1 && my <= setterAreaY2 && setters.size() > MAX_SETTERS) {
            setterScroll = Math.max(0, Math.min(setters.size() - MAX_SETTERS, setterScroll + delta));
            refreshSetterButtons();
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    private void refreshSetterButtons() {
        for (int slot = 0; slot < setterBtns.size(); slot++) {
            int dataIdx = setterScroll + slot;
            Button btn = setterBtns.get(slot);
            if (dataIdx < setters.size()) {
                String label = setters.get(dataIdx)[0] + " (" + setters.get(dataIdx)[1] + ")";
                btn.setMessage(Component.literal(label));
                btn.visible = true;
                btn.active  = true;
            } else {
                btn.visible = false;
            }
        }
    }

    private void selectSetterSlot(int slot) {
        int dataIdx = setterScroll + slot;
        if (dataIdx < 0 || dataIdx >= setters.size()) return;
        selectedIdx = dataIdx;
        inputBox.setValue("");
        inputBox.setHint(Component.literal("§7" + setters.get(dataIdx)[0] + " (" + setters.get(dataIdx)[1] + ")"));
        setFocused(inputBox);
        inputBox.setFocused(true);
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);

        // Panel box
        g.fill(panelX,              panelY,              panelX + PANEL_W,   panelY + panelH, 0xFF111111);
        g.fill(panelX,              panelY,              panelX + PANEL_W,   panelY + 1,      0xFF666666);
        g.fill(panelX,              panelY + panelH - 1, panelX + PANEL_W,   panelY + panelH, 0xFF666666);
        g.fill(panelX,              panelY,              panelX + 1,         panelY + panelH, 0xFF666666);
        g.fill(panelX + PANEL_W -1, panelY,              panelX + PANEL_W,   panelY + panelH, 0xFF666666);

        // Title
        g.drawCenteredString(font, "§b" + peripheralType, panelX + PANEL_W / 2, panelY + PAD, 0xFFFFFF);

        // Getter section
        if (!getters.isEmpty()) {
            int vis = Math.min(getters.size(), MAX_GETTERS);
            g.drawString(font, I18n.get("gui.universalkeyboard.label.values_section"), panelX + PAD, getterLabelY, 0xAAAAAA, true);

            for (int i = 0; i < vis; i++) {
                int di = getterScroll + i;
                if (di >= getters.size()) break;
                String[] e = getters.get(di);
                g.drawString(font, "§8" + e[0] + ": §f" + e[1],
                        panelX + PAD + 4, getterStartY + i * ROW_H, 0xFFFFFF, true);
            }

            // Scroll arrows on right edge when overflowing
            if (getters.size() > MAX_GETTERS) {
                int arrowX = panelX + PANEL_W - PAD - 4;
                if (getterScroll > 0)
                    g.drawString(font, "▲", arrowX, getterAreaY1, 0x888888, false);
                if (getterScroll < getters.size() - MAX_GETTERS)
                    g.drawString(font, "▼", arrowX, getterAreaY2 - ROW_H, 0x888888, false);
            }
        }

        // Setter section label
        if (!setters.isEmpty()) {
            String hint = selectedIdx >= 0
                    ? I18n.get("gui.universalkeyboard.label.controls_section_selected")
                    : I18n.get("gui.universalkeyboard.label.controls_section");
            g.drawString(font, hint, panelX + PAD, setterLabelY, 0xAAAAAA, true);

            // Scroll arrows on right edge when overflowing
            if (setters.size() > MAX_SETTERS) {
                int arrowX = panelX + PANEL_W - PAD - 4;
                if (setterScroll > 0)
                    g.drawString(font, "▲", arrowX, setterAreaY1, 0x888888, false);
                if (setterScroll < setters.size() - MAX_SETTERS)
                    g.drawString(font, "▼", arrowX, setterAreaY2 - BTN_H, 0x888888, false);
            }
        }

        for (var renderable : this.renderables) {
            renderable.render(g, mx, my, pt);
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void submitCall() {
        if (selectedIdx < 0 || selectedIdx >= setters.size()) return;
        String method  = setters.get(selectedIdx)[0];
        String argType = setters.get(selectedIdx)[1];
        String arg     = inputBox.getValue().trim();
        if (arg.isEmpty() && !argType.equals("call")) return;
        ModPackets.sendCallPeripheralMethod(keyboardPos, method, arg);
    }

    private void requestRefresh() {
        ModPackets.sendCallPeripheralMethod(keyboardPos, "", "");
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (inputBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitCall();
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (!inputBox.isFocused() && MenuNav.handleTabBack(this, keyCode, keyboardPos)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
