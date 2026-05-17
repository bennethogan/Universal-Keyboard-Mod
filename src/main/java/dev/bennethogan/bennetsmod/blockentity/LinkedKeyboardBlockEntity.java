package dev.bennethogan.bennetsmod.blockentity;

import dev.bennethogan.bennetsmod.UniversalKeyboardMod;
import dev.bennethogan.bennetsmod.compat.CreateValueHelper;
import dev.bennethogan.bennetsmod.compat.KeyboardMode;
import dev.bennethogan.bennetsmod.compat.PeripheralHelper;
import dev.bennethogan.bennetsmod.compat.SableCompat;
import dev.bennethogan.bennetsmod.compat.wireless.CreateWirelessHelper;
import dev.bennethogan.bennetsmod.compat.wireless.WirelessEntry;
import dev.bennethogan.bennetsmod.config.ModConfig;
import dev.bennethogan.bennetsmod.sequencer.SequencerStep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class LinkedKeyboardBlockEntity extends BlockEntity {

    public static final int MAX_CHANNELS = 16;

    // Per-channel mesh targets: key = channel number (1–8), value = list of positions
    private final Map<Integer, List<BlockPos>> channelTargets = new HashMap<>();
    // Which channel is currently active (1–8)
    private int activeChannel = 1;

    private String  autoTypeScript  = "";
    private boolean wasPowered      = false;
    private int     scriptLineIndex = 0;

    private @Nullable Object peripheral = null;
    private final Queue<Character> typeQueue = new LinkedList<>();
    private int typeTimer = 0;
    private static final int TICKS_PER_CHAR = 2;

    private @Nullable String inlineCaptureBuffer = null;

    // ----- Peripheral Sequencer -----

    private final List<SequencerStep> sequencerSteps    = new ArrayList<>();
    private boolean sequencerRunning     = false;
    private int     sequencerCurrentStep = 0;
    private int     sequencerDelayTicker = 0;
    private final double[] sequencerVars = new double[8]; // V1-V8, cleared on start

    // Redstone outputs — indexed by Direction.ordinal()
    private final int[] redstoneOutputs = new int[Direction.values().length];

    // Wireless redstone outputs (Create RedstoneLink integration). Indexed 0-based;
    // user-facing labels are W1..W{N}. Up to MAX_WIRELESS entries.
    public static final int MAX_WIRELESS = 12;
    private final List<WirelessEntry> wirelessEntries = new ArrayList<>();

    // Cached peripheral getter values for Create display source
    private final Map<String, String> cachedGetterValues = new LinkedHashMap<>();
    private String cachedPeripheralType = "";
    private int peripheralRefreshTimer = 0;
    private static final int PERIPHERAL_REFRESH_TICKS = 20;

    public LinkedKeyboardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LINKED_KEYBOARD.get(), pos, state);
    }

    // ----- Channel helpers -----

    private List<BlockPos> channelList(int ch) {
        return channelTargets.computeIfAbsent(ch, k -> new ArrayList<>());
    }

    public int getActiveChannel() { return activeChannel; }

    public void setActiveChannel(int ch) {
        activeChannel = Math.max(1, Math.min(MAX_CHANNELS, ch));
        setChanged();
    }

    public void cycleActiveChannel() {
        setActiveChannel((activeChannel % MAX_CHANNELS) + 1);
    }

    /**
     * Cycles to the next channel that has at least one linked target, wrapping around.
     * If no channel has targets, falls back to simple cycling.
     */
    public void cycleActiveChannelSmart() {
        for (int i = 1; i <= MAX_CHANNELS; i++) {
            int next = (activeChannel - 1 + i) % MAX_CHANNELS + 1;
            if (next != activeChannel && channelTargets.containsKey(next) && !channelTargets.get(next).isEmpty()) {
                setActiveChannel(next);
                return;
            }
        }
        // No other channel has targets — fall back to simple cycle
        cycleActiveChannel();
    }

    // ----- Linking / mesh -----

    public boolean isLinked() {
        return channelTargets.values().stream().anyMatch(l -> !l.isEmpty());
    }

    /** Returns targets for the active channel. */
    public List<BlockPos> getLinkedTargetPositions() {
        return Collections.unmodifiableList(channelList(activeChannel));
    }

    /** Returns targets for a specific channel. */
    public List<BlockPos> getLinkedTargetPositions(int channel) {
        return Collections.unmodifiableList(channelList(channel));
    }

    /** Returns the full channel map (read-only view). */
    public Map<Integer, List<BlockPos>> getAllChannelTargets() {
        return Collections.unmodifiableMap(channelTargets);
    }

    /** Primary linked target: first of the active channel, or first of any non-empty channel. */
    public @Nullable BlockPos getLinkedTargetPos() {
        List<BlockPos> active = channelList(activeChannel);
        if (!active.isEmpty()) return active.get(0);
        for (int ch = 1; ch <= MAX_CHANNELS; ch++) {
            List<BlockPos> list = channelTargets.get(ch);
            if (list != null && !list.isEmpty()) return list.get(0);
        }
        return null;
    }

    /** Replace the active channel's mesh (called when the keyboard item is placed). */
    public void setLinkedTargets(List<BlockPos> positions) {
        List<BlockPos> list = channelList(activeChannel);
        list.clear();
        for (BlockPos p : positions) list.add(p.immutable());
        setChanged();
        UniversalKeyboardMod.LOGGER.info("mesh set on channel {}: {} target(s)", activeChannel, list.size());
    }

    /** Replace all channels at once (called on keyboard item placement). */
    public void setAllChannelTargets(Map<Integer, List<BlockPos>> allTargets) {
        channelTargets.clear();
        for (Map.Entry<Integer, List<BlockPos>> entry : allTargets.entrySet()) {
            if (entry.getKey() < 1 || entry.getKey() > MAX_CHANNELS) continue;
            List<BlockPos> copy = new ArrayList<>();
            for (BlockPos p : entry.getValue()) copy.add(p.immutable());
            if (!copy.isEmpty()) channelTargets.put(entry.getKey(), copy);
        }
        setChanged();
        int total = channelTargets.values().stream().mapToInt(List::size).sum();
        UniversalKeyboardMod.LOGGER.info("all channels set: {} channels, {} total targets",
                channelTargets.size(), total);
    }

    public void unlink() {
        if (level != null && !level.isClientSide) {
            for (List<BlockPos> list : channelTargets.values())
                for (BlockPos pos : list)
                    PeripheralHelper.releaseThrusterControl(level, pos);
        }
        channelTargets.clear();
        stopSequencer();
        setChanged();
    }

    public boolean isTargetInRange() {
        BlockPos primary = getLinkedTargetPos();
        if (primary == null || level == null) return false;
        double range = ModConfig.COMMON.keyboardRange.get();
        return worldPosition.distSqr(primary) <= range * range;
    }

    public boolean isLinkedAsComputer() {
        BlockPos primary = getLinkedTargetPos();
        if (primary == null || level == null) return false;
        BlockEntity be = level.getBlockEntity(primary);
        return be != null && KeyboardMode.isCCComputer(be);
    }

    public boolean isLinkedAsCreate() {
        BlockPos primary = getLinkedTargetPos();
        if (primary == null || level == null) return false;
        BlockEntity be = level.getBlockEntity(primary);
        return be != null && CreateValueHelper.hasScrollValue(be);
    }

    public boolean isComputerInRange() { return isLinkedAsComputer() && isTargetInRange(); }

    // ----- CC keyboard event forwarding — active channel targets in range -----

    public void sendKeyEvent(int keyCode, boolean held)  { queueEventOnLinkedComputer("key", keyCode, held); }
    public void sendCharEvent(char ch)                    { queueEventOnLinkedComputer("char", String.valueOf(ch)); }
    public void sendKeyUpEvent(int keyCode)              { queueEventOnLinkedComputer("key_up", keyCode); }

    // ----- Create inline capture -----

    public boolean isInlineCapturing() { return inlineCaptureBuffer != null; }
    public void startInlineCapture()   { inlineCaptureBuffer = ""; }

    public void inlineCaptureChar(char ch) {
        if (inlineCaptureBuffer == null) return;
        if (ch == '\n' || ch == '\r') {
            String input = inlineCaptureBuffer.trim();
            inlineCaptureBuffer = "";
            if (!input.isEmpty()) applyCreateValueScript(input);
        } else if (ch == 8) {
            if (!inlineCaptureBuffer.isEmpty())
                inlineCaptureBuffer = inlineCaptureBuffer.substring(0, inlineCaptureBuffer.length() - 1);
        } else {
            inlineCaptureBuffer += ch;
        }
    }

    public void inlineCaptureEsc() {
        if (inlineCaptureBuffer == null) return;
        String input = inlineCaptureBuffer.trim();
        inlineCaptureBuffer = null;
        if (!input.isEmpty()) applyCreateValueScript(input);
    }

    /** Applies a Create scroll-value script to every active-channel target in range. */
    public void applyCreateValueScript(String script) {
        List<BlockPos> targets = getLinkedTargetPositions();
        if (targets.isEmpty() || level == null || level.isClientSide) return;

        String trimmed = script.trim();
        if (trimmed.isEmpty()) return;

        boolean isAdd = trimmed.startsWith("+");
        boolean isSub = trimmed.startsWith("--");
        int parsedValue;
        try {
            parsedValue = Integer.parseInt(isAdd ? trimmed.substring(1) : isSub ? trimmed.substring(2) : trimmed);
        } catch (NumberFormatException e) {
            UniversalKeyboardMod.LOGGER.warn("bad Create script input '{}': {}", trimmed, e.getMessage());
            return;
        }

        double range = ModConfig.COMMON.keyboardRange.get();
        double rangeSq = range * range;

        for (BlockPos targetPos : targets) {
            if (worldPosition.distSqr(targetPos) > rangeSq) continue;
            BlockEntity target = level.getBlockEntity(targetPos);
            if (target == null || !CreateValueHelper.hasScrollValue(target)) continue;

            if (isAdd) {
                int max = CreateValueHelper.getMax(target);
                CreateValueHelper.setValue(target, Math.min(CreateValueHelper.getValue(target) + parsedValue, max));
            } else if (isSub) {
                int min = CreateValueHelper.getMin(target);
                CreateValueHelper.setValue(target, Math.max(CreateValueHelper.getValue(target) - parsedValue, min));
            } else {
                CreateValueHelper.setValue(target, parsedValue);
            }
        }
    }

    // ----- autoscript / redstone -----

    public String  getAutoTypeScript()    { return autoTypeScript; }
    public int     getScriptLineIndex()   { return scriptLineIndex; }

    public void setAutoTypeScript(String script) {
        this.autoTypeScript  = script == null ? "" : script;
        this.scriptLineIndex = 0;
        this.setChanged();
    }

    private String[] getScriptLines() {
        return autoTypeScript.lines().filter(l -> !l.isBlank()).toArray(String[]::new);
    }

    public void startAutoType() {
        String[] lines = getScriptLines();
        if (lines.length == 0) return;
        if (scriptLineIndex >= lines.length) scriptLineIndex = 0;

        String currentLine = lines[scriptLineIndex];
        scriptLineIndex = (scriptLineIndex + 1) % lines.length;
        setChanged();

        if (isLinkedAsCreate()) applyCreateValueScript(currentLine);

        if (isLinkedAsComputer()) {
            typeQueue.clear();
            typeTimer = 0;
            for (char c : currentLine.toCharArray()) typeQueue.add(c);
            typeQueue.add('\n');
        }
    }

    public void onRedstoneChanged(boolean powered) {
        if (powered && !wasPowered) {
            turnOnLinkedComputer();
            startAutoType();
        }
        wasPowered = powered;
        setChanged();
    }

    public boolean wasPowered() { return wasPowered; }

    // ----- Sequencer API -----

    public List<SequencerStep> getSequencerSteps()  { return Collections.unmodifiableList(sequencerSteps); }
    public boolean isSequencerRunning()             { return sequencerRunning; }
    public int     getSequencerCurrentStep()        { return sequencerCurrentStep; }

    public void setSequencerSteps(List<SequencerStep> steps) {
        sequencerSteps.clear();
        sequencerSteps.addAll(steps);
        setChanged();
    }

    public void startSequencer() {
        if (sequencerSteps.isEmpty()) return;
        sequencerRunning     = true;
        sequencerCurrentStep = 0;
        sequencerDelayTicker = 0;
        java.util.Arrays.fill(sequencerVars, 0.0);
        setChanged();
    }

    public void stopSequencer() {
        sequencerRunning = false;
        clearRedstoneOutputs();
        clearWirelessOutputs();
        setChanged();
    }

    public int getRedstoneOutput(Direction dir) {
        return redstoneOutputs[dir.ordinal()];
    }

    private void setRedstoneOutput(Direction dir, int power) {
        int clamped = Math.max(0, Math.min(15, power));
        if (redstoneOutputs[dir.ordinal()] == clamped) return;
        redstoneOutputs[dir.ordinal()] = clamped;
        setChanged();
        if (level != null && !level.isClientSide)
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    // ── Wireless redstone outputs (Create RedstoneLink) ─────────────────────

    public int getWirelessCount() { return wirelessEntries.size(); }

    public List<WirelessEntry> getWirelessEntries() {
        return Collections.unmodifiableList(wirelessEntries);
    }

    /** Adds an empty entry if room remains. Returns the new entry's index (0-based) or -1 on failure. */
    public int addWirelessEntry() {
        if (!CreateWirelessHelper.isPresent()) return -1;
        if (wirelessEntries.size() >= MAX_WIRELESS) return -1;
        wirelessEntries.add(CreateWirelessHelper.newEntry(worldPosition));
        setChanged();
        syncToClients();
        return wirelessEntries.size() - 1;
    }

    public void removeWirelessEntry(int idx) {
        if (idx < 0 || idx >= wirelessEntries.size()) return;
        WirelessEntry e = wirelessEntries.remove(idx);
        CreateWirelessHelper.removeFromNetwork(level, e);
        setChanged();
        syncToClients();
    }

    public void setWirelessFrequencyItem(int idx, boolean first, ItemStack stack) {
        if (idx < 0 || idx >= wirelessEntries.size()) return;
        CreateWirelessHelper.updateFrequency(level, wirelessEntries.get(idx), first, stack);
        setChanged();
        syncToClients();
    }

    /** Sequencer / external callers: drive the W{idx+1} output to the given power. */
    public void setWirelessOutput(int idx, int power) {
        if (idx < 0 || idx >= wirelessEntries.size()) return;
        CreateWirelessHelper.setEntryPower(level, wirelessEntries.get(idx), power);
    }

    public int getWirelessOutput(int idx) {
        if (idx < 0 || idx >= wirelessEntries.size()) return 0;
        return wirelessEntries.get(idx).getPower();
    }

    private void clearWirelessOutputs() {
        for (WirelessEntry e : wirelessEntries)
            CreateWirelessHelper.setEntryPower(level, e, 0);
    }

    /**
     * Resolves a sequencer value source string to a double.
     * Formats: number literal | "V1"-"V8" (variable) | "RS:N/S/E/W" (redstone in) | getter name (uses channel)
     */
    double resolveSource(String src, int channel) {
        if (src == null || src.isBlank()) return 0;
        src = src.trim();
        if (src.matches("V[1-8]")) return sequencerVars[src.charAt(1) - '1'];
        if (src.startsWith("RS:")) {
            String d = src.substring(3).toUpperCase();
            Direction dir = switch (d) {
                case "N", "NORTH" -> Direction.NORTH;
                case "S", "SOUTH" -> Direction.SOUTH;
                case "E", "EAST"  -> Direction.EAST;
                case "W", "WEST"  -> Direction.WEST;
                default -> null;
            };
            if (dir != null && level != null)
                return level.getSignal(worldPosition.relative(dir), dir);
            return 0;
        }
        try { return Double.parseDouble(src); } catch (NumberFormatException ignored) {}
        if (level == null) return 0;
        // Sable sublevel getter
        if (SableCompat.isPresent() && SableCompat.isSableGetter(src) && SableCompat.isOnSublevel(level, worldPosition))
            return SableCompat.getValue(level, worldPosition, src);
        List<BlockPos> targets = getLinkedTargetPositions(channel);
        if (targets.isEmpty()) return 0;
        Object p = PeripheralHelper.getPeripheral(level, targets.get(0));
        return p == null ? 0 : PeripheralHelper.getDoubleGetter(p, src);
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

    private void clearRedstoneOutputs() {
        boolean changed = false;
        for (int i = 0; i < redstoneOutputs.length; i++) {
            if (redstoneOutputs[i] != 0) { redstoneOutputs[i] = 0; changed = true; }
        }
        if (changed && level != null && !level.isClientSide)
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    void tickSequencer() {
        if (sequencerCurrentStep >= sequencerSteps.size()) {
            sequencerRunning = false;
            setChanged();
            return;
        }
        SequencerStep step = sequencerSteps.get(sequencerCurrentStep);
        switch (step.type) {
            case DELAY -> {
                if (sequencerDelayTicker <= 0) {
                    float secs = 1.0f;
                    try { secs = Float.parseFloat(step.delaySecondsStr); } catch (NumberFormatException ignored) {}
                    sequencerDelayTicker = Math.max(1, Math.round(secs * 20));
                }
                if (--sequencerDelayTicker <= 0) advanceSequencer();
            }
            case IF -> {
                if (evaluateIfCondition(step)) {
                    if (step.ifGoTo) {
                        sequencerCurrentStep = Math.max(0, Math.min(step.jumpTarget - 1, sequencerSteps.size() - 1));
                        sequencerDelayTicker = 0;
                        setChanged();
                        return;
                    } else {
                        sequencerCurrentStep += step.ifSkipCount;
                    }
                }
                advanceSequencer();
            }
            case CONDITION -> {
                if (evaluateCondition(step)) advanceSequencer();
            }
            case SET_VALUE -> {
                applySequencerSetValue(step);
                advanceSequencer();
            }
            case SET_REDSTONE -> {
                applySequencerSetRedstone(step);
                advanceSequencer();
            }
            case TYPE_TEXT -> {
                applySequencerTypeText(step);
                advanceSequencer();
            }
            case JUMP -> {
                int target = Math.max(0, Math.min(step.jumpTarget - 1, sequencerSteps.size() - 1));
                sequencerCurrentStep = target;
                sequencerDelayTicker = 0;
                setChanged();
            }
            case MATH -> {
                double a = resolveSource(step.mathA, step.mathACh);
                double b = resolveSource(step.mathB, step.mathBCh);
                double result = applyMathOp(step.mathOp, a, b);
                String dest = step.mathDest != null ? step.mathDest.trim() : "V1";
                if (dest.matches("V[1-8]")) sequencerVars[dest.charAt(1) - '1'] = result;
                advanceSequencer();
            }
            case CYCLE -> {
                sequencerCurrentStep = 0;
                sequencerDelayTicker = 0;
                setChanged();
            }
            case END -> {
                sequencerRunning = false;
                setChanged();
            }
        }
    }

    private void advanceSequencer() {
        sequencerCurrentStep++;
        sequencerDelayTicker = 0;
        setChanged();
    }

    private boolean evaluateIfCondition(SequencerStep step) {
        if (level == null || step.ifGetter.isEmpty()) return false;
        double actual;
        int rsIdx = -1;
        for (int i = 0; i < SequencerStep.RS_INPUT_GETTER_NAMES.length; i++) {
            if (SequencerStep.RS_INPUT_GETTER_NAMES[i].equals(step.ifGetter)) { rsIdx = i; break; }
        }
        if (rsIdx >= 0) {
            Direction dir = SequencerStep.RS_INPUT_GETTER_DIRS[rsIdx];
            actual = level.getSignal(worldPosition.relative(dir), dir);
        } else if (SableCompat.isPresent() && SableCompat.isSableGetter(step.ifGetter) && SableCompat.isOnSublevel(level, worldPosition)) {
            actual = SableCompat.getValue(level, worldPosition, step.ifGetter);
        } else {
            List<BlockPos> ch = getLinkedTargetPositions(step.channel);
            if (ch.isEmpty()) return false;
            Object p = PeripheralHelper.getPeripheral(level, ch.get(0));
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
        if (level == null) return false;
        double actual;
        Direction dir = step.conditionSource.direction;
        if (dir != null) {
            actual = level.getSignal(worldPosition.relative(dir), dir);
        } else if (SableCompat.isPresent() && SableCompat.isSableGetter(step.conditionGetter) && SableCompat.isOnSublevel(level, worldPosition)) {
            actual = SableCompat.getValue(level, worldPosition, step.conditionGetter);
        } else {
            List<BlockPos> ch = getLinkedTargetPositions(step.channel);
            if (ch.isEmpty()) return false;
            Object p = PeripheralHelper.getPeripheral(level, ch.get(0));
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

    private void applySequencerSetValue(SequencerStep step) {
        List<BlockPos> targets = getLinkedTargetPositions(step.channel);
        if (targets.isEmpty() || level == null || step.setMethod.isEmpty()) return;
        double value = resolveSource(step.setValueStr, step.channel);
        double range   = ModConfig.COMMON.keyboardRange.get();
        double rangeSq = range * range;
        for (BlockPos targetPos : targets) {
            if (worldPosition.distSqr(targetPos) > rangeSq) continue;
            Object p = PeripheralHelper.getPeripheral(level, targetPos);
            if (p != null) PeripheralHelper.callMethodWithDouble(p, step.setMethod, value);
        }
    }

    private void applySequencerTypeText(SequencerStep step) {
        for (char c : step.typeTextStr.toCharArray()) typeQueue.add(c);
        if (step.typeTextEnter) typeQueue.add('\n');
    }

    private void applySequencerSetRedstone(SequencerStep step) {
        int signal = (int) Math.round(resolveSource(step.redstoneOutSignalStr, 1));
        if (step.wirelessOutIdx > 0)
            setWirelessOutput(step.wirelessOutIdx - 1, signal);
        else
            setRedstoneOutput(step.redstoneOutDir, signal);
    }

    // ----- Display source data (Create integration) -----

    public Map<String, String> getCachedGetterValues() { return cachedGetterValues; }
    public String getCachedPeripheralType()            { return cachedPeripheralType; }

    void refreshPeripheralCache() {
        BlockPos primary = getLinkedTargetPos();
        if (primary == null || level == null || level.isClientSide) return;
        if (isLinkedAsComputer()) {
            cachedGetterValues.clear();
            cachedPeripheralType = "";
            return;
        }
        try {
            var result = dev.bennethogan.bennetsmod.compat.PeripheralHelper.scanAndCall(
                    level, primary, "", "");
            if (result == null) {
                cachedGetterValues.clear();
                cachedPeripheralType = "";
                return;
            }
            cachedPeripheralType = result.type();
            cachedGetterValues.clear();
            for (String[] getter : result.getters())
                cachedGetterValues.put(getter[0], getter[1]);
        } catch (Exception e) {
            UniversalKeyboardMod.LOGGER.warn("peripheral cache refresh failed: {}", e.getMessage());
        }
    }

    public @Nullable Object getPeripheral() {
        if (peripheral == null) {
            try {
                peripheral = new dev.bennethogan.bennetsmod.peripheral.KeyboardPeripheral(this);
            } catch (NoClassDefFoundError e) {
                return null;
            }
        }
        return peripheral;
    }

    // ----- Ticking -----

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  LinkedKeyboardBlockEntity be) {
        if (!be.typeQueue.isEmpty()) {
            if (be.typeTimer <= 0) {
                char c = be.typeQueue.poll();
                if (c == '\n') {
                    be.queueEventOnLinkedComputer("key", 257, false);
                    be.queueEventOnLinkedComputer("key_up", 257);
                } else {
                    be.queueEventOnLinkedComputer("char", String.valueOf(c));
                }
                be.typeTimer = TICKS_PER_CHAR;
            } else {
                be.typeTimer--;
            }
        }

        if (be.peripheralRefreshTimer-- <= 0) {
            be.peripheralRefreshTimer = PERIPHERAL_REFRESH_TICKS;
            be.refreshPeripheralCache();
        }

        if (be.sequencerRunning) be.tickSequencer();

        // Remove broken targets across all channels (only when loaded)
        if (level.getGameTime() % 100 == 0 && be.isLinked()) {
            boolean changed = false;
            for (List<BlockPos> list : be.channelTargets.values())
                changed |= list.removeIf(t -> level.isLoaded(t) && level.getBlockEntity(t) == null);
            if (changed) be.setChanged();
        }
    }

    public void onRemoved() {
        unlink();
    }

    // ----- CC computer interaction — broadcasts to active-channel targets in range -----

    void queueEventOnLinkedComputer(String event, Object... args) {
        List<BlockPos> targets = getLinkedTargetPositions();
        if (targets.isEmpty() || level == null || level.isClientSide) return;
        double range = ModConfig.COMMON.keyboardRange.get();
        double rangeSq = range * range;
        for (BlockPos targetPos : targets) {
            if (worldPosition.distSqr(targetPos) > rangeSq) continue;
            BlockEntity target = level.getBlockEntity(targetPos);
            if (target == null || !KeyboardMode.isCCComputer(target)) continue;
            try {
                var serverComputer = target.getClass().getMethod("getServerComputer").invoke(target);
                if (serverComputer == null) continue;
                serverComputer.getClass().getMethod("queueEvent", String.class, Object[].class)
                        .invoke(serverComputer, event, args);
            } catch (Exception e) {
                UniversalKeyboardMod.LOGGER.warn("could not queue CC event '{}': {}", event, e.getMessage());
            }
        }
    }

    public void turnOnLinkedComputer() {
        List<BlockPos> targets = getLinkedTargetPositions();
        if (targets.isEmpty() || level == null || level.isClientSide) return;
        for (BlockPos targetPos : targets) {
            BlockEntity target = level.getBlockEntity(targetPos);
            if (target == null || !KeyboardMode.isCCComputer(target)) continue;
            try {
                var sc = target.getClass().getMethod("getServerComputer").invoke(target);
                if (sc != null) sc.getClass().getMethod("turnOn").invoke(sc);
            } catch (Exception e) {
                UniversalKeyboardMod.LOGGER.warn("could not turn on computer: {}", e.getMessage());
            }
        }
    }

    // ----- NBT -----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        tag.putInt("active_channel", activeChannel);

        for (Map.Entry<Integer, List<BlockPos>> entry : channelTargets.entrySet()) {
            List<BlockPos> list = entry.getValue();
            if (list.isEmpty()) continue;
            ListTag listTag = new ListTag();
            for (BlockPos p : list) {
                CompoundTag e = new CompoundTag();
                e.putInt("x", p.getX()); e.putInt("y", p.getY()); e.putInt("z", p.getZ());
                listTag.add(e);
            }
            tag.put("ch" + entry.getKey() + "_targets", listTag);
        }

        tag.putBoolean("was_powered", wasPowered);
        tag.putString("autotype_script", autoTypeScript);
        tag.putInt("script_line_index", scriptLineIndex);
        if (!sequencerSteps.isEmpty()) {
            ListTag seqList = new ListTag();
            for (SequencerStep step : sequencerSteps) seqList.add(step.save());
            tag.put("sequencer_steps", seqList);
        }
        tag.putBoolean("sequencer_running",      sequencerRunning);
        tag.putInt("sequencer_current_step",     sequencerCurrentStep);
        tag.putIntArray("redstone_outputs",      redstoneOutputs);

        if (!wirelessEntries.isEmpty()) {
            ListTag wl = new ListTag();
            for (WirelessEntry e : wirelessEntries) {
                CompoundTag c = new CompoundTag();
                c.put("first",  e.getFirstStack().saveOptional(registries));
                c.put("second", e.getSecondStack().saveOptional(registries));
                wl.add(c);
            }
            tag.put("wireless_entries", wl);
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        channelTargets.clear();

        activeChannel = tag.contains("active_channel") ? tag.getInt("active_channel") : 1;
        activeChannel = Math.max(1, Math.min(MAX_CHANNELS, activeChannel));

        // Per-channel targets (new format)
        for (int ch = 1; ch <= MAX_CHANNELS; ch++) {
            String key = "ch" + ch + "_targets";
            if (tag.contains(key, Tag.TAG_LIST)) {
                ListTag listTag = tag.getList(key, Tag.TAG_COMPOUND);
                List<BlockPos> list = new ArrayList<>();
                for (int i = 0; i < listTag.size(); i++) {
                    CompoundTag e = listTag.getCompound(i);
                    list.add(new BlockPos(e.getInt("x"), e.getInt("y"), e.getInt("z")));
                }
                if (!list.isEmpty()) channelTargets.put(ch, list);
            }
        }

        // Legacy: old single-channel mesh_targets → channel 1
        if (channelTargets.isEmpty()) {
            List<BlockPos> legacy = new ArrayList<>();
            if (tag.contains("mesh_targets", Tag.TAG_LIST)) {
                ListTag list = tag.getList("mesh_targets", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag e = list.getCompound(i);
                    legacy.add(new BlockPos(e.getInt("x"), e.getInt("y"), e.getInt("z")));
                }
            } else if (tag.contains("target_x")) {
                legacy.add(new BlockPos(tag.getInt("target_x"), tag.getInt("target_y"), tag.getInt("target_z")));
            } else if (tag.contains("linked_x")) {
                legacy.add(new BlockPos(tag.getInt("linked_x"), tag.getInt("linked_y"), tag.getInt("linked_z")));
            } else if (tag.contains("create_x")) {
                legacy.add(new BlockPos(tag.getInt("create_x"), tag.getInt("create_y"), tag.getInt("create_z")));
            } else if (tag.contains("periph_x")) {
                legacy.add(new BlockPos(tag.getInt("periph_x"), tag.getInt("periph_y"), tag.getInt("periph_z")));
            }
            if (!legacy.isEmpty()) channelTargets.put(1, legacy);
        }

        wasPowered      = tag.getBoolean("was_powered");
        autoTypeScript  = tag.getString("autotype_script");
        scriptLineIndex = tag.getInt("script_line_index");
        sequencerSteps.clear();
        if (tag.contains("sequencer_steps", Tag.TAG_LIST)) {
            ListTag seqList = tag.getList("sequencer_steps", Tag.TAG_COMPOUND);
            for (int i = 0; i < seqList.size(); i++)
                sequencerSteps.add(SequencerStep.load(seqList.getCompound(i)));
        }
        sequencerRunning     = tag.getBoolean("sequencer_running");
        sequencerCurrentStep = tag.getInt("sequencer_current_step");
        if (tag.contains("redstone_outputs")) {
            int[] saved = tag.getIntArray("redstone_outputs");
            System.arraycopy(saved, 0, redstoneOutputs, 0, Math.min(saved.length, redstoneOutputs.length));
        }

        wirelessEntries.clear();
        if (tag.contains("wireless_entries", Tag.TAG_LIST) && CreateWirelessHelper.isPresent()) {
            ListTag wl = tag.getList("wireless_entries", Tag.TAG_COMPOUND);
            for (int i = 0; i < wl.size() && wirelessEntries.size() < MAX_WIRELESS; i++) {
                CompoundTag c = wl.getCompound(i);
                WirelessEntry e = CreateWirelessHelper.newEntry(worldPosition);
                e.setFirstStack(ItemStack.parseOptional(registries, c.getCompound("first")));
                e.setSecondStack(ItemStack.parseOptional(registries, c.getCompound("second")));
                wirelessEntries.add(e);
            }
        }
    }

    @Override
    public void setRemoved() {
        for (WirelessEntry e : wirelessEntries)
            CreateWirelessHelper.removeFromNetwork(level, e);
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        for (WirelessEntry e : wirelessEntries)
            CreateWirelessHelper.removeFromNetwork(level, e);
        super.onChunkUnloaded();
    }

    // ── BlockEntity client sync ──────────────────────────────────────────────

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    /** Force a sync of this BE to all watching clients. Call after wireless config changes. */
    public void syncToClients() {
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
}
