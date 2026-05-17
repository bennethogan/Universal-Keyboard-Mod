package dev.bennethogan.bennetsmod.client.screen;

import dev.bennethogan.bennetsmod.compat.KeyboardMode;
import dev.bennethogan.bennetsmod.compat.wireless.CreateWirelessHelper;
import dev.bennethogan.bennetsmod.network.ModPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class ModeSelectionScreen extends Screen {

    private static final int PANEL_W   = 280;
    private static final int PAD       = 10;
    private static final int BTN_H     = 18;
    private static final int ROW_GAP   = 4;
    private static final int SMALL_W   = 54;
    private static final int SMALL_H   = 14;
    private static final int HEADER_H  = PAD + SMALL_H + 8; // space for title row with small buttons

    private final BlockPos keyboardPos;
    private final int      availableBits;

    private int panelX, panelY, panelH;
    private int firstRowY;

    public ModeSelectionScreen(BlockPos keyboardPos, String targetTypeName, int availableBits) {
        super(Component.empty());
        this.keyboardPos   = keyboardPos;
        this.availableBits = availableBits;
    }

    @Override
    protected void init() {
        KeyboardMode[] modes = KeyboardMode.values();
        int rowsH = modes.length * (BTN_H + ROW_GAP);
        panelH = HEADER_H + rowsH + PAD;

        panelX = (width  - PANEL_W) / 2;
        panelY = (height - panelH)  / 2;

        firstRowY = panelY + HEADER_H;

        // Small "Unlink" button — top-left of panel
        addRenderableWidget(Button.builder(Component.literal("Unlink"), b -> onUnlink())
                .pos(panelX + PAD, panelY + PAD)
                .size(SMALL_W, SMALL_H)
                .build());

        // Small "Wifi Setup" button — top-right of panel (only when Create wireless is available)
        if (CreateWirelessHelper.isPresent()) {
            addRenderableWidget(Button.builder(Component.literal("Wifi Setup"), b -> onWireless())
                    .pos(panelX + PANEL_W - PAD - SMALL_W, panelY + PAD)
                    .size(SMALL_W, SMALL_H)
                    .build());
        }

        // Full-width mode buttons
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
    }

    private void onWireless() {
        ModPackets.sendOpenWirelessConfig(keyboardPos);
        onClose();
    }

    private void onUnlink() {
        ModPackets.sendUnlinkKeyboard(keyboardPos);
        onClose();
    }

    private void selectMode(KeyboardMode mode) {
        ModPackets.sendSelectMode(keyboardPos, mode);
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);

        // Panel fill + border
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xFF111111);
        g.fill(panelX, panelY,              panelX + PANEL_W, panelY + 1,         0xFF666666);
        g.fill(panelX, panelY + panelH - 1, panelX + PANEL_W, panelY + panelH,   0xFF666666);
        g.fill(panelX, panelY,              panelX + 1,        panelY + panelH,   0xFF666666);
        g.fill(panelX + PANEL_W - 1, panelY, panelX + PANEL_W, panelY + panelH,  0xFF666666);

        // Title centred in header area, between the two small buttons
        int titleY = panelY + PAD + (SMALL_H - 8) / 2;
        g.drawCenteredString(font, "§bUniversal Keyboard", panelX + PANEL_W / 2, titleY, 0xFFFFFF);

        // Greyed-out rows for unavailable modes
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
