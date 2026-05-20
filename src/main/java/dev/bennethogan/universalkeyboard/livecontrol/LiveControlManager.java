package dev.bennethogan.universalkeyboard.livecontrol;

import dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding.Mode;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.client.Minecraft;
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

    private static int actionBarTick  = 0;

    // ── Activation ───────────────────────────────────────────────────────────

    public static void activate(BlockPos pos, List<LiveControlBinding> binds,
                                int[] localRsOutputs, int[] wirelessPowers, int[] thrusterPowers) {
        active      = true;
        keyboardPos = pos;
        bindings    = new ArrayList<>(binds);
        heldKeys.clear();
        toggledOn.clear();
        rsIncCounters.clear();
        thrIncCounters.clear();

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
            }
        }
    }

    public static void deactivate() {
        active = false;
        // Clear held keys so HLD bindings emit 0.
        // Keep toggledOn and incCounters so their values persist on the server.
        heldKeys.clear();
        computeAndSend();
        toggledOn.clear();
        rsIncCounters.clear();
        thrIncCounters.clear();
    }

    public static boolean isActive()       { return active; }
    public static BlockPos getKeyboardPos() { return keyboardPos; }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public static void tick() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (++actionBarTick >= 20) {
            actionBarTick = 0;
            mc.player.displayClientMessage(
                    Component.literal("§c[Live Control] §fPress ESC to exit"), true);
        }
    }

    // ── Key handling ─────────────────────────────────────────────────────────

    public static void handleKey(int keyCode, int glfw_action) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && glfw_action == GLFW.GLFW_PRESS) {
            deactivate();
            return;
        }

        // INC mode: step the counter once per key press
        if (glfw_action == GLFW.GLFW_PRESS) {
            for (LiveControlBinding b : bindings) {
                if (b.keyCode != keyCode || b.mode != Mode.INC) continue;
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

        // HLD / TGL modes
        if (glfw_action == GLFW.GLFW_PRESS) {
            for (int i = 0; i < bindings.size(); i++) {
                LiveControlBinding b = bindings.get(i);
                if (b.keyCode != keyCode || b.mode == Mode.INC) continue;
                if (b.mode == Mode.HLD) {
                    heldKeys.add(keyCode);
                } else { // TGL
                    if (toggledOn.contains(i)) toggledOn.remove(i);
                    else toggledOn.add(i);
                }
            }
        } else if (glfw_action == GLFW.GLFW_RELEASE) {
            heldKeys.remove(keyCode);
        }

        computeAndSend();
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
}
