package dev.bennethogan.universalkeyboard.client;

import dev.bennethogan.universalkeyboard.block.LinkedControlWheelBlock;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlManager;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;


/** Animation driver for control wheel; purely cosmetic. Every client gets the combined signed turn
 * target from LiveControlManager. Per tick polling is both a lazy solution and a practical one for
 * improving animation smoothness for INC mode
 */
public final class ControlWheelAnimator {

    private ControlWheelAnimator() {}

    private static boolean leftHeld  = false;
    private static boolean rightHeld = false;
    private static BlockPos lastSentPos = null;
    private static int      lastSentPct = 0;
    private static boolean  wasWheelActive = false;
    private static BlockPos activePos      = null;
    private static float    restingTarget  = 0f;

    // tracks hold state of configured left and right key
    public static void onKey(int keyCode, int glfwAction) {
        boolean down;
        if (glfwAction == GLFW.GLFW_PRESS || glfwAction == GLFW.GLFW_REPEAT) down = true;
        else if (glfwAction == GLFW.GLFW_RELEASE) down = false;
        else return;

        if (keyCode == ControlWheelKeys.leftKey())  leftHeld  = down;
        if (keyCode == ControlWheelKeys.rightKey()) rightHeld = down;
    }

    // polls a combined turning target from LiveControlManager
    public static void tick() {
        BlockPos pos = LiveControlManager.isActive() ? LiveControlManager.getKeyboardPos() : null;
        Minecraft mc = Minecraft.getInstance();
        boolean isWheel = pos != null && mc.level != null
                && mc.level.getBlockState(pos).getBlock() instanceof LinkedControlWheelBlock;

        if (isWheel) {
            int left = ControlWheelKeys.leftKey(), right = ControlWheelKeys.rightKey();
            float target = LiveControlManager.computeWheelTarget(left, right, leftHeld, rightHeld);
            // Cache the resting position each tick so it's still known after deactivation clears state
            restingTarget  = LiveControlManager.computeRestingWheelTarget(left, right);
            activePos      = pos;
            wasWheelActive = true;
            sendIfChanged(pos, target);
            return;
        }

        if (wasWheelActive && activePos != null) sendIfChanged(activePos, restingTarget);
        wasWheelActive = false;
        activePos = null;
        leftHeld  = false;
        rightHeld = false;
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
        leftHeld  = false;
        rightHeld = false;
        lastSentPos = null;
        lastSentPct = 0;
        wasWheelActive = false;
        activePos = null;
        restingTarget = 0f;
    }
}
