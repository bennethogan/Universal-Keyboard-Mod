package dev.bennethogan.universalkeyboard.blockentity;

import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import dev.bennethogan.universalkeyboard.compat.CreateValueHelper;
import dev.bennethogan.universalkeyboard.compat.KeyboardMode;
import dev.bennethogan.universalkeyboard.compat.MonitorHelper;
import dev.bennethogan.universalkeyboard.compat.PeripheralHelper;
import dev.bennethogan.universalkeyboard.compat.SableCompat;
import dev.bennethogan.universalkeyboard.compat.rslink.CreateRsLinkHelper;
import dev.bennethogan.universalkeyboard.compat.rslink.RsLinkEntry;
import dev.bennethogan.universalkeyboard.compat.rslink.RsLinkPresence;
import dev.bennethogan.universalkeyboard.config.ModConfig;
import dev.bennethogan.universalkeyboard.livecontrol.FavoriteScreen;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import dev.bennethogan.universalkeyboard.sequencer.SequencerStep;
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

    // ----- Sable ship mass -----
    // The keyboard contributes only this fraction of a normal block's mass to its ship.
    private static final double SHIP_MASS_FACTOR = 0.10;
    private java.lang.ref.WeakReference<Object> massSublevelRef = null;
    private double appliedMassDelta = 0.0;

    // ----- Client side control wheel animation-----
    public static final float WHEEL_OUT_TICKS    = 12.0f; // turning further from centre
    public static final float WHEEL_RETURN_TICKS = 16.0f; // settling back toward centre

    private float wheelStartFraction  = 0f;
    private float wheelTargetFraction = 0f;
    private long  wheelAnimStartTick  = Long.MIN_VALUE;

    private float wheelFraction = 0f;

    public void setStoredWheelFraction(float fraction) {
        fraction = Math.max(-1f, Math.min(1f, fraction));
        if (fraction == wheelFraction) return;
        this.wheelFraction = fraction;
        setChanged(); // persist; tracking clients already got the smooth relay packet
    }

    public float getStoredWheelFraction() { return wheelFraction; }

    public void setWheelTarget(float target) {
        if (level == null) return;
        target = Math.max(-1f, Math.min(1f, target));
        if (target == wheelTargetFraction) return;
        long now = level.getGameTime();
        this.wheelStartFraction  = computeFractionAt(now);
        this.wheelTargetFraction = target;
        this.wheelAnimStartTick  = now;
    }

    private float computeFractionAt(long tick) {
        if (wheelAnimStartTick == Long.MIN_VALUE) return wheelTargetFraction;
        float delta = wheelTargetFraction - wheelStartFraction;
        if (delta == 0f) return wheelTargetFraction;
        float duration = wheelDuration(wheelStartFraction, wheelTargetFraction);
        float t = Math.max(0f, Math.min(1f, (tick - wheelAnimStartTick) / duration));
        return wheelStartFraction + delta * wheelSmoothstep(t);
    }

    public static float wheelDuration(float from, float to) {
        return Math.abs(to) >= Math.abs(from) ? WHEEL_OUT_TICKS : WHEEL_RETURN_TICKS;
    }

    public static float wheelSmoothstep(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t * t * (3f - 2f * t);
    }

    public float getWheelStartFraction()  { return wheelStartFraction; }
    public float getWheelTargetFraction() { return wheelTargetFraction; }
    public long  getWheelAnimStartTick()  { return wheelAnimStartTick; }

    // ----- Favorite Screen -----

    private FavoriteScreen favoriteScreen = FavoriteScreen.NONE;

    public FavoriteScreen getFavoriteScreen() { return favoriteScreen; }
    public void setFavoriteScreen(FavoriteScreen fav) { this.favoriteScreen = fav; setChanged(); }

    // ----- Live Control -----

    public static final int MAX_LIVE_BINDINGS = 40;
    public static final int MAX_PROFILES = 4;
    private int activeProfile = 0;
    private final List<List<LiveControlBinding>> profileBindings = new ArrayList<>();

    private List<LiveControlBinding> activeProfileBindings() {
        return profileBindings.get(Math.max(0, Math.min(MAX_PROFILES - 1, activeProfile)));
    }

    public int getActiveProfile() { return activeProfile; }

    public List<LiveControlBinding> getLiveControlBindings() {
        return Collections.unmodifiableList(activeProfileBindings());
    }

    public void setLiveControlBindings(List<LiveControlBinding> bindings) {
        saveProfileBindings(activeProfile, bindings);
    }

    public void saveProfileBindings(int profileIdx, List<LiveControlBinding> bindings) {
        if (profileIdx < 0 || profileIdx >= MAX_PROFILES) return;
        activeProfile = profileIdx;
        List<LiveControlBinding> slot = profileBindings.get(profileIdx);
        slot.clear();
        for (int i = 0; i < Math.min(bindings.size(), MAX_LIVE_BINDINGS); i++)
            slot.add(bindings.get(i));
        setChanged();
        // sync to client needed here for schematic saving, key bindings werent always saving otherwise
        syncToClients();
    }

    public List<List<LiveControlBinding>> getAllProfileBindings() {
        List<List<LiveControlBinding>> result = new ArrayList<>();
        for (List<LiveControlBinding> p : profileBindings)
            result.add(Collections.unmodifiableList(p));
        return result;
    }

    /**
     * Imports key-frequency bindings from a typewriter, replacing wireless entries and
     * live-control bindings while preserving all channel target links*/

    public String applyTypewriterImport(
            List<dev.bennethogan.universalkeyboard.compat.TypewriterHelper.Binding> bindings,
            net.minecraft.core.HolderLookup.Provider registries) {

        if (bindings.isEmpty()) return "No bindings found in the typewriter.";

        // Collect unique (first, second) frequency pairs in the order they appear
        List<net.minecraft.world.item.ItemStack[]> freqs = new ArrayList<>();
        for (var b : bindings) {
            boolean dup = false;
            for (var f : freqs) {
                if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(f[0], b.firstItem())
                        && net.minecraft.world.item.ItemStack.isSameItemSameComponents(f[1], b.secondItem())) {
                    dup = true; break;
                }
            }
            if (!dup) freqs.add(new net.minecraft.world.item.ItemStack[]{
                    b.firstItem().copy(), b.secondItem().copy()});
        }

        if (freqs.size() > MAX_RSLINKS)
            return "Too many unique wireless frequencies (" + freqs.size() + "). " +
                   "Maximum is " + MAX_RSLINKS + ". Reduce bindings in the typewriter first.";

        // Remove existing wireless entries from the network
        if (RsLinkPresence.isPresent() && level != null)
            for (RsLinkEntry e : rsLinkEntries) CreateRsLinkHelper.removeFromNetwork(level, e);
        rsLinkEntries.clear();
        activeProfileBindings().clear();

        // Recreate wireless entries
        for (var freq : freqs) {
            RsLinkEntry e = new RsLinkEntry(worldPosition);
            e.setFirstStack(freq[0]);
            e.setSecondStack(freq[1]);
            rsLinkEntries.add(e);
            if (RsLinkPresence.isPresent() && level != null)
                CreateRsLinkHelper.ensureRegistered(level, e);
        }

        // Create live-control bindings (HLD, wireless RS, signal 15 — exactly what a typewriter emits)
        for (var b : bindings) {
            int wIdx = -1;
            for (int i = 0; i < freqs.size(); i++) {
                if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(freqs.get(i)[0], b.firstItem())
                        && net.minecraft.world.item.ItemStack.isSameItemSameComponents(freqs.get(i)[1], b.secondItem())) {
                    wIdx = i; break;
                }
            }
            if (wIdx < 0) continue;

            LiveControlBinding lcb = new LiveControlBinding();
            lcb.keyCode        = b.keyCode();
            lcb.actionType     = LiveControlBinding.ActionType.REDSTONE;
            lcb.mode           = LiveControlBinding.Mode.HLD;
            lcb.rsLinkIdx      = wIdx + 1; // 1-based (L1..L20)
            lcb.signalStrength = 15;
            lcb.rsSide         = Direction.NORTH; // unused for RS links
            activeProfileBindings().add(lcb);
        }

        setChanged();
        syncToClients(); // sync to client again for schematic saving
        return null; // null = success
    }

    // ----- Peripheral Sequencer -----

    private final List<SequencerStep> sequencerSteps = new ArrayList<>();
    private final SequencerEngine engine = new SequencerEngine(this);
    private final java.util.Set<java.util.UUID> sequencerViewers = new java.util.HashSet<>();

    // Redstone outputs — indexed by Direction.ordinal()
    private final int[] redstoneOutputs = new int[Direction.values().length];

    // Renamed for clarity - These are the Create Redstone Links, interally called L1-L20
    public static final int MAX_RSLINKS = 20;
    private final List<RsLinkEntry> rsLinkEntries = new ArrayList<>();

    // renamed for clarity - this is the wireless Copycats, interally called W1-W100
    public static final int MAX_WIRELESS_FREQS = 100;
    private final String[] wirelessFreqs = new String[MAX_WIRELESS_FREQS];

    // Track postition change so links can work with assembly & printing a schematic
    private BlockPos lastKnownPos = null;
    private boolean suppressPositionDialog = false;
    // new flag that is set with schematic paste, so assembly directly afterwards rolls better into the relative position check
    private boolean pendingPositionDialog = false;

    // Cached peripheral getter values for Create display source
    private final Map<String, String> cachedGetterValues = new LinkedHashMap<>();
    private String cachedPeripheralType = "";
    private int peripheralRefreshTimer = 0;
    private static final int PERIPHERAL_REFRESH_TICKS = 20;

    public LinkedKeyboardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LINKED_KEYBOARD.get(), pos, state);
        for (int p = 0; p < MAX_PROFILES; p++) profileBindings.add(new ArrayList<>());
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

    /** Wipes all stored data — links, sequencer, live bindings, wireless, RS outputs, scripts. */
    public void resetData() {
        if (level != null && !level.isClientSide) {
            for (List<BlockPos> list : channelTargets.values())
                for (BlockPos pos : list)
                    PeripheralHelper.releaseThrusterControl(level, pos);
            if (RsLinkPresence.isPresent())
                for (var e : rsLinkEntries)
                    CreateRsLinkHelper.removeFromNetwork(level, e);
        }
        channelTargets.clear();
        sequencerViewers.clear();
        stopSequencer();
        sequencerSteps.clear();
        for (List<LiveControlBinding> p : profileBindings) p.clear();
        activeProfile = 0;
        rsLinkEntries.clear();
        autoTypeScript  = "";
        scriptLineIndex = 0;
        java.util.Arrays.fill(redstoneOutputs, 0);
        java.util.Arrays.fill(wirelessFreqs, null);
        setChanged();
        syncToClients(); // sync to client, for schematic saving again
    }

    public boolean hasData() {
        boolean hasBindings = profileBindings.stream().anyMatch(p -> !p.isEmpty());
        return !channelTargets.isEmpty() || !sequencerSteps.isEmpty()
                || hasBindings || !rsLinkEntries.isEmpty()
                || getWirelessFreqCount() > 0;
    }

    public boolean isTargetInRange() {
        BlockPos primary = getLinkedTargetPos();
        if (primary == null || level == null) return false;
        double range = ModConfig.COMMON.keyboardRange.get();
        return worldPosition.distSqr(primary) <= range * range;
    }

    public boolean needsPositionCheck() {
        if (suppressPositionDialog) return false;
        // Schematic pasting case
        if (pendingPositionDialog) return !channelTargets.isEmpty();
        if (lastKnownPos == null) return !channelTargets.isEmpty();
        if (worldPosition.equals(lastKnownPos)) return false;
        return !channelTargets.isEmpty();
    }

    public BlockPos getLastKnownPos() { return lastKnownPos; }

    public void applyPositionChoice(int choice, boolean dontAsk) {
        if (dontAsk) suppressPositionDialog = true;
        if (choice == 1 && lastKnownPos != null) {
            // Option B: everything moves together, relative position is the same
            BlockPos delta = worldPosition.subtract(lastKnownPos);
            Map<Integer, List<BlockPos>> updated = new java.util.LinkedHashMap<>();
            for (var entry : channelTargets.entrySet()) {
                List<BlockPos> newList = new ArrayList<>();
                for (BlockPos p : entry.getValue()) newList.add(p.offset(delta));
                updated.put(entry.getKey(), newList);
            }
            channelTargets.clear();
            channelTargets.putAll(updated);
        }
        lastKnownPos = worldPosition.immutable();
        pendingPositionDialog = false;
        setChanged();
        syncToClients();
        UniversalKeyboardMod.LOGGER.info(
                "[UKB pos-apply] choice={} dontAsk={} worldPos={} resolvedTargets={}",
                choice, dontAsk, worldPosition, channelTargets);
    }

    public boolean isLinkedAsComputer() {
        BlockPos primary = getLinkedTargetPos();
        if (primary == null || level == null) return false;
        BlockEntity be = level.getBlockEntity(primary);
        return be != null && (KeyboardMode.isCCComputer(be) || MonitorHelper.isMonitor(be));
    }

    public boolean isLinkedAsCreate() {
        BlockPos primary = getLinkedTargetPos();
        if (primary == null || level == null) return false;
        BlockEntity be = level.getBlockEntity(primary);
        return be != null && CreateValueHelper.hasScrollValue(be);
    }

    public boolean isComputerInRange() { return isLinkedAsComputer() && isTargetInRange(); }

    // ----- CC keyboard event forwarding — active channel targets in range -----

    public void sendKeyEvent(int keyCode, boolean held) {
        queueEventOnLinkedComputer("key", keyCode, held);
        if (keyCode == 257) writeCharToLinkedMonitors('\n'); // Enter = newline on monitor
    }
    public void sendCharEvent(char ch) {
        queueEventOnLinkedComputer("char", String.valueOf(ch));
        writeCharToLinkedMonitors(ch);
    }
    public void sendKeyUpEvent(int keyCode) { queueEventOnLinkedComputer("key_up", keyCode); }

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

    // Package-private for SequencerEngine
    boolean isTypeQueueEmpty() { return typeQueue.isEmpty(); }
    void enqueueChars(String text, boolean addEnter) {
        for (char c : text.toCharArray()) typeQueue.add(c);
        if (addEnter) typeQueue.add('\n');
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
    public boolean isSequencerRunning()             { return engine.isRunning(); }
    public int     getSequencerCurrentStep()        { return engine.getCurrentStep(); }
    public java.util.Set<java.util.UUID> getSequencerViewers() { return sequencerViewers; }
    public void addSequencerViewer(java.util.UUID uuid)    { sequencerViewers.add(uuid); }
    public void removeSequencerViewer(java.util.UUID uuid) { sequencerViewers.remove(uuid); }

    /** Set a sequencer variable from an external source (live controller). */
    public void setSequencerVariable(int idx, double value) { engine.setVar(idx, value); }
    public double[] getSequencerVars() { return engine.getVars(); }

    public void setSequencerSteps(List<SequencerStep> steps) {
        sequencerSteps.clear();
        sequencerSteps.addAll(steps);
        setChanged();
    }

    public void startSequencer() {
        if (sequencerSteps.isEmpty()) return;
        typeQueue.clear();
        typeTimer = 0;
        engine.start();
        setChanged();
    }

    public void stopSequencer() {
        engine.stop();
        clearRedstoneOutputs();
        clearWirelessOutputs();
        setChanged();
        ModPackets.broadcastSequencerProgress(this);
    }

    public int getRedstoneOutput(Direction dir) {
        return redstoneOutputs[dir.ordinal()];
    }

    public void setRedstoneOutput(Direction dir, int power) {
        int clamped = Math.max(0, Math.min(15, power));
        if (redstoneOutputs[dir.ordinal()] == clamped) return;
        redstoneOutputs[dir.ordinal()] = clamped;
        setChanged();
        if (level != null && !level.isClientSide)
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    // ── Wireless redstone outputs (Create RedstoneLink) ─────────────────────

    public int getRsLinkCount() { return rsLinkEntries.size(); }

    public List<RsLinkEntry> getWirelessEntries() {
        return Collections.unmodifiableList(rsLinkEntries);
    }

    /** Adds an empty entry if room remains. Returns the new entry's index (0-based) or -1 on failure. */
    public int addWirelessEntry() {
        if (!RsLinkPresence.isPresent()) return -1;
        if (rsLinkEntries.size() >= MAX_RSLINKS) return -1;
        rsLinkEntries.add(CreateRsLinkHelper.newEntry(worldPosition));
        setChanged();
        syncToClients();
        return rsLinkEntries.size() - 1;
    }

    public void removeWirelessEntry(int idx) {
        if (idx < 0 || idx >= rsLinkEntries.size()) return;
        RsLinkEntry e = rsLinkEntries.remove(idx);
        CreateRsLinkHelper.removeFromNetwork(level, e);
        setChanged();
        syncToClients();
    }

    public void setWirelessFrequencyItem(int idx, boolean first, ItemStack stack) {
        if (idx < 0 || idx >= rsLinkEntries.size()) return;
        CreateRsLinkHelper.updateFrequency(level, rsLinkEntries.get(idx), first, stack);
        setChanged();
        syncToClients();
    }

    /** Sequencer / external callers: drive the W{idx+1} output to the given power. */
    public void setRsLinkOutput(int idx, int power) {
        if (idx < 0 || idx >= rsLinkEntries.size()) return;
        CreateRsLinkHelper.setEntryPower(level, rsLinkEntries.get(idx), power);
    }

    public int getRsLinkOutput(int idx) {
        if (idx < 0 || idx >= rsLinkEntries.size()) return 0;
        return rsLinkEntries.get(idx).getPower();
    }

    private void clearWirelessOutputs() {
        if (!RsLinkPresence.isPresent()) return;
        for (RsLinkEntry e : rsLinkEntries)
            CreateRsLinkHelper.setEntryPower(level, e, 0);
    }

    public String[] getLinkFreqs() { return wirelessFreqs.clone(); }

    public void setLinkFreqs(String[] newFreqs) {
        for (int i = 0; i < MAX_WIRELESS_FREQS; i++) {
            wirelessFreqs[i] = (i < newFreqs.length && newFreqs[i] != null) ? newFreqs[i] : null;
        }
        setChanged();
        syncToClients();
    }

    public void broadcastWirelessFreq(int idx, int power) {
        if (idx < 0 || idx >= MAX_WIRELESS_FREQS || level == null || level.isClientSide) return;
        String freq = wirelessFreqs[idx];
        if (freq == null || freq.isEmpty()) return;
        dev.bennethogan.universalkeyboard.wireless.rs.WirelessRSNetwork.broadcast((Level) level, freq, power);
    }

    public String getWirelessFreq(int idx) {
        return (idx >= 0 && idx < MAX_WIRELESS_FREQS) ? wirelessFreqs[idx] : null;
    }

    public int getWirelessFreqPower(int idx) {
        if (idx < 0 || idx >= MAX_WIRELESS_FREQS || level == null) return 0;
        String freq = wirelessFreqs[idx];
        if (freq == null || freq.isEmpty()) return 0;
        return dev.bennethogan.universalkeyboard.wireless.rs.WirelessRSNetwork.getInputPower((Level) level, freq);
    }

    public int getWirelessFreqCount() {
        int count = 0;
        for (String f : wirelessFreqs) if (f != null && !f.isEmpty()) count++;
        return count;
    }

    private void clearRedstoneOutputs() {
        boolean changed = false;
        for (int i = 0; i < redstoneOutputs.length; i++) {
            if (redstoneOutputs[i] != 0) { redstoneOutputs[i] = 0; changed = true; }
        }
        if (changed && level != null && !level.isClientSide)
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    // ----- Display source data (Create integration) -----

    public static final int MAX_DISPLAY_LINES = 32;
    // Written by sequencer's DISPLAY line; null if never set, "" if set blank
    private final String[] displayLines = new String[MAX_DISPLAY_LINES];

    public void setDisplayLine(int idx, String text) {
        if (idx < 0 || idx >= MAX_DISPLAY_LINES) return;
        displayLines[idx] = text == null ? "" : text;
        setChanged();
    }

    // get display lines from the sequencer, null if not set yet
    public String[] getDisplayLines() {
        for (String l : displayLines)
            if (l != null) return displayLines;
        return null;
    }

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
            var result = dev.bennethogan.universalkeyboard.compat.PeripheralHelper.scanAndCall(
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
                peripheral = new dev.bennethogan.universalkeyboard.peripheral.KeyboardPeripheral(this);
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
                be.deliverTypedChar(be.typeQueue.poll());
                be.typeTimer = TICKS_PER_CHAR;
            } else {
                be.typeTimer--;
            }
        }

        if (be.peripheralRefreshTimer-- <= 0) {
            be.peripheralRefreshTimer = PERIPHERAL_REFRESH_TICKS;
            be.refreshPeripheralCache();
        }

        if (be.engine.isRunning()) be.engine.tick(be.sequencerSteps);

        be.updateShipMass(level, pos, state);

        // Suppress cleanup until position check isnt needed, assembly causes a cleanup of targets
        if (level.getGameTime() % 100 == 0 && be.isLinked() && !be.needsPositionCheck()) {
            boolean changed = false;
            for (List<BlockPos> list : be.channelTargets.values())
                changed |= list.removeIf(t -> level.isLoaded(t) && level.getBlockEntity(t) == null);
            if (changed) {
                UniversalKeyboardMod.LOGGER.info(
                        "[UKB tick-removed] stale targets cleaned at worldPos={} remaining={}",
                        be.worldPosition, be.channelTargets);
                be.setChanged();
            }
        }
    }

    public void onRemoved() {
        reverseShipMass();
        unlink();
    }

    // ------------------ sable weight compat -----------------
    private void updateShipMass(Level level, BlockPos pos, BlockState state) {
        if (!SableCompat.isPresent()) return;
        Object sub  = SableCompat.getSublevel(level, pos);
        Object prev = massSublevelRef == null ? null : massSublevelRef.get();
        if (sub == prev) return;

        if (sub == null) {
            // Disassembled — the tracker is gone with the old sublevel, nothing to reverse.
            massSublevelRef  = null;
            appliedMassDelta = 0.0;
            return;
        }

        double def   = SableCompat.getDefaultBlockMass(state);
        double delta = def * SHIP_MASS_FACTOR - def; // negative: shed most of the weight
        if (SableCompat.addBlockMass(level, pos, state, delta)) appliedMassDelta = delta;
        else                                                    appliedMassDelta = 0.0;
        massSublevelRef = new java.lang.ref.WeakReference<>(sub); // remember even on failure to avoid retry spam
    }

    
    private void reverseShipMass() {
        if (appliedMassDelta == 0.0 || level == null || level.isClientSide) return;
        Object prev = massSublevelRef == null ? null : massSublevelRef.get();
        if (prev != null && SableCompat.getSublevel(level, worldPosition) == prev)
            SableCompat.addBlockMass(level, worldPosition, getBlockState(), -appliedMassDelta);
        appliedMassDelta = 0.0;
        massSublevelRef  = null;
    }

    // ----- CC computer interaction — broadcasts to active-channel targets in range -----

    // Routes one queued character to every target type: computers get key/char events,
    // monitors get the character written straight to their terminal.
    void deliverTypedChar(char c) {
        if (c == '\n') {
            queueEventOnLinkedComputer("key", 257, false);
            queueEventOnLinkedComputer("key_up", 257);
        } else {
            queueEventOnLinkedComputer("char", String.valueOf(c));
        }
        writeCharToLinkedMonitors(c);
    }

    private void writeCharToLinkedMonitors(char c) {
        List<BlockPos> targets = getLinkedTargetPositions();
        if (targets.isEmpty() || level == null || level.isClientSide) return;
        double range = ModConfig.COMMON.keyboardRange.get();
        double rangeSq = range * range;
        for (BlockPos targetPos : targets) {
            if (worldPosition.distSqr(targetPos) > rangeSq) continue;
            BlockEntity target = level.getBlockEntity(targetPos);
            if (!MonitorHelper.isMonitor(target)) continue;
            if (c == '\n') MonitorHelper.newline(target);
            else           MonitorHelper.writeChar(target, c);
        }
    }

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
        engine.saveToTag(tag);
        tag.putIntArray("redstone_outputs",      redstoneOutputs);

        if (!rsLinkEntries.isEmpty()) {
            ListTag wl = new ListTag();
            for (RsLinkEntry e : rsLinkEntries) {
                CompoundTag c = new CompoundTag();
                c.put("first",  e.getFirstStack().saveOptional(registries));
                c.put("second", e.getSecondStack().saveOptional(registries));
                wl.add(c);
            }
            tag.put("wireless_entries", wl);
        }
        tag.putInt("active_profile", activeProfile);
        for (int p = 0; p < MAX_PROFILES; p++) {
            List<LiveControlBinding> list = profileBindings.get(p);
            if (!list.isEmpty()) {
                net.minecraft.nbt.ListTag lcl = new net.minecraft.nbt.ListTag();
                for (LiveControlBinding b : list) {
                    CompoundTag bt = new CompoundTag();
                    b.saveToTag(bt);
                    lcl.add(bt);
                }
                tag.put("profile_" + p + "_bindings", lcl);
            }
        }
        int lfCount = getWirelessFreqCount();
        if (lfCount > 0) {
            net.minecraft.nbt.ListTag lfl = new net.minecraft.nbt.ListTag();
            for (int i = 0; i < MAX_WIRELESS_FREQS; i++) {
                lfl.add(net.minecraft.nbt.StringTag.valueOf(wirelessFreqs[i] != null ? wirelessFreqs[i] : ""));
            }
            tag.put("link_freqs", lfl);
        }
        if (favoriteScreen != FavoriteScreen.NONE)
            tag.putByte("favorite_screen", (byte) favoriteScreen.ordinal());
        if (wheelFraction != 0f)
            tag.putFloat("wheel_fraction", wheelFraction);
        ListTag dl = new ListTag();
        for (int i = 0; i < MAX_DISPLAY_LINES; i++) {
            if (displayLines[i] == null) continue;
            CompoundTag c = new CompoundTag();
            c.putInt("i", i);
            c.putString("t", displayLines[i]);
            dl.add(c);
        }
        if (!dl.isEmpty()) tag.put("display_lines", dl);

        if (lastKnownPos != null) {
            CompoundTag lkp = new CompoundTag();
            lkp.putInt("x", lastKnownPos.getX());
            lkp.putInt("y", lastKnownPos.getY());
            lkp.putInt("z", lastKnownPos.getZ());
            tag.put("last_known_pos", lkp);
        }
        if (suppressPositionDialog) tag.putBoolean("suppress_pos_dialog", true);
        if (pendingPositionDialog)  tag.putBoolean("pending_pos_dialog", true);
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

        // Relative-only data means a schematic was printed — convert offsets to absolute at this location.
        boolean loadedRelative = false;
        if (channelTargets.isEmpty()) {
            for (int ch = 1; ch <= MAX_CHANNELS; ch++) {
                String relKey = "ch" + ch + "_rel_targets";
                if (!tag.contains(relKey, Tag.TAG_LIST)) continue;
                ListTag relList = tag.getList(relKey, Tag.TAG_COMPOUND);
                List<BlockPos> converted = new ArrayList<>();
                for (int i = 0; i < relList.size(); i++) {
                    CompoundTag e = relList.getCompound(i);
                    converted.add(new BlockPos(
                            worldPosition.getX() + e.getInt("rx"),
                            worldPosition.getY() + e.getInt("ry"),
                            worldPosition.getZ() + e.getInt("rz")));
                }
                if (!converted.isEmpty()) { channelTargets.put(ch, converted); loadedRelative = true; }
            }
        }

        // TEMPORARY DIAGNOSTIC
        if (!channelTargets.isEmpty()) {
            boolean hasAbsTag = false, hasRelTag = false;
            for (int ch = 1; ch <= MAX_CHANNELS; ch++) {
                if (tag.contains("ch" + ch + "_targets", Tag.TAG_LIST))     hasAbsTag = true;
                if (tag.contains("ch" + ch + "_rel_targets", Tag.TAG_LIST)) hasRelTag = true;
            }
            int bindingCount = 0;
            for (int p = 0; p < MAX_PROFILES; p++) {
                String key = "profile_" + p + "_bindings";
                if (tag.contains(key, Tag.TAG_LIST))
                    bindingCount += tag.getList(key, Tag.TAG_COMPOUND).size();
            }
            UniversalKeyboardMod.LOGGER.info(
                    "[UKB pos-load] worldPos={} absTag={} relTag={} loadedRelative={} resolvedTargets={} bindingsInTag={}",
                    worldPosition, hasAbsTag, hasRelTag, loadedRelative, channelTargets, bindingCount);
        }
        // ------------------------------------------------



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
        engine.loadFromTag(tag);
        if (tag.contains("redstone_outputs")) {
            int[] saved = tag.getIntArray("redstone_outputs");
            System.arraycopy(saved, 0, redstoneOutputs, 0, Math.min(saved.length, redstoneOutputs.length));
        }

        rsLinkEntries.clear();
        if (tag.contains("wireless_entries", Tag.TAG_LIST) && RsLinkPresence.isPresent()) {
            ListTag wl = tag.getList("wireless_entries", Tag.TAG_COMPOUND);
            for (int i = 0; i < wl.size() && rsLinkEntries.size() < MAX_RSLINKS; i++) {
                CompoundTag c = wl.getCompound(i);
                RsLinkEntry e = CreateRsLinkHelper.newEntry(worldPosition);
                e.setFirstStack(ItemStack.parseOptional(registries, c.getCompound("first")));
                e.setSecondStack(ItemStack.parseOptional(registries, c.getCompound("second")));
                rsLinkEntries.add(e);
            }
        }

        activeProfile = tag.contains("active_profile") ? tag.getInt("active_profile") : 0;
        activeProfile = Math.max(0, Math.min(MAX_PROFILES - 1, activeProfile));
        for (int p = 0; p < MAX_PROFILES; p++) {
            List<LiveControlBinding> slot = profileBindings.get(p);
            slot.clear();
            String key = "profile_" + p + "_bindings";
            if (tag.contains(key, Tag.TAG_LIST)) {
                net.minecraft.nbt.ListTag lcl = tag.getList(key, Tag.TAG_COMPOUND);
                for (int i = 0; i < lcl.size() && slot.size() < MAX_LIVE_BINDINGS; i++)
                    slot.add(LiveControlBinding.fromTag(lcl.getCompound(i)));
            }
        }
        // Legacy comppat for old "live_control_bindings" -> profile 0
        if (profileBindings.get(0).isEmpty() && tag.contains("live_control_bindings", Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag lcl = tag.getList("live_control_bindings", Tag.TAG_COMPOUND);
            List<LiveControlBinding> slot = profileBindings.get(0);
            for (int i = 0; i < lcl.size() && slot.size() < MAX_LIVE_BINDINGS; i++)
                slot.add(LiveControlBinding.fromTag(lcl.getCompound(i)));
        }
        java.util.Arrays.fill(wirelessFreqs, null);
        if (tag.contains("link_freqs", Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag lfl = tag.getList("link_freqs", Tag.TAG_STRING);
            for (int i = 0; i < lfl.size() && i < MAX_WIRELESS_FREQS; i++) {
                String v = lfl.getString(i);
                wirelessFreqs[i] = v.isEmpty() ? null : v;
            }
        }
        java.util.Arrays.fill(displayLines, null);
        if (tag.contains("display_lines", Tag.TAG_LIST)) {
            ListTag dl = tag.getList("display_lines", Tag.TAG_COMPOUND);
            for (int i = 0; i < dl.size(); i++) {
                CompoundTag c = dl.getCompound(i);
                int idx = c.getInt("i");
                if (idx >= 0 && idx < MAX_DISPLAY_LINES) displayLines[idx] = c.getString("t");
            }
        }
        favoriteScreen = tag.contains("favorite_screen")
                ? FavoriteScreen.fromByte(tag.getByte("favorite_screen")) : FavoriteScreen.NONE;

        wheelFraction = tag.contains("wheel_fraction") ? tag.getFloat("wheel_fraction") : 0f;
        // On the client, snap the visual to the synced resting position. Easier for loading purposes
        if (level != null && level.isClientSide) {
            wheelStartFraction  = wheelFraction;
            wheelTargetFraction = wheelFraction;
            wheelAnimStartTick  = Long.MIN_VALUE;
        }

        pendingPositionDialog = tag.getBoolean("pending_pos_dialog");
        if (tag.contains("last_known_pos", Tag.TAG_COMPOUND)) {
            CompoundTag lkp = tag.getCompound("last_known_pos");
            lastKnownPos = new BlockPos(lkp.getInt("x"), lkp.getInt("y"), lkp.getInt("z"));
        } else if (loadedRelative) {
            // better handling for immediate assembly after schematic paste
            lastKnownPos = worldPosition.immutable();
            pendingPositionDialog = true;
        } else {
            // first load without saved position and no relative targets to establish base
            lastKnownPos = worldPosition.immutable();
        }
        suppressPositionDialog = tag.getBoolean("suppress_pos_dialog");
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && RsLinkPresence.isPresent()) {
            for (RsLinkEntry e : rsLinkEntries)
                CreateRsLinkHelper.ensureRegistered(level, e);
        }
        if (level != null && !level.isClientSide) {
            if (lastKnownPos == null && channelTargets.isEmpty()) {
                // newly placed without data, establish base
                lastKnownPos = worldPosition.immutable();
                setChanged();
            } else if (lastKnownPos != null && lastKnownPos.equals(worldPosition)) {
                // baseline matches, persist and set again to be sure chunk saved
                setChanged();
            }
            // else: pasting a schematic or position changes, so state left open
        }
    }

    @Override
    public void setRemoved() {
        if (RsLinkPresence.isPresent()) {
            for (RsLinkEntry e : rsLinkEntries)
                CreateRsLinkHelper.removeFromNetwork(level, e);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (RsLinkPresence.isPresent()) {
            for (RsLinkEntry e : rsLinkEntries)
                CreateRsLinkHelper.removeFromNetwork(level, e);
        }
        super.onChunkUnloaded();
    }

    // ── Schematic (PartialSafeNBT) ───────────────────────────────────────────

    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("active_channel", activeChannel);
        tag.putString("autotype_script", autoTypeScript);
        if (!sequencerSteps.isEmpty()) {
            ListTag seqList = new ListTag();
            for (SequencerStep step : sequencerSteps) seqList.add(step.save());
            tag.put("sequencer_steps", seqList);
        }
        // Note: engine.saveToTag (sequencer run-state) is intentionally omitted here —
        // a schematic should paste in idle, not resume a program mid-run.
        tag.putIntArray("redstone_outputs", redstoneOutputs);
        if (!rsLinkEntries.isEmpty()) {
            ListTag wl = new ListTag();
            for (RsLinkEntry e : rsLinkEntries) {
                CompoundTag c = new CompoundTag();
                c.put("first",  e.getFirstStack().saveOptional(registries));
                c.put("second", e.getSecondStack().saveOptional(registries));
                wl.add(c);
            }
            tag.put("wireless_entries", wl);
        }
        tag.putInt("active_profile", activeProfile);
        int writeSafeBindingCount = 0;
        for (int p = 0; p < MAX_PROFILES; p++) {
            List<LiveControlBinding> list = profileBindings.get(p);
            if (!list.isEmpty()) {
                ListTag lcl = new ListTag();
                for (LiveControlBinding b : list) {
                    CompoundTag bt = new CompoundTag();
                    b.saveToTag(bt);
                    lcl.add(bt);
                }
                tag.put("profile_" + p + "_bindings", lcl);
                writeSafeBindingCount += list.size();
            }
        }
        UniversalKeyboardMod.LOGGER.info(
                "[UKB writeSafe] worldPos={} lastKnownPos={} targets={} totalBindings={}",
                worldPosition, lastKnownPos, channelTargets, writeSafeBindingCount);
        int lfCount = getWirelessFreqCount();
        if (lfCount > 0) {
            net.minecraft.nbt.ListTag lfl = new net.minecraft.nbt.ListTag();
            for (int i = 0; i < MAX_WIRELESS_FREQS; i++) {
                lfl.add(net.minecraft.nbt.StringTag.valueOf(wirelessFreqs[i] != null ? wirelessFreqs[i] : ""));
            }
            tag.put("link_freqs", lfl);
        }

        /** Relative offsets based on keyboards lastKnownPos rather than world pos. This should fix Schematic saving
         * so that is now writeSaves with that included. So when pasting, the relative positioning math isnt
         * reversed back to the original absolute position (rel = target - pastePos, then pastePos + rel = target)
         **/
        BlockPos relBase = lastKnownPos != null ? lastKnownPos : worldPosition;
        for (Map.Entry<Integer, List<BlockPos>> entry : channelTargets.entrySet()) {
            List<BlockPos> list = entry.getValue();
            if (list.isEmpty()) continue;
            ListTag relList = new ListTag();
            for (BlockPos p : list) {
                CompoundTag e = new CompoundTag();
                e.putInt("rx", p.getX() - relBase.getX());
                e.putInt("ry", p.getY() - relBase.getY());
                e.putInt("rz", p.getZ() - relBase.getZ());
                relList.add(e);
            }
            tag.put("ch" + entry.getKey() + "_rel_targets", relList);
        }
    }

    public CompoundTag exportData(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    public void importData(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
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
