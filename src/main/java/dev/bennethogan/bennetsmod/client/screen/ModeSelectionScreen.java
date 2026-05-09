package dev.bennethogan.bennetsmod.client.screen;

import dev.bennethogan.bennetsmod.compat.KeyboardMode;
import dev.bennethogan.bennetsmod.network.ModPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class ModeSelectionScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int PAD     = 10;
    private static final int BTN_H   = 18;
    private static final int ROW_GAP = 4;

    private final BlockPos keyboardPos;
    private final String   targetTypeName;
    private final int      availableBits;

    private int panelX, panelY, panelH;
    private int firstRowY;

    public ModeSelectionScreen(BlockPos keyboardPos, String targetTypeName, int availableBits) {
        super(Component.empty());
        this.keyboardPos    = keyboardPos;
        this.targetTypeName = targetTypeName;
        this.availableBits  = availableBits;
    }

    @Override
    protected void init() {
        KeyboardMode[] modes = KeyboardMode.values();
        int titleH    = 14;
        int subtitleH = 12;
        int rowsH     = modes.length * (BTN_H + ROW_GAP);
        int closeH    = BTN_H + PAD;
        panelH = PAD + titleH + subtitleH + 6 + rowsH + closeH;

        panelX = (width  - PANEL_W) / 2;
        panelY = (height - panelH)  / 2;

        firstRowY = panelY + PAD + titleH + subtitleH + 6;

        int y = firstRowY;
        for (KeyboardMode mode : modes) {
            boolean available = (availableBits & (1 << mode.ordinal())) != 0;
            if (available) {
                addRenderableWidget(Button.builder(
                        Component.literal(mode.displayName),
                        b -> selectMode(mode))
                        .pos(panelX + PAD, y)
                        .size(PANEL_W - PAD * 2, BTN_H)
                        .build());
            }
            y += BTN_H + ROW_GAP;
        }

        y += 2;
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .pos(panelX + PANEL_W / 2 - 40, y)
                .size(80, BTN_H)
                .build());
    }

    private void selectMode(KeyboardMode mode) {
        ModPackets.sendSelectMode(keyboardPos, mode);
        // Server's response packet will replace the screen for peripheral mode,
        // or close it (for CC capture / Create capture which take over input).
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);

        // Panel + border
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xFF111111);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + 1, 0xFF666666);
        g.fill(panelX, panelY + panelH - 1, panelX + PANEL_W, panelY + panelH, 0xFF666666);
        g.fill(panelX, panelY, panelX + 1, panelY + panelH, 0xFF666666);
        g.fill(panelX + PANEL_W - 1, panelY, panelX + PANEL_W, panelY + panelH, 0xFF666666);

        // Title + target type
        g.drawCenteredString(font, "§bUniversal Keyboard",
                panelX + PANEL_W / 2, panelY + PAD, 0xFFFFFF);
        g.drawCenteredString(font, "§7" + targetTypeName,
                panelX + PANEL_W / 2, panelY + PAD + 12, 0xAAAAAA);

        // Greyed-out rows for unavailable modes (available rows are buttons drawn by widgets)
        KeyboardMode[] modes = KeyboardMode.values();
        int y = firstRowY;
        for (KeyboardMode mode : modes) {
            boolean available = (availableBits & (1 << mode.ordinal())) != 0;
            if (!available) {
                int rowX = panelX + PAD;
                int rowW = PANEL_W - PAD * 2;
                g.fill(rowX, y, rowX + rowW, y + BTN_H, 0xFF1A1A1A);
                String label = "§8" + mode.displayName + " §7(" + mode.unavailableReason() + ")";
                g.drawCenteredString(font, label, rowX + rowW / 2, y + 5, 0x666666);
            }
            y += BTN_H + ROW_GAP;
        }

        // Render widgets directly — do NOT call super.render(), which would call
        // renderBackground() a second time and blur the text we just drew.
        for (var renderable : this.renderables) {
            renderable.render(g, mx, my, pt);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
