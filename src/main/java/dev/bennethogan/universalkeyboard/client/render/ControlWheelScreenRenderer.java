package dev.bennethogan.universalkeyboard.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.universalkeyboard.compat.VistaCompat;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;


public final class ControlWheelScreenRenderer {

    private ControlWheelScreenRenderer() {}

    private static final float FLOOR_X0 = 3.6375f, FLOOR_X1 = 12.0625f;
    private static final float FLOOR_Y0 = 9.8225f, FLOOR_Y1 = 14.2775f;
    private static final float FLOOR_FACE_Z = 11.647f;

    private static final float WALL_X0 = 3.654f, WALL_X1 = 12.079f;
    private static final float WALL_Y0 = 9.801f, WALL_Y1 = 14.256f;
    private static final float WALL_FACE_Z = 2.847f;

    private static final float EPS       = 0.002f;
    private static final float FEED_EPS  = 0.08f;
    private static final float MIN_SCALE = 0.0035f;
    private static final float MAX_SCALE = 0.0072f;
    private static final float COL_FILL  = 0.92f;
    private static final int   SCREEN_LIGHT = 0xF000F0;

    public static void render(LinkedKeyboardBlockEntity be, PoseStack poseStack,
                              MultiBufferSource buffer, boolean wall, float partialTick) {
        Level level = be.getLevel();
        if (level == null) return;

        BlockPos pos = be.getBlockPos();

        float x0    = wall ? WALL_X0     : FLOOR_X0;
        float x1    = wall ? WALL_X1     : FLOOR_X1;
        float y0    = wall ? WALL_Y0     : FLOOR_Y0;
        float y1    = wall ? WALL_Y1     : FLOOR_Y1;
        float faceZ = wall ? WALL_FACE_Z : FLOOR_FACE_Z;

        float cx      = (x0 + x1) / 2f / 16f;
        float cy      = (y0 + y1) / 2f / 16f;
        float screenW = (x1 - x0) / 16f;
        float screenH = (y1 - y0) / 16f;
        float outZ    = faceZ + EPS;

        if (VistaCompat.isPresent() && LiveControlManager.isVistaCameraOn(pos)) {
            poseStack.pushPose();
            poseStack.translate(cx, cy, (faceZ + FEED_EPS) / 16f);
            poseStack.mulPose(Axis.YP.rotationDegrees(180f));
            BlockPos camPos = LiveControlManager.getActiveViewfinderPos(pos);
            boolean drew = camPos != null
                    ? VistaCompat.tryDrawFeedFromViewfinder(level.getBlockEntity(camPos), poseStack,
                            buffer, partialTick, screenW / 2f, screenH / 2f, SCREEN_LIGHT)
                    : VistaCompat.tryDrawFeed(level, pos, poseStack, buffer,
                            partialTick, screenW / 2f, screenH / 2f, SCREEN_LIGHT);
            poseStack.popPose();
            if (drew) {
                Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
                return;
            }
        }

        List<List<ScreenGauges.Line>> columns;
        try {
            columns = ScreenGauges.columns(level, pos);
        } catch (Exception e) {
            return;
        }
        if (columns.isEmpty()) return;

        poseStack.pushPose();
        poseStack.translate(cx, cy, outZ / 16f);
        ScreenGauges.drawColumns(poseStack, columns, screenW, screenH,
                MIN_SCALE, MAX_SCALE, COL_FILL, SCREEN_LIGHT);
        poseStack.popPose();
    }
}
