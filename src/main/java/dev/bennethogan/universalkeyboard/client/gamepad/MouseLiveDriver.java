package dev.bennethogan.universalkeyboard.client.gamepad;

import dev.bennethogan.universalkeyboard.client.KeyboardCaptureManager;
import dev.bennethogan.universalkeyboard.config.ModConfig;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlManager;
import dev.bennethogan.universalkeyboard.livecontrol.MouseCodes;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

public final class MouseLiveDriver {

    private MouseLiveDriver() {}

    // Tuning
    // Pixels per tick that is considered 100% at config sensitivity = 1.0
    private static final float FULL_DEFLECTION_PX = 18f;
    // based on my tests, the absolute mode needed to be 10x softer than velocity to have the same default 1.0 value work for both
    private static final float ABSOLUTE_SENS_FACTOR = 0.1f;
    // tuning animation. 1/40th of 1/16th of a block per pixel of real movement
    private static final float MODEL_SCALE = 1f / 40f;
    // rectangle shaped bounding box so you have some freedom to move forwrard and back despite the keyboard to the left
    private static final float MODEL_X_RANGE = 0.5f;
    private static final float MODEL_Y_RANGE = 2.5f;

    // State
    private static boolean focusActive = false;
    private static final Set<Integer> liveLast = new HashSet<>();

    private static double lastX = Double.NaN, lastY;
    private static float  curDX, curDY;              // this tick's pixel delta (focused only)
    private static float  absOffX, absOffY;           // absolute offset from anchor (absolute mode)
    private static double anchorX, anchorY;           // cursor position when focus was toggled on
    private static int    pendingScrollUp, pendingScrollDown;

    // Smoothed offset for the on-block mouse model
    private static float modelX, modelY, prevModelX, prevModelY;

    private static boolean enabled() {
        try { return ModConfig.CLIENT.enableMouseInput.get(); }
        catch (Exception e) { return false; }
    }

    private static boolean absoluteMode() {
        try { return ModConfig.CLIENT.mouseAbsoluteMode.get(); }
        catch (Exception e) { return false; }
    }

    private static double sensitivity() {
        try { return ModConfig.CLIENT.mouseAxisSensitivity.get(); }
        catch (Exception e) { return 1.0; }
    }

    private static long window() {
        return Minecraft.getInstance().getWindow().getWindow();
    }

    // Focus
    public static boolean isAvailable() { return enabled(); }

    // Live control mode and CC Computer typing mode are the only places the mouse animation is true, currently
    public static boolean isFocused() {
        if (!focusActive || !enabled()) return false;
        return LiveControlManager.isActive() || KeyboardCaptureManager.isCCCapturing();
    }

    public static boolean toggleFocus() {
        if (!enabled()) return false;
        if (!LiveControlManager.isActive() && !KeyboardCaptureManager.isCCCapturing()) return false;
        focusActive = !focusActive;
        // Sync the reference point so enabling focus doesn't inject a huge first delta
        lastX = Double.NaN;
        // In absolute mode, record where focus starts so offset is measured from here
        Minecraft mc = Minecraft.getInstance();
        anchorX = mc.mouseHandler.xpos();
        anchorY = mc.mouseHandler.ypos();
        absOffX = absOffY = 0f;
        return focusActive;
    }

    public static void reset() {
        focusActive = false;
        liveLast.clear();
        lastX = Double.NaN;
        curDX = curDY = 0f;
        absOffX = absOffY = 0f;
        pendingScrollUp = pendingScrollDown = 0;
        modelX = modelY = prevModelX = prevModelY = 0f;
    }

    // Live polling
    public static void pollLive() {
        prevModelX = modelX;
        prevModelY = modelY;

        if (!isFocused()) {
            if (!liveLast.isEmpty()) {
                // Release any held bindings only if Live Control is still active.
                if (LiveControlManager.isActive())
                    for (int code : liveLast) LiveControlManager.handleKey(code, GLFW.GLFW_RELEASE);
                liveLast.clear();
            }
            // Model mouse stays parked wherever the player left it.
            lastX = Double.NaN;
            return;
        }

        readDelta();

        if (LiveControlManager.isActive()) {
            // Full Live Control path: dispatch bindings + update model.
            Set<Integer> now = pressedSet();
            for (int code : now)
                if (!liveLast.contains(code)) LiveControlManager.handleKey(code, GLFW.GLFW_PRESS);
            for (int code : liveLast)
                if (!now.contains(code)) LiveControlManager.handleKey(code, GLFW.GLFW_RELEASE);
            liveLast.clear();
            liveLast.addAll(now);
        } else {
            // for CC computer capture, dont need anything but tracking cursor
            liveLast.clear();
        }

        updateModelOffset();
    }

    private static void readDelta() {
        Minecraft mc = Minecraft.getInstance();
        double x = mc.mouseHandler.xpos();
        double y = mc.mouseHandler.ypos();
        if (Double.isNaN(lastX)) { lastX = x; lastY = y; curDX = curDY = 0f; return; }
        curDX = (float) (x - lastX);
        curDY = (float) (y - lastY);
        lastX = x;
        lastY = y;
        absOffX = (float) (x - anchorX);
        absOffY = (float) (y - anchorY);
    }


    // normalized magnitude of pixel delta along an axis
    private static float axisMag(float deltaPx) {
        // sensitivity adjustment so this works for both modes
        float sens = (float) sensitivity() * (absoluteMode() ? ABSOLUTE_SENS_FACTOR : 1f);
        float m = Math.abs(deltaPx) / FULL_DEFLECTION_PX * sens;
        return Math.max(0f, Math.min(1f, m));
    }

    private static Set<Integer> pressedSet() {
        double thr = ModConfig.CLIENT.stickThreshold.get();
        Set<Integer> set = new HashSet<>();

        // mouse buttons
        for (int i = 0; i < MouseCodes.MAX_BUTTONS; i++)
            if (GLFW.glfwGetMouseButton(window(), i) == GLFW.GLFW_PRESS)
                set.add(MouseCodes.button(i));

        // Movement axes, velocity mode and absolute (mimic-ing aeroworks joystick)
        if (absoluteMode()) {
            if (axisMag(absOffX) >= thr) set.add(absOffX > 0 ? MouseCodes.AXIS_X_POS : MouseCodes.AXIS_X_NEG);
            if (axisMag(absOffY) >= thr) set.add(absOffY > 0 ? MouseCodes.AXIS_Y_POS : MouseCodes.AXIS_Y_NEG);
        } else {
            if (axisMag(curDX) >= thr) set.add(curDX > 0 ? MouseCodes.AXIS_X_POS : MouseCodes.AXIS_X_NEG);
            if (axisMag(curDY) >= thr) set.add(curDY > 0 ? MouseCodes.AXIS_Y_POS : MouseCodes.AXIS_Y_NEG);
        }

        // Scroll pulses (1 tick each)
        if (pendingScrollUp   > 0) { set.add(MouseCodes.SCROLL_UP);   pendingScrollUp--; }
        if (pendingScrollDown > 0) { set.add(MouseCodes.SCROLL_DOWN); pendingScrollDown--; }

        return set;
    }

    // Analog deflection for an axis code, post-threshold, matching the stick curve
    public static float analogMagnitude(int code) {
        // Buttons and scroll are full-magnitude; only the movement axes scale
        if (!isFocused() || !MouseCodes.isAxis(code)) return 1.0f;
        double thr = ModConfig.CLIENT.stickThreshold.get();
        boolean xAxis = MouseCodes.axisOf(code) == 0;
        float source = absoluteMode()
                ? (xAxis ? absOffX : absOffY)
                : (xAxis ? curDX   : curDY);
        float raw = axisMag(source);
        boolean dirOk = MouseCodes.axisPositive(code) ? source > 0 : source < 0;
        if (!dirOk || raw <= thr) return 0f;
        float m = (float) ((raw - thr) / (1.0 - thr));
        return Math.max(0f, Math.min(1f, m));
    }

    public static void addScroll(double scrollY) {
        if (scrollY > 0) pendingScrollUp++;
        else if (scrollY < 0) pendingScrollDown++;
    }

    // Track cursor for animation
    private static void updateModelOffset() {
        modelX = clamp(modelX + curDX * MODEL_SCALE, MODEL_X_RANGE);
        modelY = clamp(modelY + curDY * MODEL_SCALE, MODEL_Y_RANGE);
    }

    private static float clamp(float v, float range) {
        return Math.max(-range, Math.min(range, v));
    }

    // mouse animation doesnt bounce back to 0
    public static float modelOffsetX(float partialTick) {
        return prevModelX + (modelX - prevModelX) * partialTick;
    }

    public static float modelOffsetY(float partialTick) {
        return prevModelY + (modelY - prevModelY) * partialTick;
    }
}
