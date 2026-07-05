package dev.bennethogan.universalkeyboard.compat;

import net.minecraft.world.item.ItemStack;

// Server-safe Vista item checks
public final class VistaItems {

    private VistaItems() {}

    private static boolean initialized = false;
    private static Class<?> cassetteIface;

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            cassetteIface = Class.forName("net.mehvahdjukaar.vista.common.cassette.ITvCassette");
        } catch (Throwable ignored) {
        }
    }

    public static boolean isCassette(ItemStack stack) {
        init();
        return cassetteIface != null && stack != null && !stack.isEmpty()
                && cassetteIface.isInstance(stack.getItem());
    }
}
