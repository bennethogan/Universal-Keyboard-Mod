package dev.bennethogan.universalkeyboard.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class IconButton extends Button {

    private ModIcons icon;
    private final Component tooltip;
    private final int accentBg; // -1 = default dark style; otherwise used as normal bg
    private final int iconDx;   // horizontal offset added to centered icon position

    // default style square button
    public static IconButton make(ModIcons icon, Component tooltip, OnPress press, int x, int y, int size) {
        return new IconButton(Button.builder(Component.empty(), press).pos(x, y).size(size, size), icon, tooltip, -1, 0);
    }

    // default style rectangular button
    public static IconButton make(ModIcons icon, Component tooltip, OnPress press, int x, int y, int w, int h) {
        return new IconButton(Button.builder(Component.empty(), press).pos(x, y).size(w, h), icon, tooltip, -1, 0);
    }

    // Rectangle button with background color and icon x offset if needed
    public static IconButton make(ModIcons icon, Component tooltip, OnPress press,
                                  int x, int y, int w, int h, int accentBg, int iconDx) {
        return new IconButton(Button.builder(Component.empty(), press).pos(x, y).size(w, h), icon, tooltip, accentBg, iconDx);
    }

    private IconButton(Builder builder, ModIcons icon, Component tooltip, int accentBg, int iconDx) {
        super(builder);
        this.icon = icon;
        this.tooltip = tooltip;
        this.accentBg = accentBg;
        this.iconDx = iconDx;
        if (tooltip != null) setTooltip(net.minecraft.client.gui.components.Tooltip.create(tooltip));
    }

    public void setIcon(ModIcons icon) { this.icon = icon; }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        boolean hov = isHovered() && isActive();
        int bg, brd;
        if (accentBg != -1) {
            bg  = hov ? brighten(accentBg) : accentBg;
            brd = 0xFF448844;
        } else {
            bg  = hov ? 0xFF2A2A3A : (isActive() ? 0xFF1C1C1C : 0xFF111111);
            brd = isActive() ? 0xFF555555 : 0xFF333333;
        }
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        g.fill(x,         y,         x + w,     y + h,     bg);
        g.fill(x,         y,         x + w,     y + 1,     brd);
        g.fill(x,         y + h - 1, x + w,     y + h,     brd);
        g.fill(x,         y,         x + 1,     y + h,     brd);
        g.fill(x + w - 1, y,         x + w,     y + h,     brd);
        icon.render(g, x + (w - 16) / 2 + iconDx, y + (h - 16) / 2);
    }

    private static int brighten(int c) {
        int a = (c >> 24) & 0xFF;
        int r = Math.min(255, ((c >> 16) & 0xFF) + 20);
        int g = Math.min(255, ((c >>  8) & 0xFF) + 20);
        int b = Math.min(255, ( c        & 0xFF) + 20);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
