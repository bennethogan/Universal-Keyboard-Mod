package dev.bennethogan.bennetsmod.blockentity;

import dev.bennethogan.bennetsmod.UniversalKeyboardMod;
import dev.bennethogan.bennetsmod.compat.CreateValueHelper;
import dev.bennethogan.bennetsmod.compat.KeyboardMode;
import dev.bennethogan.bennetsmod.compat.PeripheralHelper;
import dev.bennethogan.bennetsmod.config.ModConfig;
import dev.bennethogan.bennetsmod.sequencer.SequencerStep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class LinkedKeyboardBlockEntity extends BlockEntity {

    // All linked targets — must all be the same BlockEntityType (mesh constraint).
    private final List<BlockPos> linkedTargetPositions = new ArrayList<>();

    private String  autoTypeScript  = "";
    private boolean wasPowered      = false;
    private int     scriptLineIndex = 0;

    private @Nullable Object peripheral = null; // KeyboardPeripheral, typed as Object so we load without CC
    private final Queue<Character> typeQueue = new LinkedList<>();
    private int typeTimer = 0;
    private static final int TICKS_PER_CHAR = 2;

    private @Nullable String inlineCaptureBuffer = null;

    // ----- Peripheral Sequencer -----

    private final List<SequencerStep> sequencerSteps    = new ArrayList<>();
    private boolean sequencerRunning     = false;
    private int     sequencerCurrentStep = 0;
    private int     sequencerDelayTicker = 0; // ticks remaining for current DELAY step

    // Redstone outputs written by SET_REDSTONE steps — indexed by Direction.ordinal()
    private final int[] redstoneOutputs = new int[Direction.values().length];

    // Cached peripheral getter values for Create display source
    private final Map<String, String> cachedGetterValues = new LinkedHashMap<>();
    private String cachedPeripheralType = "";
    private int peripheralRefreshTimer = 0;
    private static final int PERIPHERAL_REFRESH_TICKS = 20;

    public LinkedKeyboardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LINKED_KEYBOARD.get(), pos, state);
    }

    // ----- linking / mesh -----

    public boolean isLinked()                              { return !linkedTargetPositions.isEmpty(); }
    public List<BlockPos> getLinkedTargetPositions()       { return Collections.unmodifiableList(linkedTargetPositions); }

    /** Returns the primary (first) linked target, used for mode detection and peripheral scan. */
    public @Nullable BlockPos getLinkedTargetPos() {
        return linkedTargetPositions.isEmpty() ? null : linkedTargetPositions.get(0);
    }

    /** Replace the whole mesh with the given list (called when keyboard item is placed). */
    public void setLinkedTargets(List<BlockPos> positions) {
        linkedTargetPositions.clear();
        for (BlockPos p : positions) linkedTargetPositions.add(p.immutable());
        setChanged();
        UniversalKeyboardMod.LOGGER.info("mesh set: {} target(s)", linkedTargetPositions.size());
    }

    public void unlink() {
        if (level != null && !level.isClientSide) {
            for (BlockPos pos : linkedTargetPositions)
                PeripheralHelper.releaseThrusterControl(level, pos);
        }
        linkedTargetPositions.clear();
        stopSequencer(); // clears redstone outputs too
        setChanged();
    }

    public boolean isTargetInRange() {
        if (linkedTargetPositions.isEmpty() || level == null) return false;
        double range = ModConfig.COMMON.keyboardRange.get();
        return worldPosition.distSqr(linkedTargetPositions.get(0)) <= range * range;
    }

    public boolean isLinkedAsComputer() {
        if (linkedTargetPositions.isEmpty() || level == null) return false;
        BlockEntity be = level.getBlockEntity(linkedTargetPositions.get(0));
        return be != null && KeyboardMode.isCCComputer(be);
    }

    public boolean isLinkedAsCreate() {
        if (linkedTargetPositions.isEmpty() || level == null) return false;
        BlockEntity be = level.getBlockEntity(linkedTargetPositions.get(0));
        return be != null && CreateValueHelper.hasScrollValue(be);
    }

    public boolean isComputerInRange() { return isLinkedAsComputer() && isTargetInRange(); }

    // ----- CC keyboard event forwarding — broadcasts to all mesh targets in range -----

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

    /** Applies a Create scroll-value script to every mesh target in range. */
    public void applyCreateValueScript(String script) {
        if (linkedTargetPositions.isEmpty() || level == null || level.isClientSide) return;

        String trimmed = script.trim();
        if (trimmed.isEmpty()) return;

        // Parse once, apply to all in-range Create targets
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

        for (BlockPos targetPos : linkedTargetPositions) {
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
        setChanged();
    }

    public void stopSequencer() {
        sequencerRunning = false;
        clearRedstoneOutputs();
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

    private boolean evaluateCondition(SequencerStep step) {
        if (level == null) return false;
        double actual;
        Direction dir = step.conditionSource.direction;
        if (dir != null) {
            // Ask the neighboring block what signal it emits toward us (same convention as Level.hasNeighborSignal)
            actual = level.getSignal(worldPosition.relative(dir), dir);
        } else {
            BlockPos primary = getLinkedTargetPos();
            if (primary == null) return false;
            Object p = PeripheralHelper.getPeripheral(level, primary);
            if (p == null) return false;
            actual = PeripheralHelper.getDoubleGetter(p, step.conditionGetter);
        }
        double threshold;
        try   { threshold = Double.parseDouble(step.conditionThresholdStr); }
        catch (NumberFormatException e) { threshold = 0; }
        return switch (step.conditionOp) {
            case ">" -> actual > threshold;
            case "<" -> actual < threshold;
            case "=" -> Math.abs(actual - threshold) < 0.001;
            default  -> false;
        };
    }

    private void applySequencerSetValue(SequencerStep step) {
        if (linkedTargetPositions.isEmpty() || level == null || step.setMethod.isEmpty()) return;
        double value;
        try   { value = Double.parseDouble(step.setValueStr); }
        catch (NumberFormatException e) { return; }
        double range   = ModConfig.COMMON.keyboardRange.get();
        double rangeSq = range * range;
        for (BlockPos targetPos : linkedTargetPositions) {
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
        int signal;
        try   { signal = Integer.parseInt(step.redstoneOutSignalStr.trim()); }
        catch (NumberFormatException e) { signal = 0; }
        setRedstoneOutput(step.redstoneOutDir, signal);
    }

    // ----- Display source data (Create integration) -----

    public Map<String, String> getCachedGetterValues() { return cachedGetterValues; }
    public String getCachedPeripheralType()            { return cachedPeripheralType; }

    void refreshPeripheralCache() {
        if (linkedTargetPositions.isEmpty() || level == null || level.isClientSide) return;
        // CC computers expose zero-arg @LuaFunction methods like turnOn() — calling them
        // via scanMethods() would power-cycle the computer every refresh tick.
        if (isLinkedAsComputer()) {
            cachedGetterValues.clear();
            cachedPeripheralType = "";
            return;
        }
        BlockPos primaryPos = linkedTargetPositions.get(0);
        try {
            var result = dev.bennethogan.bennetsmod.compat.PeripheralHelper.scanAndCall(
                    level, primaryPos, "", "");
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

    // KeyboardPeripheral instance — for CC code calling peripheral.wrap on this block
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

    // ----- ticking -----

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

        // Remove any mesh targets whose block entity was broken (only when chunk is loaded)
        if (level.getGameTime() % 100 == 0 && !be.linkedTargetPositions.isEmpty()) {
            boolean changed = be.linkedTargetPositions.removeIf(
                    t -> level.isLoaded(t) && level.getBlockEntity(t) == null);
            if (changed) be.setChanged();
        }
    }

    public void onRemoved() {
        unlink();
    }

    // ----- CC computer interaction — broadcasts to all mesh targets in range -----

    void queueEventOnLinkedComputer(String event, Object... args) {
        if (linkedTargetPositions.isEmpty() || level == null || level.isClientSide) return;
        double range = ModConfig.COMMON.keyboardRange.get();
        double rangeSq = range * range;
        for (BlockPos targetPos : linkedTargetPositions) {
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
        if (linkedTargetPositions.isEmpty() || level == null || level.isClientSide) return;
        for (BlockPos targetPos : linkedTargetPositions) {
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
        if (!linkedTargetPositions.isEmpty()) {
            ListTag list = new ListTag();
            for (BlockPos p : linkedTargetPositions) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("x", p.getX());
                entry.putInt("y", p.getY());
                entry.putInt("z", p.getZ());
                list.add(entry);
            }
            tag.put("mesh_targets", list);
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
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        linkedTargetPositions.clear();

        if (tag.contains("mesh_targets", Tag.TAG_LIST)) {
            ListTag list = tag.getList("mesh_targets", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                linkedTargetPositions.add(new BlockPos(e.getInt("x"), e.getInt("y"), e.getInt("z")));
            }
        } else if (tag.contains("target_x")) {
            linkedTargetPositions.add(new BlockPos(tag.getInt("target_x"), tag.getInt("target_y"), tag.getInt("target_z")));
        } else if (tag.contains("linked_x")) {
            linkedTargetPositions.add(new BlockPos(tag.getInt("linked_x"), tag.getInt("linked_y"), tag.getInt("linked_z")));
        } else if (tag.contains("create_x")) {
            linkedTargetPositions.add(new BlockPos(tag.getInt("create_x"), tag.getInt("create_y"), tag.getInt("create_z")));
        } else if (tag.contains("periph_x")) {
            linkedTargetPositions.add(new BlockPos(tag.getInt("periph_x"), tag.getInt("periph_y"), tag.getInt("periph_z")));
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
    }
}
