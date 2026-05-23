package dev.bennethogan.universalkeyboard.client.screen;

import dev.bennethogan.universalkeyboard.compat.KeyboardMode;
import dev.bennethogan.universalkeyboard.compat.PeripheralHelper;
import dev.bennethogan.universalkeyboard.compat.wireless.WirelessPresence;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.FormattedCharSequence;

public class ModeSelectionScreen extends Screen {

    private static final int PANEL_W   = 280;
    private static final int PAD       = 10;
    private static final int BTN_H     = 18;
    private static final int ROW_GAP   = 4;
    private static final int SMALL_W   = 62;
    private static final int SMALL_H   = 14;
    private static final int HEADER_H  = PAD + SMALL_H + 8;

    private static final int CONF_W    = 200;
    private static final int CONF_PAD  = 6;
    private static final int CONF_GAP  = 2;
    private static final int CONF_BTN_H = 14;

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
        addRenderableWidget(Button.builder(Component.translatable("gui.universalkeyboard.btn.reset_data"),
                        b -> showResetConfirm = true)
                .pos(panelX + PAD, panelY + PAD)
                .size(SMALL_W, SMALL_H)
                .build());

        // "Wifi Setup" button — top-right (Create wireless only)
        if (WirelessPresence.isPresent()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.universalkeyboard.btn.wifi_setup"), b -> onWireless())
                    .pos(panelX + PANEL_W - PAD - SMALL_W, panelY + PAD)
                    .size(SMALL_W, SMALL_H)
                    .build());
        }

        // Live Controller always at top
        addRenderableWidget(Button.builder(Component.translatable("gui.universalkeyboard.btn.live_controller"),
                b -> { ModPackets.sendOpenLiveControl(keyboardPos); onClose(); })
                .pos(panelX + PAD, firstRowY)
                .size(PANEL_W - PAD * 2, BTN_H)
                .build());

        // Full-width mode buttons below Live Controller
        int y = firstRowY + BTN_H + ROW_GAP;
        for (KeyboardMode mode : modes) {
            boolean available = (availableBits & (1 << mode.ordinal())) != 0;
            if (available) {
                addRenderableWidget(Button.builder(
                        Component.translatable(mode.displayName),
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
        g.drawCenteredString(font, I18n.get("gui.universalkeyboard.screen.mode_selection.title"), panelX + PANEL_W / 2, titleY, 0xFFFFFF);

        if (!showResetConfirm) {
            boolean ccMissing = !PeripheralHelper.isCCPresent();
            KeyboardMode[] modes = KeyboardMode.values();
            int y = firstRowY + BTN_H + ROW_GAP;
            KeyboardMode tooltipMode = null;
            for (KeyboardMode mode : modes) {
                boolean available = (availableBits & (1 << mode.ordinal())) != 0;
                if (!available) {
                    int rowX = panelX + PAD;
                    int rowW = PANEL_W - PAD * 2;
                    boolean ccRow = ccMissing && mode.requiresCC();
                    g.fill(rowX, y, rowX + rowW, y + BTN_H, 0xFF1A1A1A);
                    String reason = ccRow ? I18n.get("gui.universalkeyboard.msg.cc_not_installed") : I18n.get(mode.unavailableReason());
                    String label  = "§8" + I18n.get(mode.displayName) + " §7(" + reason + ")";
                    g.drawCenteredString(font, label, rowX + rowW / 2, y + 5, 0x666666);
                    if (ccRow && mx >= rowX && mx < rowX + rowW && my >= y && my < y + BTN_H) {
                        tooltipMode = mode;
                    }
                }
                y += BTN_H + ROW_GAP;
            }
            for (var renderable : this.renderables) renderable.render(g, mx, my, pt);
            if (tooltipMode != null) {
                List<FormattedCharSequence> lines = List.of(
                        Component.translatable("gui.universalkeyboard.tooltip.cc_modrinth_1").getVisualOrderText(),
                        Component.translatable("gui.universalkeyboard.tooltip.cc_modrinth_2").getVisualOrderText());
                g.renderTooltip(font, lines, mx, my);
            }
        } else {
            renderResetConfirm(g, mx, my);
        }
    }

    // ── Reset confirm dialog ─────────────────────────────────────────────────

    private int resetDialogHeight() {
        int textW = CONF_W - CONF_PAD * 2;
        return CONF_PAD
                + GuiText.wrappedHeight(font, I18n.get("gui.universalkeyboard.dialog.reset_title"), textW) + CONF_GAP
                + GuiText.wrappedHeight(font, I18n.get("gui.universalkeyboard.dialog.reset_body"), textW) + CONF_GAP + 4
                + CONF_BTN_H + CONF_PAD;
    }

    private void renderResetConfirm(GuiGraphics g, int mx, int my) {
        int dh = resetDialogHeight();
        int textW = CONF_W - CONF_PAD * 2;
        int cx = panelX + (PANEL_W - CONF_W) / 2;
        int cy = panelY + (panelH - dh) / 2;

        g.fill(cx, cy, cx + CONF_W, cy + dh, 0xFF0A0A14);
        g.fill(cx, cy, cx + CONF_W, cy + 1, 0xFFCC4444);
        g.fill(cx, cy + dh - 1, cx + CONF_W, cy + dh, 0xFFCC4444);
        g.fill(cx, cy, cx + 1, cy + dh, 0xFFCC4444);
        g.fill(cx + CONF_W - 1, cy, cx + CONF_W, cy + dh, 0xFFCC4444);

        int centerX = cx + CONF_W / 2;
        int y = cy + CONF_PAD;
        y += GuiText.drawWrappedCentered(g, font, I18n.get("gui.universalkeyboard.dialog.reset_title"), centerX, y, textW, 0xFFFFFF) + CONF_GAP;
        GuiText.drawWrappedCentered(g, font, I18n.get("gui.universalkeyboard.dialog.reset_body"), centerX, y, textW, 0xAAAAAA);

        int btnW = 54;
        int yesX = cx + CONF_W / 2 - btnW - 4;
        int noX  = cx + CONF_W / 2 + 4;
        int btnY = cy + dh - CONF_PAD - CONF_BTN_H;
        boolean yesHov = isIn(mx, my, yesX, btnY, btnW, CONF_BTN_H);
        boolean noHov  = isIn(mx, my, noX,  btnY, btnW, CONF_BTN_H);
        g.fill(yesX, btnY, yesX + btnW, btnY + CONF_BTN_H, yesHov ? 0xFF882222 : 0xFF551111);
        g.fill(noX,  btnY, noX  + btnW, btnY + CONF_BTN_H, noHov  ? 0xFF334433 : 0xFF223322);
        g.fill(yesX, btnY, yesX + btnW, btnY + 1, 0xFFCC4444);
        g.fill(noX,  btnY, noX  + btnW, btnY + 1, 0xFF448844);
        g.drawCenteredString(font, I18n.get("gui.universalkeyboard.btn.yes_reset"), yesX + btnW / 2, btnY + 3, 0xFFFFFF);
        g.drawCenteredString(font, I18n.get("gui.universalkeyboard.btn.cancel"), noX + btnW / 2, btnY + 3, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (showResetConfirm && btn == 0) {
            int dh   = resetDialogHeight();
            int cx   = panelX + (PANEL_W - CONF_W) / 2;
            int cy   = panelY + (panelH  - dh)  / 2;
            int btnW = 54;
            int yesX = cx + CONF_W / 2 - btnW - 4;
            int noX  = cx + CONF_W / 2 + 4;
            int btnY = cy + dh - CONF_PAD - CONF_BTN_H;
            if (isIn((int) mx, (int) my, yesX, btnY, btnW, CONF_BTN_H)) { doResetData();          return true; }
            if (isIn((int) mx, (int) my, noX,  btnY, btnW, CONF_BTN_H)) { showResetConfirm = false; return true; }
            return true;
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
