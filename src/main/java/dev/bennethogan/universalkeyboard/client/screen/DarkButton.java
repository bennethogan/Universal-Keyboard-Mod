package dev.bennethogan.universalkeyboard.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class DarkButton extends Button {

    public static DarkButton make(Component msg, OnPress press, int x, int y, int w, int h) {
        return new DarkButton(Button.builder(msg, press).pos(x, y).size(w, h));
    }

    private DarkButton(Builder builder) { super(builder); }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        boolean hov = isHovered() && isActive();
        int bg  = hov ? 0xFF2A2A3A : (isActive() ? 0xFF1C1C1C : 0xFF111111);
        int brd = isActive() ? 0xFF555555 : 0xFF333333;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        g.fill(x,         y,         x + w,     y + h,     bg);
        g.fill(x,         y,         x + w,     y + 1,     brd);
        g.fill(x,         y + h - 1, x + w,     y + h,     brd);
        g.fill(x,         y,         x + 1,     y + h,     brd);
        g.fill(x + w - 1, y,         x + w,     y + h,     brd);
        int textColor = isActive() ? 0xFFFFFF : 0x777777;
        g.drawCenteredString(Minecraft.getInstance().font,
                getMessage(), x + w / 2, y + (h - 8) / 2, textColor);
    }
}
