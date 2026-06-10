package dev.bennethogan.universalkeyboard.client;

import dev.bennethogan.universalkeyboard.block.LinkedControlWheelBlock;
import dev.bennethogan.universalkeyboard.config.ModConfig;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlManager;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.Set;


/** Animation driver for the control wheel; purely cosmetic. The target is derived from the live
 * magnitudes of designated row index number rather than keyboard keys, so now keyboard,
 * gamepad sticks, triggers, and anything else will work
 */
public final class ControlWheelAnimator {

    private ControlWheelAnimator() {}

    private static BlockPos lastSentPos = null;
    private static int      lastSentPct = 0;
    private static boolean  wasWheelActive = false;
    private static BlockPos activePos      = null;
    private static float    restingTarget  = 0f;

    // Row-set cache, reparsed only when the config string changes
    private static String       cachedLeftCfg  = null;
    private static String       cachedRightCfg = null;
    private static Set<Integer> leftRows       = Set.of();
    private static Set<Integer> rightRows      = Set.of();

    private static void refreshRowSets() {
        String lCfg = ModConfig.CLIENT.controlWheelLeftRows.get();
        String rCfg = ModConfig.CLIENT.controlWheelRightRows.get();
        if (!lCfg.equals(cachedLeftCfg)) { cachedLeftCfg = lCfg; leftRows  = LiveControlManager.parseRowSet(lCfg); }
        if (!rCfg.equals(cachedRightCfg)) { cachedRightCfg = rCfg; rightRows = LiveControlManager.parseRowSet(rCfg); }
    }

    public static void tick() {
        BlockPos pos = LiveControlManager.isActive() ? LiveControlManager.getKeyboardPos() : null;
        Minecraft mc = Minecraft.getInstance();
        boolean isWheel = pos != null && mc.level != null
                && mc.level.getBlockState(pos).getBlock() instanceof LinkedControlWheelBlock;

        if (isWheel) {
            refreshRowSets();
            float target = LiveControlManager.computeWheelTarget(leftRows, rightRows);
            restingTarget  = LiveControlManager.computeRestingWheelTarget(leftRows, rightRows);
            activePos      = pos;
            wasWheelActive = true;
            sendIfChanged(pos, target);
            return;
        }

        if (wasWheelActive && activePos != null) sendIfChanged(activePos, restingTarget);
        wasWheelActive = false;
        activePos = null;
    }

    private static void sendIfChanged(BlockPos pos, float target) {
        int pct = Math.max(-100, Math.min(100, Math.round(target * 100f)));
        if (!pos.equals(lastSentPos) || pct != lastSentPct) {
            ModPackets.sendControlWheelAnimate(pos, target);
            lastSentPos = pos;
            lastSentPct = pct;
        }
    }

    public static void reset() {
        lastSentPos = null;
        lastSentPct = 0;
        wasWheelActive = false;
        activePos = null;
        restingTarget = 0f;
        cachedLeftCfg  = null;
        cachedRightCfg = null;
        leftRows  = Set.of();
        rightRows = Set.of();
    }
}
