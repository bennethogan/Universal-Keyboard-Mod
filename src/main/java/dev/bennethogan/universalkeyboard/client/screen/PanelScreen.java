package dev.bennethogan.universalkeyboard.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class PanelScreen extends Screen {

    protected int panelX, panelY;

    protected PanelScreen(Component title) {
        super(title);
    }

    protected abstract int getPanelW();
    protected abstract int getPanelH();

    protected void renderPanel(GuiGraphics g) {
        int w = getPanelW(), h = getPanelH();
        g.fill(panelX,          panelY,          panelX + w, panelY + h, 0xFF1A1A1A);
        g.fill(panelX,          panelY,          panelX + w, panelY + 1, 0xFF555555);
        g.fill(panelX,          panelY + h - 1,  panelX + w, panelY + h, 0xFF555555);
        g.fill(panelX,          panelY,          panelX + 1, panelY + h, 0xFF555555);
        g.fill(panelX + w - 1,  panelY,          panelX + w, panelY + h, 0xFF555555);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderBackground(g, mouseX, mouseY, pt);
        renderPanel(g);
        renderContents(g, mouseX, mouseY, pt);
        for (var w : renderables) w.render(g, mouseX, mouseY, pt);
    }

    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float pt) {}

    @Override
    public boolean isPauseScreen() { return false; }
}
