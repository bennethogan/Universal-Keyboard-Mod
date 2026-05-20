package dev.bennethogan.universalkeyboard.client.screen;

import dev.bennethogan.universalkeyboard.menu.WirelessConfigMenu;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class WirelessConfigScreen extends AbstractContainerScreen<WirelessConfigMenu> {


    private static final int FREQ1_TINT = 0x55FF3333; // transparent red  (freq 1 / left slot)
    private static final int FREQ2_TINT = 0x553333FF; // transparent blue (freq 2 / right slot)

    private Button addBtn;
    private Button removeBtn;

    public WirelessConfigScreen(WirelessConfigMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth      = 222;
        this.imageHeight     = 18 + WirelessConfigMenu.COL_ROWS * 18 + 28 + 76;
        this.inventoryLabelY = 18 + WirelessConfigMenu.COL_ROWS * 18 + 18;
    }

    @Override
    protected void init() {
        super.init();
        int btnY = topPos + 18 + WirelessConfigMenu.COL_ROWS * 18 + 6;
        addBtn = Button.builder(Component.literal("Add"),
                        b -> ModPackets.sendWirelessAddRemove(menu.getKeyboardPos(), true))
                .pos(leftPos + 73, btnY).size(28, 12).build();
        removeBtn = Button.builder(Component.literal("Remove"),
                        b -> ModPackets.sendWirelessAddRemove(menu.getKeyboardPos(), false))
                .pos(leftPos + 105, btnY).size(44, 12).build();
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
        int colRows = WirelessConfigMenu.COL_ROWS;

        // Column 1: entries 0–6
        for (int r = 0; r < colRows; r++) {
            int entry = r;
            if (entry >= WirelessConfigMenu.ROWS) break;
            renderRow(g, r, entry, count, leftPos + 8, leftPos + 73,
                    WirelessConfigMenu.COL1_SLOT1, WirelessConfigMenu.COL1_SLOT2);
        }

        // Column 2: entries 7–13
        for (int r = 0; r < colRows; r++) {
            int entry = colRows + r;
            if (entry >= WirelessConfigMenu.ROWS) break;
            renderRow(g, r, entry, count, leftPos + 78, leftPos + 143,
                    WirelessConfigMenu.COL2_SLOT1, WirelessConfigMenu.COL2_SLOT2);
        }

        // Column 3: entries 14–19
        for (int r = 0; r < colRows; r++) {
            int entry = colRows * 2 + r;
            if (entry >= WirelessConfigMenu.ROWS) break;
            renderRow(g, r, entry, count, leftPos + 148, leftPos + 213,
                    WirelessConfigMenu.COL3_SLOT1, WirelessConfigMenu.COL3_SLOT2);
        }

        // Vertical dividers between columns
        int divTop = topPos + 18;
        int divBot = topPos + 18 + colRows * 18;
        g.fill(leftPos + 75, divTop, leftPos + 76, divBot, 0xFF333333);
        g.fill(leftPos + 145, divTop, leftPos + 146, divBot, 0xFF333333);

        // Player inventory area background — cap at imageHeight-2 so the bottom border stays visible
        int invY = topPos + 18 + colRows * 18 + 28;
        g.fill(leftPos + 6, invY - 2, leftPos + imageWidth - 6, topPos + imageHeight - 2, 0xFF222222);
    }

    private void renderRow(GuiGraphics g, int rowInCol, int entryIdx, int count,
                           int rowFillX, int rowFillXEnd, int slot1x, int slot2x) {
        int y = topPos + 18 + rowInCol * 18;
        boolean active = entryIdx < count;
        g.fill(rowFillX, y, rowFillXEnd, y + 17, active ? 0xFF2A2A2A : 0xFF181818);
        g.drawString(font, "W" + (entryIdx + 1), rowFillX + 1, y + 5, active ? 0xFFFFFF : 0x555555, false);
        drawSlotBg(g, leftPos + slot1x, y, FREQ1_TINT);
        drawSlotBg(g, leftPos + slot2x, y, FREQ2_TINT);
    }

    private void drawSlotBg(GuiGraphics g, int x, int y, int tint) {
        g.fill(x - 1, y,     x + 17, y + 1,  0xFF3D3D3D);
        g.fill(x - 1, y + 1, x,      y + 17, 0xFF3D3D3D);
        g.fill(x,     y + 1, x + 17, y + 17, 0xFF1F1F1F); // dark base
        g.fill(x,     y + 1, x + 17, y + 17, tint);       // transparent color tint on top
        g.fill(x - 1, y + 17, x + 17, y + 18, 0xFF3D3D3D);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        if (slot != null && slotId >= 0 && slotId < WirelessConfigMenu.GHOST_COUNT
                && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) {
            int entryIdx = slotId / WirelessConfigMenu.GHOST_COLS;
            if (entryIdx < menu.getWirelessCount()) {
                ItemStack carried = menu.getCarried();
                ItemStack toSet   = carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1);
                slot.set(toSet); // immediate client-side visual update
                ModPackets.sendWirelessGhostSet(menu.getKeyboardPos(), slotId, toSet);
            }
            return; // never call super for ghost slots
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, "Wireless Redstone", 8, 6, 0xFFFFFF, false);
        g.drawString(font, playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xAAAAAA, false);
    }
}
