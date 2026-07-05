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


public final class DashboardScreenRenderer {

    private DashboardScreenRenderer() {}

    private static final float FACE_X    = 0.0f;
    private static final float CENTER_Y  = 15.5f;
    private static final float CENTER_Z  = 8.0f;
    private static final float SCREEN_W  = 11.5f;
    private static final float SCREEN_H  = 4.6f;

    private static final float FACE_YAW  = -90.0f;

    private static final float EPS        = 0.02f;
    private static final float FEED_EPS   = 0.08f;   // feed floats off the face (solid quad)
    private static final float MIN_SCALE  = 0.0065f;
    private static final float MAX_SCALE  = 0.0100f;
    private static final float COL_FILL   = 0.90f;
    private static final int   SCREEN_LIGHT = 0xF000F0;

    public static void render(LinkedKeyboardBlockEntity be, PoseStack poseStack,
                              MultiBufferSource buffer, float partialTick) {
        Level level = be.getLevel();
        if (level == null) return;
        BlockPos pos = be.getBlockPos();

        // Vista camera feed
        if (VistaCompat.isPresent() && LiveControlManager.isVistaCameraOn(pos)) {
            poseStack.pushPose();
            poseStack.translate((FACE_X - FEED_EPS) / 16f, CENTER_Y / 16f, CENTER_Z / 16f);
            poseStack.mulPose(Axis.YP.rotationDegrees(FACE_YAW));
            poseStack.mulPose(Axis.YP.rotationDegrees(180f));   // feed faces -Z; flip to the viewer
            net.minecraft.world.item.ItemStack cassette = be.getCassette();
            boolean drew = !cassette.isEmpty()
                    ? VistaCompat.tryDrawFeedFromItem(cassette, poseStack, buffer,
                            partialTick, SCREEN_W / 16f / 2f, SCREEN_H / 16f / 2f, SCREEN_LIGHT)
                    : VistaCompat.tryDrawFeed(level, pos, poseStack, buffer,
                            partialTick, SCREEN_W / 16f / 2f, SCREEN_H / 16f / 2f, SCREEN_LIGHT);
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
        poseStack.translate((FACE_X - EPS) / 16f, CENTER_Y / 16f, CENTER_Z / 16f);
        poseStack.mulPose(Axis.YP.rotationDegrees(FACE_YAW));
        ScreenGauges.drawColumns(poseStack, columns, SCREEN_W / 16f, SCREEN_H / 16f,
                MIN_SCALE, MAX_SCALE, COL_FILL, SCREEN_LIGHT);
        poseStack.popPose();
    }
}
