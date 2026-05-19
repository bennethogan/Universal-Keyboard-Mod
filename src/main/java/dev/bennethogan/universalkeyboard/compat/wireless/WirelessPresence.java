package dev.bennethogan.universalkeyboard.compat.wireless;

import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;

/**
 * Safe presence check for Create's wireless redstone API.
 * This class has NO references to WirelessEntry or any Create type in its
 * signatures, so it is safe to load (and call isPresent()) from any context —
 * including client screens — even when Create is absent.
 *
 * All other Create-dependent code lives in CreateWirelessHelper, which is only
 * loaded once isPresent() has already returned true.
 */
public final class WirelessPresence {

    private static volatile boolean initialized = false;
    private static volatile boolean present     = false;

    private WirelessPresence() {}

    public static boolean isPresent() {
        if (!initialized) init();
        return present;
    }

    private static synchronized void init() {
        if (initialized) return;
        try {
            Class.forName("com.simibubi.create.content.redstone.link.IRedstoneLinkable");
            present = true;
            UniversalKeyboardMod.LOGGER.info("Create redstone link API detected — wireless outputs enabled.");
        } catch (ClassNotFoundException e) {
            UniversalKeyboardMod.LOGGER.info("Create redstone link API not present — wireless outputs disabled.");
        } catch (Throwable t) {
            UniversalKeyboardMod.LOGGER.warn("Wireless redstone presence check failed: {}", t.getMessage());
        }
        initialized = true;
    }
}
