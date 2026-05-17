package dev.bennethogan.bennetsmod.compat.wireless;

import com.simibubi.create.Create;
import dev.bennethogan.bennetsmod.UniversalKeyboardMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Entry point for wireless redstone link integration. Gates all references to
 * Create's RedstoneLinkNetworkHandler / IRedstoneLinkable so the rest of the
 * mod can call into here without ever touching Create classes when Create is
 * absent.
 *
 * The WirelessEntry class itself imports Create types — so we Class.forName
 * the network handler before touching anything in this class. Java will only
 * link WirelessEntry's bytecode when one of these methods is actually invoked.
 */
public final class CreateWirelessHelper {

    private static boolean initialized = false;
    private static boolean createPresent = false;

    private CreateWirelessHelper() {}

    public static boolean isPresent() {
        if (!initialized) init();
        return createPresent;
    }

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class.forName("com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler");
            Class.forName("com.simibubi.create.content.redstone.link.IRedstoneLinkable");
            createPresent = true;
            UniversalKeyboardMod.LOGGER.info("Create redstone link API detected — wireless outputs enabled.");
        } catch (ClassNotFoundException e) {
            UniversalKeyboardMod.LOGGER.info("Create redstone link API not present — wireless outputs disabled.");
        } catch (Throwable t) {
            UniversalKeyboardMod.LOGGER.warn("Wireless redstone init failed: {}", t.getMessage());
        }
    }

    /** Create a new (unconfigured) entry. Safe to call only when isPresent(). */
    public static WirelessEntry newEntry(BlockPos pos) {
        return new WirelessEntry(pos);
    }

    /**
     * Apply a new power level to the entry, registering or unregistering with
     * Create's network as needed.
     *
     * - Going 0 → >0:  addToNetwork
     * - Going >0 → 0:  removeFromNetwork
     * - Going >0 → >0: just updateNetworkOf
     */
    public static void setEntryPower(Level level, WirelessEntry entry, int power) {
        if (!isPresent() || level == null || level.isClientSide || entry == null) return;
        int clamped = Math.max(0, Math.min(15, power));
        int prev = entry.getPower();
        if (clamped == prev) return;

        entry.setPower(clamped);

        try {
            if (prev == 0 && clamped > 0) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, entry);
            } else if (prev > 0 && clamped == 0) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, entry);
            } else {
                Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, entry);
            }
        } catch (Throwable t) {
            UniversalKeyboardMod.LOGGER.warn("Wireless setEntryPower failed: {}", t.toString());
        }
    }

    /**
     * Update one of the two frequency items on an entry. Removes the entry
     * from its current network (under the OLD key) before mutating, then
     * re-adds under the NEW key if the entry is currently transmitting.
     */
    public static void updateFrequency(Level level, WirelessEntry entry, boolean first, net.minecraft.world.item.ItemStack stack) {
        if (entry == null) return;
        boolean wasActive = entry.getPower() > 0;
        if (wasActive && isPresent() && level != null && !level.isClientSide) {
            try { Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, entry); }
            catch (Throwable t) { UniversalKeyboardMod.LOGGER.warn("Wireless removeFromNetwork (rejoin): {}", t.toString()); }
        }

        if (first) entry.setFirstStack(stack); else entry.setSecondStack(stack);

        if (wasActive && isPresent() && level != null && !level.isClientSide) {
            try { Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, entry); }
            catch (Throwable t) { UniversalKeyboardMod.LOGGER.warn("Wireless addToNetwork (rejoin): {}", t.toString()); }
        }
    }

    /** Force-remove an entry from the network. Called on block invalidate/destroy. */
    public static void removeFromNetwork(Level level, WirelessEntry entry) {
        if (!isPresent() || level == null || level.isClientSide || entry == null) return;
        if (entry.getPower() == 0) return;
        try {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, entry);
            entry.setPower(0);
        } catch (Throwable t) {
            UniversalKeyboardMod.LOGGER.warn("Wireless removeFromNetwork failed: {}", t.toString());
        }
    }
}
