package dev.bennethogan.universalkeyboard.blockentity;

import dev.bennethogan.universalkeyboard.compat.CreateValueHelper;
import dev.bennethogan.universalkeyboard.compat.PeripheralHelper;
import dev.bennethogan.universalkeyboard.compat.SableCompat;
import dev.bennethogan.universalkeyboard.compat.rslink.RsLinkPresence;
import dev.bennethogan.universalkeyboard.config.ModConfig;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import dev.bennethogan.universalkeyboard.sequencer.SequencerStep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

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
    private final double[] vars = new double[SequencerStep.VAR_COUNT];

    private double rN, rSumX, rSumY, rSumXY, rSumXX;

    private int     lastBroadcastStep    = -1;
    private boolean lastBroadcastRunning = false;

    SequencerEngine(LinkedKeyboardBlockEntity be) { this.be = be; }

    boolean isRunning()      { return running; }
    int     getCurrentStep() { return currentStep; }

    /** Externally set a variable from live controller. Applies whether or not running. */
    void setVar(int idx, double value) {
        if (idx >= 0 && idx < vars.length) vars[idx] = value;
    }

    double[] getVars() { return vars.clone(); }

    void start() {
        running     = true;
        currentStep = 0;
        delayTicker = 0;
        Arrays.fill(vars, 0.0);
        resetRegression();
    }

    private void resetRegression() {
        rN = rSumX = rSumY = rSumXY = rSumXX = 0;
    }

    /** Marks the engine stopped; caller is responsible for clearing RS/wireless outputs. */
    void stop() {
        running = false;
    }

    void tick(List<SequencerStep> steps) {
        // I messed this up before and made it run only once per tick, so now we
        // run as many steps as possible this tick; only stop on steps that need it
        // This should make PID sequences work much better, I'm hoping
        int budget = 512; // safety cap against infinite loops
        while (running && budget-- > 0) {
            if (currentStep >= steps.size()) {
                running = false;
                be.setChanged();
                break;
            }
            if (tickOne(steps)) break;
        }
        mayBroadcastProgress();
    }

    /**
     * Executes the current step. Returns true if the engine should yield
     * (stop processing further steps this game tick), false to continue.
     */
    private boolean tickOne(List<SequencerStep> steps) {
        SequencerStep step = steps.get(currentStep);
        switch (step.type) {
            case DELAY -> {
                if (delayTicker <= 0) {
                    float secs = 1.0f;
                    try { secs = Float.parseFloat(step.delaySecondsStr); } catch (NumberFormatException ignored) {}
                    delayTicker = Math.max(1, Math.round(secs * 20));
                }
                if (--delayTicker <= 0) advance();
                return true; // always yield on DELAY so it marks a real tick boundary
            }
            case IF -> {
                if (evaluateIfCondition(step)) {
                    if (step.ifGoTo) {
                        currentStep = Math.max(0, Math.min(step.jumpTarget - 1, steps.size() - 1));
                        delayTicker = 0;
                        be.setChanged();
                        return true; // jumped — yield to avoid runaway loops
                    } else {
                        currentStep += step.ifSkipCount;
                    }
                }
                advance();
                return false;
            }
            case CONDITION -> {
                if (evaluateCondition(step)) advance();
                return true; // yield whether waiting or just passed
            }
            case SET_VALUE -> { applySetValue(step);  advance(); return false; }
            case SET_REDSTONE -> { applySetRedstone(step); advance(); return false; }
            case TYPE_TEXT -> {
                if (!be.isTypeQueueEmpty()) return true;
                be.enqueueChars(step.typeTextStr, step.typeTextEnter);
                advance();
                return false;
            }
            case TYPE_VARIABLE -> {
                if (!be.isTypeQueueEmpty()) return true;
                String src = SequencerStep.isVar(step.setValueStr) ? step.setValueStr : "V1";
                be.enqueueChars(formatVar(resolveSource(src, 1)), step.typeTextEnter);
                advance();
                return false;
            }
            case JUMP -> {
                currentStep = Math.max(0, Math.min(step.jumpTarget - 1, steps.size() - 1));
                delayTicker = 0;
                be.setChanged();
                return true; // yield after any jump to prevent tight-loop runaway
            }
            case MATH -> {
                double a = resolveSource(step.mathA, step.mathACh);
                double b = resolveSource(step.mathB, step.mathBCh);
                double result = applyMathOp(step.mathOp, a, b);
                String dest = step.mathDest != null ? step.mathDest.trim() : "V1";
                if (SequencerStep.isVar(dest)) vars[SequencerStep.varIndex(dest)] = result;
                advance();
                return false;
            }
            case REGRESS -> {
                applyRegress(step);
                advance();
                return false;
            }
            case CYCLE -> {
                currentStep = 0;
                delayTicker = 0;
                be.setChanged();
                return true; // yield so the next full pass starts on the next tick
            }
            case END -> {
                running = false;
                be.setChanged();
                return true;
            }
            default -> { advance(); return false; }
        }
    }

    double resolveSource(String src, int channel) {
        if (src == null || src.isBlank()) return 0;
        src = src.trim();
        if (SequencerStep.isVar(src)) return vars[SequencerStep.varIndex(src)];
        if (src.startsWith("RS:")) {
            String d = src.substring(3).toUpperCase();
            Direction dir = switch (d) {
                case "N", "NORTH"           -> Direction.NORTH;
                case "S", "SOUTH"           -> Direction.SOUTH;
                case "E", "EAST"            -> Direction.EAST;
                case "W", "WEST"            -> Direction.WEST;
                case "U", "UP", "T", "TOP"  -> Direction.UP;
                case "D", "DOWN", "B", "BOTTOM" -> Direction.DOWN;
                default -> null;
            };
            if (dir != null && be.getLevel() != null)
                return be.getLevel().getSignal(be.getBlockPos().relative(dir), dir);
            return 0;
        }
        if (src.matches("L([1-9]|1[0-9]|20)")) {
            int idx = Integer.parseInt(src.substring(1)) - 1;
            if (RsLinkPresence.isPresent() && idx < be.getRsLinkCount())
                return be.getWirelessEntries().get(idx).getReceivedPower();
            return 0;
        }
        if (src.matches("W([1-9]|[1-9][0-9]|100)")) {
            return be.getWirelessFreqPower(Integer.parseInt(src.substring(1)) - 1);
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
        long[] packed = new long[vars.length];
        for (int i = 0; i < vars.length; i++) packed[i] = Double.doubleToLongBits(vars[i]);
        tag.putLongArray("sequencer_vars", packed);
    }

    void loadFromTag(CompoundTag tag) {
        running     = tag.getBoolean("sequencer_running");
        currentStep = tag.getInt("sequencer_current_step");
        if (tag.contains("sequencer_vars")) {
            long[] packed = tag.getLongArray("sequencer_vars");
            for (int i = 0; i < Math.min(packed.length, vars.length); i++)
                vars[i] = Double.longBitsToDouble(packed[i]);
        }
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
        } else if (SequencerStep.isVar(step.ifGetter)) {
            actual = vars[SequencerStep.varIndex(step.ifGetter)];
        } else if (step.ifGetter.matches("L([1-9]|1[0-9]|20)")) {
            int idx = Integer.parseInt(step.ifGetter.substring(1)) - 1;
            actual = (RsLinkPresence.isPresent() && idx < be.getRsLinkCount())
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
        } else if (step.conditionGetter.matches("L([1-9]|1[0-9]|20)")) {
            int idx = Integer.parseInt(step.conditionGetter.substring(1)) - 1;
            actual = (RsLinkPresence.isPresent() && idx < be.getRsLinkCount())
                    ? be.getWirelessEntries().get(idx).getReceivedPower() : 0;
        } else if (step.conditionGetter.matches("W([1-9]|[1-9][0-9]|100)")) {
            actual = be.getWirelessFreqPower(Integer.parseInt(step.conditionGetter.substring(1)) - 1);
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

    private void applyRegress(SequencerStep step) {
        switch (step.regressMode) {
            case "RESET" -> resetRegression();
            case "SAMPLE" -> {
                String[] xs = step.mathA.split(",");
                String[] ys = step.mathB.split(",");
                int count = Math.min(xs.length, ys.length);
                for (int i = 0; i < count; i++) {
                    double x = resolveSource(xs[i].trim(), step.mathACh);
                    double y = resolveSource(ys[i].trim(), step.mathBCh);
                    rN++; rSumX += x; rSumY += y; rSumXY += x * y; rSumXX += x * x;
                }
            }
            case "SOLVE" -> {
                double denom = rN * rSumXX - rSumX * rSumX;
                if (rN < 2 || denom == 0) return;
                double slope     = (rN * rSumXY - rSumX * rSumY) / denom;
                double intercept = (rSumY - slope * rSumX) / rN;
                if (SequencerStep.isVar(step.mathDest))
                    vars[SequencerStep.varIndex(step.mathDest)] = slope;
                if (SequencerStep.isVar(step.regressDest2))
                    vars[SequencerStep.varIndex(step.regressDest2)] = intercept;
            }
        }
    }

    private void applySetValue(SequencerStep step) {
        if (step.setMethod.isEmpty()) return;
        double value = resolveSource(step.setValueStr, step.channel);
        if (SequencerStep.isVar(step.setMethod)) {
            vars[SequencerStep.varIndex(step.setMethod)] = value;
            return;
        }
        List<BlockPos> targets = be.getLinkedTargetPositions(step.channel);
        if (targets.isEmpty() || be.getLevel() == null) return;
        double rangeSq = ModConfig.COMMON.keyboardRange.get();
        rangeSq *= rangeSq;
        boolean valuePanel = step.setMethod.equals(CreateValueHelper.VALUE_PANEL_SETTER);
        for (BlockPos targetPos : targets) {
            if (be.getBlockPos().distSqr(targetPos) > rangeSq) continue;
            if (valuePanel) {
                BlockEntity tbe = be.getLevel().getBlockEntity(targetPos);
                if (CreateValueHelper.hasScrollValue(tbe)) {
                    int min = CreateValueHelper.getMin(tbe);
                    int max = CreateValueHelper.getMax(tbe);
                    CreateValueHelper.setValue(tbe, Math.max(min, Math.min(max, (int) Math.round(value))));
                }
                continue;
            }
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
        if (step.wirelessFreqOutIdx > 0)
            be.broadcastWirelessFreq(step.wirelessFreqOutIdx - 1, signal);
        else if (step.rsLinkOutIdx > 0)
            be.setRsLinkOutput(step.rsLinkOutIdx - 1, signal);
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
            case "pow"   -> Math.pow(a, b);
            case "sin"   -> Math.sin(Math.toRadians(a));
            case "cos"   -> Math.cos(Math.toRadians(a));
            case "tan"   -> Math.tan(Math.toRadians(a));
            case "asin"  -> Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, a))));
            case "acos"  -> Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, a))));
            case "atan"  -> Math.toDegrees(Math.atan(a));
            case "atan2" -> Math.toDegrees(Math.atan2(a, b));
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
