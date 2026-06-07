package dev.bennethogan.universalkeyboard.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

// informational popup that has a "dont ask me again" checkbox. Currently only used for
// the Create: Connected wildcard issue
public class NoticeDialog {

    private static final int W      = 220;
    private static final int PAD    = 8;
    private static final int GAP    = 3;
    private static final int BTN_H  = 14;
    private static final int BTN_W  = 70;
    private static final int CHK    = 9;   // checkbox size

    private final Font font;

    private boolean  open;
    private String   title;
    private String   body;
    private String   dismissLabel;
    private String   checkboxLabel;
    private boolean  dontShowAgain;
    private Consumer<Boolean> onDismiss;

    private int px, py, pw, ph;

    public NoticeDialog(Font font) { this.font = font; }

    public void setParentBounds(int x, int y, int w, int h) {
        px = x; py = y; pw = w; ph = h;
    }

    public void open(String title, String body, String dismissLabel,
                     String checkboxLabel, Consumer<Boolean> onDismiss) {
        this.title         = title;
        this.body          = body;
        this.dismissLabel  = dismissLabel;
        this.checkboxLabel = checkboxLabel;
        this.onDismiss     = onDismiss;
        this.dontShowAgain = false;
        this.open          = true;
    }

    public void close()     { open = false; }
    public boolean isOpen() { return open; }

    private int textW()   { return W - PAD * 2; }

    private int dialogHeight() {
        int h = PAD + GuiText.wrappedHeight(font, title, textW()) + GAP;
        if (body != null && !body.isEmpty())
            h += GuiText.wrappedHeight(font, body, textW()) + GAP;
        h += CHK + GAP;          // checkbox row
        return h + 4 + BTN_H + PAD;
    }

    private int dialogX() { return px + (pw - W) / 2; }
    private int dialogY() { return py + (ph - dialogHeight()) / 2; }

    public void render(GuiGraphics g, int mx, int my) {
        if (!open) return;

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

        g.fill(px, py, px + pw, py + ph, 0xAA000000);

        int dh = dialogHeight();
        int cx = dialogX(), cy = dialogY();
        int tw = textW();

        // box with amber border
        g.fill(cx,         cy,          cx + W,    cy + dh,    0xFF0A0A14);
        g.fill(cx,         cy,          cx + W,    cy + 1,     0xFFE0A030);
        g.fill(cx,         cy + dh - 1, cx + W,    cy + dh,    0xFFE0A030);
        g.fill(cx,         cy,          cx + 1,    cy + dh,    0xFFE0A030);
        g.fill(cx + W - 1, cy,          cx + W,    cy + dh,    0xFFE0A030);

        int centerX = cx + W / 2;
        int y = cy + PAD;
        y += GuiText.drawWrappedCentered(g, font, title, centerX, y, tw, 0xFFFFD27F) + GAP;
        if (body != null && !body.isEmpty())
            y += GuiText.drawWrappedCentered(g, font, body, centerX, y, tw, 0xFFCCCCCC) + GAP;

        // checkbox row
        int boxX = cx + PAD;
        int boxY = y;
        boolean chkHov = isIn(mx, my, boxX, boxY, CHK + 4 + font.width(checkboxLabel), CHK);
        g.fill(boxX, boxY, boxX + CHK, boxY + CHK, chkHov ? 0xFF333333 : 0xFF1C1C1C);
        g.fill(boxX, boxY, boxX + CHK, boxY + 1, 0xFF888888);
        g.fill(boxX, boxY + CHK - 1, boxX + CHK, boxY + CHK, 0xFF888888);
        g.fill(boxX, boxY, boxX + 1, boxY + CHK, 0xFF888888);
        g.fill(boxX + CHK - 1, boxY, boxX + CHK, boxY + CHK, 0xFF888888);
        if (dontShowAgain) {
            g.fill(boxX + 2, boxY + 2, boxX + CHK - 2, boxY + CHK - 2, 0xFFE0A030);
        }
        g.drawString(font, checkboxLabel, boxX + CHK + 4, boxY + 1, 0xFFAAAAAA, false);

        // dismiss button
        int btnX = cx + (W - BTN_W) / 2;
        int btnY = cy + dh - PAD - BTN_H;
        boolean btnHov = isIn(mx, my, btnX, btnY, BTN_W, BTN_H);
        g.fill(btnX, btnY, btnX + BTN_W, btnY + BTN_H, btnHov ? 0xFF5A4A22 : 0xFF3A3015);
        g.fill(btnX, btnY, btnX + BTN_W, btnY + 1, 0xFFE0A030);
        g.drawCenteredString(font, dismissLabel, btnX + BTN_W / 2, btnY + 3, 0xFFFFFFFF);

        g.pose().popPose();
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!open) return false;
        if (button != 0) return true;

        int dh = dialogHeight();
        int cx = dialogX(), cy = dialogY();
        int tw = textW();

        // recompute checkbox row position
        int y = cy + PAD + GuiText.wrappedHeight(font, title, tw) + GAP;
        if (body != null && !body.isEmpty())
            y += GuiText.wrappedHeight(font, body, tw) + GAP;
        int boxX = cx + PAD;
        int boxY = y;
        if (isIn((int) mx, (int) my, boxX, boxY, CHK + 4 + font.width(checkboxLabel), CHK)) {
            dontShowAgain = !dontShowAgain;
            return true;
        }

        int btnX = cx + (W - BTN_W) / 2;
        int btnY = cy + dh - PAD - BTN_H;
        if (isIn((int) mx, (int) my, btnX, btnY, BTN_W, BTN_H)) {
            dismiss();
            return true;
        }
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (!open) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
            dismiss();
            return true;
        }
        return true;
    }

    private void dismiss() {
        close();
        if (onDismiss != null) onDismiss.accept(dontShowAgain);
    }

    private static boolean isIn(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
