package dev.bennethogan.universalkeyboard.client.render;

import java.util.HashSet;
import java.util.Set;


// client side set of currently-held keys for keyboard animation rendering
public final class PressedKeys {

    private PressedKeys() {}

    private static final int TOP_W = 24, TOP_H = 8;

    private static final Set<Integer> pressed = new HashSet<>();

    public static void press(int glfwKey)   { pressed.add(glfwKey); }
    public static void release(int glfwKey) { pressed.remove(glfwKey); }
    public static void clear()              { pressed.clear(); }

    public static boolean[] texelGrid() {
        if (pressed.isEmpty()) return null;
        boolean[] g = new boolean[TOP_W * TOP_H];
        boolean any = false;
        for (int key : pressed) {
            int[] t = KeyTexels.get(key);
            if (t != null) { g[t[1] * TOP_W + t[0]] = true; any = true; }
        }
        return any ? g : null;
    }
}
