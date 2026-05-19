package dev.bennethogan.universalkeyboard.client.screen;

import dev.bennethogan.universalkeyboard.menu.WirelessConfigMenu;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Configure up to 12 wireless redstone outputs in a 2×6 grid.
 * Left column: W1–W6; right column: W7–W12.
 * Each row has two ghost item slots for the two Create frequency items.
 * Left-click while holding an item stamps the type; left-click empty-handed clears.
 * REI's drag-drop targets these slots because they're real Slot instances on the menu.
 */
public class WirelessConfigScreen extends AbstractContainerScreen<WirelessConfigMenu> {

    // Slot x positions (menu-relative, used to draw slot backgrounds in renderBg)
    private static final int LEFT_SLOT1_X  = 26;
    private static final int LEFT_SLOT2_X  = 46;
    private static final int RIGHT_SLOT1_X = 120;
    private static final int RIGHT_SLOT2_X = 140;

    private Button addBtn;
    private Button removeBtn;

    public WirelessConfigScreen(WirelessConfigMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth      = 190;
        this.imageHeight     = 18 + WirelessConfigMenu.HALF_ROWS * 18 + 28 + 76;
        // Place "Inventory" label just above the first inventory row (invStart - 10).
        // invStart = 18 + HALF_ROWS*18 + 28, so label sits at 18 + HALF_ROWS*18 + 18.
        this.inventoryLabelY = 18 + WirelessConfigMenu.HALF_ROWS * 18 + 18;
    }

    @Override
    protected void init() {
        super.init();
        // Buttons sit in the gap below the rows, above the "Inventory" label.
        // rows end at topPos + 18 + HALF_ROWS*18 = topPos + 126; gap is 28px.
        int btnY = topPos + 18 + WirelessConfigMenu.HALF_ROWS * 18 + 6;
        addBtn = Button.builder(Component.literal("Add"),
                        b -> ModPackets.sendWirelessAddRemove(menu.getKeyboardPos(), true))
                .pos(leftPos + 57, btnY).size(28, 12).build();
        removeBtn = Button.builder(Component.literal("Remove"),
                        b -> ModPackets.sendWirelessAddRemove(menu.getKeyboardPos(), false))
                .pos(leftPos + 89, btnY).size(44, 12).build();
        addRenderableWidget(addBtn);
        addRenderableWidget(removeBtn);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        int count = menu.getWirelessCount();
        addBtn.active    = count < WirelessConfigMenu.ROWS;
        removeBtn.active = count > 0;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF1A1A1A);
        // 1-px border
        g.fill(leftPos,                  topPos,                   leftPos + imageWidth, topPos + 1,              0xFF555555);
        g.fill(leftPos,                  topPos + imageHeight - 1, leftPos + imageWidth, topPos + imageHeight,    0xFF555555);
        g.fill(leftPos,                  topPos,                   leftPos + 1,          topPos + imageHeight,    0xFF555555);
        g.fill(leftPos + imageWidth - 1, topPos,                   leftPos + imageWidth, topPos + imageHeight,    0xFF555555);

        int count = menu.getWirelessCount();

        // Left group: W1–W6 (entries 0–5)
        for (int r = 0; r < WirelessConfigMenu.HALF_ROWS; r++) {
            int y = topPos + 18 + r * 18;
            boolean active = r < count;
            g.fill(leftPos + 8, y, leftPos + 73, y + 17, active ? 0xFF2A2A2A : 0xFF181818);
            g.drawString(font, "W" + (r + 1), leftPos + 9, y + 5, active ? 0xFFFFFF : 0x555555, false);
            drawSlotBg(g, leftPos + LEFT_SLOT1_X, y);
            drawSlotBg(g, leftPos + LEFT_SLOT2_X, y);
        }

        // Thin vertical divider between the two groups
        g.fill(leftPos + 87, topPos + 18, leftPos + 88, topPos + 18 + WirelessConfigMenu.HALF_ROWS * 18, 0xFF333333);

        // Right group: W7–W12 (entries 6–11)
        for (int r = 0; r < WirelessConfigMenu.HALF_ROWS; r++) {
            int entryIdx = r + WirelessConfigMenu.HALF_ROWS;
            int y = topPos + 18 + r * 18;
            boolean active = entryIdx < count;
            g.fill(leftPos + 96, y, leftPos + 168, y + 17, active ? 0xFF2A2A2A : 0xFF181818);
            g.drawString(font, "W" + (entryIdx + 1), leftPos + 97, y + 5, active ? 0xFFFFFF : 0x555555, false);
            drawSlotBg(g, leftPos + RIGHT_SLOT1_X, y);
            drawSlotBg(g, leftPos + RIGHT_SLOT2_X, y);
        }

        // Player inventory area background
        int invY = topPos + 18 + WirelessConfigMenu.HALF_ROWS * 18 + 28;
        g.fill(leftPos + 6, invY - 2, leftPos + imageWidth - 6, invY + 76, 0xFF222222);
    }

    private void drawSlotBg(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y,     x + 17, y + 1,  0xFF3D3D3D);
        g.fill(x - 1, y + 1, x,      y + 17, 0xFF3D3D3D);
        g.fill(x,     y + 1, x + 17, y + 17, 0xFF1F1F1F);
        g.fill(x - 1, y + 17, x + 17, y + 18, 0xFF3D3D3D);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, "Wireless Redstone", 8, 6, 0xFFFFFF, false);
        g.drawString(font, playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xAAAAAA, false);
    }
}
