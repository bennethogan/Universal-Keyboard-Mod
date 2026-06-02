package dev.bennethogan.universalkeyboard.livecontrol;



//Assigning gamepad inputs to arbitrary keycodes so they can be read by live controller
public final class GamepadCodes {

    private GamepadCodes() {}

    public static final int BASE        = 2000;
    public static final int STRIDE      = 200;
    public static final int MAX_DEVICES = 16;

    // ── Within-device offset ranges ───────────────────────────────────────────────
    // Basic (standard gamepad mapping)
    private static final int BASIC_BUTTON_OFF = 0;   // 0..14
    public  static final int BUTTON_COUNT     = 15;
    private static final int TRIGGER_LT_OFF   = 20;
    private static final int TRIGGER_RT_OFF   = 21;
    private static final int STICK_OFF        = 30;  // 30..37 (8 directions)
    // Advanced (raw joystick)
    private static final int RAW_BUTTON_OFF   = 100; // 100..131 (up to 32 buttons)
    private static final int RAW_AXIS_POS_OFF = 140; // 140..155 (up to 16 axes, + direction)
    private static final int RAW_AXIS_NEG_OFF = 160; // 160..175 (up to 16 axes, - direction)
    private static final int RAW_HAT_OFF      = 180; // 180..195 (up to 4 hats × 4 directions)

    // ── Device-0 aliases (original layout, kept for readability/back-compat) ──────
    public static final int BUTTON_BASE = BASE;
    public static final int TRIGGER_LT  = BASE + TRIGGER_LT_OFF; // 2020
    public static final int TRIGGER_RT  = BASE + TRIGGER_RT_OFF; // 2021
    public static final int LS_RIGHT = BASE + STICK_OFF;     // 2030
    public static final int LS_LEFT  = BASE + STICK_OFF + 1; // 2031
    public static final int LS_DOWN  = BASE + STICK_OFF + 2; // 2032
    public static final int LS_UP    = BASE + STICK_OFF + 3; // 2033
    public static final int RS_RIGHT = BASE + STICK_OFF + 4; // 2034
    public static final int RS_LEFT  = BASE + STICK_OFF + 5; // 2035
    public static final int RS_DOWN  = BASE + STICK_OFF + 6; // 2036
    public static final int RS_UP    = BASE + STICK_OFF + 7; // 2037

    // ── Decoding ──────────────────────────────────────────────────────────────────
    public static boolean isGamepadCode(int code) { return code >= BASE && code < BASE + STRIDE * MAX_DEVICES; }
    public static int deviceOf(int code) { return (code - BASE) / STRIDE; }
    public static int offsetOf(int code) { return (code - BASE) % STRIDE; }


    public static boolean isAnalogCode(int code) {
        if (!isGamepadCode(code)) return false;
        int o = offsetOf(code);
        return o == TRIGGER_LT_OFF || o == TRIGGER_RT_OFF
                || (o >= STICK_OFF && o < STICK_OFF + 8)
                || (o >= RAW_AXIS_POS_OFF && o < RAW_AXIS_POS_OFF + 16)
                || (o >= RAW_AXIS_NEG_OFF && o < RAW_AXIS_NEG_OFF + 16);
    }

    // ── Encoding helpers ──────────────────────────────────────────────────────────
    private static int dev(int device) { return BASE + device * STRIDE; }

    public static int basicButton(int device, int i) { return dev(device) + BASIC_BUTTON_OFF + i; }
    public static int triggerLT(int device)          { return dev(device) + TRIGGER_LT_OFF; }
    public static int triggerRT(int device)          { return dev(device) + TRIGGER_RT_OFF; }
    public static int stickCode(int device, int k)   { return dev(device) + STICK_OFF + k; } // k 0..7
    public static int rawButton(int device, int i)   { return dev(device) + RAW_BUTTON_OFF + i; }
    public static int rawAxisPos(int device, int a)  { return dev(device) + RAW_AXIS_POS_OFF + a; }
    public static int rawAxisNeg(int device, int a)  { return dev(device) + RAW_AXIS_NEG_OFF + a; }
    public static int rawHat(int device, int h, int dir) { return dev(device) + RAW_HAT_OFF + h * 4 + dir; }

    // Decoders for analog magnitude lookups
    public static boolean isBasicTrigger(int code) { int o = offsetOf(code); return o == TRIGGER_LT_OFF || o == TRIGGER_RT_OFF; }
    public static boolean isBasicStick(int code)   { int o = offsetOf(code); return o >= STICK_OFF && o < STICK_OFF + 8; }
    public static boolean isRawAxisPos(int code)   { int o = offsetOf(code); return o >= RAW_AXIS_POS_OFF && o < RAW_AXIS_POS_OFF + 16; }
    public static boolean isRawAxisNeg(int code)   { int o = offsetOf(code); return o >= RAW_AXIS_NEG_OFF && o < RAW_AXIS_NEG_OFF + 16; }


    public static int triggerAxis(int code) { return offsetOf(code) == TRIGGER_LT_OFF ? 4 : 5; }

    public static int stickAxis(int code) { return (offsetOf(code) - STICK_OFF) / 2; }

    public static boolean stickPositive(int code) { return ((offsetOf(code) - STICK_OFF) & 1) == 0; }

    public static int rawAxisIndex(int code) {
        int o = offsetOf(code);
        return (o >= RAW_AXIS_POS_OFF && o < RAW_AXIS_POS_OFF + 16) ? o - RAW_AXIS_POS_OFF : o - RAW_AXIS_NEG_OFF;
    }

    // ── Display names ─────────────────────────────────────────────────────────────
    public static String name(int code) {
        if (!isGamepadCode(code)) return "?" + code;
        int device = deviceOf(code);
        int o      = offsetOf(code);
        String prefix = device > 0 ? (device + 1) + "·" : ""; // "2·" for the second controller

        // Basic gamepad inputs
        if (o >= BASIC_BUTTON_OFF && o < BASIC_BUTTON_OFF + BUTTON_COUNT)
            return "🎮" + prefix + basicButtonName(o - BASIC_BUTTON_OFF);
        if (o == TRIGGER_LT_OFF) return "🎮" + prefix + "LT";
        if (o == TRIGGER_RT_OFF) return "🎮" + prefix + "RT";
        if (o >= STICK_OFF && o < STICK_OFF + 8)
            return "🎮" + prefix + stickName(o - STICK_OFF);

        // Advanced raw inputs (joystick icon)
        if (o >= RAW_BUTTON_OFF && o < RAW_BUTTON_OFF + 32)
            return "🕹" + prefix + "B" + (o - RAW_BUTTON_OFF);
        if (o >= RAW_AXIS_POS_OFF && o < RAW_AXIS_POS_OFF + 16)
            return "🕹" + prefix + "A" + (o - RAW_AXIS_POS_OFF) + "+";
        if (o >= RAW_AXIS_NEG_OFF && o < RAW_AXIS_NEG_OFF + 16)
            return "🕹" + prefix + "A" + (o - RAW_AXIS_NEG_OFF) + "-";
        if (o >= RAW_HAT_OFF && o < RAW_HAT_OFF + 16) {
            int h = (o - RAW_HAT_OFF) / 4, dir = (o - RAW_HAT_OFF) % 4;
            return "🕹" + prefix + "H" + h + hatArrow(dir);
        }
        return "🎮" + prefix + "?" + o;
    }

    private static String basicButtonName(int i) {
        return switch (i) {
            case 0  -> "A";   case 1  -> "B";   case 2  -> "X";   case 3  -> "Y";
            case 4  -> "LB";  case 5  -> "RB";  case 6  -> "Back"; case 7 -> "Start";
            case 8  -> "Guide"; case 9 -> "LStick"; case 10 -> "RStick";
            case 11 -> "D↑"; case 12 -> "D→"; case 13 -> "D↓"; case 14 -> "D←";
            default -> "Btn" + i;
        };
    }

    private static String stickName(int k) {
        return switch (k) {
            case 0 -> "LS→"; case 1 -> "LS←"; case 2 -> "LS↓"; case 3 -> "LS↑";
            case 4 -> "RS→"; case 5 -> "RS←"; case 6 -> "RS↓"; case 7 -> "RS↑";
            default -> "S?";
        };
    }

    private static String hatArrow(int dir) {
        return switch (dir) {
            case 0 -> "↑"; case 1 -> "→"; case 2 -> "↓"; case 3 -> "←";
            default -> "?";
        };
    }
}
