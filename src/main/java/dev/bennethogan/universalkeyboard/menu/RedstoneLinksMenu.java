package dev.bennethogan.universalkeyboard.menu;

import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class RedstoneLinksMenu extends AbstractContainerMenu {

    public static final int ROWS       = LinkedKeyboardBlockEntity.MAX_RSLINKS; // 20
    public static final int COL_ROWS   = (ROWS + 2) / 3;                        // 7 rows per column
    public static final int GHOST_COLS = 2;
    public static final int GHOST_COUNT = ROWS * GHOST_COLS;                    // 40

    // Slot x positions (screen-relative) for each of the 3 columns
    public static final int COL1_SLOT1 = 30;
    public static final int COL1_SLOT2 = 50;
    public static final int COL2_SLOT1 = 100;
    public static final int COL2_SLOT2 = 120;
    public static final int COL3_SLOT1 = 170;
    public static final int COL3_SLOT2 = 190;

    private final BlockPos keyboardPos;
    private final Level    level;
    private final SimpleContainer ghosts;
    private boolean suppressWriteThrough = false;

    public RedstoneLinksMenu(int id, Inventory inv, BlockPos pos) {
        super(ModMenus.REDSTONE_LINKS_MENU.get(), id);
        this.keyboardPos = pos;
        this.level       = inv.player.level();

        this.ghosts = new SimpleContainer(GHOST_COUNT) {
            @Override public int getMaxStackSize() { return 1; }

            @Override public void setChanged() {
                super.setChanged();
                if (!suppressWriteThrough && !level.isClientSide) {
                    LinkedKeyboardBlockEntity be = currentBe();
                    if (be == null) return;
                    for (int i = 0; i < GHOST_COUNT; i++) {
                        int entryIdx = i / GHOST_COLS;
                        if (entryIdx >= be.getRsLinkCount()) continue;
                        boolean first = (i % GHOST_COLS) == 0;
                        be.setWirelessFrequencyItem(entryIdx, first, getItem(i));
                    }
                }
            }
        };

        // Populate ghost slots from BE state
        LinkedKeyboardBlockEntity be = currentBe();
        if (be != null && !level.isClientSide) {
            suppressWriteThrough = true;
            try {
                for (int i = 0; i < be.getRsLinkCount() && i < ROWS; i++) {
                    var e = be.getWirelessEntries().get(i);
                    ghosts.setItem(i * GHOST_COLS,     e.getFirstStack());
                    ghosts.setItem(i * GHOST_COLS + 1, e.getSecondStack());
                }
            } finally { suppressWriteThrough = false; }
        }

        // Register ghost slots across 3 columns, COL_ROWS entries each
        for (int entry = 0; entry < ROWS; entry++) {
            int col     = entry / COL_ROWS;           // 0, 1, or 2
            int rowInCol = entry % COL_ROWS;
            int y = 18 + rowInCol * 18;
            int slot1x, slot2x;
            if      (col == 0) { slot1x = COL1_SLOT1; slot2x = COL1_SLOT2; }
            else if (col == 1) { slot1x = COL2_SLOT1; slot2x = COL2_SLOT2; }
            else               { slot1x = COL3_SLOT1; slot2x = COL3_SLOT2; }

            final int sx1 = slot1x, sx2 = slot2x;
            addSlot(new Slot(ghosts, entry * GHOST_COLS,     sx1, y) {
                @Override public int getMaxStackSize()       { return 1; }
                @Override public boolean mayPickup(Player p) { return false; }
                @Override public boolean mayPlace(ItemStack stack) { return true; }
            });
            addSlot(new Slot(ghosts, entry * GHOST_COLS + 1, sx2, y) {
                @Override public int getMaxStackSize()       { return 1; }
                @Override public boolean mayPickup(Player p) { return false; }
                @Override public boolean mayPlace(ItemStack stack) { return true; }
            });
        }

        int invStart = 18 + COL_ROWS * 18 + 28;
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, invStart + r * 18));
        for (int c = 0; c < 9; c++)
            addSlot(new Slot(inv, c, 8 + c * 18, invStart + 58));
    }

    public BlockPos getKeyboardPos() { return keyboardPos; }

    private LinkedKeyboardBlockEntity currentBe() {
        BlockEntity b = level.getBlockEntity(keyboardPos);
        return b instanceof LinkedKeyboardBlockEntity kb ? kb : null;
    }

    public int getRsLinkCount() {
        LinkedKeyboardBlockEntity kb = currentBe();
        return kb == null ? 0 : kb.getRsLinkCount();
    }

    @Override
    public void broadcastChanges() {
        if (!level.isClientSide) {
            LinkedKeyboardBlockEntity be = currentBe();
            if (be != null) {
                suppressWriteThrough = true;
                try {
                    int count = be.getRsLinkCount();
                    for (int i = 0; i < GHOST_COUNT; i++) {
                        int entryIdx = i / GHOST_COLS;
                        ItemStack target;
                        if (entryIdx < count) {
                            var e = be.getWirelessEntries().get(entryIdx);
                            target = (i % GHOST_COLS == 0) ? e.getFirstStack() : e.getSecondStack();
                        } else {
                            target = ItemStack.EMPTY;
                        }
                        if (!ItemStack.matches(ghosts.getItem(i), target))
                            ghosts.setItem(i, target);
                    }
                } finally { suppressWriteThrough = false; }
            }
        }
        super.broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < GHOST_COUNT) {
            int entryIdx = slotId / GHOST_COLS;
            if (entryIdx >= getRsLinkCount()) return;

            Slot s = slots.get(slotId);
            ItemStack carried = getCarried();
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
                if (!carried.isEmpty()) {
                    ItemStack copy = carried.copy(); copy.setCount(1);
                    s.set(copy);
                } else {
                    s.set(ItemStack.EMPTY);
                }
                // DONT CALL broadcastChanges() here-- the server's post-click broadcast
                // handles sync. Calling it twice causes state-ID mismatches in NeoForge 1.21
                // that cause the client to reject the click
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= 0 && index < GHOST_COUNT) {
            slots.get(index).set(ItemStack.EMPTY);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return currentBe() != null && player.distanceToSqr(
                keyboardPos.getX() + 0.5, keyboardPos.getY() + 0.5, keyboardPos.getZ() + 0.5) < 64 * 64;
    }

    public static RedstoneLinksMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new RedstoneLinksMenu(id, inv, buf.readBlockPos());
    }
}
