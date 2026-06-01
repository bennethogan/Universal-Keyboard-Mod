package dev.bennethogan.universalkeyboard.livecontrol;

import dev.bennethogan.universalkeyboard.client.gamepad.GamepadLiveDriver;
import dev.bennethogan.universalkeyboard.config.ModConfig;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding.ActionType;
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
    private static final Map<Integer, Integer> varIncCounters = new HashMap<>();
    private static final Map<Long, Double>     rsIncFrac      = new HashMap<>();
    private static final Map<Integer, Double>  thrIncFrac     = new HashMap<>();
    private static final Map<Integer, Double>  varIncFrac     = new HashMap<>();
    private static final Map<Integer, Integer> incHoldTicks   = new HashMap<>();
    private static final Set<Integer>          hldVarOn       = new HashSet<>();

    private static final int INC_REPEAT_DELAY_TICKS = 10;
    private static final int MAX_DISPLAY_KEYS        = 3;

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
        rsIncFrac.clear();
        thrIncFrac.clear();
        varIncFrac.clear();
        incHoldTicks.clear();
        hldVarOn.clear();

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
        if (Minecraft.getInstance().getConnection() != null) {
            List<ModPackets.LiveAction> varOff = new ArrayList<>();
            for (int i = 0; i < bindings.size(); i++) {
                LiveControlBinding b = bindings.get(i);
                if (b.actionType == LiveControlBinding.ActionType.VARIABLE
                        && b.mode == Mode.HLD && heldKeys.contains(b.keyCode))
                    varOff.add(varActionOd(b.varIndex, 0, i));
            }
            if (!varOff.isEmpty()) ModPackets.sendLiveAction(keyboardPos, varOff);
        }
        heldKeys.clear();
        incHoldTicks.clear();
        hldVarOn.clear();
        if (Minecraft.getInstance().getConnection() != null) computeAndSend();
        toggledOn.clear();
        rsIncCounters.clear();
        thrIncCounters.clear();
        varIncCounters.clear();
        rsIncFrac.clear();
        thrIncFrac.clear();
        varIncFrac.clear();
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

        boolean incFired = false;
        Set<Integer> countedThisTick = new java.util.HashSet<>();
        List<ModPackets.LiveAction> varActions = new ArrayList<>();
        for (int bi = 0; bi < bindings.size(); bi++) {
            LiveControlBinding b = bindings.get(bi);
            if (b.actionType == LiveControlBinding.ActionType.VARIABLE && b.mode == Mode.HLD) {
                if (heldKeys.contains(b.keyCode)) {
                    varActions.add(varActionOd(b.varIndex, b.varOnValue, bi));
                    hldVarOn.add(b.varIndex);
                } else if (hldVarOn.contains(b.varIndex)) {
                    varActions.add(varActionOd(b.varIndex, 0, bi));
                    hldVarOn.remove(b.varIndex);
                }
                continue;
            }
            if (b.mode != Mode.INC || !heldKeys.contains(b.keyCode)) continue;
            if (countedThisTick.add(b.keyCode))
                incHoldTicks.merge(b.keyCode, 1, Integer::sum);
            boolean pastDelay = incHoldTicks.getOrDefault(b.keyCode, 0) >= INC_REPEAT_DELAY_TICKS;
            int baseDelta = b.incPlus ? 1 : -1;
            double odFactor = overdriveFactor(bi);
            if (b.actionType == LiveControlBinding.ActionType.VARIABLE) {
                if (pastDelay) {
                    int scaled = varScaledStep(b.varIndex, baseDelta, odFactor);
                    if (scaled != 0)
                        varIncCounters.merge(b.varIndex, scaled, (cur, d) -> Math.max(0, Math.min(100, cur + d)));
                }
                varActions.add(varAction(b.varIndex, varIncCounters.getOrDefault(b.varIndex, 0)));
                continue;
            }
            if (!pastDelay) continue;
            switch (b.actionType) {
                case REDSTONE -> {
                    long key = rsKey(b.wirelessIdx, b.rsSide);
                    int scaled = rsScaledStep(key, baseDelta, odFactor);
                    if (scaled != 0)
                        rsIncCounters.merge(key, scaled, (cur, d) -> Math.max(0, Math.min(15, cur + d)));
                }
                case THRUSTER_POWER -> {
                    int scaled = thrScaledStep(b.channel, baseDelta, odFactor);
                    if (scaled != 0)
                        thrIncCounters.merge(b.channel, scaled, (cur, d) -> Math.max(0, Math.min(15, cur + d)));
                }
                default -> {}
            }
            incFired = true;
        }
        if (!varActions.isEmpty()) ModPackets.sendLiveAction(keyboardPos, varActions);
        if (incFired || hasActiveAnalogOutput()) computeAndSend();
    }

    // ── Key handling ─────────────────────────────────────────────────────────

    public static void handleKey(int keyCode, int glfw_action) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && glfw_action == GLFW.GLFW_PRESS) {
            deactivate();
            return;
        }

        handleVariableKey(keyCode, glfw_action);

        if (glfw_action == GLFW.GLFW_PRESS) {
            for (int i = 0; i < bindings.size(); i++) {
                LiveControlBinding b = bindings.get(i);
                if (b.keyCode != keyCode || b.mode != Mode.INC
                        || b.actionType == LiveControlBinding.ActionType.VARIABLE) continue;
                heldKeys.add(keyCode);
                if (!incHoldTicks.containsKey(keyCode))
                    incHoldTicks.put(keyCode, 0);
                int baseDelta = b.incPlus ? 1 : -1;
                double odFactor = overdriveFactor(i);
                switch (b.actionType) {
                    case REDSTONE -> {
                        long key = rsKey(b.wirelessIdx, b.rsSide);
                        int scaled = rsScaledStep(key, baseDelta, odFactor);
                        if (scaled != 0)
                            rsIncCounters.merge(key, scaled,
                                    (cur, d) -> Math.max(0, Math.min(15, cur + d)));
                    }
                    case THRUSTER_POWER -> {
                        int scaled = thrScaledStep(b.channel, baseDelta, odFactor);
                        if (scaled != 0)
                            thrIncCounters.merge(b.channel, scaled,
                                    (cur, d) -> Math.max(0, Math.min(15, cur + d)));
                    }
                    default -> {}
                }
            }
        }

        if (glfw_action == GLFW.GLFW_REPEAT) {
            for (LiveControlBinding b : bindings) {
                if (b.keyCode != keyCode || b.mode != Mode.INC
                        || b.actionType == LiveControlBinding.ActionType.VARIABLE) continue;
                heldKeys.add(keyCode);
                incHoldTicks.putIfAbsent(keyCode, 0);
            }
        }

        if (glfw_action == GLFW.GLFW_PRESS) {
            for (int i = 0; i < bindings.size(); i++) {
                LiveControlBinding b = bindings.get(i);
                if (b.keyCode != keyCode || b.mode == Mode.INC
                        || b.actionType == LiveControlBinding.ActionType.VARIABLE) continue;
                if (b.mode == Mode.HLD) {
                    heldKeys.add(keyCode);
                } else {
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

    private static void handleVariableKey(int keyCode, int glfw_action) {
        List<ModPackets.LiveAction> out = new ArrayList<>();
        if (glfw_action == GLFW.GLFW_PRESS) {
            for (int i = 0; i < bindings.size(); i++) {
                LiveControlBinding b = bindings.get(i);
                if (b.keyCode != keyCode || b.actionType != LiveControlBinding.ActionType.VARIABLE) continue;
                switch (b.mode) {
                    case HLD -> {
                        heldKeys.add(keyCode);
                        out.add(varActionOd(b.varIndex, b.varOnValue, i));
                    }
                    case INC -> {
                        heldKeys.add(keyCode);
                        if (!incHoldTicks.containsKey(keyCode)) incHoldTicks.put(keyCode, 0);
                        int baseDelta = b.incPlus ? 1 : -1;
                        int scaled = varScaledStep(b.varIndex, baseDelta, overdriveFactor(i));
                        if (scaled != 0)
                            varIncCounters.merge(b.varIndex, scaled, (cur, d) -> Math.max(0, Math.min(100, cur + d)));
                        out.add(varAction(b.varIndex, varIncCounters.getOrDefault(b.varIndex, 0)));
                    }
                    case TGL -> {
                        if (toggledOn.contains(i)) toggledOn.remove(i); else toggledOn.add(i);
                        out.add(varActionOd(b.varIndex, toggledOn.contains(i) ? b.varOnValue : 0, i));
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
            for (int i = 0; i < bindings.size(); i++) {
                LiveControlBinding b = bindings.get(i);
                if (b.keyCode != keyCode || b.actionType != LiveControlBinding.ActionType.VARIABLE) continue;
                if (b.mode == Mode.HLD) { out.add(varActionOd(b.varIndex, 0, i)); hldVarOn.remove(b.varIndex); }
            }
        }
        if (!out.isEmpty()) ModPackets.sendLiveAction(keyboardPos, out);
    }

    private static ModPackets.LiveAction varAction(int varIndex, double value) {
        return new ModPackets.LiveAction((byte) 4, varIndex, value, 0.0);
    }

    private static ModPackets.LiveAction varActionOd(int varIndex, double value, int bindingIdx) {
        double od  = (value == 0.0) ? 1.0 : overdriveFactor(bindingIdx);
        double mag = (value == 0.0 || bindingIdx < 0 || bindingIdx >= bindings.size())
                ? 1.0 : joystickMagnitude(bindings.get(bindingIdx));
        double scaled = (value == 0.0) ? 0.0 : Math.min(100.0, value * od * mag);
        return new ModPackets.LiveAction((byte) 4, varIndex, scaled, 0.0);
    }

    private static double overdriveFactor(int bindingIdx) {
        double factor = 1.0;
        for (int i = 0; i < bindings.size(); i++) {
            LiveControlBinding b = bindings.get(i);
            if (b.actionType != LiveControlBinding.ActionType.OVERDRIVE) continue;
            boolean odActive = (b.mode == Mode.HLD && heldKeys.contains(b.keyCode))
                            || (b.mode == Mode.TGL && toggledOn.contains(i));
            if (!odActive) continue;
            if (bindingIdx >= 0 && isExcluded(b.odExcludes, bindingIdx + 1)) continue;
            factor *= b.overdriveMultiplier;
        }
        return factor;
    }

    private static boolean isExcluded(String excludes, int slotNum) {
        if (excludes == null || excludes.isEmpty()) return false;
        for (String part : excludes.split(",")) {
            try {
                if (Integer.parseInt(part.trim()) == slotNum) return true;
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    private static int rsScaledStep(long key, int baseDelta, double factor) {
        if (factor == 1.0) return baseDelta;
        double want = baseDelta * factor + rsIncFrac.getOrDefault(key, 0.0);
        int whole = (int) Math.round(want);
        rsIncFrac.put(key, want - whole);
        return whole;
    }

    private static int thrScaledStep(int channel, int baseDelta, double factor) {
        if (factor == 1.0) return baseDelta;
        double want = baseDelta * factor + thrIncFrac.getOrDefault(channel, 0.0);
        int whole = (int) Math.round(want);
        thrIncFrac.put(channel, want - whole);
        return whole;
    }

    private static int varScaledStep(int varIndex, int baseDelta, double factor) {
        if (factor == 1.0) return baseDelta;
        double want = baseDelta * factor + varIncFrac.getOrDefault(varIndex, 0.0);
        int whole = (int) Math.round(want);
        varIncFrac.put(varIndex, want - whole);
        return whole;
    }

    // ── Output computation ───────────────────────────────────────────────────

    public static void computeAndSend() {
        Map<Long, Integer>     rsTargetToSignal = new HashMap<>();
        Map<Integer, Double>   powerByChannel   = new HashMap<>();
        Map<Integer, double[]> vectorByChannel  = new HashMap<>();
        Map<Integer, Integer>  linkSignals      = new HashMap<>();

        for (int i = 0; i < bindings.size(); i++) {
            LiveControlBinding b            = bindings.get(i);
            boolean            bindingActive = isBindingActive(i);
            double             odFactor     = overdriveFactor(i);
            double             mag          = joystickMagnitude(b);

            switch (b.actionType) {
                case REDSTONE -> {
                    if (b.linkIdx > 0) {
                        int contribution = bindingActive ? Math.min(15, (int) Math.round(b.signalStrength * odFactor * mag)) : 0;
                        linkSignals.merge(b.linkIdx, contribution, Math::max);
                    } else {
                        long key = rsKey(b.wirelessIdx, b.rsSide);
                        if (b.mode == Mode.INC) {
                            rsTargetToSignal.putIfAbsent(key, 0);
                        } else {
                            int contribution = bindingActive ? Math.min(15, (int) Math.round(b.signalStrength * odFactor * mag)) : 0;
                            rsTargetToSignal.merge(key, contribution, Math::max);
                        }
                    }
                }
                case THRUSTER_POWER -> {
                    if (b.mode == Mode.INC) {
                        powerByChannel.putIfAbsent(b.channel, 0.0);
                    } else {
                        double contribution = bindingActive ? Math.min(1.0, b.powerLevel * odFactor * mag) : 0.0;
                        powerByChannel.merge(b.channel, contribution, Math::max);
                    }
                }
                case THRUSTER_VECTOR -> {
                    if (bindingActive) {
                        double[] vec = vectorByChannel.computeIfAbsent(b.channel, k -> new double[]{0.0, 0.0});
                        vec[0] += b.vectorX * mag;
                        vec[1] += b.vectorY * mag;
                    } else {
                        vectorByChannel.computeIfAbsent(b.channel, k -> new double[]{0.0, 0.0});
                    }
                }
                default -> {}
            }
        }

        // Merge INC counters (already OD-stepped at per-binding rate)
        for (Map.Entry<Long, Integer> e : rsIncCounters.entrySet())
            rsTargetToSignal.merge(e.getKey(), e.getValue(), Math::max);
        for (Map.Entry<Integer, Integer> e : thrIncCounters.entrySet())
            powerByChannel.merge(e.getKey(), e.getValue() / 15.0, Math::max);

        // Normalise vectors with magnitude > 1
        for (double[] vec : vectorByChannel.values()) {
            double mag = Math.sqrt(vec[0] * vec[0] + vec[1] * vec[1]);
            if (mag > 1.0) { vec[0] /= mag; vec[1] /= mag; }
        }

        List<ModPackets.LiveAction> actions = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : rsTargetToSignal.entrySet()) {
            long key    = e.getKey();
            int  signal = e.getValue();
            int  wIdx   = (int) (key >> 16);
            int  side   = (int) (key & 0xFFFF);
            if (wIdx == 0) actions.add(new ModPackets.LiveAction((byte) 0, side,     signal, 0.0));
            else           actions.add(new ModPackets.LiveAction((byte) 1, wIdx - 1, signal, 0.0));
        }
        for (Map.Entry<Integer, Integer> e : linkSignals.entrySet())
            actions.add(new ModPackets.LiveAction((byte) 5, e.getKey() - 1, e.getValue(), 0.0));
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
            case INC -> false;
        };
    }

    // ── Joystick power scaling ─────────────────────────────────────────────────


    private static boolean scalingOn() {
        try { return ModConfig.CLIENT.joystickScaling.get(); }
        catch (Exception e) { return false; }
    }

    private static boolean isScaledAnalog(LiveControlBinding b) {
        return scalingOn()
                && b.mode == Mode.HLD
                && b.actionType != ActionType.OVERDRIVE
                && GamepadCodes.isAnalogCode(b.keyCode);
    }

    private static double joystickMagnitude(LiveControlBinding b) {
        if (!isScaledAnalog(b)) return 1.0;
        return GamepadLiveDriver.analogMagnitude(b.keyCode);
    }
    
    private static boolean hasActiveAnalogOutput() {
        for (int i = 0; i < bindings.size(); i++) {
            LiveControlBinding b = bindings.get(i);
            if (b.actionType == ActionType.VARIABLE) continue; // variables resend each tick already
            if (!isScaledAnalog(b)) continue;
            if (isBindingActive(i)) return true;
        }
        return false;
    }

    private static long rsKey(int wirelessIdx, Direction rsSide) {
        int sideOrd = (wirelessIdx == 0) ? rsSide.ordinal() : 0;
        return ((long) wirelessIdx << 16) | (sideOrd & 0xFFFF);
    }

    private static String buildKeyDisplay() {
        java.util.LinkedHashSet<Integer> keys = new java.util.LinkedHashSet<>();
        for (int idx : toggledOn)
            if (idx >= 0 && idx < bindings.size()) keys.add(bindings.get(idx).keyCode);
        keys.addAll(heldKeys);
        // OD-only keys are silent in the action bar
        keys.removeIf(k -> bindings.stream().noneMatch(b -> b.keyCode == k
                && b.actionType != LiveControlBinding.ActionType.OVERDRIVE));

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
        Double odMult = overdriveMultForKey(keyCode);
        String suffix = odMult != null ? " §d" + formatOdMult(odMult) : "";
        if (heldKeys.contains(keyCode)) {
            Integer varVal = varValueForKey(keyCode);
            if (varVal != null) return "§e[" + name + " =" + varVal + suffix + "§e]";
        }
        if (heldKeys.contains(keyCode)) {
            Integer level = incLevelForKey(keyCode);
            if (level != null)
                return "§f[" + name + " " + Math.round(level / 15.0 * 100) + "%" + suffix + "§f]";
        }
        if (isKeyToggledOn(keyCode)) return "§a[" + name + suffix + "§a]";
        return "§f[" + name + suffix + "§f]";
    }

    private static Double overdriveMultForKey(int keyCode) {
        for (int i = 0; i < bindings.size(); i++) {
            LiveControlBinding b = bindings.get(i);
            if (b.keyCode == keyCode && b.actionType != LiveControlBinding.ActionType.OVERDRIVE) {
                double factor = overdriveFactor(i);
                return factor > 1.0 ? factor : null;
            }
        }
        return null;
    }

    private static String formatOdMult(double m) {
        return (m == (int) m) ? ((int) m) + "x" : m + "x";
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
        if (GamepadCodes.isGamepadCode(keyCode)) return GamepadCodes.name(keyCode);
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
