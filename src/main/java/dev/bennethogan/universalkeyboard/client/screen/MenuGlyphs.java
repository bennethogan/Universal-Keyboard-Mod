package dev.bennethogan.universalkeyboard.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;


public final class MenuGlyphs {

    private MenuGlyphs() {}

    private static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath(
            "universalkeyboard", "textures/gui/bennets_icons.png");
    //public so I can put a wifi icon on the Wireless copycat sprite
    public static final ResourceLocation ICONS_LOC = ICONS;
    private static final int ICON_SIZE  = 50;
    private static final int SHEET_SIZE = 100;

    private static void blitIcon(GuiGraphics g, int col, int row, int cx, int cy, boolean on) {
        if (!on) RenderSystem.setShaderColor(0.5f, 0.5f, 0.5f, 1.0f);
        int x = cx - ICON_SIZE / 2;
        int y = cy - ICON_SIZE / 2;
        g.blit(ICONS, x, y, 0, col * ICON_SIZE, row * ICON_SIZE, ICON_SIZE, ICON_SIZE, SHEET_SIZE, SHEET_SIZE);
        if (!on) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }



    public static void computer(GuiGraphics g, int cx, int cy, boolean on) {
        blitIcon(g, 0, 0, cx, cy, on);
    }


    public static void wifi(GuiGraphics g, int cx, int cy, boolean on) {
        blitIcon(g, 1, 0, cx, cy, on);
    }


    public static void gamepad(GuiGraphics g, int cx, int cy, boolean on) {
        blitIcon(g, 0, 1, cx, cy, on);
    }


    public static void arrowRight(GuiGraphics g, int cx, int cy, boolean on) {
        blitIcon(g, 1, 1, cx, cy, on);
    }

    public static void wifiBadge(GuiGraphics g, int cx, int cy, boolean on) {
        if (!on) RenderSystem.setShaderColor(0.5f, 0.5f, 0.5f, 1.0f);
        float scale = 0.32f; // ~16px rendered size
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.scale(scale, scale, 1f);
        g.blit(ICONS, -ICON_SIZE / 2, -ICON_SIZE / 2, 0, ICON_SIZE, 0, ICON_SIZE, ICON_SIZE, SHEET_SIZE, SHEET_SIZE);
        pose.popPose();
        if (!on) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public static void item(GuiGraphics g, ItemStack stack, int cx, int cy, float scale) {
        if (stack == null || stack.isEmpty()) return;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx - 8f * scale, cy - 8f * scale, 0);
        pose.scale(scale, scale, 1f);
        g.renderItem(stack, 0, 0);
        pose.popPose();
    }
}
