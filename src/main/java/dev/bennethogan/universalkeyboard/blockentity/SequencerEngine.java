package dev.bennethogan.universalkeyboard.blockentity;

import dev.bennethogan.universalkeyboard.compat.PeripheralHelper;
import dev.bennethogan.universalkeyboard.compat.SableCompat;
import dev.bennethogan.universalkeyboard.compat.wireless.WirelessPresence;
import dev.bennethogan.universalkeyboard.config.ModConfig;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import dev.bennethogan.universalkeyboard.sequencer.SequencerStep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import java.util.Arrays;
import java.util.List;

/**
 * Owns all mutable sequencer runtime state and execution logic.
 * The BE holds an instance and delegates tick/start/stop to it.
 */
class SequencerEngine {

    private final LinkedKeyboardBlockEntity be;

    private boolean running     = false;
    private int     currentStep = 0;
    private int     delayTicker = 0;
    private final double[] vars = new double[8]; // V1-V8

    private int     lastBroadcastStep    = -1;
    private boolean lastBroadcastRunning = false;

    SequencerEngine(LinkedKeyboardBlockEntity be) { this.be = be; }

    boolean isRunning()      { return running; }
    int     getCurrentStep() { return currentStep; }

    void start() {
        running     = true;
        currentStep = 0;
        delayTicker = 0;
        Arrays.fill(vars, 0.0);
    }

    /** Marks the engine stopped; caller is responsible for clearing RS/wireless outputs. */
    void stop() {
        running = false;
    }

    void tick(List<SequencerStep> steps) {
        if (currentStep >= steps.size()) {
            running = false;
            be.setChanged();
            mayBroadcastProgress();
            return;
        }
        SequencerStep step = steps.get(currentStep);
        switch (step.type) {
            case DELAY -> {
                if (delayTicker <= 0) {
                    float secs = 1.0f;
                    try { secs = Float.parseFloat(step.delaySecondsStr); } catch (NumberFormatException ignored) {}
                    delayTicker = Math.max(1, Math.round(secs * 20));
                }
                if (--delayTicker <= 0) advance();
            }
            case IF -> {
                if (evaluateIfCondition(step)) {
                    if (step.ifGoTo) {
                        currentStep = Math.max(0, Math.min(step.jumpTarget - 1, steps.size() - 1));
                        delayTicker = 0;
                        be.setChanged();
                        mayBroadcastProgress();
                        return;
                    } else {
                        currentStep += step.ifSkipCount;
                    }
                }
                advance();
            }
            case CONDITION -> {
                if (evaluateCondition(step)) advance();
            }
            case SET_VALUE -> {
                applySetValue(step);
                advance();
            }
            case SET_REDSTONE -> {
                applySetRedstone(step);
                advance();
            }
            case TYPE_TEXT -> {
                if (!be.isTypeQueueEmpty()) break;
                be.enqueueChars(step.typeTextStr, step.typeTextEnter);
                advance();
            }
            case TYPE_VARIABLE -> {
                if (!be.isTypeQueueEmpty()) break;
                String src = step.setValueStr.matches("V[1-8]") ? step.setValueStr : "V1";
                be.enqueueChars(formatVar(resolveSource(src, 1)), step.typeTextEnter);
                advance();
            }
            case JUMP -> {
                currentStep = Math.max(0, Math.min(step.jumpTarget - 1, steps.size() - 1));
                delayTicker = 0;
                be.setChanged();
            }
            case MATH -> {
                double a = resolveSource(step.mathA, step.mathACh);
                double b = resolveSource(step.mathB, step.mathBCh);
                double result = applyMathOp(step.mathOp, a, b);
                String dest = step.mathDest != null ? step.mathDest.trim() : "V1";
                if (dest.matches("V[1-8]")) vars[dest.charAt(1) - '1'] = result;
                advance();
            }
            case CYCLE -> {
                currentStep = 0;
                delayTicker = 0;
                be.setChanged();
            }
            case END -> {
                running = false;
                be.setChanged();
            }
        }
        mayBroadcastProgress();
    }

    double resolveSource(String src, int channel) {
        if (src == null || src.isBlank()) return 0;
        src = src.trim();
        if (src.matches("V[1-8]")) return vars[src.charAt(1) - '1'];
        if (src.startsWith("RS:")) {
            String d = src.substring(3).toUpperCase();
            Direction dir = switch (d) {
                case "N", "NORTH" -> Direction.NORTH;
                case "S", "SOUTH" -> Direction.SOUTH;
                case "E", "EAST"  -> Direction.EAST;
                case "W", "WEST"  -> Direction.WEST;
                default -> null;
            };
            if (dir != null && be.getLevel() != null)
                return be.getLevel().getSignal(be.getBlockPos().relative(dir), dir);
            return 0;
        }
        if (src.matches("W([1-9]|1[0-2])")) {
            int idx = Integer.parseInt(src.substring(1)) - 1;
            if (WirelessPresence.isPresent() && idx < be.getWirelessCount())
                return be.getWirelessEntries().get(idx).getReceivedPower();
            return 0;
        }
        try { return Double.parseDouble(src); } catch (NumberFormatException ignored) {}
        if (be.getLevel() == null) return 0;
        if (SableCompat.isPresent() && SableCompat.isSableGetter(src)
                && SableCompat.isOnSublevel(be.getLevel(), be.getBlockPos()))
            return SableCompat.getValue(be.getLevel(), be.getBlockPos(), src);
        List<BlockPos> targets = be.getLinkedTargetPositions(channel);
        if (targets.isEmpty()) return 0;
        Object p = PeripheralHelper.getPeripheral(be.getLevel(), targets.get(0));
        return p == null ? 0 : PeripheralHelper.getDoubleGetter(p, src);
    }

    void saveToTag(CompoundTag tag) {
        tag.putBoolean("sequencer_running",  running);
        tag.putInt("sequencer_current_step", currentStep);
    }

    void loadFromTag(CompoundTag tag) {
        running     = tag.getBoolean("sequencer_running");
        currentStep = tag.getInt("sequencer_current_step");
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void advance() {
        currentStep++;
        delayTicker = 0;
        be.setChanged();
    }

    private void mayBroadcastProgress() {
        if (currentStep == lastBroadcastStep && running == lastBroadcastRunning) return;
        lastBroadcastStep    = currentStep;
        lastBroadcastRunning = running;
        ModPackets.broadcastSequencerProgress(be);
    }

    private boolean evaluateIfCondition(SequencerStep step) {
        if (be.getLevel() == null || step.ifGetter.isEmpty()) return false;
        double actual;
        int rsIdx = -1;
        for (int i = 0; i < SequencerStep.RS_INPUT_GETTER_NAMES.length; i++) {
            if (SequencerStep.RS_INPUT_GETTER_NAMES[i].equals(step.ifGetter)) { rsIdx = i; break; }
        }
        if (rsIdx >= 0) {
            Direction dir = SequencerStep.RS_INPUT_GETTER_DIRS[rsIdx];
            actual = be.getLevel().getSignal(be.getBlockPos().relative(dir), dir);
        } else if (step.ifGetter.matches("V[1-8]")) {
            actual = vars[step.ifGetter.charAt(1) - '1'];
        } else if (step.ifGetter.matches("W([1-9]|1[0-2])")) {
            int idx = Integer.parseInt(step.ifGetter.substring(1)) - 1;
            actual = (WirelessPresence.isPresent() && idx < be.getWirelessCount())
                    ? be.getWirelessEntries().get(idx).getReceivedPower() : 0;
        } else if (SableCompat.isPresent() && SableCompat.isSableGetter(step.ifGetter)
                && SableCompat.isOnSublevel(be.getLevel(), be.getBlockPos())) {
            actual = SableCompat.getValue(be.getLevel(), be.getBlockPos(), step.ifGetter);
        } else {
            List<BlockPos> ch = be.getLinkedTargetPositions(step.channel);
            if (ch.isEmpty()) return false;
            Object p = PeripheralHelper.getPeripheral(be.getLevel(), ch.get(0));
            if (p == null) return false;
            actual = PeripheralHelper.getDoubleGetter(p, step.ifGetter);
        }
        double threshold = resolveSource(step.ifValueStr, step.channel);
        return switch (step.ifOp) {
            case ">"  -> actual > threshold;
            case ">=" -> actual >= threshold;
            case "="  -> Math.abs(actual - threshold) < 0.001;
            case "<=" -> actual <= threshold;
            case "<"  -> actual < threshold;
            case "!=" -> Math.abs(actual - threshold) >= 0.001;
            default   -> false;
        };
    }

    private boolean evaluateCondition(SequencerStep step) {
        if (be.getLevel() == null) return false;
        double actual;
        Direction dir = step.conditionSource.direction;
        if (dir != null) {
            actual = be.getLevel().getSignal(be.getBlockPos().relative(dir), dir);
        } else if (step.conditionGetter.matches("W([1-9]|1[0-2])")) {
            int idx = Integer.parseInt(step.conditionGetter.substring(1)) - 1;
            actual = (WirelessPresence.isPresent() && idx < be.getWirelessCount())
                    ? be.getWirelessEntries().get(idx).getReceivedPower() : 0;
        } else if (SableCompat.isPresent() && SableCompat.isSableGetter(step.conditionGetter)
                && SableCompat.isOnSublevel(be.getLevel(), be.getBlockPos())) {
            actual = SableCompat.getValue(be.getLevel(), be.getBlockPos(), step.conditionGetter);
        } else {
            List<BlockPos> ch = be.getLinkedTargetPositions(step.channel);
            if (ch.isEmpty()) return false;
            Object p = PeripheralHelper.getPeripheral(be.getLevel(), ch.get(0));
            if (p == null) return false;
            actual = PeripheralHelper.getDoubleGetter(p, step.conditionGetter);
        }
        double threshold = resolveSource(step.conditionThresholdStr, step.channel);
        return switch (step.conditionOp) {
            case ">" -> actual > threshold;
            case "<" -> actual < threshold;
            case "=" -> Math.abs(actual - threshold) < 0.001;
            default  -> false;
        };
    }

    private void applySetValue(SequencerStep step) {
        if (step.setMethod.isEmpty()) return;
        double value = resolveSource(step.setValueStr, step.channel);
        if (step.setMethod.matches("V[1-8]")) {
            vars[step.setMethod.charAt(1) - '1'] = value;
            return;
        }
        List<BlockPos> targets = be.getLinkedTargetPositions(step.channel);
        if (targets.isEmpty() || be.getLevel() == null) return;
        double rangeSq = ModConfig.COMMON.keyboardRange.get();
        rangeSq *= rangeSq;
        for (BlockPos targetPos : targets) {
            if (be.getBlockPos().distSqr(targetPos) > rangeSq) continue;
            Object p = PeripheralHelper.getPeripheral(be.getLevel(), targetPos);
            if (p == null) continue;
            String err = PeripheralHelper.callMethodWithDouble(p, step.setMethod, value);
            if (err != null) notifyNearbyPlayers("§c[Keyboard] §f" + err);
        }
    }

    private void notifyNearbyPlayers(String msg) {
        if (!(be.getLevel() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        net.minecraft.network.chat.Component c = net.minecraft.network.chat.Component.literal(msg);
        double rangeSq = 32.0 * 32.0;
        for (net.minecraft.server.level.ServerPlayer sp : sl.players()) {
            if (sp.blockPosition().distSqr(be.getBlockPos()) <= rangeSq)
                sp.displayClientMessage(c, true);
        }
    }

    private void applySetRedstone(SequencerStep step) {
        int signal = (int) Math.round(resolveSource(step.redstoneOutSignalStr, 1));
        if (step.wirelessOutIdx > 0)
            be.setWirelessOutput(step.wirelessOutIdx - 1, signal);
        else
            be.setRedstoneOutput(step.redstoneOutDir, signal);
    }

    private static double applyMathOp(String op, double a, double b) {
        return switch (op) {
            case "+"     -> a + b;
            case "-"     -> a - b;
            case "*"     -> a * b;
            case "/"     -> b != 0 ? a / b : 0;
            case "%"     -> b != 0 ? a % b : 0;
            case "min"   -> Math.min(a, b);
            case "max"   -> Math.max(a, b);
            case "abs"   -> Math.abs(a);
            case "neg"   -> -a;
            case "round" -> (double) Math.round(a);
            case "floor" -> Math.floor(a);
            case "ceil"  -> Math.ceil(a);
            default      -> a;
        };
    }

    private static String formatVar(double val) {
        String s;
        if (val == Math.floor(val) && !Double.isInfinite(val) && Math.abs(val) < 1e15)
            s = Long.toString((long) val);
        else
            s = String.format("%.8f", val).replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.length() > 16 ? s.substring(0, 16) : s;
    }
}
