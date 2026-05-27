package dev.bennethogan.universalkeyboard.livecontrol;

import dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding.Mode;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LiveControlManager {

    // ── State ────────────────────────────────────────────────────────────────

    private static boolean active = false;
    private static BlockPos keyboardPos;
    private static List<LiveControlBinding> bindings = new ArrayList<>();

    private static final Set<Integer>          heldKeys       = new HashSet<>();
    private static final Set<Integer>          toggledOn      = new HashSet<>();
    private static final Map<Long, Integer>    rsIncCounters  = new HashMap<>();
    private static final Map<Integer, Integer> thrIncCounters = new HashMap<>();
    private static final Map<Integer, Integer> varIncCounters = new HashMap<>(); // varIndex → 0-100 counter
    private static final Map<Integer, Integer> incHoldTicks   = new HashMap<>();
    private static final Set<Integer>          hldVarOn       = new HashSet<>(); // varIndex currently driven by a held HLD binding

    private static final int INC_REPEAT_DELAY_TICKS = 10; // 0.5 s before auto-repeat
    private static final int MAX_DISPLAY_KEYS        = 3;  // action-bar key labels before "+N more"

    private static int actionBarTick = 0;

    // ── Activation ───────────────────────────────────────────────────────────

    public static void activate(BlockPos pos, List<LiveControlBinding> binds,
                                int[] localRsOutputs, int[] wirelessPowers, int[] thrusterPowers,
                                double[] varValues) {
        active      = true;
        keyboardPos = pos;
        bindings    = new ArrayList<>(binds);
        heldKeys.clear();
        toggledOn.clear();
        rsIncCounters.clear();
        thrIncCounters.clear();
        varIncCounters.clear();
        incHoldTicks.clear();
        hldVarOn.clear();

        // Seed INC counters and TGL toggle state from current server values so the
        // first keypress doesn't overwrite persisted signals with zeros.
        for (int i = 0; i < bindings.size(); i++) {
            LiveControlBinding b = bindings.get(i);
            if (b.actionType == LiveControlBinding.ActionType.REDSTONE) {
                int cur;
                if (b.wirelessIdx == 0) {
                    int ord = b.rsSide.ordinal();
                    cur = (ord < localRsOutputs.length) ? localRsOutputs[ord] : 0;
                } else {
                    int wi = b.wirelessIdx - 1;
                    cur = (wi < wirelessPowers.length) ? wirelessPowers[wi] : 0;
                }
                if (b.mode == Mode.INC)
                    rsIncCounters.putIfAbsent(rsKey(b.wirelessIdx, b.rsSide), cur);
                else if (b.mode == Mode.TGL && cur == b.signalStrength)
                    toggledOn.add(i);
            } else if (b.actionType == LiveControlBinding.ActionType.THRUSTER_POWER) {
                int cur = (b.channel < thrusterPowers.length) ? thrusterPowers[b.channel] : 0;
                if (b.mode == Mode.INC)
                    thrIncCounters.putIfAbsent(b.channel, cur);
                else if (b.mode == Mode.TGL && cur == (int) Math.round(b.powerLevel * 15))
                    toggledOn.add(i);
            } else if (b.actionType == LiveControlBinding.ActionType.VARIABLE) {
                int cur = (b.varIndex < varValues.length) ? (int) Math.round(varValues[b.varIndex]) : 0;
                if (b.mode == Mode.INC)
                    varIncCounters.putIfAbsent(b.varIndex, Math.max(0, Math.min(100, cur)));
                else if (b.mode == Mode.TGL && cur == b.varOnValue)
                    toggledOn.add(i);
            }
        }
    }

    public static void deactivate() {
        active = false;
        // Release any HLD variable bindings still held so the sequencer regains ownership.
        if (Minecraft.getInstance().getConnection() != null) {
            List<ModPackets.LiveAction> varOff = new ArrayList<>();
            for (LiveControlBinding b : bindings)
                if (b.actionType == LiveControlBinding.ActionType.VARIABLE
                        && b.mode == Mode.HLD && heldKeys.contains(b.keyCode))
                    varOff.add(varAction(b.varIndex, 0));
            if (!varOff.isEmpty()) ModPackets.sendLiveAction(keyboardPos, varOff);
        }
        heldKeys.clear();
        incHoldTicks.clear();
        hldVarOn.clear();
        // Send zero-state only when still connected — avoids NPE on disconnect.
        if (Minecraft.getInstance().getConnection() != null) computeAndSend();
        toggledOn.clear();
        rsIncCounters.clear();
        thrIncCounters.clear();
        varIncCounters.clear();
    }

    public static boolean isActive()       { return active; }
    public static BlockPos getKeyboardPos() { return keyboardPos; }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public static void tick() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!heldKeys.isEmpty() || !toggledOn.isEmpty()) {
            mc.player.displayClientMessage(Component.literal(buildKeyDisplay()), true);
            actionBarTick = 0;
        } else if (++actionBarTick >= 20) {
            actionBarTick = 0;
            mc.player.displayClientMessage(
                    Component.literal(I18n.get("gui.universalkeyboard.msg.live_control_active")), true);
        }

        // INC mode: auto-repeat after INC_REPEAT_DELAY_TICKS of holding.
        // VARIABLE bindings are handled separately (event-driven, re-asserted while held).
        boolean incFired = false;
        Set<Integer> countedThisTick = new java.util.HashSet<>();
        List<ModPackets.LiveAction> varActions = new ArrayList<>();
        for (LiveControlBinding b : bindings) {
            // HLD variable: re-assert the on-value every tick while held so the
            // sequencer cannot overwrite it mid-hold. When the key is no longer held,
            // bring it back to 0 from heldKeys state (not just the discrete key-up event),
            // so a missed or repeat-superseded release can never leave it stuck on.
            if (b.actionType == LiveControlBinding.ActionType.VARIABLE && b.mode == Mode.HLD) {
                if (heldKeys.contains(b.keyCode)) {
                    varActions.add(varAction(b.varIndex, b.varOnValue));
                    hldVarOn.add(b.varIndex);
                } else if (hldVarOn.contains(b.varIndex)) {
                    varActions.add(varAction(b.varIndex, 0));
                    hldVarOn.remove(b.varIndex);
                }
                continue;
            }
            if (b.mode != Mode.INC || !heldKeys.contains(b.keyCode)) continue;
            // Advance the hold timer once per key per tick
            if (countedThisTick.add(b.keyCode))
                incHoldTicks.merge(b.keyCode, 1, Integer::sum);
            boolean pastDelay = incHoldTicks.getOrDefault(b.keyCode, 0) >= INC_REPEAT_DELAY_TICKS;
            int delta = b.incPlus ? 1 : -1;
            if (b.actionType == LiveControlBinding.ActionType.VARIABLE) {
                // INC variable: step on auto-repeat, and re-assert every tick to own it while held.
                if (pastDelay)
                    varIncCounters.merge(b.varIndex, delta, (cur, d) -> Math.max(0, Math.min(100, cur + d)));
                varActions.add(varAction(b.varIndex, varIncCounters.getOrDefault(b.varIndex, 0)));
                continue;
            }
            if (!pastDelay) continue;
            switch (b.actionType) {
                case REDSTONE -> rsIncCounters.merge(
                        rsKey(b.wirelessIdx, b.rsSide), delta,
                        (cur, d) -> Math.max(0, Math.min(15, cur + d)));
                case THRUSTER_POWER -> thrIncCounters.merge(
                        b.channel, delta,
                        (cur, d) -> Math.max(0, Math.min(15, cur + d)));
                default -> {}
            }
            incFired = true;
        }
        if (!varActions.isEmpty()) ModPackets.sendLiveAction(keyboardPos, varActions);
        if (incFired) computeAndSend();
    }

    // ── Key handling ─────────────────────────────────────────────────────────

    public static void handleKey(int keyCode, int glfw_action) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && glfw_action == GLFW.GLFW_PRESS) {
            deactivate();
            return;
        }

        handleVariableKey(keyCode, glfw_action);

        // INC mode: step the counter immediately on press, then per-tick via heldKeys
        if (glfw_action == GLFW.GLFW_PRESS) {
            for (LiveControlBinding b : bindings) {
                if (b.keyCode != keyCode || b.mode != Mode.INC
                        || b.actionType == LiveControlBinding.ActionType.VARIABLE) continue;
                heldKeys.add(keyCode);
                // Only reset the delay timer if this is a genuine new press (key was not
                // already tracked). On Wayland the OS key-repeat can generate rapid
                // press/release pairs, in that case the key may still be in heldKeys
                // from the previous press
                if (!incHoldTicks.containsKey(keyCode))
                    incHoldTicks.put(keyCode, 0);
                int delta = b.incPlus ? 1 : -1;
                switch (b.actionType) {
                    case REDSTONE -> rsIncCounters.merge(
                            rsKey(b.wirelessIdx, b.rsSide), delta,
                            (cur, d) -> Math.max(0, Math.min(15, cur + d)));
                    case THRUSTER_POWER -> thrIncCounters.merge(
                            b.channel, delta,
                            (cur, d) -> Math.max(0, Math.min(15, cur + d)));
                    default -> {}
                }
            }
        }

        // GLFW_REPEAT - OS key-repeat event: keep the key tracked as held without
        // resetting the delay timer, so tick() eventually fires auto-repeat
        if (glfw_action == GLFW.GLFW_REPEAT) {
            for (LiveControlBinding b : bindings) {
                if (b.keyCode != keyCode || b.mode != Mode.INC
                        || b.actionType == LiveControlBinding.ActionType.VARIABLE) continue;
                heldKeys.add(keyCode);
                incHoldTicks.putIfAbsent(keyCode, 0);
            }
        }

        // HLD / TGL modes
        if (glfw_action == GLFW.GLFW_PRESS) {
            for (int i = 0; i < bindings.size(); i++) {
                LiveControlBinding b = bindings.get(i);
                if (b.keyCode != keyCode || b.mode == Mode.INC
                        || b.actionType == LiveControlBinding.ActionType.VARIABLE) continue;
                if (b.mode == Mode.HLD) {
                    heldKeys.add(keyCode);
                } else { // TGL
                    if (toggledOn.contains(i)) toggledOn.remove(i);
                    else toggledOn.add(i);
                }
            }
        } else if (glfw_action == GLFW.GLFW_RELEASE) {
            heldKeys.remove(keyCode);
            incHoldTicks.remove(keyCode);
        }

        computeAndSend();
    }

    /**
     * VARIABLE bindings are event-driven: they only write the variable at the moment of
     * input, leaving the sequencer in sole control between presses.
     *  - HLD: write the on-value on press; write 0 on release (re-asserted each tick while held).
     *  - TGL: write the on-value or 0 on each toggle.
     *  - INC: step the counter on press (and auto-repeat in tick); owns the variable only while held.
     */
    private static void handleVariableKey(int keyCode, int glfw_action) {
        List<ModPackets.LiveAction> out = new ArrayList<>();
        if (glfw_action == GLFW.GLFW_PRESS) {
            for (int i = 0; i < bindings.size(); i++) {
                LiveControlBinding b = bindings.get(i);
                if (b.keyCode != keyCode || b.actionType != LiveControlBinding.ActionType.VARIABLE) continue;
                switch (b.mode) {
                    case HLD -> {
                        heldKeys.add(keyCode);
                        out.add(varAction(b.varIndex, b.varOnValue));
                    }
                    case INC -> {
                        heldKeys.add(keyCode);
                        if (!incHoldTicks.containsKey(keyCode)) incHoldTicks.put(keyCode, 0);
                        int delta = b.incPlus ? 1 : -1;
                        varIncCounters.merge(b.varIndex, delta, (cur, d) -> Math.max(0, Math.min(100, cur + d)));
                        out.add(varAction(b.varIndex, varIncCounters.getOrDefault(b.varIndex, 0)));
                    }
                    case TGL -> {
                        if (toggledOn.contains(i)) toggledOn.remove(i); else toggledOn.add(i);
                        out.add(varAction(b.varIndex, toggledOn.contains(i) ? b.varOnValue : 0));
                    }
                }
            }
        } else if (glfw_action == GLFW.GLFW_REPEAT) {
            for (LiveControlBinding b : bindings) {
                if (b.keyCode != keyCode || b.actionType != LiveControlBinding.ActionType.VARIABLE
                        || b.mode != Mode.INC) continue;
                heldKeys.add(keyCode);
                incHoldTicks.putIfAbsent(keyCode, 0);
            }
        } else if (glfw_action == GLFW.GLFW_RELEASE) {
            for (LiveControlBinding b : bindings) {
                if (b.keyCode != keyCode || b.actionType != LiveControlBinding.ActionType.VARIABLE) continue;
                // HLD hands the variable back to the sequencer; INC leaves its last value in place.
                if (b.mode == Mode.HLD) { out.add(varAction(b.varIndex, 0)); hldVarOn.remove(b.varIndex); }
            }
        }
        if (!out.isEmpty()) ModPackets.sendLiveAction(keyboardPos, out);
    }

    private static ModPackets.LiveAction varAction(int varIndex, double value) {
        return new ModPackets.LiveAction((byte) 4, varIndex, value, 0.0);
    }

    // ── Output computation ───────────────────────────────────────────────────

    public static void computeAndSend() {
        Map<Long, Integer>    rsTargetToSignal = new HashMap<>();
        Map<Integer, Double>  powerByChannel   = new HashMap<>();
        Map<Integer, double[]> vectorByChannel = new HashMap<>();

        for (int i = 0; i < bindings.size(); i++) {
            LiveControlBinding b           = bindings.get(i);
            boolean            bindingActive = isBindingActive(i);

            switch (b.actionType) {
                case REDSTONE -> {
                    long key = rsKey(b.wirelessIdx, b.rsSide);
                    if (b.mode == Mode.INC) {
                        rsTargetToSignal.putIfAbsent(key, 0); // counter merged below
                    } else {
                        int contribution = bindingActive ? b.signalStrength : 0;
                        rsTargetToSignal.merge(key, contribution, Math::max);
                    }
                }
                case THRUSTER_POWER -> {
                    if (b.mode == Mode.INC) {
                        powerByChannel.putIfAbsent(b.channel, 0.0); // counter merged below
                    } else {
                        double contribution = bindingActive ? b.powerLevel : 0.0;
                        powerByChannel.merge(b.channel, contribution, Math::max);
                    }
                }
                case THRUSTER_VECTOR -> {
                    if (bindingActive) {
                        double[] vec = vectorByChannel.computeIfAbsent(b.channel, k -> new double[]{0.0, 0.0});
                        vec[0] += b.vectorX;
                        vec[1] += b.vectorY;
                    } else {
                        vectorByChannel.computeIfAbsent(b.channel, k -> new double[]{0.0, 0.0});
                    }
                }
            }
        }

        // Merge INC counters (always emitted to reflect current count on server)
        for (Map.Entry<Long, Integer> e : rsIncCounters.entrySet())
            rsTargetToSignal.merge(e.getKey(), e.getValue(), Math::max);
        for (Map.Entry<Integer, Integer> e : thrIncCounters.entrySet())
            powerByChannel.merge(e.getKey(), e.getValue() / 15.0, Math::max);

        // Normalise vectors with magnitude > 1
        for (double[] vec : vectorByChannel.values()) {
            double mag = Math.sqrt(vec[0] * vec[0] + vec[1] * vec[1]);
            if (mag > 1.0) { vec[0] /= mag; vec[1] /= mag; }
        }

        // Build action list
        List<ModPackets.LiveAction> actions = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : rsTargetToSignal.entrySet()) {
            long key    = e.getKey();
            int  signal = e.getValue();
            int  wIdx   = (int) (key >> 16);
            int  side   = (int) (key & 0xFFFF);
            if (wIdx == 0) actions.add(new ModPackets.LiveAction((byte) 0, side,    signal,       0.0));
            else           actions.add(new ModPackets.LiveAction((byte) 1, wIdx - 1, signal,      0.0));
        }
        for (Map.Entry<Integer, Double>   e : powerByChannel.entrySet())
            actions.add(new ModPackets.LiveAction((byte) 2, e.getKey(), e.getValue(), 0.0));
        for (Map.Entry<Integer, double[]> e : vectorByChannel.entrySet()) {
            double[] vec = e.getValue();
            actions.add(new ModPackets.LiveAction((byte) 3, e.getKey(), vec[0], vec[1]));
        }

        if (!actions.isEmpty()) ModPackets.sendLiveAction(keyboardPos, actions);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static boolean isBindingActive(int idx) {
        LiveControlBinding b = bindings.get(idx);
        return switch (b.mode) {
            case HLD -> heldKeys.contains(b.keyCode);
            case TGL -> toggledOn.contains(idx);
            case INC -> false; // handled via counters, not hold/toggle state
        };
    }

    private static long rsKey(int wirelessIdx, Direction rsSide) {
        int sideOrd = (wirelessIdx == 0) ? rsSide.ordinal() : 0;
        return ((long) wirelessIdx << 16) | (sideOrd & 0xFFFF);
    }

    private static String buildKeyDisplay() {
        // Toggled-on keys first (persistent state stays visible), then momentarily
        // held keys. A key that is both held and toggled appears once, as a toggle.
        java.util.LinkedHashSet<Integer> keys = new java.util.LinkedHashSet<>();
        for (int idx : toggledOn)
            if (idx >= 0 && idx < bindings.size()) keys.add(bindings.get(idx).keyCode);
        keys.addAll(heldKeys);

        StringBuilder sb = new StringBuilder("§c[Live] ");
        int shown = 0;
        for (int key : keys) {
            if (shown >= MAX_DISPLAY_KEYS) {
                sb.append(" §7+").append(keys.size() - MAX_DISPLAY_KEYS).append(" more");
                break;
            }
            if (shown > 0) sb.append(" ");
            sb.append(keyLabel(key));
            shown++;
        }
        return sb.toString();
    }

    private static String keyLabel(int keyCode) {
        String name = keyDisplayName(keyCode);
        // Variable bindings: show the raw value being written while held.
        if (heldKeys.contains(keyCode)) {
            Integer varVal = varValueForKey(keyCode);
            if (varVal != null) return "§e[" + name + " =" + varVal + "]";
        }
        // INC mode (only relevant while held): append current power level as a percent.
        if (heldKeys.contains(keyCode)) {
            Integer level = incLevelForKey(keyCode);
            if (level != null)
                return "§f[" + name + " " + Math.round(level / 15.0 * 100) + "%]";
        }
        // Toggled-on keys render green so on/off state is obvious at a glance.
        if (isKeyToggledOn(keyCode)) return "§a[" + name + "]";
        return "§f[" + name + "]";
    }

    private static Integer varValueForKey(int keyCode) {
        for (LiveControlBinding b : bindings) {
            if (b.keyCode != keyCode || b.actionType != LiveControlBinding.ActionType.VARIABLE) continue;
            return switch (b.mode) {
                case INC -> varIncCounters.getOrDefault(b.varIndex, 0);
                default  -> b.varOnValue;
            };
        }
        return null;
    }

    private static Integer incLevelForKey(int keyCode) {
        for (LiveControlBinding b : bindings) {
            if (b.keyCode != keyCode || b.mode != Mode.INC) continue;
            switch (b.actionType) {
                case REDSTONE       -> { return rsIncCounters.getOrDefault(rsKey(b.wirelessIdx, b.rsSide), 0); }
                case THRUSTER_POWER -> { return thrIncCounters.getOrDefault(b.channel, 0); }
                default -> {}
            }
        }
        return null;
    }

    private static boolean isKeyToggledOn(int keyCode) {
        for (int idx : toggledOn)
            if (idx >= 0 && idx < bindings.size() && bindings.get(idx).keyCode == keyCode) return true;
        return false;
    }

    private static String keyDisplayName(int keyCode) {
        String name = org.lwjgl.glfw.GLFW.glfwGetKeyName(keyCode, 0);
        if (name != null && !name.isEmpty()) return name.toUpperCase();
        return switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE       -> "Space";
            case GLFW.GLFW_KEY_ENTER,
                 GLFW.GLFW_KEY_KP_ENTER   -> "Enter";
            case GLFW.GLFW_KEY_TAB         -> "Tab";
            case GLFW.GLFW_KEY_BACKSPACE   -> "Bksp";
            case GLFW.GLFW_KEY_UP          -> "Up";
            case GLFW.GLFW_KEY_DOWN        -> "Down";
            case GLFW.GLFW_KEY_LEFT        -> "Left";
            case GLFW.GLFW_KEY_RIGHT       -> "Right";
            case GLFW.GLFW_KEY_LEFT_SHIFT,
                 GLFW.GLFW_KEY_RIGHT_SHIFT -> "Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL,
                 GLFW.GLFW_KEY_RIGHT_CONTROL -> "Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT,
                 GLFW.GLFW_KEY_RIGHT_ALT   -> "Alt";
            default -> "Key" + keyCode;
        };
    }
}
