package dev.bennethogan.universalkeyboard.compat.rslink;

import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

public class RsLinkEntry implements IRedstoneLinkable {

    private ItemStack firstStack  = ItemStack.EMPTY;
    private ItemStack secondStack = ItemStack.EMPTY;
    private Frequency firstFreq   = Frequency.EMPTY;
    private Frequency secondFreq  = Frequency.EMPTY;

    private int      transmittedPower = 0;
    private int      receivedPower    = 0;
    private boolean  inNetwork        = false;
    private BlockPos location;

    public RsLinkEntry(BlockPos location) {
        this.location = location;
    }

    public void setLocation(BlockPos pos) { this.location = pos; }

    public ItemStack getFirstStack()  { return firstStack;  }
    public ItemStack getSecondStack() { return secondStack; }

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

    public int  getReceivedPower()          { return receivedPower; }
    public boolean isInNetwork()            { return inNetwork; }
    public void setInNetwork(boolean v)     { inNetwork = v; }


    public boolean hasFrequency() {
        return !firstStack.isEmpty() || !secondStack.isEmpty();
    }

    // IRedstoneLinkable

    @Override public int  getTransmittedStrength()       { return transmittedPower; }
    @Override public void setReceivedStrength(int power) { receivedPower = Math.max(0, Math.min(15, power)); }
    @Override public boolean isListening()               { return true; }
    @Override public boolean isAlive()                   { return location != null; }
    @Override public BlockPos getLocation()              { return location; }
    @Override public Couple<Frequency> getNetworkKey()   { return Couple.create(firstFreq, secondFreq); }
}
