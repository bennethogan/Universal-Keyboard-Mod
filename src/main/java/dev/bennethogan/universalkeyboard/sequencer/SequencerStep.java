package dev.bennethogan.universalkeyboard.sequencer;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public class SequencerStep {

    // ── Step types ────────────────────────────────────────────────────────────

    public enum Type {
        SET_VALUE("Set Value"),
        SET_REDSTONE("Set RS Out"),
        TYPE_TEXT("Type Text"),
        IF("If / Skip"),
        CONDITION("Wait For"),
        DELAY("Delay"),
        JUMP("Jump To"),
        MATH("Math / Var"),
        CYCLE("Loop"),
        END("End"),
        TYPE_VARIABLE("Type Var");

        public final String label;
        Type(String label) { this.label = label; }

        /** Minecraft formatting code for this step type (without the §). */
        public String colorCode() {
            return switch (this) {
                case SET_VALUE      -> "§b"; // cyan      — peripheral setter
                case SET_REDSTONE   -> "§c"; // red       — redstone output
                case TYPE_TEXT,
                     TYPE_VARIABLE  -> "§d"; // pink      — keyboard typing
                case IF             -> "§e"; // yellow    — branch
                case CONDITION      -> "§6"; // gold      — wait/condition
                case DELAY          -> "§7"; // gray      — time
                case JUMP           -> "§f"; // white     — goto
                case MATH           -> "§a"; // green     — math/var
                case CYCLE          -> "§5"; // purple    — loop
                case END            -> "§8"; // dark gray — end
            };
        }

        /** Colored label for use in buttons and dropdowns. */
        public String coloredLabel() { return colorCode() + label; }
    }

    // ── Condition input sources ───────────────────────────────────────────────

    public enum ConditionSource {
        REDSTONE_NORTH("RS North", Direction.NORTH),
        REDSTONE_SOUTH("RS South", Direction.SOUTH),
        REDSTONE_EAST( "RS East",  Direction.EAST),
        REDSTONE_WEST( "RS West",  Direction.WEST),
        PERIPHERAL(    "Periph",   null);

        public final String    label;
        public final Direction direction; // null means read from peripheral getter

        ConditionSource(String label, Direction direction) {
            this.label     = label;
            this.direction = direction;
        }
    }

    // Redstone input side names used as special IF getter values (shared client/server)
    public static final String[]    RS_INPUT_GETTER_NAMES = {"RS North", "RS South", "RS East", "RS West"};
    public static final Direction[] RS_INPUT_GETTER_DIRS  = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    // ── Fields ────────────────────────────────────────────────────────────────

    public Type            type;

    // SET_VALUE
    public String setMethod   = "";
    public String setValueStr = "0";

    // CONDITION
    public ConditionSource conditionSource       = ConditionSource.REDSTONE_NORTH;
    public String          conditionGetter       = "";
    public String          conditionOp           = ">";   // ">", "<", "="
    public String          conditionThresholdStr = "0";

    // SET_REDSTONE
    public Direction redstoneOutDir       = Direction.NORTH;
    public String    redstoneOutSignalStr = "0";
    // 0 = use redstoneOutDir (a side); 1..12 = wireless slot W1..W12
    public int       wirelessOutIdx       = 0;

    // TYPE_TEXT
    public String  typeTextStr   = "";
    public boolean typeTextEnter = true; // press Enter after typing

    // IF
    public String ifGetter    = "";
    public String ifOp        = ">";
    public String ifValueStr  = "0";
    public int    ifSkipCount = 1;     // steps to skip when condition is TRUE (skip mode)
    public boolean ifGoTo     = false; // false=skip mode, true=jump-to mode (uses jumpTarget)

    // DELAY
    public String delaySecondsStr = "1.0";

    // JUMP
    public int jumpTarget = 1;

    // MATH  (sources: literal number, "V1"-"V8", "RS:N/S/E/W", or getter name)
    public String  mathDest    = "V1";  // destination variable V1-V8
    public String  mathA       = "";    // operand A source
    public int     mathACh     = 1;     // channel if A is a getter
    public boolean mathAManual = false; // false=dropdown, true=text-box input
    public String  mathOp      = "+";   // operator
    public String  mathB       = "";    // operand B source
    public int     mathBCh     = 1;     // channel if B is a getter
    public boolean mathBManual = false;

    // Channel (1-16) used by peripheral steps (SET_VALUE, TYPE_TEXT, IF, CONDITION)
    public int channel = 1;

    // ── Construction ──────────────────────────────────────────────────────────

    public SequencerStep(Type type) { this.type = type; }

    public static SequencerStep ofType(Type type) {
        return new SequencerStep(type);
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type",                  type.name());
        tag.putString("setMethod",             setMethod);
        tag.putString("setValueStr",           setValueStr);
        tag.putString("redstoneOutDir",        redstoneOutDir.name());
        tag.putString("redstoneOutSignalStr",  redstoneOutSignalStr);
        tag.putInt("wirelessOutIdx",           wirelessOutIdx);
        tag.putString("typeTextStr",           typeTextStr);
        tag.putBoolean("typeTextEnter",        typeTextEnter);
        tag.putString("conditionSource",       conditionSource.name());
        tag.putString("conditionGetter",       conditionGetter);
        tag.putString("conditionOp",           conditionOp);
        tag.putString("conditionThresholdStr", conditionThresholdStr);
        tag.putString("ifGetter",              ifGetter);
        tag.putString("ifOp",                  ifOp);
        tag.putString("ifValueStr",            ifValueStr);
        tag.putInt("ifSkipCount",              ifSkipCount);
        tag.putString("delaySecondsStr",       delaySecondsStr);
        tag.putInt("jumpTarget",               jumpTarget);
        tag.putBoolean("ifGoTo",               ifGoTo);
        tag.putString("mathDest",              mathDest);
        tag.putString("mathA",                 mathA);
        tag.putInt("mathACh",                  mathACh);
        tag.putBoolean("mathAManual",          mathAManual);
        tag.putString("mathOp",                mathOp);
        tag.putString("mathB",                 mathB);
        tag.putInt("mathBCh",                  mathBCh);
        tag.putBoolean("mathBManual",          mathBManual);
        tag.putInt("channel",                  channel);
        return tag;
    }

    public static SequencerStep load(CompoundTag tag) {
        Type type;
        try   { type = Type.valueOf(tag.getString("type")); }
        catch (Exception e) { type = Type.END; }

        SequencerStep s = new SequencerStep(type);
        s.setMethod             = tag.getString("setMethod");
        s.setValueStr           = def(tag.getString("setValueStr"),           "0");
        try { s.redstoneOutDir  = Direction.valueOf(tag.getString("redstoneOutDir")); }
        catch (Exception e)     { s.redstoneOutDir = Direction.NORTH; }
        s.redstoneOutSignalStr  = def(tag.getString("redstoneOutSignalStr"),  "0");
        s.wirelessOutIdx        = tag.contains("wirelessOutIdx") ? Math.max(0, Math.min(12, tag.getInt("wirelessOutIdx"))) : 0;
        s.typeTextStr           = tag.getString("typeTextStr");
        s.typeTextEnter         = !tag.contains("typeTextEnter") || tag.getBoolean("typeTextEnter");
        try { s.conditionSource = ConditionSource.valueOf(tag.getString("conditionSource")); }
        catch (Exception e)     { s.conditionSource = ConditionSource.REDSTONE_NORTH; }
        s.conditionGetter       = tag.getString("conditionGetter");
        s.conditionOp           = def(tag.getString("conditionOp"),           ">");
        s.conditionThresholdStr = def(tag.getString("conditionThresholdStr"),  "0");
        s.ifGetter              = tag.getString("ifGetter");
        s.ifOp                  = def(tag.getString("ifOp"),                   ">");
        s.ifValueStr            = def(tag.getString("ifValueStr"),              "0");
        s.ifSkipCount           = tag.contains("ifSkipCount") ? tag.getInt("ifSkipCount") : 1;
        s.delaySecondsStr       = def(tag.getString("delaySecondsStr"),        "1.0");
        s.jumpTarget            = tag.contains("jumpTarget") ? Math.max(1, tag.getInt("jumpTarget")) : 1;
        s.ifGoTo                = tag.contains("ifGoTo") && tag.getBoolean("ifGoTo");
        s.mathDest              = def(tag.getString("mathDest"), "V1");
        s.mathA                 = tag.getString("mathA"); // empty default
        s.mathACh               = tag.contains("mathACh") ? Math.max(1, Math.min(16, tag.getInt("mathACh"))) : 1;
        s.mathAManual           = tag.contains("mathAManual") && tag.getBoolean("mathAManual");
        s.mathOp                = def(tag.getString("mathOp"),   "+");
        s.mathB                 = tag.getString("mathB"); // empty default
        s.mathBCh               = tag.contains("mathBCh") ? Math.max(1, Math.min(16, tag.getInt("mathBCh"))) : 1;
        s.mathBManual           = tag.contains("mathBManual") && tag.getBoolean("mathBManual");
        s.channel               = tag.contains("channel") ? Math.max(1, Math.min(16, tag.getInt("channel"))) : 1;
        return s;
    }

    // ── Network ───────────────────────────────────────────────────────────────

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(type.ordinal());
        buf.writeUtf(setMethod);
        buf.writeUtf(setValueStr);
        buf.writeByte(redstoneOutDir.ordinal());
        buf.writeUtf(redstoneOutSignalStr);
        buf.writeByte(Math.max(0, Math.min(12, wirelessOutIdx)));
        buf.writeUtf(typeTextStr);
        buf.writeBoolean(typeTextEnter);
        buf.writeByte(conditionSource.ordinal());
        buf.writeUtf(conditionGetter);
        buf.writeUtf(conditionOp);
        buf.writeUtf(conditionThresholdStr);
        buf.writeUtf(ifGetter);
        buf.writeUtf(ifOp);
        buf.writeUtf(ifValueStr);
        buf.writeByte(Math.max(1, Math.min(99, ifSkipCount)));
        buf.writeUtf(delaySecondsStr);
        buf.writeByte(Math.max(1, Math.min(16, channel)));
        buf.writeShort(Math.max(1, Math.min(100, jumpTarget)));
        buf.writeByte(ifGoTo ? 1 : 0);
        buf.writeUtf(mathDest.isEmpty() ? "V1" : mathDest);
        buf.writeUtf(mathA);
        buf.writeByte(Math.max(1, Math.min(16, mathACh)));
        buf.writeByte(mathAManual ? 1 : 0);
        buf.writeUtf(mathOp.isEmpty() ? "+" : mathOp);
        buf.writeUtf(mathB);
        buf.writeByte(Math.max(1, Math.min(16, mathBCh)));
        buf.writeByte(mathBManual ? 1 : 0);
    }

    public static SequencerStep decode(FriendlyByteBuf buf) {
        Type[] types = Type.values();
        int ti = buf.readByte() & 0xFF;
        SequencerStep s = new SequencerStep(ti < types.length ? types[ti] : Type.END);
        s.setMethod             = buf.readUtf();
        s.setValueStr           = buf.readUtf();
        Direction[] dirs = Direction.values();
        int di = buf.readByte() & 0xFF;
        s.redstoneOutDir        = di < dirs.length ? dirs[di] : Direction.NORTH;
        s.redstoneOutSignalStr  = buf.readUtf();
        s.wirelessOutIdx        = Math.max(0, Math.min(12, buf.readByte() & 0xFF));
        s.typeTextStr           = buf.readUtf();
        s.typeTextEnter         = buf.readBoolean();
        ConditionSource[] cs = ConditionSource.values();
        int ci = buf.readByte() & 0xFF;
        s.conditionSource       = ci < cs.length ? cs[ci] : ConditionSource.REDSTONE_NORTH;
        s.conditionGetter       = buf.readUtf();
        s.conditionOp           = buf.readUtf();
        s.conditionThresholdStr = buf.readUtf();
        s.ifGetter              = buf.readUtf();
        s.ifOp                  = buf.readUtf();
        s.ifValueStr            = buf.readUtf();
        s.ifSkipCount           = buf.readByte() & 0xFF;
        s.delaySecondsStr       = buf.readUtf();
        s.channel               = Math.max(1, Math.min(16, buf.readByte() & 0xFF));
        s.jumpTarget  = Math.max(1, buf.readShort() & 0xFFFF);
        s.ifGoTo      = buf.readByte() != 0;
        s.mathDest    = buf.readUtf();
        s.mathA       = buf.readUtf();
        s.mathACh     = Math.max(1, Math.min(16, buf.readByte() & 0xFF));
        s.mathAManual = buf.readByte() != 0;
        s.mathOp      = buf.readUtf();
        s.mathB       = buf.readUtf();
        s.mathBCh     = Math.max(1, Math.min(16, buf.readByte() & 0xFF));
        s.mathBManual = buf.readByte() != 0;
        return s;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String def(String s, String fallback) {
        return (s == null || s.isEmpty()) ? fallback : s;
    }
}
