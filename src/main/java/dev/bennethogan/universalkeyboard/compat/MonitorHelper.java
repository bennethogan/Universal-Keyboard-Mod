package dev.bennethogan.universalkeyboard.compat;

import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;

// CC:Tweaked monitor access via reflection (soft dependency). Monitors have no program
// reading key/char events, so text is written straight to the monitor's terminal instead.
public class MonitorHelper {

    public static boolean isMonitor(BlockEntity be) {
        if (be == null) return false;
        String n = be.getClass().getName();
        return n.contains("dan200.computercraft") && n.contains("Monitor");
    }

    // Write one character at the cursor; the terminal advances the cursor itself.
    public static void writeChar(BlockEntity monitorBE, char c) {
        Object terminal = getTerminal(monitorBE);
        if (terminal == null) return;
        try {
            Class<?> tc = terminal.getClass();
            int x = ((Number) tc.getMethod("getCursorX").invoke(terminal)).intValue();
            int y = ((Number) tc.getMethod("getCursorY").invoke(terminal)).intValue();
            tc.getMethod("write", String.class).invoke(terminal, String.valueOf(c));
            tc.getMethod("setCursorPos", int.class, int.class).invoke(terminal, x + 1, y);
        } catch (Exception ignored) {}
    }

    // print()-style line break: cursor to start of next row, scrolling up at the bottom.
    public static void newline(BlockEntity monitorBE) {
        Object terminal = getTerminal(monitorBE);
        if (terminal == null) return;
        try {
            int y = ((Number) terminal.getClass().getMethod("getCursorY").invoke(terminal)).intValue();
            int h = ((Number) terminal.getClass().getMethod("getHeight").invoke(terminal)).intValue();
            Method setCursorPos = terminal.getClass().getMethod("setCursorPos", int.class, int.class);
            if (y + 1 < h) {
                setCursorPos.invoke(terminal, 0, y + 1);
            } else {
                terminal.getClass().getMethod("scroll", int.class).invoke(terminal, 1);
                setCursorPos.invoke(terminal, 0, h - 1);
            }
        } catch (Exception ignored) {}
    }

    private static Object getTerminal(BlockEntity monitorBE) {
        Object serverMonitor = getServerMonitor(monitorBE);
        if (serverMonitor == null) return null;
        try {
            return serverMonitor.getClass().getMethod("getTerminal").invoke(serverMonitor);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object getServerMonitor(BlockEntity be) {
        if (be == null) return null;
        try {
            Object sm = be.getClass().getMethod("getCachedServerMonitor").invoke(be);
            if (sm != null) return sm;
        } catch (Exception ignored) {}
        try {
            return be.getClass().getMethod("createServerMonitor").invoke(be);
        } catch (Exception ignored) {}
        return null;
    }
}
