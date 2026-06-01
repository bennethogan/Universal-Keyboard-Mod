package dev.bennethogan.universalkeyboard.livecontrol;


//Assigning gamepad inputs to arbitrary keycodes so they can be read by live controller

public final class GamepadCodes {

    private GamepadCodes() {}

    public static final int BASE = 2000;

    // 15 standard GLFW gamepad buttons
    public static final int BUTTON_BASE = 2000;
    public static final int BUTTON_COUNT = 15;

    // Triggers as digital buttons (past threshold)
    public static final int TRIGGER_BASE = 2020;
    public static final int TRIGGER_LT   = 2020;
    public static final int TRIGGER_RT   = 2021;

    // Stick directions as digital buttons
    public static final int STICK_BASE  = 2030;
    public static final int LS_RIGHT = 2030;
    public static final int LS_LEFT  = 2031;
    public static final int LS_DOWN  = 2032;
    public static final int LS_UP    = 2033;
    public static final int RS_RIGHT = 2034;
    public static final int RS_LEFT  = 2035;
    public static final int RS_DOWN  = 2036;
    public static final int RS_UP    = 2037;

    public static boolean isGamepadCode(int code) {
        return code >= BASE;
    }

    /** True for analog inputs (sticks and triggers) whose deflection can scale output. */
    public static boolean isAnalogCode(int code) {
        return code == TRIGGER_LT || code == TRIGGER_RT
                || (code >= STICK_BASE && code <= RS_UP);
    }

    public static int buttonCode(int buttonIndex) {
        return BUTTON_BASE + buttonIndex;
    }


    public static String name(int code) {
        if (code >= BUTTON_BASE && code < BUTTON_BASE + BUTTON_COUNT) {
            return "🎮" + switch (code - BUTTON_BASE) {
                case 0  -> "A";
                case 1  -> "B";
                case 2  -> "X";
                case 3  -> "Y";
                case 4  -> "LB";
                case 5  -> "RB";
                case 6  -> "Back";
                case 7  -> "Start";
                case 8  -> "Guide";
                case 9  -> "LStick";
                case 10 -> "RStick";
                case 11 -> "D↑";
                case 12 -> "D→";
                case 13 -> "D↓";
                case 14 -> "D←";
                default -> "Btn" + (code - BUTTON_BASE);
            };
        }
        return switch (code) {
            case TRIGGER_LT -> "🎮LT";
            case TRIGGER_RT -> "🎮RT";
            case LS_RIGHT   -> "🎮LS→";
            case LS_LEFT    -> "🎮LS←";
            case LS_DOWN    -> "🎮LS↓";
            case LS_UP      -> "🎮LS↑";
            case RS_RIGHT   -> "🎮RS→";
            case RS_LEFT    -> "🎮RS←";
            case RS_DOWN    -> "🎮RS↓";
            case RS_UP      -> "🎮RS↑";
            default         -> "🎮?" + code;
        };
    }
}
