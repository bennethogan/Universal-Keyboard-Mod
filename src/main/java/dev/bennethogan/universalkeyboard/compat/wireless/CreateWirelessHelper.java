package dev.bennethogan.universalkeyboard.compat.wireless;

import com.simibubi.create.Create;
import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Wireless redstone link operations. Only safe to load AFTER WirelessPresence.isPresent()
 * has returned true — this class has WirelessEntry in its method signatures, so loading
 * it will trigger WirelessEntry class loading (which imports Create types).
 */
public final class CreateWirelessHelper {

    private CreateWirelessHelper() {}

    /** Create a new (unconfigured) entry. Safe to call only when WirelessPresence.isPresent(). */
    public static WirelessEntry newEntry(BlockPos pos) {
        return new WirelessEntry(pos);
    }

    /**
     * Apply a new power level to the entry.
     * If the entry has a frequency configured it stays in the network (for receive);
     * the transmitted strength is simply updated via updateNetworkOf.
     */
    public static void setEntryPower(Level level, WirelessEntry entry, int power) {
        if (!WirelessPresence.isPresent() || level == null || level.isClientSide || entry == null) return;
        int clamped = Math.max(0, Math.min(15, power));
        if (clamped == entry.getPower()) return;
        entry.setPower(clamped);
        try {
            if (entry.isInNetwork()) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, entry);
            } else if (entry.hasFrequency()) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, entry);
                entry.setInNetwork(true);
            }
        } catch (Throwable t) {
            UniversalKeyboardMod.LOGGER.warn("Wireless setEntryPower failed: {}", t.toString());
        }
    }

    /**
     * Update one of the two frequency items on an entry. Removes the entry
     * from its current network (under the OLD key) before mutating, then
     * re-adds under the NEW key if the entry has any frequency configured.
     */
    public static void updateFrequency(Level level, WirelessEntry entry, boolean first, net.minecraft.world.item.ItemStack stack) {
        if (entry == null) return;
        boolean wasInNetwork = entry.isInNetwork();
        if (wasInNetwork && WirelessPresence.isPresent() && level != null && !level.isClientSide) {
            try {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, entry);
                entry.setInNetwork(false);
            } catch (Throwable t) { UniversalKeyboardMod.LOGGER.warn("Wireless removeFromNetwork (rejoin): {}", t.toString()); }
        }

        if (first) entry.setFirstStack(stack); else entry.setSecondStack(stack);

        if (WirelessPresence.isPresent() && level != null && !level.isClientSide && entry.hasFrequency()) {
            try {
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, entry);
                entry.setInNetwork(true);
            } catch (Throwable t) { UniversalKeyboardMod.LOGGER.warn("Wireless addToNetwork (rejoin): {}", t.toString()); }
        }
    }

    /**
     * Ensure the entry is registered with the network so it can receive signals.
     * Called after chunk load for entries that have a frequency but haven't been registered yet.
     */
    public static void ensureRegistered(Level level, WirelessEntry entry) {
        if (!WirelessPresence.isPresent() || level == null || level.isClientSide || entry == null) return;
        if (entry.isInNetwork() || !entry.hasFrequency()) return;
        try {
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, entry);
            entry.setInNetwork(true);
        } catch (Throwable t) {
            UniversalKeyboardMod.LOGGER.warn("Wireless ensureRegistered failed: {}", t.toString());
        }
    }

    /** Force-remove an entry from the network. Called on block invalidate/destroy. */
    public static void removeFromNetwork(Level level, WirelessEntry entry) {
        if (!WirelessPresence.isPresent() || level == null || level.isClientSide || entry == null) return;
        if (!entry.isInNetwork()) return;
        try {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, entry);
            entry.setInNetwork(false);
            entry.setPower(0);
        } catch (Throwable t) {
            UniversalKeyboardMod.LOGGER.warn("Wireless removeFromNetwork failed: {}", t.toString());
        }
    }
}
