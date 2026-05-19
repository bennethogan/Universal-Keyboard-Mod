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

public class WirelessConfigMenu extends AbstractContainerMenu {

    public static final int ROWS        = LinkedKeyboardBlockEntity.MAX_WIRELESS;
    public static final int HALF_ROWS   = ROWS / 2; // 6 rows per visual column
    public static final int GHOST_COLS  = 2;
    public static final int GHOST_COUNT = ROWS * GHOST_COLS;

    private final BlockPos keyboardPos;
    private final Level    level;
    private final SimpleContainer ghosts;
    private boolean suppressWriteThrough = false;

    public WirelessConfigMenu(int id, Inventory inv, BlockPos pos) {
        super(ModMenus.WIRELESS_CONFIG_MENU.get(), id);
        this.keyboardPos = pos;
        this.level       = inv.player.level();

        this.ghosts = new SimpleContainer(GHOST_COUNT) {
            @Override public int getMaxStackSize() { return 1; }

            @Override public void setChanged() {
                super.setChanged();
                if (!suppressWriteThrough && !level.isClientSide) {
                    LinkedKeyboardBlockEntity be = currentBe();
                    if (be == null) return;
                    // Reflect ALL slots back to the BE 
                    for (int i = 0; i < GHOST_COUNT; i++) {
                        int entryIdx = i / GHOST_COLS;
                        if (entryIdx >= be.getWirelessCount()) continue;
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
                for (int i = 0; i < be.getWirelessCount() && i < ROWS; i++) {
                    var e = be.getWirelessEntries().get(i);
                    ghosts.setItem(i * GHOST_COLS,     e.getFirstStack());
                    ghosts.setItem(i * GHOST_COLS + 1, e.getSecondStack());
                }
            } finally { suppressWriteThrough = false; }
        }

        // Left group (W1–W6): entries 0–5, visual rows 0–5
        for (int row = 0; row < HALF_ROWS; row++) {
            int y = 18 + row * 18;
            for (int col = 0; col < GHOST_COLS; col++) {
                addSlot(new Slot(ghosts, row * GHOST_COLS + col, 26 + col * 20, y) {
                    @Override public int getMaxStackSize()       { return 1; }
                    @Override public boolean mayPickup(Player p) { return false; }
                    @Override public boolean mayPlace(ItemStack stack) { return true; }
                });
            }
        }
        // Right group (W7–W12): entries 6–11, visual rows 0–5 at x+88
        for (int row = HALF_ROWS; row < ROWS; row++) {
            int y = 18 + (row - HALF_ROWS) * 18;
            for (int col = 0; col < GHOST_COLS; col++) {
                addSlot(new Slot(ghosts, row * GHOST_COLS + col, 120 + col * 20, y) {
                    @Override public int getMaxStackSize()       { return 1; }
                    @Override public boolean mayPickup(Player p) { return false; }
                    @Override public boolean mayPlace(ItemStack stack) { return true; }
                });
            }
        }

        int invStart = 18 + HALF_ROWS * 18 + 28;
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

    public int getWirelessCount() {
        LinkedKeyboardBlockEntity kb = currentBe();
        return kb == null ? 0 : kb.getWirelessCount();
    }

    @Override
    public void broadcastChanges() {
        // Pull authoritative state from the BE before container sync runs so any
        // add/remove that happened between ticks is reflected to the client.
        if (!level.isClientSide) {
            LinkedKeyboardBlockEntity be = currentBe();
            if (be != null) {
                suppressWriteThrough = true;
                try {
                    int count = be.getWirelessCount();
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
            if (entryIdx >= getWirelessCount()) return; // row not "added" yet

            Slot s = slots.get(slotId);
            ItemStack carried = getCarried();
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
                if (!carried.isEmpty()) {
                    ItemStack copy = carried.copy(); copy.setCount(1);
                    s.set(copy);
                } else {
                    s.set(ItemStack.EMPTY);
                }
                broadcastChanges();
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= 0 && index < GHOST_COUNT) {
            slots.get(index).set(ItemStack.EMPTY);
            broadcastChanges();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return currentBe() != null && player.distanceToSqr(
                keyboardPos.getX() + 0.5, keyboardPos.getY() + 0.5, keyboardPos.getZ() + 0.5) < 64 * 64;
    }

    public static WirelessConfigMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new WirelessConfigMenu(id, inv, buf.readBlockPos());
    }
}
