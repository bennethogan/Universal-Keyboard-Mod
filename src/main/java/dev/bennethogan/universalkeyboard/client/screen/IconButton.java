package dev.bennethogan.universalkeyboard.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class IconButton extends Button {

    private ModIcons icon;
    private final Component tooltip;

    public static IconButton make(ModIcons icon, Component tooltip, OnPress press, int x, int y, int size) {
        return new IconButton(Button.builder(Component.empty(), press).pos(x, y).size(size, size), icon, tooltip);
    }

    private IconButton(Builder builder, ModIcons icon, Component tooltip) {
        super(builder);
        this.icon = icon;
        this.tooltip = tooltip;
        if (tooltip != null) setTooltip(net.minecraft.client.gui.components.Tooltip.create(tooltip));
    }

    public void setIcon(ModIcons icon) { this.icon = icon; }

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
        icon.render(g, x + (w - 16) / 2, y + (h - 16) / 2);
    }
}
