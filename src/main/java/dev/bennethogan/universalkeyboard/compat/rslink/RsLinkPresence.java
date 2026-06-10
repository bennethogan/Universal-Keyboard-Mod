package dev.bennethogan.universalkeyboard.compat.rslink;

import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;

// safe to call presence check for Create redstone API
public final class RsLinkPresence {

    private static volatile boolean initialized = false;
    private static volatile boolean present     = false;

    private RsLinkPresence() {}

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
