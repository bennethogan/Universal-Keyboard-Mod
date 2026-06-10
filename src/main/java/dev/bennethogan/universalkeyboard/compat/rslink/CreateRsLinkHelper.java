package dev.bennethogan.universalkeyboard.compat.rslink;

import com.simibubi.create.Create;
import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Wireless redstone link operations. Only safe to load after RsLinkPresence.isPresent()
 * has returned true. this class has RsLinkEntry in its method signatures, so loading
 * it will trigger RsLinkEntry class loading (which imports Create types)
 */
public final class CreateRsLinkHelper {

    private CreateRsLinkHelper() {}

    // Create a new entry. Safe to call only when RsLinkPresence.isPresent()
    public static RsLinkEntry newEntry(BlockPos pos) {
        return new RsLinkEntry(pos);
    }


    public static void setEntryPower(Level level, RsLinkEntry entry, int power) {
        if (!RsLinkPresence.isPresent() || level == null || level.isClientSide || entry == null) return;
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


    public static void updateFrequency(Level level, RsLinkEntry entry, boolean first, net.minecraft.world.item.ItemStack stack) {
        if (entry == null) return;
        boolean wasInNetwork = entry.isInNetwork();
        if (wasInNetwork && RsLinkPresence.isPresent() && level != null && !level.isClientSide) {
            try {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, entry);
                entry.setInNetwork(false);
            } catch (Throwable t) { UniversalKeyboardMod.LOGGER.warn("Wireless removeFromNetwork (rejoin): {}", t.toString()); }
        }

        if (first) entry.setFirstStack(stack); else entry.setSecondStack(stack);

        if (RsLinkPresence.isPresent() && level != null && !level.isClientSide && entry.hasFrequency()) {
            try {
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, entry);
                entry.setInNetwork(true);
            } catch (Throwable t) { UniversalKeyboardMod.LOGGER.warn("Wireless addToNetwork (rejoin): {}", t.toString()); }
        }
    }


    public static void ensureRegistered(Level level, RsLinkEntry entry) {
        if (!RsLinkPresence.isPresent() || level == null || level.isClientSide || entry == null) return;
        if (entry.isInNetwork() || !entry.hasFrequency()) return;
        try {
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, entry);
            entry.setInNetwork(true);
        } catch (Throwable t) {
            UniversalKeyboardMod.LOGGER.warn("Wireless ensureRegistered failed: {}", t.toString());
        }
    }

    public static void removeFromNetwork(Level level, RsLinkEntry entry) {
        if (!RsLinkPresence.isPresent() || level == null || level.isClientSide || entry == null) return;
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
