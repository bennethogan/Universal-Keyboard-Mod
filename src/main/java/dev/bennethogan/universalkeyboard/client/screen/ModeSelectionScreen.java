package dev.bennethogan.universalkeyboard.client.screen;

import dev.bennethogan.universalkeyboard.compat.KeyboardMode;
import dev.bennethogan.universalkeyboard.compat.wireless.WirelessPresence;
import dev.bennethogan.universalkeyboard.network.ModPackets;
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
    private static final int SMALL_W   = 62;
    private static final int SMALL_H   = 14;
    private static final int HEADER_H  = PAD + SMALL_H + 8;

    // Confirmation dialog dimensions
    private static final int CONF_W    = 200;
    private static final int CONF_H    = 52;

    private final BlockPos keyboardPos;
    private final int      availableBits;

    private int panelX, panelY, panelH;
    private int firstRowY;

    private boolean showResetConfirm = false;

    public ModeSelectionScreen(BlockPos keyboardPos, String targetTypeName, int availableBits) {
        super(Component.empty());
        this.keyboardPos   = keyboardPos;
        this.availableBits = availableBits;
    }

    @Override
    protected void init() {
        KeyboardMode[] modes = KeyboardMode.values();
        int rowsH = (modes.length + 1) * (BTN_H + ROW_GAP);
        panelH = HEADER_H + rowsH + PAD;

        panelX = (width  - PANEL_W) / 2;
        panelY = (height - panelH)  / 2;

        firstRowY = panelY + HEADER_H;

        // "Reset Data" button — top-left
        addRenderableWidget(Button.builder(Component.literal("Reset Data"),
                        b -> showResetConfirm = true)
                .pos(panelX + PAD, panelY + PAD)
                .size(SMALL_W, SMALL_H)
                .build());

        // "Wifi Setup" button — top-right (Create wireless only)
        if (WirelessPresence.isPresent()) {
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
        // Live Controller button
        addRenderableWidget(Button.builder(Component.literal("Live Controller"),
                b -> { ModPackets.sendOpenLiveControl(keyboardPos); onClose(); })
                .pos(panelX + PAD, y)
                .size(PANEL_W - PAD * 2, BTN_H)
                .build());
    }

    private void onWireless() {
        ModPackets.sendOpenWirelessConfig(keyboardPos);
        onClose();
    }

    private void doResetData() {
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

        int titleY = panelY + PAD + (SMALL_H - 8) / 2;
        g.drawCenteredString(font, "§bUniversal Keyboard", panelX + PANEL_W / 2, titleY, 0xFFFFFF);

        // Greyed-out rows for unavailable modes
        if (!showResetConfirm) {
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
            for (var renderable : this.renderables) renderable.render(g, mx, my, pt);
        } else {
            renderResetConfirm(g, mx, my);
        }
    }

    private void renderResetConfirm(GuiGraphics g, int mx, int my) {
        int cx = panelX + (PANEL_W - CONF_W) / 2;
        int cy = panelY + (panelH - CONF_H) / 2;

        // Dialog box
        g.fill(cx, cy, cx + CONF_W, cy + CONF_H, 0xFF0A0A14);
        g.fill(cx, cy, cx + CONF_W, cy + 1, 0xFFCC4444);
        g.fill(cx, cy + CONF_H - 1, cx + CONF_W, cy + CONF_H, 0xFFCC4444);
        g.fill(cx, cy, cx + 1, cy + CONF_H, 0xFFCC4444);
        g.fill(cx + CONF_W - 1, cy, cx + CONF_W, cy + CONF_H, 0xFFCC4444);

        g.drawCenteredString(font, "§cReset all keyboard data?", cx + CONF_W / 2, cy + 6, 0xFFFFFF);
        g.drawCenteredString(font, "§7This cannot be undone.", cx + CONF_W / 2, cy + 16, 0xAAAAAA);

        // Yes button
        int btnW = 54;
        int yesX = cx + CONF_W / 2 - btnW - 4;
        int noX  = cx + CONF_W / 2 + 4;
        int btnY = cy + CONF_H - 18;
        boolean yesHov = isIn(mx, my, yesX, btnY, btnW, 14);
        boolean noHov  = isIn(mx, my, noX,  btnY, btnW, 14);
        g.fill(yesX, btnY, yesX + btnW, btnY + 14, yesHov ? 0xFF882222 : 0xFF551111);
        g.fill(noX,  btnY, noX  + btnW, btnY + 14, noHov  ? 0xFF334433 : 0xFF223322);
        g.fill(yesX, btnY, yesX + btnW, btnY + 1, 0xFFCC4444);
        g.fill(noX,  btnY, noX  + btnW, btnY + 1, 0xFF448844);
        g.drawCenteredString(font, "§cYes, reset", yesX + btnW / 2, btnY + 3, 0xFFFFFF);
        g.drawCenteredString(font, "§aCancel", noX + btnW / 2, btnY + 3, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (showResetConfirm && btn == 0) {
            int cx  = panelX + (PANEL_W - CONF_W) / 2;
            int cy  = panelY + (panelH  - CONF_H)  / 2;
            int btnW = 54;
            int yesX = cx + CONF_W / 2 - btnW - 4;
            int noX  = cx + CONF_W / 2 + 4;
            int btnY = cy + CONF_H - 18;
            if (isIn((int) mx, (int) my, yesX, btnY, btnW, 14)) {
                doResetData();
                return true;
            }
            if (isIn((int) mx, (int) my, noX, btnY, btnW, 14)) {
                showResetConfirm = false;
                return true;
            }
            return true; // consume clicks while dialog is open
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (showResetConfirm) { showResetConfirm = false; return true; }
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static boolean isIn(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
