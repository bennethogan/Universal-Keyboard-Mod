package dev.bennethogan.universalkeyboard.livecontrol;

import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
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
        OVERDRIVE,       // 4 — multiplies output of other active bindings (RS/Thr/Var)
        RPM_CONTROL,     // 5 — sets motor RPM (electric_motor / rotation_speed_controller)
        CAM,             // 6 — Vista compat
        GUN              // 7 — Supplementaries' cannon compat
    }


    // CAM/GUN mode selectable labels
    public enum CamDir {
        UP, DOWN, LEFT, RIGHT, ZOOM_IN, ZOOM_OUT, TOGGLE;

        public String label() {
            return switch (this) {
                case UP -> "▲";
                case DOWN -> "▼";
                case LEFT -> "◄";
                case RIGHT -> "►";
                case ZOOM_IN -> "+";
                case ZOOM_OUT -> "-";
                case TOGGLE -> "⏻";
            };
        }
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
    /** 0 = local RS side; 1-20 = Create redstone link slot L1..L20 */
    public int       rsLinkIdx       = 0;
    /** 0 = not a wireless freq; 1-100 = wireless frequency slot W1..W100 */
    public int       wirelessFreqIdx = 0;
    /** Used when rsLinkIdx == 0 */
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

    // HLD inversion — output is active (full value) when key is NOT held
    public boolean inverted = false;

    // VARIABLE
    // Target sequencer variable, V1-16 options
    public int varIndex   = 0;
    /** HLD/TGL "on" value, 1-100; the variable switches between 0 and this */
    public int varOnValue = 100;

    // OVERDRIVE
    public static final double[] OVERDRIVE_VALUES = {1.5, 2.0, 3.0, 5.0, 10.0};
    public double overdriveMultiplier = 2.0;
    /** Comma-separated 1-based slot numbers excluded from this OD binding's effect. */
    public String odExcludes = "";

    // RPM_CONTROL -256 to 256
    public int rpmTarget = 0;

    // CAM
    public CamDir camDir = CamDir.UP;

    // ── Serialization ────────────────────────────────────────────────────────

    public void saveToTag(CompoundTag tag) {
        tag.putInt("keyCode",        keyCode);
        tag.putInt("actionType",     actionType.ordinal());
        tag.putInt("mode",           mode.ordinal());
        // NBT keys keep the legacy names ("wirelessIdx" = RS link, "linkIdx" = wireless
        // frequency) so existing worlds and schematics load unchanged.
        tag.putInt("wirelessIdx",    rsLinkIdx);
        tag.putInt("linkIdx",        wirelessFreqIdx);
        tag.putInt("rsSide",         rsSide.ordinal());
        tag.putInt("signalStrength", signalStrength);
        tag.putInt("channel",        channel);
        tag.putDouble("powerLevel",  powerLevel);
        tag.putDouble("vectorX",     vectorX);
        tag.putDouble("vectorY",     vectorY);
        tag.putBoolean("incPlus",    incPlus);
        if (inverted) tag.putBoolean("inverted", true);
        tag.putInt("varIndex",       varIndex);
        tag.putInt("varOnValue",     varOnValue);
        tag.putDouble("overdriveMultiplier", overdriveMultiplier);
        tag.putString("odExcludes",          odExcludes == null ? "" : odExcludes);
        tag.putInt("rpmTarget", rpmTarget);
        tag.putInt("camDir", camDir.ordinal());
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

        b.rsLinkIdx = Math.max(0, Math.min(LinkedKeyboardBlockEntity.MAX_RSLINKS, tag.getInt("wirelessIdx")));
        b.wirelessFreqIdx = Math.max(0, Math.min(LinkedKeyboardBlockEntity.MAX_WIRELESS_FREQS,
                tag.contains("linkIdx") ? tag.getInt("linkIdx") : 0));

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
        b.inverted   = tag.contains("inverted") && tag.getBoolean("inverted");

        b.varIndex   = Math.max(0, Math.min(15, tag.getInt("varIndex")));
        b.varOnValue = tag.contains("varOnValue") ? Math.max(1, Math.min(100, tag.getInt("varOnValue"))) : 100;

        b.overdriveMultiplier = tag.contains("overdriveMultiplier") ? tag.getDouble("overdriveMultiplier") : 2.0;
        b.odExcludes          = tag.contains("odExcludes") ? tag.getString("odExcludes") : "";
        b.rpmTarget           = tag.contains("rpmTarget") ? Math.max(-256, Math.min(256, tag.getInt("rpmTarget"))) : 0;
        b.camDir              = camDirFromOrdinal(tag.getInt("camDir"));

        return b;
    }

    private static CamDir camDirFromOrdinal(int ord) {
        CamDir[] v = CamDir.values();
        return (ord >= 0 && ord < v.length) ? v[ord] : CamDir.UP;
    }

    // ── Network encoding ─────────────────────────────────────────────────────

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(keyCode);
        buf.writeByte(actionType.ordinal());
        buf.writeByte(mode.ordinal());
        buf.writeInt(rsLinkIdx);
        buf.writeShort(Math.max(0, Math.min(100, wirelessFreqIdx)));
        buf.writeByte(rsSide.ordinal());
        buf.writeByte(Math.max(0, Math.min(15, signalStrength)));
        buf.writeByte(Math.max(1, Math.min(16, channel)));
        buf.writeDouble(powerLevel);
        buf.writeDouble(vectorX);
        buf.writeDouble(vectorY);
        buf.writeBoolean(incPlus);
        buf.writeBoolean(inverted);
        buf.writeByte(Math.max(0, Math.min(15, varIndex)));
        buf.writeByte(Math.max(1, Math.min(100, varOnValue)));
        buf.writeDouble(overdriveMultiplier);
        buf.writeUtf(odExcludes == null ? "" : odExcludes, 513);
        buf.writeShort(Math.max(-256, Math.min(256, rpmTarget)));
        buf.writeByte(camDir.ordinal());
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

        b.rsLinkIdx = buf.readInt();
        b.wirelessFreqIdx     = buf.readShort() & 0xFFFF;

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
        b.inverted   = buf.readBoolean();
        b.varIndex   = buf.readByte() & 0xFF;
        b.varOnValue = buf.readByte() & 0xFF;
        if (b.varOnValue < 1) b.varOnValue = 1;

        b.overdriveMultiplier = buf.readDouble();
        b.odExcludes          = buf.readUtf(513);
        b.rpmTarget           = buf.isReadable(2) ? Math.max(-256, Math.min(256, (int) buf.readShort())) : 0;
        b.camDir              = buf.isReadable() ? camDirFromOrdinal(buf.readByte() & 0xFF) : CamDir.UP;

        return b;
    }

    // ── Display helper ───────────────────────────────────────────────────────

    public static String keyName(int keyCode) {
        if (keyCode < 0) return "(none)";
        if (MouseCodes.isMouseCode(keyCode)) return MouseCodes.name(keyCode);
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
