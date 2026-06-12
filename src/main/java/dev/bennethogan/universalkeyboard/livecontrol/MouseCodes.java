package dev.bennethogan.universalkeyboard.livecontrol;


// More synthetic keycodes for mouse input

public final class MouseCodes {

    private MouseCodes() {}

    public static final int BASE  = 6000;
    public static final int RANGE = 100;

    private static final int BUTTON_OFF = 0;
    public  static final int MAX_BUTTONS = 8;
    private static final int AXIS_OFF   = 20;
    private static final int SCROLL_OFF = 30;

    public static final int AXIS_X_POS = BASE + AXIS_OFF;     // 6020 move right
    public static final int AXIS_X_NEG = BASE + AXIS_OFF + 1; // 6021 move left
    public static final int AXIS_Y_POS = BASE + AXIS_OFF + 2; // 6022 move down
    public static final int AXIS_Y_NEG = BASE + AXIS_OFF + 3; // 6023 move up

    public static final int SCROLL_UP   = BASE + SCROLL_OFF;     // 6030
    public static final int SCROLL_DOWN = BASE + SCROLL_OFF + 1; // 6031

    // encoding
    public static int button(int i) { return BASE + BUTTON_OFF + i; }

    // decoding
    public static boolean isMouseCode(int code) { return code >= BASE && code < BASE + RANGE; }

    public static boolean isButton(int code) {
        if (!isMouseCode(code)) return false;
        int o = code - BASE;
        return o >= BUTTON_OFF && o < BUTTON_OFF + 16;
    }

    public static boolean isAxis(int code) {
        if (!isMouseCode(code)) return false;
        int o = code - BASE;
        return o >= AXIS_OFF && o < AXIS_OFF + 4;
    }

    public static boolean isScroll(int code) {
        return code == SCROLL_UP || code == SCROLL_DOWN;
    }

    // scaled magnitude for the movement axis
    public static boolean isAnalog(int code) { return isAxis(code); }

    public static int buttonIndex(int code) { return code - BASE - BUTTON_OFF; }

    // axis 0 is x, 1 is y
    public static int axisOf(int code) { return ((code - BASE - AXIS_OFF) >= 2) ? 1 : 0; }

    public static boolean axisPositive(int code) { return ((code - BASE - AXIS_OFF) & 1) == 0; }

    // display nme
    public static String name(int code) {
        if (!isMouseCode(code)) return "?" + code;
        if (code == SCROLL_UP)   return "🖱Wheel↑";
        if (code == SCROLL_DOWN) return "🖱Wheel↓";
        if (isAxis(code)) {
            return switch (code) {
                case AXIS_X_POS -> "🖱→";
                case AXIS_X_NEG -> "🖱←";
                case AXIS_Y_POS -> "🖱↓";
                case AXIS_Y_NEG -> "🖱↑";
                default -> "🖱?";
            };
        }
        return "M" + (buttonIndex(code) + 1);
    }
}
