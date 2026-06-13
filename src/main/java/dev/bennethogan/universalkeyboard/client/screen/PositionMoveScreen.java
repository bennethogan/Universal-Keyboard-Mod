package dev.bennethogan.universalkeyboard.client.screen;

import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class PositionMoveScreen extends Screen {

    private static final int W       = 250;
    private static final int PAD     = 10;
    private static final int GAP     = 5;
    private static final int BTN_H   = 20;
    private static final int BTN_W   = 224;
    private static final int CHK     = 9;

    private final BlockPos kbPos;
    private boolean dontAsk = false;
    private String pendingTooltip = null;
    private int tooltipX, tooltipY;

    public PositionMoveScreen(BlockPos kbPos) {
        super(Component.translatable("gui.universalkeyboard.dialog.position_move.title"));
        this.kbPos = kbPos;
    }

    @Override public boolean isPauseScreen()      { return false; }

    private static final String[] BTN_LABEL_KEYS = {
        "gui.universalkeyboard.dialog.position_move.btn_a",
        "gui.universalkeyboard.dialog.position_move.btn_b",
        "gui.universalkeyboard.dialog.position_move.btn_c"
    };
    private static final String[] BTN_TIP_KEYS = {
        "gui.universalkeyboard.dialog.position_move.tip_a",
        "gui.universalkeyboard.dialog.position_move.tip_b",
        "gui.universalkeyboard.dialog.position_move.tip_c"
    };

    // resolve translation key
    private static String tr(String key) { return Component.translatable(key).getString(); }

    private int dialogHeight() {
        return PAD + 9 + GAP      // title
             + 9 + GAP            // subtitle
             + BTN_H + GAP        // A
             + BTN_H + GAP        // B
             + BTN_H + GAP        // C
             + CHK + GAP          // checkbox
             + PAD;
    }

    private int dlgX() { return (width  - W) / 2; }
    private int dlgY() { return (height - dialogHeight()) / 2; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        pendingTooltip = null;
        g.fill(0, 0, width, height, 0xAA000000);

        int dh = dialogHeight();
        int cx = dlgX(), cy = dlgY();

        // background + amber border
        g.fill(cx, cy, cx + W, cy + dh, 0xFF0A0A14);
        g.fill(cx, cy,         cx + W,    cy + 1,     0xFFE0A030);
        g.fill(cx, cy + dh - 1, cx + W,  cy + dh,    0xFFE0A030);
        g.fill(cx, cy,         cx + 1,    cy + dh,    0xFFE0A030);
        g.fill(cx + W - 1, cy, cx + W,   cy + dh,    0xFFE0A030);

        int centerX = cx + W / 2;
        int y = cy + PAD;

        g.drawCenteredString(font, tr("gui.universalkeyboard.dialog.position_move.heading"), centerX, y, 0xFFFFD27F);
        y += 9 + GAP;
        g.drawCenteredString(font, tr("gui.universalkeyboard.dialog.position_move.subtitle"), centerX, y, 0xFFCCCCCC);
        y += 9 + GAP;

        for (int i = 0; i < 3; i++) {
            y = renderBtn(g, mx, my, cx, y, tr(BTN_LABEL_KEYS[i]), tr(BTN_TIP_KEYS[i]), i);
            y += GAP;
        }

        // "Don't ask again" checkbox
        int boxX = cx + PAD;
        String chkLabel = tr("gui.universalkeyboard.dialog.position_move.dont_ask");
        int chkLabelW = font.width(chkLabel);
        boolean chkHov = isIn(mx, my, boxX, y, CHK + 4 + chkLabelW, CHK);
        g.fill(boxX, y, boxX + CHK, y + CHK, chkHov ? 0xFF333333 : 0xFF1C1C1C);
        g.fill(boxX, y,         boxX + CHK, y + 1,      0xFF888888);
        g.fill(boxX, y + CHK-1, boxX + CHK, y + CHK,    0xFF888888);
        g.fill(boxX, y,         boxX + 1,   y + CHK,    0xFF888888);
        g.fill(boxX + CHK-1, y, boxX + CHK, y + CHK,    0xFF888888);
        if (dontAsk) g.fill(boxX + 2, y + 2, boxX + CHK - 2, y + CHK - 2, 0xFFE0A030);
        g.drawString(font, chkLabel, boxX + CHK + 4, y + 1, 0xFFAAAAAA, false);
        for (var r : this.renderables) r.render(g, mx, my, pt);

        if (pendingTooltip != null) {
            String[] lines = pendingTooltip.split("\n");
            int tw = 0;
            for (String l : lines) tw = Math.max(tw, font.width(l));
            int th = lines.length * (font.lineHeight + 1) - 1;
            int tx = Math.min(tooltipX + 12, width  - tw - 6);
            int ty = Math.min(tooltipY + 12, height - th - 6);
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            g.fill(tx - 3, ty - 3, tx + tw + 3, ty + th + 3, 0xEE1A1A2A);
            g.fill(tx - 3, ty - 3, tx + tw + 3, ty - 2,      0xFFB060A0);
            for (int i = 0; i < lines.length; i++)
                g.drawString(font, lines[i], tx, ty + i * (font.lineHeight + 1), 0xFFFFFFFF, false);
            g.pose().popPose();
        }
    }

    private int renderBtn(GuiGraphics g, int mx, int my, int cx, int y,
                           String label, String tip, int choice) {
        int bx = cx + (W - BTN_W) / 2;
        boolean hov = isIn(mx, my, bx, y, BTN_W, BTN_H);
        g.fill(bx, y, bx + BTN_W, y + BTN_H, hov ? 0xFF3A3A3A : 0xFF202020);
        g.fill(bx, y, bx + BTN_W, y + 1, 0xFF666666);
        g.drawCenteredString(font, label, bx + BTN_W / 2, y + (BTN_H - 8) / 2, 0xFFFFFFFF);
        if (hov) { pendingTooltip = tip; tooltipX = mx; tooltipY = my; }
        return y + BTN_H;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return true;
        int cx = dlgX(), cy = dlgY();
        int y = cy + PAD + 9 + GAP + 9 + GAP;
        int bx = cx + (W - BTN_W) / 2;

        for (int i = 0; i < 3; i++) {
            if (isIn((int) mx, (int) my, bx, y, BTN_W, BTN_H)) {
                sendChoice(i);
                return true;
            }
            y += BTN_H + GAP;
        }

        // checkbox
        int boxX = cx + PAD;
        if (isIn((int) mx, (int) my, boxX, y, CHK + 4 + font.width(tr("gui.universalkeyboard.dialog.position_move.dont_ask")), CHK)) {
            dontAsk = !dontAsk;
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return true; // block ESC — player must make a choice
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void sendChoice(int choice) {
        PacketDistributor.sendToServer(new ModPackets.ApplyPositionMovePacket(kbPos, (byte) choice, dontAsk));
        onClose();
    }

    private static boolean isIn(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
