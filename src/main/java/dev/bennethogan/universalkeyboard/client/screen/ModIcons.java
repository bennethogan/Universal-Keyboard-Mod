package dev.bennethogan.universalkeyboard.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public class ModIcons {

    public static final ResourceLocation CREATE_ATLAS =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/icons.png");
    private static final int ATLAS_SIZE = 256;


    public static final ModIcons TRASH        = new ModIcons(1,  0);
    public static final ModIcons ENABLED      = new ModIcons(0,  1);
    public static final ModIcons DISABLED     = new ModIcons(8, 10);
    public static final ModIcons RANDOMIZE    = new ModIcons(3,  1);
    public static final ModIcons COPY         = new ModIcons(5, 11);
    public static final ModIcons PASTE        = new ModIcons(0,  3);
    public static final ModIcons PLAY         = new ModIcons(0,  5);
    public static final ModIcons CONFIG_SAVE  = new ModIcons(3, 10);
    public static final ModIcons ADD          = new ModIcons(0,  0);
    public static final ModIcons ACTIVE       = ENABLED;
    public static final ModIcons PASSIVE      = DISABLED;
    public static final ModIcons DICE         = RANDOMIZE;
    public static final ModIcons LOCATE       = PLAY;

    private final int iconX;
    private final int iconY;

    private ModIcons(int col, int row) {
        this.iconX = col * 16;
        this.iconY = row * 16;
    }

    public void render(GuiGraphics g, int x, int y) {
        g.blit(CREATE_ATLAS, x, y, 0, iconX, iconY, 16, 16, ATLAS_SIZE, ATLAS_SIZE);
    }
}
