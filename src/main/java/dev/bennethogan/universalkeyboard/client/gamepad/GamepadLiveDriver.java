package dev.bennethogan.universalkeyboard.client.gamepad;

import dev.bennethogan.universalkeyboard.config.ModConfig;
import dev.bennethogan.universalkeyboard.livecontrol.GamepadCodes;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlManager;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;


public final class GamepadLiveDriver {

    private static final GamepadPoller POLLER = new GamepadPoller();

    private static final Set<Integer> liveLast    = new HashSet<>();
    private static final Set<Integer> captureLast = new HashSet<>();

    private GamepadLiveDriver() {}

    private static boolean enabled() {
        return ModConfig.CLIENT.enableGamepad.get();
    }

    // Live driving

    public static void pollLive() {
        if (!enabled()) { liveLast.clear(); return; }
        POLLER.poll();
        Set<Integer> now = pressedSet();

        for (int code : now)
            if (!liveLast.contains(code)) LiveControlManager.handleKey(code, GLFW.GLFW_PRESS);
        for (int code : liveLast)
            if (!now.contains(code)) LiveControlManager.handleKey(code, GLFW.GLFW_RELEASE);

        liveLast.clear();
        liveLast.addAll(now);
    }

    public static void resetLive() {
        liveLast.clear();
    }

    // Binding captures


    public static void beginCapture() {
        if (!enabled()) return;
        POLLER.poll();
        captureLast.clear();
        captureLast.addAll(pressedSet());
    }


    public static int pollCapture() {
        if (!enabled()) return -1;
        POLLER.poll();
        Set<Integer> now = pressedSet();
        int found = -1;
        for (int code : now) {
            if (!captureLast.contains(code)) { found = code; break; }
        }
        captureLast.clear();
        captureLast.addAll(now);
        return found;
    }

    public static boolean hasGamepad() {
        return enabled() && POLLER.hasGamepad();
    }

    // State -> synthetic code set

    private static Set<Integer> pressedSet() {
        double stickThr = ModConfig.CLIENT.stickThreshold.get();
        double trigThr  = ModConfig.CLIENT.triggerThreshold.get();

        Set<Integer> set = new HashSet<>();

        for (int i = 0; i < GamepadCodes.BUTTON_COUNT; i++)
            if (POLLER.button(i)) set.add(GamepadCodes.buttonCode(i));

        // GLFW gamepad axes: triggers rest at -1, fully pulled at +1
        if ((POLLER.axis(4) + 1) / 2 >= trigThr) set.add(GamepadCodes.TRIGGER_LT);
        if ((POLLER.axis(5) + 1) / 2 >= trigThr) set.add(GamepadCodes.TRIGGER_RT);

        // Sticks: X right is +, Y up is - (GLFW convention)
        addStick(set, POLLER.axis(0), POLLER.axis(1), stickThr,
                GamepadCodes.LS_RIGHT, GamepadCodes.LS_LEFT, GamepadCodes.LS_DOWN, GamepadCodes.LS_UP);
        addStick(set, POLLER.axis(2), POLLER.axis(3), stickThr,
                GamepadCodes.RS_RIGHT, GamepadCodes.RS_LEFT, GamepadCodes.RS_DOWN, GamepadCodes.RS_UP);

        return set;
    }

    private static void addStick(Set<Integer> set, float x, float y, double thr,
                                 int right, int left, int down, int up) {
        if (x >=  thr) set.add(right);
        if (x <= -thr) set.add(left);
        if (y >=  thr) set.add(down);
        if (y <= -thr) set.add(up);
    }
}
