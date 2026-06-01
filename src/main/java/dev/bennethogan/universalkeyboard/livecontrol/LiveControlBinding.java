package dev.bennethogan.universalkeyboard.livecontrol;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.lwjgl.glfw.GLFW;

public class LiveControlBinding {

    // ── Nested enums ─────────────────────────────────────────────────────────

    public enum ActionType {
        REDSTONE,        // 0
        THRUSTER_POWER,  // 1
        THRUSTER_VECTOR, // 2
        VARIABLE,        // 3 — sets a sequencer variable
        OVERDRIVE        // 4 — multiplies output of other active bindings (RS/Thr/Var)
    }

    /** Activation mode for a binding. VEC does not support INC. */
    public enum Mode {
        HLD, // hold — active while key is held
        TGL, // toggle — flips on/off each press
        INC  // increment — each press steps the counter ±1 (auto-repeats when held)
    }

    // ── Fields ───────────────────────────────────────────────────────────────

    /** GLFW key code; -1 = unbound */
    public int keyCode = -1;

    public ActionType actionType = ActionType.REDSTONE;
    public Mode       mode       = Mode.HLD;

    // REDSTONE fields
    /** 0 = local RS side; 1-12 = wireless slot W1..W12 */
    public int       wirelessIdx    = 0;
    /** 0 = not a link channel; 1-100 = link freq slot L1..L100 */
    public int       linkIdx        = 0;
    /** Used when wirelessIdx == 0 */
    public Direction rsSide         = Direction.NORTH;
    /** 0-15 */
    public int       signalStrength = 15;

    // THRUSTER shared
    /** 1-16 */
    public int channel = 1;

    // THRUSTER_POWER
    /** 0.0-1.0 */
    public double powerLevel = 1.0;

    // THRUSTER_VECTOR
    public double vectorX = 0.0;
    public double vectorY = 0.0;

    // INC mode — true = ++, false = --
    public boolean incPlus = true;

    // VARIABLE
    /** Target sequencer variable, 0-15 → V1..V16 */
    public int varIndex   = 0;
    /** HLD/TGL "on" value, 1-100; the variable switches between 0 and this */
    public int varOnValue = 100;

    // OVERDRIVE
    public static final double[] OVERDRIVE_VALUES = {1.5, 2.0, 3.0, 5.0, 10.0};
    public double overdriveMultiplier = 2.0;
    /** Comma-separated 1-based slot numbers excluded from this OD binding's effect. */
    public String odExcludes = "";

    // ── Serialization ────────────────────────────────────────────────────────

    public void saveToTag(CompoundTag tag) {
        tag.putInt("keyCode",        keyCode);
        tag.putInt("actionType",     actionType.ordinal());
        tag.putInt("mode",           mode.ordinal());
        tag.putInt("wirelessIdx",    wirelessIdx);
        tag.putInt("linkIdx",        linkIdx);
        tag.putInt("rsSide",         rsSide.ordinal());
        tag.putInt("signalStrength", signalStrength);
        tag.putInt("channel",        channel);
        tag.putDouble("powerLevel",  powerLevel);
        tag.putDouble("vectorX",     vectorX);
        tag.putDouble("vectorY",     vectorY);
        tag.putBoolean("incPlus",    incPlus);
        tag.putInt("varIndex",       varIndex);
        tag.putInt("varOnValue",     varOnValue);
        tag.putDouble("overdriveMultiplier", overdriveMultiplier);
        tag.putString("odExcludes",          odExcludes == null ? "" : odExcludes);
    }

    public static LiveControlBinding fromTag(CompoundTag tag) {
        LiveControlBinding b = new LiveControlBinding();

        b.keyCode = tag.getInt("keyCode");

        int atOrd = tag.getInt("actionType");
        ActionType[] atValues = ActionType.values();
        if (atOrd < 0 || atOrd >= atValues.length) atOrd = 0;
        b.actionType = atValues[atOrd];

        // Mode: prefer "mode" key; fall back to old "toggle" boolean for old saves.
        Mode[] mValues = Mode.values();
        if (tag.contains("mode")) {
            int mOrd = tag.getInt("mode");
            b.mode = (mOrd >= 0 && mOrd < mValues.length) ? mValues[mOrd] : Mode.HLD;
        } else {
            b.mode = tag.getBoolean("toggle") ? Mode.TGL : Mode.HLD;
        }

        b.wirelessIdx = Math.max(0, Math.min(12, tag.getInt("wirelessIdx")));
        b.linkIdx     = Math.max(0, Math.min(100, tag.contains("linkIdx") ? tag.getInt("linkIdx") : 0));

        int sideOrd = tag.getInt("rsSide");
        Direction[] dirs = Direction.values();
        b.rsSide = (sideOrd >= 0 && sideOrd < dirs.length) ? dirs[sideOrd] : Direction.NORTH;

        b.signalStrength = Math.max(0, Math.min(15, tag.getInt("signalStrength")));

        b.channel = Math.max(1, Math.min(16, tag.getInt("channel")));
        if (b.channel == 0) b.channel = 1;

        b.powerLevel = Math.max(0.0, Math.min(1.0, tag.getDouble("powerLevel")));
        b.vectorX    = tag.getDouble("vectorX");
        b.vectorY    = tag.getDouble("vectorY");
        b.incPlus    = !tag.contains("incPlus") || tag.getBoolean("incPlus");

        b.varIndex   = Math.max(0, Math.min(15, tag.getInt("varIndex")));
        b.varOnValue = tag.contains("varOnValue") ? Math.max(1, Math.min(100, tag.getInt("varOnValue"))) : 100;

        b.overdriveMultiplier = tag.contains("overdriveMultiplier") ? tag.getDouble("overdriveMultiplier") : 2.0;
        b.odExcludes          = tag.contains("odExcludes") ? tag.getString("odExcludes") : "";

        return b;
    }

    // ── Network encoding ─────────────────────────────────────────────────────

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(keyCode);
        buf.writeByte(actionType.ordinal());
        buf.writeByte(mode.ordinal());
        buf.writeInt(wirelessIdx);
        buf.writeShort(Math.max(0, Math.min(100, linkIdx)));
        buf.writeByte(rsSide.ordinal());
        buf.writeByte(Math.max(0, Math.min(15, signalStrength)));
        buf.writeByte(Math.max(1, Math.min(16, channel)));
        buf.writeDouble(powerLevel);
        buf.writeDouble(vectorX);
        buf.writeDouble(vectorY);
        buf.writeBoolean(incPlus);
        buf.writeByte(Math.max(0, Math.min(15, varIndex)));
        buf.writeByte(Math.max(1, Math.min(100, varOnValue)));
        buf.writeDouble(overdriveMultiplier);
        buf.writeUtf(odExcludes == null ? "" : odExcludes, 256);
    }

    public static LiveControlBinding decode(FriendlyByteBuf buf) {
        LiveControlBinding b = new LiveControlBinding();

        b.keyCode = buf.readInt();

        int atOrd = buf.readByte() & 0xFF;
        ActionType[] atValues = ActionType.values();
        if (atOrd >= atValues.length) atOrd = 0;
        b.actionType = atValues[atOrd];

        int mOrd = buf.readByte() & 0xFF;
        Mode[] mValues = Mode.values();
        b.mode = (mOrd < mValues.length) ? mValues[mOrd] : Mode.HLD;

        b.wirelessIdx = buf.readInt();
        b.linkIdx     = buf.readShort() & 0xFFFF;

        int sideOrd = buf.readByte() & 0xFF;
        Direction[] dirs = Direction.values();
        b.rsSide = (sideOrd < dirs.length) ? dirs[sideOrd] : Direction.NORTH;

        b.signalStrength = buf.readByte() & 0xFF;
        b.channel        = buf.readByte() & 0xFF;
        if (b.channel == 0) b.channel = 1;

        b.powerLevel = buf.readDouble();
        b.vectorX    = buf.readDouble();
        b.vectorY    = buf.readDouble();
        b.incPlus    = buf.readBoolean();
        b.varIndex   = buf.readByte() & 0xFF;
        b.varOnValue = buf.readByte() & 0xFF;
        if (b.varOnValue < 1) b.varOnValue = 1;

        b.overdriveMultiplier = buf.readDouble();
        b.odExcludes          = buf.readUtf(256);

        return b;
    }

    // ── Display helper ───────────────────────────────────────────────────────

    public static String keyName(int keyCode) {
        if (keyCode < 0) return "(none)";
        if (GamepadCodes.isGamepadCode(keyCode)) return GamepadCodes.name(keyCode);
        String glfwName = GLFW.glfwGetKeyName(keyCode, 0);
        if (glfwName != null && !glfwName.isEmpty()) return glfwName.toUpperCase();
        return switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE         -> "SPACE";
            case GLFW.GLFW_KEY_ESCAPE        -> "ESC";
            case GLFW.GLFW_KEY_ENTER         -> "ENTER";
            case GLFW.GLFW_KEY_TAB           -> "TAB";
            case GLFW.GLFW_KEY_BACKSPACE     -> "BKSP";
            case GLFW.GLFW_KEY_UP            -> "UP";
            case GLFW.GLFW_KEY_DOWN          -> "DOWN";
            case GLFW.GLFW_KEY_LEFT          -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT         -> "RIGHT";
            case GLFW.GLFW_KEY_F1            -> "F1";
            case GLFW.GLFW_KEY_F2            -> "F2";
            case GLFW.GLFW_KEY_F3            -> "F3";
            case GLFW.GLFW_KEY_F4            -> "F4";
            case GLFW.GLFW_KEY_F5            -> "F5";
            case GLFW.GLFW_KEY_F6            -> "F6";
            case GLFW.GLFW_KEY_F7            -> "F7";
            case GLFW.GLFW_KEY_F8            -> "F8";
            case GLFW.GLFW_KEY_F9            -> "F9";
            case GLFW.GLFW_KEY_F10           -> "F10";
            case GLFW.GLFW_KEY_F11           -> "F11";
            case GLFW.GLFW_KEY_F12           -> "F12";
            case GLFW.GLFW_KEY_LEFT_SHIFT    -> "LSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL  -> "LCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT      -> "LALT";
            default                          -> "K:" + keyCode;
        };
    }
}
