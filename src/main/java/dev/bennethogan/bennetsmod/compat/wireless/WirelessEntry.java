package dev.bennethogan.bennetsmod.compat.wireless;

import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * One wireless redstone output slot owned by a LinkedKeyboardBlockEntity.
 *
 * Implements Create's IRedstoneLinkable so a sequencer step (or anything else
 * calling {@link #setPower(int)}) can transmit on the configured two-item
 * frequency via {@code Create.REDSTONE_LINK_NETWORK_HANDLER}.
 *
 * Compile-time references to Create types only — the class is gated by a
 * Class.forName check in {@link CreateWirelessHelper} so it never loads when
 * Create is absent.
 */
public class WirelessEntry implements IRedstoneLinkable {

    private ItemStack firstStack  = ItemStack.EMPTY;
    private ItemStack secondStack = ItemStack.EMPTY;
    private Frequency firstFreq   = Frequency.EMPTY;
    private Frequency secondFreq  = Frequency.EMPTY;

    private int      transmittedPower = 0;
    private BlockPos location;

    public WirelessEntry(BlockPos location) {
        this.location = location;
    }

    public void setLocation(BlockPos pos) { this.location = pos; }

    public ItemStack getFirstStack()  { return firstStack;  }
    public ItemStack getSecondStack() { return secondStack; }

    /** Returns true if the frequency item actually changed (network needs re-registration). */
    public boolean setFirstStack(ItemStack stack) {
        ItemStack copy = stack.copy(); copy.setCount(1);
        boolean changed = !ItemStack.isSameItemSameComponents(copy, firstStack);
        firstStack = copy;
        firstFreq  = Frequency.of(copy);
        return changed;
    }

    public boolean setSecondStack(ItemStack stack) {
        ItemStack copy = stack.copy(); copy.setCount(1);
        boolean changed = !ItemStack.isSameItemSameComponents(copy, secondStack);
        secondStack = copy;
        secondFreq  = Frequency.of(copy);
        return changed;
    }

    public int  getPower() { return transmittedPower; }
    public void setPower(int p) { transmittedPower = Math.max(0, Math.min(15, p)); }

    /** True if at least one of the two frequency slots has been configured. */
    public boolean hasFrequency() {
        return !firstStack.isEmpty() || !secondStack.isEmpty();
    }

    // ── IRedstoneLinkable ─────────────────────────────────────────────────────

    @Override public int  getTransmittedStrength()       { return transmittedPower; }
    @Override public void setReceivedStrength(int power) { /* transmit-only */ }
    @Override public boolean isListening()               { return false; }
    @Override public boolean isAlive()                   { return location != null; }
    @Override public BlockPos getLocation()              { return location; }
    @Override public Couple<Frequency> getNetworkKey()   { return Couple.create(firstFreq, secondFreq); }
}
