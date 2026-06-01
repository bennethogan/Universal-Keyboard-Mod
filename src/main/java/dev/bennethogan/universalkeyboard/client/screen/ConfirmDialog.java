package dev.bennethogan.universalkeyboard.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.glfw.GLFW;

/**
 * An "are you sure?" confirmation dialog box I can re-use when needed
 * Centers over a parent panel region and dims it while open.
 * Call setParentBounds() in init(), call open() on the trigger action,
 * then delegate render / mouseClicked / keyPressed to this object.
 */
public class ConfirmDialog {

    private static final int W     = 200;
    private static final int PAD   = 6;
    private static final int GAP   = 2;
    private static final int BTN_H = 14;
    private static final int BTN_W = 54;

    private final Font font;

    private boolean  open;
    private String   title;
    private String   body;
    private String   confirmLabel;
    private Runnable onConfirm;
    private Runnable onCancel;

    private int px, py, pw, ph;

    public ConfirmDialog(Font font) { this.font = font; }

    public void setParentBounds(int x, int y, int w, int h) {
        px = x; py = y; pw = w; ph = h;
    }


    public void open(String titleKey, String bodyKey, String confirmKey, Runnable onConfirm) {
        openFormatted(I18n.get(titleKey), I18n.get(bodyKey), I18n.get(confirmKey), onConfirm, this::close);
    }


    public void openFormatted(String title, String body, String confirmLabel,
                               Runnable onConfirm, Runnable onCancel) {
        this.title        = title;
        this.body         = body;
        this.confirmLabel = confirmLabel;
        this.onConfirm    = onConfirm;
        this.onCancel     = onCancel;
        this.open         = true;
    }

    public void close()      { open = false; }
    public boolean isOpen()  { return open; }

    private int dialogHeight() {
        int textW = W - PAD * 2;
        int h = PAD + GuiText.wrappedHeight(font, title, textW) + GAP;
        if (body != null && !body.isEmpty())
            h += GuiText.wrappedHeight(font, body, textW) + GAP;
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
        int textW = W - PAD * 2;

        g.fill(cx,         cy,          cx + W,    cy + dh,    0xFF0A0A14);
        g.fill(cx,         cy,          cx + W,    cy + 1,     0xFFCC4444);
        g.fill(cx,         cy + dh - 1, cx + W,    cy + dh,    0xFFCC4444);
        g.fill(cx,         cy,          cx + 1,    cy + dh,    0xFFCC4444);
        g.fill(cx + W - 1, cy,          cx + W,    cy + dh,    0xFFCC4444);

        int centerX = cx + W / 2;
        int y = cy + PAD;
        y += GuiText.drawWrappedCentered(g, font, title, centerX, y, textW, 0xFFFFFF) + GAP;
        if (body != null && !body.isEmpty())
            GuiText.drawWrappedCentered(g, font, body, centerX, y, textW, 0xAAAAAA);

        int yesX = cx + W / 2 - BTN_W - 4;
        int noX  = cx + W / 2 + 4;
        int btnY = cy + dh - PAD - BTN_H;
        boolean yesHov = isIn(mx, my, yesX, btnY, BTN_W, BTN_H);
        boolean noHov  = isIn(mx, my, noX,  btnY, BTN_W, BTN_H);
        g.fill(yesX, btnY, yesX + BTN_W, btnY + BTN_H, yesHov ? 0xFF882222 : 0xFF551111);
        g.fill(noX,  btnY, noX  + BTN_W, btnY + BTN_H, noHov  ? 0xFF334433 : 0xFF223322);
        g.fill(yesX, btnY, yesX + BTN_W, btnY + 1, 0xFFCC4444);
        g.fill(noX,  btnY, noX  + BTN_W, btnY + 1, 0xFF448844);
        g.drawCenteredString(font, confirmLabel,
                yesX + BTN_W / 2, btnY + 3, 0xFFFFFF);
        g.drawCenteredString(font, I18n.get("gui.universalkeyboard.btn.cancel"),
                noX + BTN_W / 2, btnY + 3, 0xFFFFFF);

        g.pose().popPose();
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!open) return false;
        if (button != 0) return true;
        int dh   = dialogHeight();
        int cx   = dialogX(), cy = dialogY();
        int yesX = cx + W / 2 - BTN_W - 4;
        int noX  = cx + W / 2 + 4;
        int btnY = cy + dh - PAD - BTN_H;
        if (isIn((int) mx, (int) my, yesX, btnY, BTN_W, BTN_H)) { close(); onConfirm.run(); return true; }
        if (isIn((int) mx, (int) my, noX,  btnY, BTN_W, BTN_H)) { onCancel.run();            return true; }
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (!open) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onCancel.run(); return true; }
        return true;
    }

    private static boolean isIn(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
