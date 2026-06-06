package dev.bennethogan.universalkeyboard.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.bennethogan.universalkeyboard.config.ModConfig;


 // Resolve two configurable Control Wheel turn keys into GLFW codes

public final class ControlWheelKeys {

    private ControlWheelKeys() {}

    private static String leftName  = null;
    private static String rightName = null;
    private static int    leftCode  = InputConstants.UNKNOWN.getValue();
    private static int    rightCode = InputConstants.UNKNOWN.getValue();

    public static int leftKey() {
        String cfg = ModConfig.CLIENT.controlWheelLeftKey.get();
        if (!cfg.equals(leftName)) { leftName = cfg; leftCode = resolve(cfg); }
        return leftCode;
    }

    public static int rightKey() {
        String cfg = ModConfig.CLIENT.controlWheelRightKey.get();
        if (!cfg.equals(rightName)) { rightName = cfg; rightCode = resolve(cfg); }
        return rightCode;
    }

    private static int resolve(String name) {
        if (name == null || name.isBlank()) return InputConstants.UNKNOWN.getValue();
        String n = name.trim().toLowerCase();
        if (!n.startsWith("key.")) n = "key.keyboard." + n;
        try {
            return InputConstants.getKey(n).getValue();
        } catch (Exception e) {
            return InputConstants.UNKNOWN.getValue();
        }
    }
}
