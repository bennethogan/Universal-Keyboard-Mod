package dev.bennethogan.universalkeyboard.client.gamepad;

import dev.bennethogan.universalkeyboard.config.ModConfig;
import dev.bennethogan.universalkeyboard.livecontrol.GamepadCodes;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlManager;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;


public final class GamepadLiveDriver {

    private static final JoystickPoller POLLER = new JoystickPoller();

    private static final Set<Integer> liveLast    = new HashSet<>();
    private static final Set<Integer> captureLast = new HashSet<>();

    private GamepadLiveDriver() {}

    private static boolean enabled()  { return ModConfig.CLIENT.enableGamepad.get(); }
    private static boolean advanced() { return ModConfig.CLIENT.enableAdvancedInput.get(); }

    // ── Live driving ──────────────────────────────────────────────────────────────

    public static void pollLive() {
        if (!enabled()) { liveLast.clear(); return; }
        POLLER.poll(advanced());
        Set<Integer> now = pressedSet();

        for (int code : now)
            if (!liveLast.contains(code)) LiveControlManager.handleKey(code, GLFW.GLFW_PRESS);
        for (int code : liveLast)
            if (!now.contains(code)) LiveControlManager.handleKey(code, GLFW.GLFW_RELEASE);

        liveLast.clear();
        liveLast.addAll(now);
    }

    public static void resetLive() { liveLast.clear(); }

    // ── Binding captures ──────────────────────────────────────────────────────────

    public static void beginCapture() {
        if (!enabled()) return;
        POLLER.poll(advanced());
        captureLast.clear();
        captureLast.addAll(pressedSet());
    }

    public static int pollCapture() {
        if (!enabled()) return -1;
        POLLER.poll(advanced());
        Set<Integer> now = pressedSet();
        int found = -1;
        for (int code : now) {
            if (!captureLast.contains(code)) { found = code; break; }
        }
        captureLast.clear();
        captureLast.addAll(now);
        return found;
    }

    public static boolean hasGamepad() { return enabled() && POLLER.anyPresent(); }

    // ── Calibration support (primary device, basic sticks) ────────────────────────

    public static void pollOnce() { if (enabled()) POLLER.poll(advanced()); }

    //attempting to make the gampad calibration screen work for all devices
    //this is a lazy fix while I design a much better screen
    public static float rawAxis(int i) {
        float best = 0f;
        for (int d = 0; d < JoystickPoller.MAX_DEVICES; d++) {
            if (!POLLER.present(d)) continue;
            float v = POLLER.axis(d, i);
            if (Math.abs(v) > Math.abs(best)) best = v;
        }
        return best;
    }

    public static double stickMax(int axis) {
        try {
            var list = ModConfig.CLIENT.stickCalibration.get();
            if (axis >= 0 && axis < list.size()) {
                double v = list.get(axis);
                if (v >= 0.3 && v <= 1.0) return v;
            }
        } catch (Exception ignored) {}
        return 1.0;
    }


    private static float calAxis(int device, int axis) {
        float raw = POLLER.axis(device, axis);
        if (axis < 0) return raw;
        // The single calibration set applies to every device for now, I will improve this later
        float v = (float) (raw / stickMax(axis));
        return Math.max(-1f, Math.min(1f, v));
    }


    public static float analogMagnitude(int code) {
        if (!enabled() || !GamepadCodes.isGamepadCode(code)) return 1.0f;
        double stickThr = ModConfig.CLIENT.stickThreshold.get();
        double trigThr  = ModConfig.CLIENT.triggerThreshold.get();
        int device = GamepadCodes.deviceOf(code);

        float raw;
        double thr;
        if (GamepadCodes.isBasicTrigger(code)) {
            raw = (POLLER.axis(device, GamepadCodes.triggerAxis(code)) + 1) / 2f;
            thr = trigThr;
        } else if (GamepadCodes.isBasicStick(code)) {
            float v = calAxis(device, GamepadCodes.stickAxis(code));
            raw = Math.max(0, GamepadCodes.stickPositive(code) ? v : -v);
            thr = stickThr;
        } else if (GamepadCodes.isRawAxisPos(code)) {
            raw = Math.max(0, calAxis(device, GamepadCodes.rawAxisIndex(code)));
            thr = stickThr;
        } else if (GamepadCodes.isRawAxisNeg(code)) {
            raw = Math.max(0, -calAxis(device, GamepadCodes.rawAxisIndex(code)));
            thr = stickThr;
        } else {
            return 1.0f; // buttons, hats
        }
        if (raw <= thr) return 0f;
        float m = (float) ((raw - thr) / (1.0 - thr));
        return Math.max(0f, Math.min(1f, m));
    }

    // ── State -> synthetic code set ───────────────────────────────────────────────

    private static Set<Integer> pressedSet() {
        double stickThr = ModConfig.CLIENT.stickThreshold.get();
        double trigThr  = ModConfig.CLIENT.triggerThreshold.get();
        Set<Integer> set = new HashSet<>();

        for (int d = 0; d < JoystickPoller.MAX_DEVICES; d++) {
            if (!POLLER.present(d)) continue;
            if (POLLER.isBasic(d)) addBasic(set, d, stickThr, trigThr);
            else                   addRaw(set, d, stickThr);
        }
        return set;
    }

    private static void addBasic(Set<Integer> set, int d, double stickThr, double trigThr) {
        for (int i = 0; i < GamepadCodes.BUTTON_COUNT; i++)
            if (POLLER.button(d, i)) set.add(GamepadCodes.basicButton(d, i));

        // GLFW gamepad axes: triggers rest at -1, fully pulled at +1
        if ((POLLER.axis(d, 4) + 1) / 2 >= trigThr) set.add(GamepadCodes.triggerLT(d));
        if ((POLLER.axis(d, 5) + 1) / 2 >= trigThr) set.add(GamepadCodes.triggerRT(d));

        // Sticks (calibration-corrected for device 0). k base 0 = LS, 4 = RS.
        addStick(set, d, calAxis(d, 0), calAxis(d, 1), stickThr, 0);
        addStick(set, d, calAxis(d, 2), calAxis(d, 3), stickThr, 4);
    }

    private static void addStick(Set<Integer> set, int d, float x, float y, double thr, int kBase) {
        if (x >=  thr) set.add(GamepadCodes.stickCode(d, kBase));     // right (+x)
        if (x <= -thr) set.add(GamepadCodes.stickCode(d, kBase + 1)); // left  (-x)
        if (y >=  thr) set.add(GamepadCodes.stickCode(d, kBase + 2)); // down  (+y)
        if (y <= -thr) set.add(GamepadCodes.stickCode(d, kBase + 3)); // up    (-y)
    }

    private static void addRaw(Set<Integer> set, int d, double stickThr) {
        int bc = POLLER.buttonCount(d);
        for (int i = 0; i < bc; i++)
            if (POLLER.button(d, i)) set.add(GamepadCodes.rawButton(d, i));

        int ac = POLLER.axisCount(d);
        for (int a = 0; a < ac; a++) {
            float v = POLLER.axis(d, a);
            if (v >=  stickThr) set.add(GamepadCodes.rawAxisPos(d, a));
            if (v <= -stickThr) set.add(GamepadCodes.rawAxisNeg(d, a));
        }

        int hc = POLLER.hatCount(d);
        for (int h = 0; h < hc; h++) {
            byte mask = POLLER.hat(d, h);
            if ((mask & GLFW.GLFW_HAT_UP)    != 0) set.add(GamepadCodes.rawHat(d, h, 0));
            if ((mask & GLFW.GLFW_HAT_RIGHT) != 0) set.add(GamepadCodes.rawHat(d, h, 1));
            if ((mask & GLFW.GLFW_HAT_DOWN)  != 0) set.add(GamepadCodes.rawHat(d, h, 2));
            if ((mask & GLFW.GLFW_HAT_LEFT)  != 0) set.add(GamepadCodes.rawHat(d, h, 3));
        }
    }
}
