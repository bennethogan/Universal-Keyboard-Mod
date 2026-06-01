package dev.bennethogan.universalkeyboard.client.gamepad;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;


public final class GamepadPoller {

    private final boolean[] buttons = new boolean[15];
    private final float[]   axes    = new float[6];

    private GLFWGamepadState state;
    private int selectedGamepad = -1;

    // poll active gamepad
    public void poll() {
        if (state == null) state = GLFWGamepadState.create();

        if (selectedGamepad < 0) {
            selectedGamepad = discover();
        }

        if (selectedGamepad < 0 || !GLFW.glfwJoystickIsGamepad(selectedGamepad)) {
            empty();
            selectedGamepad = -1;
            return;
        }

        if (GLFW.glfwGetGamepadState(selectedGamepad, state)) {
            for (int i = 0; i < buttons.length; i++) buttons[i] = state.buttons(i) != 0;
            for (int i = 0; i < axes.length;    i++) axes[i]    = state.axes(i);
        } else {
            empty();
            selectedGamepad = -1;
        }
    }

    // discover gamepad
    private int discover() {
        int unique = -1;
        for (int i = 0; i <= GLFW.GLFW_JOYSTICK_LAST; i++) {
            if (!GLFW.glfwJoystickIsGamepad(i)) continue;
            if (unique == -1) unique = i;       // first gamepad found
            else              unique = -2;      // more than one — require activity to pick
            if (GLFW.glfwGetGamepadState(i, state)) {
                for (int b = 0; b < buttons.length; b++) {
                    if (state.buttons(b) != 0) return i; // this one is being used
                }
            }
        }
        return unique >= 0 ? unique : -1;       // exactly one → pick it; none → -1
    }

    private void empty() {
        for (int i = 0; i < buttons.length; i++) buttons[i] = false;
        for (int i = 0; i < axes.length;    i++) axes[i]    = (i < 4) ? 0.0f : -1.0f;
    }

    public boolean hasGamepad()       { return selectedGamepad >= 0; }
    public boolean button(int i)      { return i >= 0 && i < buttons.length && buttons[i]; }
    public float   axis(int i)        { return (i >= 0 && i < axes.length) ? axes[i] : 0.0f; }

    // force redetection for swapping / unplugging controllers
    public void forget() { selectedGamepad = -1; }
}
