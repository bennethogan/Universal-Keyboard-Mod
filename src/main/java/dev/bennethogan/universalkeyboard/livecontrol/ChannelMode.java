package dev.bennethogan.universalkeyboard.livecontrol;

import dev.bennethogan.universalkeyboard.compat.PeripheralHelper;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding.ActionType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/**
 * Describes one "scalar peripheral channel" live-control output mode-- the RPM
 * family. Each such mode targets a numeric channel (1-N); holds an integer value
 * that supports HLD / TGL / INC; clamps to a range; merges contributions a chosen
 * way; applies to peripherals server-side via a setter; and renders one value cell
 *
 * To help me remember later:
 *
 *   add the enum value + a value field to LiveControlBinding
 *   (and its saveToTag / fromTag / encode / decode),
 *  register one ChannelMode in the static block below,
 *  add the type to the picker in LiveControlScreen
 *  (isTypeAvailable / nextAvailableType)
 *
 * Everything else (the manager INC/HLD/TGL loop, action bar, packet emit and the
 * server applier) is driven from this descriptor, so its more straightforward to add
 */
public final class ChannelMode {

    /** How an INC counter combines with a simultaneously-active HLD/TGL on the same channel. */
    public enum IncMerge {
        /** Larger magnitude wins; an INC counter yields entirely to an active HLD/TGL (RPM, signed). */
        SIGNED_YIELD,
        /** Highest value wins (thruster-style, non-negative). */
        MAX
    }

    public interface ServerApplier {
        boolean apply(Object peripheral, int value);
    }

    public final ActionType type;
    public final byte       opcode;       // LiveAction packet type byte
    public final int        min, max;     // value clamp (counter == output units)
    public final IncMerge   incMerge;
    public final ToIntFunction<LiveControlBinding>  target;    // read HLD/TGL target value
    public final ObjIntConsumer<LiveControlBinding> setTarget; // write HLD/TGL target value (UI editing)
    public final ServerApplier server;
    public final String     labelKey;     // i18n type label
    public final int        valueColor;   // accent for the value cell border
    public final String     unitSuffix;   // action-bar unit, e.g. "rpm"

    public ChannelMode(ActionType type, byte opcode, int min, int max, IncMerge incMerge,
                       ToIntFunction<LiveControlBinding> target, ObjIntConsumer<LiveControlBinding> setTarget,
                       ServerApplier server, String labelKey, int valueColor, String unitSuffix) {
        this.type = type;
        this.opcode = opcode;
        this.min = min;
        this.max = max;
        this.incMerge = incMerge;
        this.target = target;
        this.setTarget = setTarget;
        this.server = server;
        this.labelKey = labelKey;
        this.valueColor = valueColor;
        this.unitSuffix = unitSuffix;
    }

    public int clamp(int v) { return Math.max(min, Math.min(max, v)); }

    // The HLD/TGL target value, currently displayed/applied for b
    public int targetValue(LiveControlBinding b) { return target.applyAsInt(b); }

    // Adjust the target value by delta, clamped to range.
    public void stepTarget(LiveControlBinding b, int delta) {
        setTarget.accept(b, clamp(targetValue(b) + delta));
    }

    // Merge two contributions on the same channel for the HLD/TGL accumulator
    public int merge(int current, int incoming) {
        return switch (incMerge) {
            case MAX          -> Math.max(current, incoming);
            case SIGNED_YIELD -> Math.abs(incoming) >= Math.abs(current) ? incoming : current;
        };
    }

    // ── Registry ──────────────────────────────────────────────────────────────

    private static final List<ChannelMode>            REGISTRY = new ArrayList<>();
    private static final Map<ActionType, ChannelMode> BY_TYPE  = new EnumMap<>(ActionType.class);

    static {
        // RPM mode: Covered electric motor, speed controlller, and creative motor. Signed range to run in reverse
        register(new ChannelMode(
                ActionType.RPM_CONTROL, (byte) 6, -256, 256, IncMerge.SIGNED_YIELD,
                b -> b.rpmTarget, (b, v) -> b.rpmTarget = v,
                (p, v) -> {
                    if (PeripheralHelper.isRpmCapable(p)) { PeripheralHelper.callRpmSetter(p, v); return true; }
                    return false;
                },
                "gui.universalkeyboard.label.action_rpm", 0xFF3355AA, "rpm"));
    }

    public static void register(ChannelMode m) {
        REGISTRY.add(m);
        BY_TYPE.put(m.type, m);
    }

    public static List<ChannelMode> all()                   { return REGISTRY; }
    public static ChannelMode       byType(ActionType t)    { return BY_TYPE.get(t); }
    public static boolean           isChannelMode(ActionType t) { return BY_TYPE.containsKey(t); }

    public static ChannelMode byOpcode(byte op) {
        for (ChannelMode m : REGISTRY) if (m.opcode == op) return m;
        return null;
    }
}
