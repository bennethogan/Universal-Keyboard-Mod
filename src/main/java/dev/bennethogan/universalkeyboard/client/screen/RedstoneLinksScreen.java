package dev.bennethogan.universalkeyboard.client.screen;

import dev.bennethogan.universalkeyboard.compat.CreateConnectedCompat;
import dev.bennethogan.universalkeyboard.config.ModConfig;
import dev.bennethogan.universalkeyboard.menu.RedstoneLinksMenu;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;

public class RedstoneLinksScreen extends AbstractContainerScreen<RedstoneLinksMenu> {


    private static final int FREQ1_TINT = 0x55FF3333; // transparent red  (freq 1 / left slot)
    private static final int FREQ2_TINT = 0x553333FF; // transparent blue (freq 2 / right slot)

    private NoticeDialog wildcardNotice;
    private ConfirmDialog revertDialog;

    // adding revert support to this screen I forgot before
    private ItemStack[] openSnapshot;
    private boolean     snapshotFrozen = false;

    public RedstoneLinksScreen(RedstoneLinksMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth      = 222;
        this.imageHeight     = 18 + RedstoneLinksMenu.COL_ROWS * 18 + 28 + 76;
        this.inventoryLabelY = 18 + RedstoneLinksMenu.COL_ROWS * 18 + 18;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(DarkButton.make(Component.literal("?"),
                Component.translatable("gui.universalkeyboard.tooltip.wiki"),
                b -> net.minecraft.client.Minecraft.getInstance().setScreen(new WikiScreen(this)),
                leftPos + imageWidth - 8 - 16, topPos + 2, 16, 16));

        revertDialog = new ConfirmDialog(font);
        revertDialog.setParentBounds(leftPos, topPos, imageWidth, imageHeight);

        addRenderableWidget(IconButton.make(ModIcons.REVERT,
                Component.translatable("gui.universalkeyboard.tooltip.revert"),
                b -> revertDialog.open(
                        "gui.universalkeyboard.dialog.revert_title",
                        "gui.universalkeyboard.dialog.revert_body",
                        "gui.universalkeyboard.btn.yes_revert",
                        this::revertToSnapshot),
                leftPos + imageWidth - 8 - 16 - 4 - 16, topPos + 2, 16));

        wildcardNotice = new NoticeDialog(font);
        wildcardNotice.setParentBounds(leftPos, topPos, imageWidth, imageHeight);
        if (CreateConnectedCompat.isRedstoneWildcardActive()
                && !ModConfig.CLIENT.redstoneWildcardWarningDismissed.get()) {
            wildcardNotice.open(
                    I18n.get("gui.universalkeyboard.notice.cc_wildcard.title"),
                    I18n.get("gui.universalkeyboard.notice.cc_wildcard.body"),
                    I18n.get("gui.universalkeyboard.notice.got_it"),
                    I18n.get("gui.universalkeyboard.notice.dont_show_again"),
                    dontShowAgain -> {
                        if (dontShowAgain) {
                            ModConfig.CLIENT.redstoneWildcardWarningDismissed.set(true);
                            ModConfig.CLIENT.redstoneWildcardWarningDismissed.save();
                        }
                    });
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (wildcardNotice != null && wildcardNotice.isOpen()) {
            wildcardNotice.render(g, mx, my);
        }
        if (revertDialog != null && revertDialog.isOpen()) {
            revertDialog.render(g, mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (wildcardNotice != null && wildcardNotice.isOpen()) {
            return wildcardNotice.mouseClicked(mx, my, button);
        }
        if (revertDialog != null && revertDialog.isOpen()) {
            return revertDialog.mouseClicked(mx, my, button);
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF1A1A1A);
        // 1-px border
        g.fill(leftPos,                  topPos,                   leftPos + imageWidth, topPos + 1,              0xFF555555);
        g.fill(leftPos,                  topPos + imageHeight - 1, leftPos + imageWidth, topPos + imageHeight,    0xFF555555);
        g.fill(leftPos,                  topPos,                   leftPos + 1,          topPos + imageHeight,    0xFF555555);
        g.fill(leftPos + imageWidth - 1, topPos,                   leftPos + imageWidth, topPos + imageHeight,    0xFF555555);

        int count = menu.getRsLinkCount();
        int colRows = RedstoneLinksMenu.COL_ROWS;

        // Column 1: entries 0–6
        for (int r = 0; r < colRows; r++) {
            int entry = r;
            if (entry >= RedstoneLinksMenu.ROWS) break;
            renderRow(g, r, entry, count, leftPos + 8, leftPos + 73,
                    RedstoneLinksMenu.COL1_SLOT1, RedstoneLinksMenu.COL1_SLOT2);
        }

        // Column 2: entries 7–13
        for (int r = 0; r < colRows; r++) {
            int entry = colRows + r;
            if (entry >= RedstoneLinksMenu.ROWS) break;
            renderRow(g, r, entry, count, leftPos + 78, leftPos + 143,
                    RedstoneLinksMenu.COL2_SLOT1, RedstoneLinksMenu.COL2_SLOT2);
        }

        // Column 3: entries 14–19
        for (int r = 0; r < colRows; r++) {
            int entry = colRows * 2 + r;
            if (entry >= RedstoneLinksMenu.ROWS) break;
            renderRow(g, r, entry, count, leftPos + 148, leftPos + 213,
                    RedstoneLinksMenu.COL3_SLOT1, RedstoneLinksMenu.COL3_SLOT2);
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
        g.drawString(font, "L" + (entryIdx + 1), rowFillX + 1, y + 5, active ? 0xFFFFFF : 0x555555, false);
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
    protected void containerTick() {
        super.containerTick();
        if (!snapshotFrozen) {
            captureSnapshot();
            if (snapshotHasContent()) snapshotFrozen = true;
        }
    }

    private void captureSnapshot() {
        ItemStack[] snap = new ItemStack[RedstoneLinksMenu.GHOST_COUNT];
        for (int i = 0; i < snap.length && i < menu.slots.size(); i++)
            snap[i] = menu.slots.get(i).getItem().copy();
        openSnapshot = snap;
    }

    private boolean snapshotHasContent() {
        if (openSnapshot == null) return false;
        for (ItemStack s : openSnapshot)
            if (s != null && !s.isEmpty()) return true;
        return false;
    }

    private void revertToSnapshot() {
        if (openSnapshot == null) return;
        for (int i = 0; i < openSnapshot.length && i < menu.slots.size(); i++) {
            ItemStack target  = openSnapshot[i] == null ? ItemStack.EMPTY : openSnapshot[i];
            ItemStack current = menu.slots.get(i).getItem();
            if (ItemStack.matches(current, target)) continue;
            ItemStack toSet = target.isEmpty() ? ItemStack.EMPTY : target.copyWithCount(1);
            menu.slots.get(i).set(toSet);                                   // client visual
            ModPackets.sendWirelessGhostSet(menu.getKeyboardPos(), i, toSet); // push to server
        }
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        if (slot != null && slotId >= 0 && slotId < RedstoneLinksMenu.GHOST_COUNT
                && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) {
            snapshotFrozen = true;
            ItemStack carried = menu.getCarried();
            ItemStack toSet   = carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1);
            slot.set(toSet); // immediate client-side visual update
            ModPackets.sendWirelessGhostSet(menu.getKeyboardPos(), slotId, toSet);
            return; // never call super for ghost slots
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, I18n.get("gui.universalkeyboard.screen.wireless_config.title"), 8, 6, 0xFFFFFF, false);
        g.drawString(font, playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xAAAAAA, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (wildcardNotice != null && wildcardNotice.isOpen()) {
            return wildcardNotice.keyPressed(keyCode);
        }
        if (revertDialog != null && revertDialog.isOpen()) {
            return revertDialog.keyPressed(keyCode);
        }
        if (MenuNav.handleTabBack(this, keyCode, menu.getKeyboardPos())) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
