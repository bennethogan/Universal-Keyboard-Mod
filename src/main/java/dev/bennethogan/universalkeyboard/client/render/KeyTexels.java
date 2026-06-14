package dev.bennethogan.universalkeyboard.client.render;

import org.lwjgl.glfw.GLFW;
import java.util.HashMap;
import java.util.Map;

public final class KeyTexels {

    private KeyTexels() {}

    public static int[] get(int glfwKey) {
        return MAP.get(glfwKey);
    }

    private static final Map<Integer, int[]> MAP = new HashMap<>();

    private static void k(int key, int tx, int ty) {
        MAP.put(key, new int[]{tx, ty, 1, 1});
    }

    private static void k(int key, int tx, int ty, int w, int h) {
        MAP.put(key, new int[]{tx, ty, w, h});
    }

    static {
        //  Function row
        k(GLFW.GLFW_KEY_ESCAPE,    22,  6);
        k(GLFW.GLFW_KEY_F1,        20,  6);
        k(GLFW.GLFW_KEY_F2,        19,  6);
        k(GLFW.GLFW_KEY_F3,        18,  6);
        k(GLFW.GLFW_KEY_F4,        17,  6);
        k(GLFW.GLFW_KEY_F5,        15,  6);
        k(GLFW.GLFW_KEY_F6,        14,  6);
        k(GLFW.GLFW_KEY_F7,        13,  6);
        k(GLFW.GLFW_KEY_F8,        12,  6);
        k(GLFW.GLFW_KEY_F9,        10,  6);
        k(GLFW.GLFW_KEY_F10,       9,  6);
        k(GLFW.GLFW_KEY_F11,       8,  6);
        k(GLFW.GLFW_KEY_F12,       7,  6);

        // Number row
        k(GLFW.GLFW_KEY_GRAVE_ACCENT, 22, 4);
        k(GLFW.GLFW_KEY_1,         21,  4);
        k(GLFW.GLFW_KEY_2,         20,  4);
        k(GLFW.GLFW_KEY_3,         19,  4);
        k(GLFW.GLFW_KEY_4,         18,  4);
        k(GLFW.GLFW_KEY_5,         17,  4);
        k(GLFW.GLFW_KEY_6,         16,  4);
        k(GLFW.GLFW_KEY_7,         15,  4);
        k(GLFW.GLFW_KEY_8,         14,  4);
        k(GLFW.GLFW_KEY_9,         13,  4);
        k(GLFW.GLFW_KEY_0,         12,  4);
        k(GLFW.GLFW_KEY_MINUS,     11,  4);
        k(GLFW.GLFW_KEY_EQUAL,     10,  4);
        k(GLFW.GLFW_KEY_BACKSPACE, 9,  4, 2, 1);

        // QWERTY row
        k(GLFW.GLFW_KEY_TAB,       22,  3, 2, 1);
        k(GLFW.GLFW_KEY_Q,         21,  3);
        k(GLFW.GLFW_KEY_W,         20,  3);
        k(GLFW.GLFW_KEY_E,         19,  3);
        k(GLFW.GLFW_KEY_R,         18,  3);
        k(GLFW.GLFW_KEY_T,         17,  3);
        k(GLFW.GLFW_KEY_Y,         16,  3);
        k(GLFW.GLFW_KEY_U,         15,  3);
        k(GLFW.GLFW_KEY_I,         14,  3);
        k(GLFW.GLFW_KEY_O,         13,  3);
        k(GLFW.GLFW_KEY_P,         12,  3);
        k(GLFW.GLFW_KEY_LEFT_BRACKET,  11, 3);
        k(GLFW.GLFW_KEY_RIGHT_BRACKET, 10, 3);
        k(GLFW.GLFW_KEY_BACKSLASH, 9,  3);

        // ASDF row
        k(GLFW.GLFW_KEY_CAPS_LOCK, 22,  2, 2, 1);
        k(GLFW.GLFW_KEY_A,         21,  2);
        k(GLFW.GLFW_KEY_S,         20,  2);
        k(GLFW.GLFW_KEY_D,         19,  2);
        k(GLFW.GLFW_KEY_F,         18,  2);
        k(GLFW.GLFW_KEY_G,         17,  2);
        k(GLFW.GLFW_KEY_H,         16,  2);
        k(GLFW.GLFW_KEY_J,         15,  2);
        k(GLFW.GLFW_KEY_K,         14,  2);
        k(GLFW.GLFW_KEY_L,         13,  2);
        k(GLFW.GLFW_KEY_SEMICOLON, 12,  2);
        k(GLFW.GLFW_KEY_APOSTROPHE,11,  2);
        k(GLFW.GLFW_KEY_ENTER,     9,  2, 2, 1);

        // ZXCV row
        k(GLFW.GLFW_KEY_LEFT_SHIFT, 22, 1, 3, 1);
        k(GLFW.GLFW_KEY_Z,         21,  1);
        k(GLFW.GLFW_KEY_X,         20,  1);
        k(GLFW.GLFW_KEY_C,         19,  1);
        k(GLFW.GLFW_KEY_V,         18,  1);
        k(GLFW.GLFW_KEY_B,         17,  1);
        k(GLFW.GLFW_KEY_N,         16,  1);
        k(GLFW.GLFW_KEY_M,         15,  1);
        k(GLFW.GLFW_KEY_COMMA,     12,  1);
        k(GLFW.GLFW_KEY_PERIOD,    11,  1);
        k(GLFW.GLFW_KEY_SLASH,     10,  1);
        k(GLFW.GLFW_KEY_RIGHT_SHIFT, 9, 1, 2, 1);

        // Bottom row
        k(GLFW.GLFW_KEY_LEFT_CONTROL, 22, 1);
        k(GLFW.GLFW_KEY_LEFT_SUPER,   21, 1);
        k(GLFW.GLFW_KEY_LEFT_ALT,     20, 1);
        k(GLFW.GLFW_KEY_SPACE,        13, 1, 6, 1);
        k(GLFW.GLFW_KEY_RIGHT_ALT,    12, 1);
        k(GLFW.GLFW_KEY_RIGHT_SUPER,  11, 1);
        k(GLFW.GLFW_KEY_MENU,         10, 1);
        k(GLFW.GLFW_KEY_RIGHT_CONTROL,9, 1);

        // Nav cluster
        k(GLFW.GLFW_KEY_INSERT,    6,  4);
        k(GLFW.GLFW_KEY_HOME,      5,  4);
        k(GLFW.GLFW_KEY_PAGE_UP,   4,  4);
        k(GLFW.GLFW_KEY_DELETE,    6,  4);
        k(GLFW.GLFW_KEY_END,       5,  4);
        k(GLFW.GLFW_KEY_PAGE_DOWN, 4,  4);
        k(GLFW.GLFW_KEY_UP,        5,  2);
        k(GLFW.GLFW_KEY_LEFT,      6,  1);
        k(GLFW.GLFW_KEY_DOWN,      5,  1);
        k(GLFW.GLFW_KEY_RIGHT,     4,  1);

        // Numpad
        k(GLFW.GLFW_KEY_NUM_LOCK,  2,  4);
        k(GLFW.GLFW_KEY_KP_DIVIDE, 2,  4);
        k(GLFW.GLFW_KEY_KP_MULTIPLY, 1, 4);
        k(GLFW.GLFW_KEY_KP_SUBTRACT, 1, 4);
        k(GLFW.GLFW_KEY_KP_7,      2,  3);
        k(GLFW.GLFW_KEY_KP_8,      2,  3);
        k(GLFW.GLFW_KEY_KP_9,      1,  3);
        k(GLFW.GLFW_KEY_KP_ADD,    1,  3);
        k(GLFW.GLFW_KEY_KP_4,      2,  3);
        k(GLFW.GLFW_KEY_KP_5,      2,  3);
        k(GLFW.GLFW_KEY_KP_6,      1,  3);
        k(GLFW.GLFW_KEY_KP_1,      2,  2);
        k(GLFW.GLFW_KEY_KP_2,      2,  2);
        k(GLFW.GLFW_KEY_KP_3,      1,  2);
        k(GLFW.GLFW_KEY_KP_ENTER,  1,  1);
        k(GLFW.GLFW_KEY_KP_0,      2,  1);
        k(GLFW.GLFW_KEY_KP_DECIMAL,2,  1);
    }
}
