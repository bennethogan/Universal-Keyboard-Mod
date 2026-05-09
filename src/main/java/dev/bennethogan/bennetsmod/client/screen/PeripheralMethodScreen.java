package dev.bennethogan.bennetsmod.client.screen;

import dev.bennethogan.bennetsmod.network.ModPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class PeripheralMethodScreen extends Screen {

    private static final int PANEL_W  = 250;
    private static final int PAD      = 8;
    private static final int ROW_H    = 9;
    private static final int BTN_H    = 18;

    private final BlockPos    keyboardPos;
    private final String      peripheralType;
    private final List<String[]> getters; // [name, value]
    private final List<String[]> setters; // [name, argType]

    private int panelX, panelY, panelH;
    private int selectedIdx = -1;

    // y positions computed in init, reused in render
    private int getterLabelY, getterStartY;
    private int setterLabelY;
    private int inputY;

    private EditBox inputBox;

    public PeripheralMethodScreen(BlockPos keyboardPos, String peripheralType,
                                   List<String[]> getters, List<String[]> setters) {
        super(Component.empty());
        this.keyboardPos    = keyboardPos;
        this.peripheralType = peripheralType;
        this.getters        = getters;
        this.setters        = setters;
    }

    @Override
    protected void init() {
        int shownGetters = Math.min(getters.size(), 6);
        int shownSetters = Math.min(setters.size(), 6);

        int getterH = getters.isEmpty() ? 0 : 10 + shownGetters * ROW_H + 4;
        int setterH = setters.isEmpty() ? 0 : 10 + shownSetters * BTN_H + 4;
        int inputH  = BTN_H + 4;
        int closeH  = BTN_H + PAD;

        panelH = PAD + 12 + 4 + getterH + setterH + inputH + closeH;
        panelH = Math.min(panelH, height - 16);
        panelX = (width  - PANEL_W) / 2;
        panelY = (height - panelH)  / 2;

        int y = panelY + PAD + 12 + 4; // below title

        // Getter section positions (text only, no widgets)
        if (!getters.isEmpty()) {
            getterLabelY = y;
            y += 10;
            getterStartY = y;
            y += shownGetters * ROW_H + 4;
        }

        // Setter section
        if (!setters.isEmpty()) {
            setterLabelY = y;
            y += 10;
            for (int i = 0; i < shownSetters; i++) {
                final int idx = i;
                String label = setters.get(i)[0] + " (" + setters.get(i)[1] + ")";
                addRenderableWidget(Button.builder(Component.literal(label), b -> selectSetter(idx))
                        .pos(panelX + PAD, y)
                        .size(PANEL_W - PAD * 2, BTN_H)
                        .build());
                y += BTN_H;
            }
            y += 4;
        }

        // Input row
        inputY = y;
        int editW = PANEL_W - PAD * 2 - 44;
        inputBox = new EditBox(font, panelX + PAD, y, editW, BTN_H,
                Component.literal("value"));
        inputBox.setMaxLength(256);
        inputBox.setHint(Component.literal("§7select a control above"));
        addRenderableWidget(inputBox);

        addRenderableWidget(Button.builder(Component.literal("Set"), b -> submitCall())
                .pos(panelX + PAD + editW + 4, y)
                .size(36, BTN_H)
                .build());
        y += BTN_H + 4;

        // Close + Refresh
        int halfW = PANEL_W / 2 - PAD;
        addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> requestRefresh())
                .pos(panelX + PAD, y)
                .size(halfW, BTN_H)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .pos(panelX + PAD + halfW + 4, y)
                .size(halfW - 4, BTN_H)
                .build());
    }

    private void selectSetter(int idx) {
        selectedIdx = idx;
        inputBox.setValue("");
        setFocused(inputBox);
        inputBox.setFocused(true);
    }

    private void submitCall() {
        if (selectedIdx < 0 || selectedIdx >= setters.size()) return;
        String method = setters.get(selectedIdx)[0];
        String arg    = inputBox.getValue().trim();
        if (arg.isEmpty()) return;
        ModPackets.sendCallPeripheralMethod(keyboardPos, method, arg);
    }

    private void requestRefresh() {
        // empty methodName = server just rescans and resends the menu
        ModPackets.sendCallPeripheralMethod(keyboardPos, "", "");
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);

        // Panel box
        g.fill(panelX,              panelY,              panelX + PANEL_W,     panelY + panelH,     0xFF111111);
        g.fill(panelX,              panelY,              panelX + PANEL_W,     panelY + 1,           0xFF666666);
        g.fill(panelX,              panelY + panelH - 1, panelX + PANEL_W,     panelY + panelH,     0xFF666666);
        g.fill(panelX,              panelY,              panelX + 1,           panelY + panelH,     0xFF666666);
        g.fill(panelX + PANEL_W -1, panelY,              panelX + PANEL_W,     panelY + panelH,     0xFF666666);

        // Title
        g.drawCenteredString(font, "§b" + peripheralType, panelX + PANEL_W / 2, panelY + PAD, 0xFFFFFF);

        // Getter section
        if (!getters.isEmpty()) {
            g.drawString(font, "§7Values:", panelX + PAD, getterLabelY, 0xAAAAAA, true);
            int shown = Math.min(getters.size(), 6);
            for (int i = 0; i < shown; i++) {
                String[] e = getters.get(i);
                g.drawString(font, "§8" + e[0] + ": §f" + e[1],
                        panelX + PAD + 4, getterStartY + i * ROW_H, 0xFFFFFF, true);
            }
            if (getters.size() > 6)
                g.drawString(font, "§8(+" + (getters.size() - 6) + " more)",
                        panelX + PAD + 4, getterStartY + 6 * ROW_H, 0x666666, true);
        }

        // Setter section label
        if (!setters.isEmpty()) {
            String hint = selectedIdx >= 0
                    ? "§7Controls: (§e" + setters.get(selectedIdx)[0] + "§7 selected)"
                    : "§7Controls:";
            g.drawString(font, hint, panelX + PAD, setterLabelY, 0xAAAAAA, true);
        }

        // Input row label
        if (selectedIdx >= 0 && selectedIdx < setters.size()) {
            String argType = setters.get(selectedIdx)[1];
            g.drawString(font, "§7(" + argType + ")", panelX + PAD, inputY - 9, 0x888888, true);
        }

        // Render widgets directly — do NOT call super.render() here because Screen.render()
        // calls renderBackground() a second time, blurring everything we just drew above.
        for (var renderable : this.renderables) {
            renderable.render(g, mx, my, pt);
        }
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
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
