package dev.bennethogan.universalkeyboard.livecontrol;

import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.universalkeyboard.client.GunManeuver;
import dev.bennethogan.universalkeyboard.client.gamepad.GamepadLiveDriver;
import dev.bennethogan.universalkeyboard.compat.PeripheralHelper;
import dev.bennethogan.universalkeyboard.config.ModConfig;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding.ActionType;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding.CamDir;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding.Mode;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
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
    private static BlockPos                    lastTogglePos        = null;
    private static final Set<Integer>          rememberedToggleKeys = new HashSet<>();
    private static final Map<Long, Integer>    rsIncCounters  = new HashMap<>();
    private static final Map<Integer, Integer> thrIncCounters = new HashMap<>();
    private static final Map<Integer, Integer> varIncCounters = new HashMap<>();
    private static final Map<Long, Double>     rsIncFrac      = new HashMap<>();
    private static final Map<Integer, Double>  thrIncFrac     = new HashMap<>();
    private static final Map<Integer, Double>  varIncFrac     = new HashMap<>();

    // Per-channel-mode INC state (RPM and any future scalar peripheral modes), keyed by
    // ActionType then channel. Registry-driven so a new mode needs no new fields here.
    private static final Map<ActionType, Map<Integer, Integer>> chIncCounters = new EnumMap<>(ActionType.class);
    private static final Map<ActionType, Map<Integer, Double>>  chIncFrac     = new EnumMap<>(ActionType.class);
    // Current peripheral values to seed channel modes from on activate (e.g. RPM ← rpmValues).
    private static final Map<ActionType, int[]>                 channelSeeds  = new EnumMap<>(ActionType.class);
    // Per-channel RPM increment quantum for stirling_engine which uses discrete steps
    private static final Map<Integer, Integer> rpmIncStep = new HashMap<>();
    private static final Map<Integer, Integer> incHoldTicks   = new HashMap<>();
    private static final Set<Integer>          hldVarOn       = new HashSet<>();
    private static final Map<Integer, Double>  tglPendingPeak = new HashMap<>();
    private static final Map<Integer, Double>  tglLockedMag   = new HashMap<>();

    private static final int INC_REPEAT_DELAY_TICKS = 10;
    private static final int MAX_DISPLAY_KEYS        = 3;

    private static int actionBarTick = 0;

    // Gives certain warning messages a longer time before being overwritten by default hotbar message
    private static String pendingWarning = null;
    private static int    pendingWarningTicks = 0;

    public static void showTimedWarning(String msg, int ticks) {
        pendingWarning = msg;
        pendingWarningTicks = Math.max(0, ticks);
    }

    // index of most recently activated RPM binding (-1 for none yet)
    private static int lastRpmBindingIdx = -1;

    //most recently clicked row, for screen display
    private static long chooseSeq = 0;
    private static final Map<Integer, Long> rowChosenAt = new HashMap<>();

    // ------- vista camera and supplementaries cannon controls -----
    // Per-target fractional carry (keyed by camera pos / cannon channel), since a binding's channel
    // now scopes which linked target it controls.
    private static final Map<BlockPos, Double> camZoomAccum = new HashMap<>();

    // per tick rates
    private static final double CAM_AIM_DEG_PER_TICK = 1.0;   // ~20°/s at base
    private static final double CAM_ZOOM_PER_TICK    = 0.2;   // ~1 zoom level / 5 ticks


    // two GUN sub-modes, MAN and TGT
    private static final Map<Integer, Double> gunPowerAccum = new HashMap<>(); // per-channel MAN power carry
    private static boolean gunTgtWasFiring = false;
    private static boolean  tgtEngaged  = false;
    private static int      tgtGrace    = 0;
    private static int      tgtPovIndex = 0;
    private static int      tgtChannel  = 1;   // channel the current TGT session is engaged on
    private static List<BlockPos> tgtCannons = new ArrayList<>();
    private static final int GUN_TGT_GRACE_TICKS = 10;
    private static final double GUN_AIM_DEG_PER_TICK = 1.0;
    private static final double GUN_POWER_PER_TICK   = 0.1;

    // Range limit on Supplementaries cannon until thats worked out
    private static final double GUN_POV_RANGE_SQR = 7.5 * 7.5;


    // Dashpanels (PAN). Per channel, per module analog value carry (0-15), and last value sent so packets arent oversent when not needed
    private static final Map<Long, Double>  panValues   = new HashMap<>();
    private static final Map<Long, Integer> panLastSent = new HashMap<>();
    private static final double PAN_RAMP_PER_TICK = 0.5;   // ~15 over ~1.5s at base
    // ── Activation ───────────────────────────────────────────────────────────

    public static void activate(BlockPos pos, List<LiveControlBinding> binds,
                                int[] localRsOutputs, int[] rsLinkPowers, int[] thrusterPowers,
                                double[] varValues, int[] rpmValues) {
        if (active && pos.equals(keyboardPos)) rememberTogglesNow();
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
        chIncCounters.clear();
        chIncFrac.clear();
        channelSeeds.clear();
        rpmIncStep.clear();
        incHoldTicks.clear();
        hldVarOn.clear();
        tglPendingPeak.clear();
        tglLockedMag.clear();

        // Wire the current-value arrays each channel mode seeds from on resume.
        channelSeeds.put(ActionType.RPM_CONTROL, rpmValues);

        // Detect per-channel RPM quanta for Propulsion Stirling Engine
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            net.minecraft.world.level.block.entity.BlockEntity kbe = mc.level.getBlockEntity(pos);
            if (kbe instanceof LinkedKeyboardBlockEntity kb) {
                for (Map.Entry<Integer, List<BlockPos>> e : kb.getAllChannelTargets().entrySet()) {
                    List<BlockPos> tps = e.getValue();
                    if (tps == null || tps.isEmpty()) continue;
                    BlockPos tp = tps.get(0);
                    if (!mc.level.isLoaded(tp)) continue;
                    Object p = PeripheralHelper.getPeripheral(mc.level, tp);
                    if (p == null) continue;
                    int step = PeripheralHelper.getRpmIncStep(p);
                    if (step > 1) rpmIncStep.put(e.getKey(), step);
                }
            }
        }

        for (int i = 0; i < bindings.size(); i++) {
            LiveControlBinding b = bindings.get(i);
            if (b.actionType == LiveControlBinding.ActionType.REDSTONE) {
                int cur;
                if (b.rsLinkIdx == 0) {
                    int ord = b.rsSide.ordinal();
                    cur = (ord < localRsOutputs.length) ? localRsOutputs[ord] : 0;
                } else {
                    int wi = b.rsLinkIdx - 1;
                    cur = (wi < rsLinkPowers.length) ? rsLinkPowers[wi] : 0;
                }
                if (b.mode == Mode.INC)
                    rsIncCounters.putIfAbsent(rsKey(b.rsLinkIdx, b.rsSide), cur);
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
            } else if (ChannelMode.isChannelMode(b.actionType)) {
                ChannelMode m = ChannelMode.byType(b.actionType);
                int[] seed = channelSeeds.get(b.actionType);
                int cur = (seed != null && b.channel < seed.length) ? seed[b.channel] : 0;
                if (b.mode == Mode.INC) {
                    // Snap seed to this channel's quantum so the counter starts on a valid step
                    int step = (b.actionType == ActionType.RPM_CONTROL) ? rpmIncStep.getOrDefault(b.channel, 1) : 1;
                    int snapped = step > 1 ? (int) Math.round(cur / (double) step) * step : cur;
                    chInc(b.actionType).putIfAbsent(b.channel, m.clamp(snapped));
                } else if (b.mode == Mode.TGL && cur == m.targetValue(b))
                    toggledOn.add(i);
            }
        }

        if (pos.equals(lastTogglePos)) {
            for (int i = 0; i < bindings.size(); i++) {
                LiveControlBinding b = bindings.get(i);
                if (b.mode == Mode.TGL && b.actionType != ActionType.VARIABLE
                        && rememberedToggleKeys.contains(b.keyCode))
                    toggledOn.add(i);
            }
        }
    }

    private static void rememberTogglesNow() {
        lastTogglePos = keyboardPos;
        rememberedToggleKeys.clear();
        for (int idx : toggledOn) {
            if (idx < 0 || idx >= bindings.size()) continue;
            LiveControlBinding b = bindings.get(idx);
            if (b.actionType != ActionType.VARIABLE) rememberedToggleKeys.add(b.keyCode);
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
        tglPendingPeak.clear();
        tglLockedMag.clear();
        lastRpmBindingIdx = -1;
        rowChosenAt.clear();
        if (GunManeuver.isActive()) GunManeuver.stop();
        tgtEngaged = false;
        tgtGrace = 0;
        tgtPovIndex = 0;
        tgtCannons = new ArrayList<>();
        gunPowerAccum.clear();
        camZoomAccum.clear();
        panValues.clear();
        panLastSent.clear();
        pendingWarning = null;
        pendingWarningTicks = 0;
        gunTgtWasFiring = false;
        if (keyboardPos != null && Minecraft.getInstance().getConnection() != null)
            ModPackets.sendGunRelease(keyboardPos, 0);   // channel 0 = release every leased cannon
        if (Minecraft.getInstance().getConnection() != null) computeAndSend();
        rememberTogglesNow();   // so re-opening keyboard restores toggles
        toggledOn.clear();
        rsIncCounters.clear();
        thrIncCounters.clear();
        varIncCounters.clear();
        rsIncFrac.clear();
        thrIncFrac.clear();
        varIncFrac.clear();
        chIncCounters.clear();
        chIncFrac.clear();
    }

    public static boolean isActive()       { return active; }
    public static BlockPos getKeyboardPos() { return keyboardPos; }


    //active-key labels for the screen/dashboard animations
    public static List<String> screenKeyLabels(int max) {
        if (!active) return List.of();
        java.util.LinkedHashSet<Integer> keys = new java.util.LinkedHashSet<>();
        for (int idx : toggledOn)
            if (idx >= 0 && idx < bindings.size()) keys.add(bindings.get(idx).keyCode);
        keys.addAll(heldKeys);
        keys.removeIf(k -> bindings.stream().noneMatch(b -> b.keyCode == k
                && b.actionType != LiveControlBinding.ActionType.OVERDRIVE));
        List<String> out = new ArrayList<>();
        for (int key : keys) {
            if (out.size() >= max) break;
            out.add(keyDisplayName(key));
        }
        return out;
    }

    // ── Control Wheel animation query ─────────────────────────────────────────


    // Parses the comma seperated list from config file
    public static Set<Integer> parseRowSet(String cfg) {
        Set<Integer> result = new HashSet<>();
        if (cfg == null || cfg.isBlank()) return result;
        for (String part : cfg.split(",")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            try {
                int n = Integer.parseInt(part);
                if (n >= 1 && n <= 40) result.add(n - 1);
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    public static float computeWheelTarget(Set<Integer> leftRows, Set<Integer> rightRows) {
        if (!active) return 0f;
        Float valve = incValveFraction(leftRows, rightRows);
        if (valve != null) return -clamp01(valve);
        float combined = rowContribution(leftRows, true) - rowContribution(rightRows, true);
        return Math.max(-1f, Math.min(1f, combined));
    }

    // Resting target: ignores HLD (held) inputs; counts only TGL and INC valve state
    public static float computeRestingWheelTarget(Set<Integer> leftRows, Set<Integer> rightRows) {
        if (!active) return 0f;
        Float valve = incValveFraction(leftRows, rightRows);
        if (valve != null) return -clamp01(valve);
        float combined = rowContribution(leftRows, false) - rowContribution(rightRows, false);
        return Math.max(-1f, Math.min(1f, combined));
    }

    // Magnitude 0–1 for a set of row indices. includeHld=false used for resting state computation
    private static float rowContribution(Set<Integer> rows, boolean includeHld) {
        float mag = 0f;
        for (int i : rows) {
            if (i < 0 || i >= bindings.size()) continue;
            LiveControlBinding b = bindings.get(i);
            switch (b.mode) {
                case HLD -> { if (includeHld) mag = Math.max(mag, heldKeys.contains(b.keyCode) ? 1f : 0f); }
                case TGL -> mag = Math.max(mag, toggledOn.contains(i) ? 1f : 0f);
                case INC -> { /* handled by incValveFraction */ }
            }
        }
        return mag;
    }

    // Largest INC power fraction across all INC-mode bindings in either row set, or null if none.
    private static Float incValveFraction(Set<Integer> leftRows, Set<Integer> rightRows) {
        Float frac = null;
        for (Set<Integer> rows : List.of(leftRows, rightRows)) {
            for (int i : rows) {
                if (i < 0 || i >= bindings.size()) continue;
                LiveControlBinding b = bindings.get(i);
                if (b.mode != Mode.INC) continue;
                float f = incFractionForBinding(b);
                frac = (frac == null) ? f : Math.max(frac, f);
            }
        }
        return frac;
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

   // Normalised fraction (0–1) of the INC counter for a binding, post-increment
    private static float incFractionForBinding(LiveControlBinding b) {
        return switch (b.actionType) {
            case REDSTONE -> {
                int v = rsIncCounters.getOrDefault(rsKey(b.rsLinkIdx, b.rsSide), 0);
                yield Math.max(0f, Math.min(1f, v / 15.0f));
            }
            case THRUSTER_POWER -> {
                int v = thrIncCounters.getOrDefault(b.channel, 0);
                yield Math.max(0f, Math.min(1f, v / 15.0f));
            }
            case VARIABLE -> {
                int v = varIncCounters.getOrDefault(b.varIndex, 0);
                yield b.varOnValue > 0 ? Math.max(0f, Math.min(1f, (float) v / b.varOnValue)) : 0f;
            }
            default -> {
                // Channel modes (RPM_CONTROL, etc.)
                int v = chIncCounters.getOrDefault(b.actionType, Map.of()).getOrDefault(b.channel, 0);
                int maxAbs = Math.max(1, Math.abs(b.rpmTarget));
                yield Math.max(0f, Math.min(1f, Math.abs((float) v / maxAbs)));
            }
        };
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public static void tick() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        for (int i = 0; i < bindings.size(); i++) {
            if (!tglPendingPeak.containsKey(i)) continue;
            double mag = GamepadLiveDriver.analogMagnitude(bindings.get(i).keyCode);
            tglPendingPeak.merge(i, mag, Math::max);
        }

        if (pendingWarningTicks > 0 && pendingWarning != null) {
            mc.player.displayClientMessage(Component.literal(pendingWarning), true);
            pendingWarningTicks--;
            actionBarTick = 0;
        } else if (!heldKeys.isEmpty() || !toggledOn.isEmpty()) {
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
                    long key = rsKey(b.rsLinkIdx, b.rsSide);
                    int scaled = rsScaledStep(key, baseDelta, odFactor);
                    if (scaled != 0)
                        rsIncCounters.merge(key, scaled, (cur, d) -> Math.max(0, Math.min(15, cur + d)));
                }
                case THRUSTER_POWER -> {
                    int scaled = thrScaledStep(b.channel, baseDelta, odFactor);
                    if (scaled != 0)
                        thrIncCounters.merge(b.channel, scaled, (cur, d) -> Math.max(0, Math.min(15, cur + d)));
                }
                default -> stepChannelInc(b, baseDelta, odFactor);
            }
            incFired = true;
        }
        if (!varActions.isEmpty()) ModPackets.sendLiveAction(keyboardPos, varActions);
        if (incFired || hasActiveAnalogOutput()) computeAndSend();

        tickCameraControl();
        tickGunControl();
        tickPanControl();
    }

    private static int sharedCameraIndex(BlockPos pos) {
        net.minecraft.world.level.Level level = Minecraft.getInstance().level;
        if (level != null && pos != null
                && level.getBlockEntity(pos) instanceof LinkedKeyboardBlockEntity kb)
            return kb.getActiveCameraIndex();
        return -1;
    }

    private static void tickCameraControl() {
        if (keyboardPos == null) return;
        Map<BlockPos, double[]> perCam = new HashMap<>();   // camPos -> {dYaw, dPitch, dZoom}
        for (int i = 0; i < bindings.size(); i++) {
            LiveControlBinding b = bindings.get(i);
            if (b.actionType != ActionType.CAM || !isBindingActive(i)) continue;
            BlockPos camPos = viewfinderOnChannel(keyboardPos, b.channel);
            if (camPos == null) continue;
            double od   = overdriveFactor(i);
            double aim  = CAM_AIM_DEG_PER_TICK * od * joystickMagnitude(b, i);
            double zoom = CAM_ZOOM_PER_TICK    * od * joystickMagnitude(b, i);
            double[] a = perCam.computeIfAbsent(camPos, k -> new double[3]);
            switch (b.camDir) {
                case UP       -> a[1] -= aim;
                case DOWN     -> a[1] += aim;
                case LEFT     -> a[0] += aim;
                case RIGHT    -> a[0] -= aim;
                case ZOOM_IN  -> a[2] += zoom;
                case ZOOM_OUT -> a[2] -= zoom;
                case TOGGLE   -> { }
            }
        }
        for (Map.Entry<BlockPos, double[]> e : perCam.entrySet()) {
            BlockPos camPos = e.getKey();
            double[] a = e.getValue();
            double z = camZoomAccum.merge(camPos, a[2], Double::sum);
            int zoomStep = (int) z;
            camZoomAccum.put(camPos, z - zoomStep);
            if (a[0] != 0 || a[1] != 0 || zoomStep != 0)
                ModPackets.sendCameraNudge(camPos, a[0], a[1], zoomStep);
        }
    }

    private static BlockPos viewfinderOnChannel(BlockPos kbPos, int channel) {
        net.minecraft.world.level.Level level = Minecraft.getInstance().level;
        if (level == null || kbPos == null
                || !(level.getBlockEntity(kbPos) instanceof LinkedKeyboardBlockEntity kb)) return null;
        List<BlockPos> list = kb.getAllChannelTargets().get(channel);
        if (list == null) return null;
        for (BlockPos p : list)
            if (dev.bennethogan.universalkeyboard.compat.VistaCamera.isViewfinder(level, p)) return p;
        return null;
    }

    private static void tickGunControl() {
        if (keyboardPos == null) return;

        Map<Integer, double[]> perCh = new HashMap<>();   // channel -> {dYaw, dPitch, dPow}
        for (int i = 0; i < bindings.size(); i++) {
            LiveControlBinding b = bindings.get(i);
            if (b.actionType != ActionType.GUN || b.mode != Mode.HLD || !isBindingActive(i)) continue;
            double od  = overdriveFactor(i);
            double aim = GUN_AIM_DEG_PER_TICK * od * joystickMagnitude(b, i);
            double pw  = GUN_POWER_PER_TICK   * od * joystickMagnitude(b, i);
            double[] a = perCh.computeIfAbsent(b.channel, k -> new double[3]);
            switch (b.camDir) {
                case UP       -> a[1] -= aim;
                case DOWN     -> a[1] += aim;
                case LEFT     -> a[0] += aim;
                case RIGHT    -> a[0] -= aim;
                case ZOOM_IN  -> a[2] += pw;   // "power +" for cannon
                case ZOOM_OUT -> a[2] -= pw;   // "power -" for cannon
                case TOGGLE   -> { }           // fire is on-press
            }
        }
        for (Map.Entry<Integer, double[]> e : perCh.entrySet()) {
            int ch = e.getKey();
            double[] a = e.getValue();
            double pAcc = gunPowerAccum.merge(ch, a[2], Double::sum);
            int powStep = (int) pAcc;
            gunPowerAccum.put(ch, pAcc - powStep);
            if (a[0] != 0 || a[1] != 0 || powStep != 0)
                ModPackets.sendGunManual(keyboardPos, ch, a[0], a[1], powStep);
        }

        tickGunTargeting();
    }

    // Dashpanels (PAN mode) driver
    private static void tickPanControl() {
        if (keyboardPos == null || bindings.isEmpty()) return;

        Map<Long, boolean[]> hasToggle = new HashMap<>();   // key -> {hasToggleBinding, toggleActive}
        Map<Long, Double>    rampDelta = new HashMap<>();
        Set<Long>            touched   = new java.util.HashSet<>();

        for (int i = 0; i < bindings.size(); i++) {
            LiveControlBinding b = bindings.get(i);
            if (b.actionType != ActionType.PAN) continue;
            long key = panKey(b.channel, b.panModule);
            touched.add(key);
            boolean activeNow = isBindingActive(i);
            switch (b.camDir) {
                case TOGGLE -> {
                    boolean[] t = hasToggle.computeIfAbsent(key, k -> new boolean[2]);
                    t[0] = true;
                    if (activeNow) t[1] = true;
                }
                case UP, ZOOM_IN -> {
                    if (activeNow) rampDelta.merge(key, PAN_RAMP_PER_TICK * overdriveFactor(i) * joystickMagnitude(b, i), Double::sum);
                }
                case DOWN, ZOOM_OUT -> {
                    if (activeNow) rampDelta.merge(key, -PAN_RAMP_PER_TICK * overdriveFactor(i) * joystickMagnitude(b, i), Double::sum);
                }
                default -> { }
            }
        }

        for (long key : touched) {
            double cur = panValues.getOrDefault(key, (double) panLastSent.getOrDefault(key, 0));
            Double delta = rampDelta.get(key);
            if (delta != null && delta != 0) cur = Math.max(0.0, Math.min(15.0, cur + delta));
            boolean[] t = hasToggle.get(key);
            if (t != null && t[0]) cur = t[1] ? 15.0 : 0.0;   // on/off is absolute, wins over ramp
            panValues.put(key, cur);

            int iv = (int) Math.round(cur);
            if (panLastSent.getOrDefault(key, -1) != iv) {
                panLastSent.put(key, iv);
                ModPackets.sendPanSet(keyboardPos, panChannelOf(key), panModuleOf(key), iv);
            }
        }
    }

    private static long panKey(int channel, int module) {
        return ((long) (channel & 0xFFF) << 20) | (module & 0xFFFFF);
    }
    private static int panChannelOf(long key) { return (int) ((key >> 20) & 0xFFF); }
    private static int panModuleOf(long key)  { return (int) (key & 0xFFFFF); }

    private static void tickGunTargeting() {
        if (!tgtEngaged) { gunTgtWasFiring = false; return; }
        boolean onCannon = tgtPovIndex < tgtCannons.size();
        if (!onCannon) { gunTgtWasFiring = false; return; }
        if (!GunManeuver.isActive()) {
            if (tgtGrace > 0) gunDisengage(); else deactivate();
            return;
        }

        net.minecraft.world.level.Level level = Minecraft.getInstance().level;
        net.minecraft.world.phys.Vec3 target = GunManeuver.currentTarget();
        BlockPos primary = GunManeuver.controlledCannonPos();
        if (target != null) {
            int power = (primary != null && level != null)
                    ? dev.bennethogan.universalkeyboard.compat.CannonControl.getPowerLevel(level, primary) : 1;
            // aim the rest of this channel's cannons at the same ground point
            ModPackets.sendGunAim(keyboardPos, tgtChannel, target, power);
        }
        boolean firing = primary != null && level != null
                && dev.bennethogan.universalkeyboard.compat.CannonControl.isFiring(level, primary);
        if (firing && !gunTgtWasFiring) ModPackets.sendGunFire(keyboardPos, tgtChannel);
        gunTgtWasFiring = firing;
    }

    // engage to start targeting mode on a specific channel's cannons
    private static void gunEngageToggle(int channel) {
        if (tgtEngaged) { gunDisengage(); return; }
        List<BlockPos> cannons = cannonsOnChannel(keyboardPos, channel);
        if (cannons.isEmpty()) return;
        // Only the cannons we can actually POV into (within Supplementaries' maneuver range) become POV
        // candidates. Every cannon on the channel still converges via the server aim packet.
        List<BlockPos> pov = new ArrayList<>();
        for (BlockPos c : sortByDistanceToPlayer(cannons)) if (withinPovRange(c)) pov.add(c);
        if (pov.isEmpty()) {
            showTimedWarning("§e[Keyboard] §fCannon must be within ~8 blocks, use MAN (manual) to aim remotely.", 100);
            return;
        }
        tgtChannel  = channel;
        tgtCannons  = pov;
        tgtEngaged  = true;
        tgtPovIndex = 0;
        tgtGrace    = GUN_TGT_GRACE_TICKS;
        gunTgtWasFiring = false;
        GunManeuver.start(Minecraft.getInstance().level, tgtCannons.get(0));
    }

    private static boolean withinPovRange(BlockPos cannon) {
        net.minecraft.client.player.LocalPlayer p = Minecraft.getInstance().player;
        return p != null && cannon.distToCenterSqr(p.position()) <= GUN_POV_RANGE_SQR;
    }

    private static void gunDisengage() {
        tgtEngaged  = false;
        tgtPovIndex = 0;
        tgtGrace    = 0;
        tgtCannons = new ArrayList<>();
        gunTgtWasFiring = false;
        GunManeuver.stop();
        if (keyboardPos != null && Minecraft.getInstance().getConnection() != null)
            ModPackets.sendGunRelease(keyboardPos, tgtChannel);   // free the cannons we were leasing
    }



    private static void gunPovCycle() {
        if (!tgtEngaged || tgtCannons.isEmpty()) return;
        net.minecraft.world.level.Level level = Minecraft.getInstance().level;
        GunManeuver.stop();   // release the previous POV so start() re-targets
        int size = tgtCannons.size();
        for (int i = 0; i < size + 1; i++) {
            tgtPovIndex = (tgtPovIndex + 1) % (size + 1);
            if (tgtPovIndex == size) return;   // player POV
            if (withinPovRange(tgtCannons.get(tgtPovIndex))) {
                GunManeuver.start(level, tgtCannons.get(tgtPovIndex));
                tgtGrace = GUN_TGT_GRACE_TICKS;
                return;
            }
        }
    }

    private static List<BlockPos> sortByDistanceToPlayer(List<BlockPos> cannons) {
        List<BlockPos> out = new ArrayList<>(cannons);
        net.minecraft.client.player.LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) {
            net.minecraft.world.phys.Vec3 pp = p.position();
            out.sort(java.util.Comparator.comparingDouble(bp -> bp.distToCenterSqr(pp)));
        }
        return out;
    }

    private static List<BlockPos> cannonsOnChannel(BlockPos kbPos, int channel) {
        net.minecraft.world.level.Level level = Minecraft.getInstance().level;
        if (level == null || kbPos == null
                || !(level.getBlockEntity(kbPos) instanceof LinkedKeyboardBlockEntity kb))
            return List.of();
        List<BlockPos> list = kb.getAllChannelTargets().get(channel);
        if (list == null) return List.of();
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : list)
            if (!out.contains(p) && dev.bennethogan.universalkeyboard.compat.CannonControl.isCannon(level, p))
                out.add(p);
        return out;
    }

    public static BlockPos panelOnChannel(BlockPos kbPos, int channel) {
        net.minecraft.world.level.Level level = Minecraft.getInstance().level;
        if (level == null || kbPos == null
                || !(level.getBlockEntity(kbPos) instanceof LinkedKeyboardBlockEntity kb)) return null;
        List<BlockPos> list = kb.getAllChannelTargets().get(channel);
        if (list == null) return null;
        for (BlockPos p : list)
            if (dev.bennethogan.universalkeyboard.compat.PanelControl.isPanel(level, p)) return p;
        return null;
    }

    // ── Key handling ─────────────────────────────────────────────────────────

    public static void handleKey(int keyCode, int glfw_action) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && glfw_action == GLFW.GLFW_PRESS) {
            deactivate();
            return;
        }

        handleVariableKey(keyCode, glfw_action);

        if (glfw_action == GLFW.GLFW_PRESS) maybeToggleVistaCamera(keyCode);
        if (glfw_action == GLFW.GLFW_PRESS) maybeGunPress(keyCode);

        if (glfw_action == GLFW.GLFW_PRESS) {
            for (int i = 0; i < bindings.size(); i++) {
                LiveControlBinding b = bindings.get(i);
                if (b.keyCode != keyCode || b.mode != Mode.INC
                        || b.actionType == LiveControlBinding.ActionType.VARIABLE) continue;
                if (b.actionType == ActionType.RPM_CONTROL) lastRpmBindingIdx = i;
                markRowChosen(i, b);
                heldKeys.add(keyCode);
                if (!incHoldTicks.containsKey(keyCode))
                    incHoldTicks.put(keyCode, 0);
                int baseDelta = b.incPlus ? 1 : -1;
                double odFactor = overdriveFactor(i);
                switch (b.actionType) {
                    case REDSTONE -> {
                        long key = rsKey(b.rsLinkIdx, b.rsSide);
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
                    default -> stepChannelInc(b, baseDelta, odFactor);
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
                if (b.actionType == ActionType.RPM_CONTROL) lastRpmBindingIdx = i;
                markRowChosen(i, b);
                if (b.mode == Mode.HLD) {
                    heldKeys.add(keyCode);
                } else {
                    if (toggledOn.contains(i)) {
                        toggledOn.remove(i);
                        tglLockedMag.remove(i);
                    } else if (isAnalogInput(b.keyCode) && scalingOn()) {
                        tglPendingPeak.put(i, 0.0);
                    } else {
                        toggledOn.add(i);
                    }
                }
            }
        } else if (glfw_action == GLFW.GLFW_RELEASE) {
            for (int i = 0; i < bindings.size(); i++) {
                LiveControlBinding b = bindings.get(i);
                if (b.keyCode != keyCode || b.mode == Mode.INC
                        || b.actionType == LiveControlBinding.ActionType.VARIABLE) continue;
                Double peak = tglPendingPeak.remove(i);
                if (peak != null) {
                    tglLockedMag.put(i, peak);
                    toggledOn.add(i);
                }
            }
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
                        if (toggledOn.contains(i)) {
                            toggledOn.remove(i);
                            tglLockedMag.remove(i);
                            out.add(varActionOd(b.varIndex, 0, i));
                        } else if (isAnalogInput(b.keyCode) && scalingOn()) {
                            tglPendingPeak.put(i, 0.0);
                        } else {
                            toggledOn.add(i);
                            out.add(varActionOd(b.varIndex, b.varOnValue, i));
                        }
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
                if (b.mode == Mode.HLD) {
                    out.add(varActionOd(b.varIndex, 0, i));
                    hldVarOn.remove(b.varIndex);
                } else if (b.mode == Mode.TGL) {
                    Double peak = tglPendingPeak.remove(i);
                    if (peak != null) {
                        tglLockedMag.put(i, peak);
                        toggledOn.add(i);
                        out.add(varActionOd(b.varIndex, b.varOnValue, i));
                    }
                }
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
                ? 1.0 : joystickMagnitude(bindings.get(bindingIdx), bindingIdx);
        double scaled = (value == 0.0) ? 0.0 : Math.min(100.0, value * od * mag);
        return new ModPackets.LiveAction((byte) 4, varIndex, scaled, 0.0);
    }

    private static double overdriveFactor(int bindingIdx) {
        double factor = 1.0;
        for (int i = 0; i < bindings.size(); i++) {
            LiveControlBinding b = bindings.get(i);
            if (b.actionType != LiveControlBinding.ActionType.OVERDRIVE) continue;
            boolean odActive = isBindingActive(i);
            if (!odActive) continue;
            if (bindingIdx >= 0 && isExcluded(b.odExcludes, bindingIdx + 1)) continue;
            // Inverted OD: when active (key not held for -Hld), divide instead of multiply
            factor *= b.inverted ? (1.0 / b.overdriveMultiplier) : b.overdriveMultiplier;
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

    // ── Channel-mode (RPM family) helpers ──────────────────────────────────────

    private static Map<Integer, Integer> chInc(ActionType t) {
        return chIncCounters.computeIfAbsent(t, k -> new HashMap<>());
    }

    private static Map<Integer, Double> chFrac(ActionType t) {
        return chIncFrac.computeIfAbsent(t, k -> new HashMap<>());
    }

    /** One INC step for a channel-mode binding: quantum-scaled, OD-scaled, fractional carry, clamped. */
    private static void stepChannelInc(LiveControlBinding b, int baseDelta, double odFactor) {
        ChannelMode m = ChannelMode.byType(b.actionType);
        if (m == null) return;
        int step = (b.actionType == ActionType.RPM_CONTROL) ? rpmIncStep.getOrDefault(b.channel, 1) : 1;
        int effectiveDelta = baseDelta * step;
        int scaled;
        if (odFactor == 1.0) {
            scaled = effectiveDelta;
        } else {
            Map<Integer, Double> frac = chFrac(b.actionType);
            double want = effectiveDelta * odFactor + frac.getOrDefault(b.channel, 0.0);
            scaled = (int) Math.round(want);
            frac.put(b.channel, want - scaled);
        }
        if (scaled != 0)
            chInc(b.actionType).merge(b.channel, scaled, (cur, d) -> m.clamp(cur + d));
    }

    // ── Output computation ───────────────────────────────────────────────────

    public static void computeAndSend() {
        Map<Long, Integer>     rsTargetToSignal = new HashMap<>();
        Map<Integer, Double>   powerByChannel   = new HashMap<>();
        Map<Integer, double[]> vectorByChannel  = new HashMap<>();
        Map<Integer, Integer>  linkSignals      = new HashMap<>();
        // Channel-mode (RPM family) accumulators, keyed by ActionType then channel.
        Map<ActionType, Map<Integer, Integer>> chOut         = new EnumMap<>(ActionType.class);
        Map<ActionType, Set<Integer>>          chActiveHldTgl = new EnumMap<>(ActionType.class);

        for (int i = 0; i < bindings.size(); i++) {
            LiveControlBinding b            = bindings.get(i);
            boolean            bindingActive = isBindingActive(i);
            double             odFactor     = overdriveFactor(i);
            double             mag          = joystickMagnitude(b, i);

            switch (b.actionType) {
                case REDSTONE -> {
                    if (b.wirelessFreqIdx > 0) {
                        int contribution = bindingActive ? Math.min(15, (int) Math.round(b.signalStrength * odFactor * mag)) : 0;
                        linkSignals.merge(b.wirelessFreqIdx, contribution, Math::max);
                    } else {
                        long key = rsKey(b.rsLinkIdx, b.rsSide);
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
                default -> {
                    ChannelMode m = ChannelMode.byType(b.actionType);
                    if (m != null) {
                        Map<Integer, Integer> out = chOut.computeIfAbsent(b.actionType, k -> new HashMap<>());
                        if (b.mode == Mode.INC) {
                            out.putIfAbsent(b.channel, 0);
                        } else {
                            // Apply overdrive and analog deflection then clamp
                            int contribution = bindingActive
                                    ? m.clamp((int) Math.round(m.targetValue(b) * odFactor * mag))
                                    : 0;
                            // Mode-defined merge (RPM is magnitude-preserving so an active +/- target beats
                            // an inactive 0 and negative targets aren't clamped away by max)
                            out.merge(b.channel, contribution, m::merge);
                            if (bindingActive)
                                chActiveHldTgl.computeIfAbsent(b.actionType, k -> new HashSet<>()).add(b.channel);
                        }
                    }
                }
            }
        }

        // Merge INC counters (already OD-stepped at per-binding rate)
        for (Map.Entry<Long, Integer> e : rsIncCounters.entrySet())
            rsTargetToSignal.merge(e.getKey(), e.getValue(), Math::max);
        for (Map.Entry<Integer, Integer> e : thrIncCounters.entrySet())
            powerByChannel.merge(e.getKey(), e.getValue() / 15.0, Math::max);
        // Channel-mode INC counters: SIGNED_YIELD modes (RPM) drive their channel but yield to a
        // HLD/TGL active on the same channel (so a toggle can override the INC value); MAX modes compete
        for (ChannelMode m : ChannelMode.all()) {
            Map<Integer, Integer> counters = chIncCounters.get(m.type);
            if (counters == null || counters.isEmpty()) continue;
            Map<Integer, Integer> out      = chOut.computeIfAbsent(m.type, k -> new HashMap<>());
            Set<Integer>          activeHT = chActiveHldTgl.getOrDefault(m.type, Set.of());
            for (Map.Entry<Integer, Integer> e : counters.entrySet()) {
                if (m.incMerge == ChannelMode.IncMerge.SIGNED_YIELD) {
                    if (!activeHT.contains(e.getKey())) out.put(e.getKey(), e.getValue());
                } else {
                    out.merge(e.getKey(), e.getValue(), Math::max);
                }
            }
        }

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
        for (ChannelMode m : ChannelMode.all()) {
            Map<Integer, Integer> out = chOut.get(m.type);
            if (out == null) continue;
            for (Map.Entry<Integer, Integer> e : out.entrySet())
                actions.add(new ModPackets.LiveAction(m.opcode, e.getKey(), e.getValue(), 0.0));
        }

        if (!actions.isEmpty()) ModPackets.sendLiveAction(keyboardPos, actions);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static boolean isBindingActive(int idx) {
        LiveControlBinding b = bindings.get(idx);
        if (b.inverted && b.mode == Mode.HLD) {
            // for analog inverted, always active, magnitude handles the curve
            if (isAnalogInput(b.keyCode) && scalingOn()) return true;
            return !heldKeys.contains(b.keyCode);
        }
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

    // added so mouse inputs can be read as analog too
    private static boolean isAnalogInput(int code) {
        return GamepadCodes.isAnalogCode(code) || MouseCodes.isAnalog(code);
    }

    private static boolean isScaledAnalog(LiveControlBinding b) {
        return scalingOn()
                && b.mode == Mode.HLD
                && b.actionType != ActionType.OVERDRIVE
                && isAnalogInput(b.keyCode);
    }

    private static double joystickMagnitude(LiveControlBinding b, int bindingIdx) {
        if (b.inverted && b.mode == Mode.HLD && isAnalogInput(b.keyCode) && scalingOn())
            return 1.0 - GamepadLiveDriver.analogMagnitude(b.keyCode);
        if (b.mode == Mode.TGL && bindingIdx >= 0 && isAnalogInput(b.keyCode) && scalingOn())
            return tglLockedMag.getOrDefault(bindingIdx, 1.0);
        if (!isScaledAnalog(b)) return 1.0;
        return GamepadLiveDriver.analogMagnitude(b.keyCode);
    }

    private static boolean hasActiveAnalogOutput() {
        for (int i = 0; i < bindings.size(); i++) {
            LiveControlBinding b = bindings.get(i);
            if (b.actionType == ActionType.VARIABLE || ChannelMode.isChannelMode(b.actionType)) continue;
            if (!isScaledAnalog(b)) continue;
            if (isBindingActive(i)) return true;
        }
        return false;
    }

    private static long rsKey(int rsLinkIdx, Direction rsSide) {
        int sideOrd = (rsLinkIdx == 0) ? rsSide.ordinal() : 0;
        return ((long) rsLinkIdx << 16) | (sideOrd & 0xFFFF);
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
            String chVal = channelIncLabelForKey(keyCode);
            if (chVal != null) return "§b[" + name + " " + chVal + suffix + "§b]";
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

    private static String channelIncLabelForKey(int keyCode) {
        for (LiveControlBinding b : bindings) {
            if (b.keyCode != keyCode || b.mode != Mode.INC || !ChannelMode.isChannelMode(b.actionType)) continue;
            ChannelMode m = ChannelMode.byType(b.actionType);
            Map<Integer, Integer> c = chIncCounters.get(b.actionType);
            int val = (c == null) ? 0 : c.getOrDefault(b.channel, 0);
            return val + m.unitSuffix;
        }
        return null;
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
                case REDSTONE       -> { return rsIncCounters.getOrDefault(rsKey(b.rsLinkIdx, b.rsSide), 0); }
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

    // Dashboard query API

    public static boolean isRowActive(int bindingIdx) {
        if (!active || bindingIdx < 0 || bindingIdx >= bindings.size()) return false;
        return isBindingActive(bindingIdx);
    }

    public static int getLastRpmBindingIdx() { return lastRpmBindingIdx; }

    public static int getRpmValue(int bindingIdx) {
        if (!active || bindingIdx < 0 || bindingIdx >= bindings.size()) return 0;
        LiveControlBinding b = bindings.get(bindingIdx);
        if (b.actionType != ActionType.RPM_CONTROL) return 0;
        if (b.mode == Mode.INC) {
            Map<Integer, Integer> c = chIncCounters.get(b.actionType);
            return Math.abs(c == null ? 0 : c.getOrDefault(b.channel, 0));
        }
        return isBindingActive(bindingIdx) ? Math.abs(b.rpmTarget) : 0;
    }

    public static int getRowSignalStrength(int bindingIdx) {
        if (!active || bindingIdx < 0 || bindingIdx >= bindings.size()) return 0;
        LiveControlBinding b = bindings.get(bindingIdx);
        if (b.actionType != ActionType.REDSTONE) return 0;
        if (b.mode == Mode.INC) {
            return rsIncCounters.getOrDefault(rsKey(b.rsLinkIdx, b.rsSide), 0);
        }
        return isBindingActive(bindingIdx) ? b.signalStrength : 0;
    }

    public static int getThrusterValue(int bindingIdx) {
        if (!active || bindingIdx < 0 || bindingIdx >= bindings.size()) return 0;
        LiveControlBinding b = bindings.get(bindingIdx);
        if (b.actionType != ActionType.THRUSTER_POWER) return 0;
        if (b.mode == Mode.INC) return thrIncCounters.getOrDefault(b.channel, 0);
        return isBindingActive(bindingIdx) ? (int) Math.round(b.powerLevel * 15) : 0;
    }

    public static ActionType getRowActionType(int bindingIdx) {
        if (bindingIdx < 0 || bindingIdx >= bindings.size()) return null;
        return bindings.get(bindingIdx).actionType;
    }

    public static int mostRecentlyChosenAmong(int[] bindingIdxs) {
        int best = -1;
        long bestSeq = Long.MIN_VALUE;
        for (int idx : bindingIdxs) {
            Long s = rowChosenAt.get(idx);
            if (s != null && s > bestSeq) { bestSeq = s; best = idx; }
        }
        return best;
    }

    public static boolean isVistaCameraOn(BlockPos pos) { return getActiveViewfinderPos(pos) != null; }

    // check for "cameras only" config
    public static boolean cameraOnly() {
        try { return ModConfig.CLIENT.vistaCameraOnly.get(); } catch (Exception e) { return false; }
    }

    public static BlockPos getActiveViewfinderPos(BlockPos pos) {
        if (pos == null) return null;
        List<BlockPos> vfs = linkedViewfinders(pos);
        if (vfs.isEmpty()) return null;
        int idx = sharedCameraIndex(pos);
        if (idx < 0 && cameraOnly()) idx = 0;   // cameras-only: default to first (local display)
        return (idx >= 0 && idx < vfs.size()) ? vfs.get(idx) : null;
    }

    public static boolean hasLinkedViewfinder(BlockPos kbPos) {
        return !linkedViewfinders(kbPos).isEmpty();
    }

    public static boolean hasLinkedCannon(BlockPos kbPos) {
        return anyLinkedTarget(kbPos, p ->
                dev.bennethogan.universalkeyboard.compat.CannonControl.isCannon(Minecraft.getInstance().level, p));
    }

    public static boolean hasLinkedPanel(BlockPos kbPos) {
        return anyLinkedTarget(kbPos, p ->
                dev.bennethogan.universalkeyboard.compat.PanelControl.isPanel(Minecraft.getInstance().level, p));
    }

    private static boolean anyLinkedTarget(BlockPos kbPos, java.util.function.Predicate<BlockPos> test) {
        net.minecraft.world.level.Level level = Minecraft.getInstance().level;
        if (level == null || kbPos == null
                || !(level.getBlockEntity(kbPos) instanceof LinkedKeyboardBlockEntity kb)) return false;
        for (List<BlockPos> list : kb.getAllChannelTargets().values()) {
            if (list == null) continue;
            for (BlockPos p : list) if (test.test(p)) return true;
        }
        return false;
    }

    private static List<BlockPos> linkedViewfinders(BlockPos kbPos) {
        net.minecraft.world.level.Level level = Minecraft.getInstance().level;
        if (level == null || kbPos == null
                || !(level.getBlockEntity(kbPos) instanceof LinkedKeyboardBlockEntity kb))
            return List.of();
        List<BlockPos> out = new ArrayList<>();
        Map<Integer, List<BlockPos>> targets = kb.getAllChannelTargets();
        for (int ch = 1; ch <= LinkedKeyboardBlockEntity.MAX_CHANNELS; ch++) {
            List<BlockPos> list = targets.get(ch);
            if (list == null) continue;
            for (BlockPos p : list)
                if (!out.contains(p) && dev.bennethogan.universalkeyboard.compat.VistaCamera.isViewfinder(level, p))
                    out.add(p);
        }
        return out;
    }


    private static void maybeToggleVistaCamera(int keyCode) {
        if (keyboardPos == null) return;
        for (LiveControlBinding b : bindings)
            if (b.actionType == ActionType.CAM && b.camDir == CamDir.TOGGLE && b.keyCode == keyCode) {
                ModPackets.sendCameraToggle(keyboardPos, cameraOnly());
                return;
            }
    }

    private static void maybeGunPress(int keyCode) {
        if (keyboardPos == null) return;
        for (LiveControlBinding b : bindings) {
            if (b.actionType != ActionType.GUN || b.keyCode != keyCode) continue;
            if (b.mode == Mode.TGL) {
                if (b.camDir == CamDir.TOGGLE) gunEngageToggle(b.channel); else gunPovCycle();
                return;
            }
            if (b.mode == Mode.HLD && b.camDir == CamDir.TOGGLE) { ModPackets.sendGunFire(keyboardPos, b.channel); return; }
        }
    }

    private static void markRowChosen(int idx, LiveControlBinding b) {
        if (b.actionType == ActionType.RPM_CONTROL
                || b.actionType == ActionType.REDSTONE
                || b.actionType == ActionType.THRUSTER_POWER)
            rowChosenAt.put(idx, ++chooseSeq);
    }

    private static String keyDisplayName(int keyCode) {
        if (MouseCodes.isMouseCode(keyCode)) return MouseCodes.name(keyCode);
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
