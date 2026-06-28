package dev.bennethogan.universalkeyboard.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.joml.Quaternionf;


public final class DashboardRenderer {

    private DashboardRenderer() {}

    public static final ModelResourceLocation WHEEL_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "block/dashboard_wheel"));

    private static final float PIVOT_X = 0.0f      / 16f;
    private static final float PIVOT_Y = 11.96447f / 16f;
    private static final float PIVOT_Z = 7.96447f  / 16f;

    private static final org.joml.Vector3f WHEEL_AXIS =
            new org.joml.Vector3f(0.96593f, -0.25882f, 0f).normalize();

    public static void render(LinkedKeyboardBlockEntity be, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (be.getLevel() == null) return;

        AttachFace face   = state.getValue(dev.bennethogan.universalkeyboard.block.LinkedKeyboardBlock.FACE);
        Direction  facing = state.getValue(dev.bennethogan.universalkeyboard.block.LinkedKeyboardBlock.FACING);

        BakedModel model = Minecraft.getInstance().getModelManager().getModel(WHEEL_MODEL);
        float angle = ControlWheelRenderer.currentAngle(be, partialTick);

        poseStack.pushPose();


        BlockModelRotation orient = ControlWheelRenderer.orientationFor(face, facing);
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(orient.getRotation().getMatrix());
        poseStack.translate(-0.5, -0.5, -0.5);


        if (angle != 0.0f) {
            poseStack.translate(PIVOT_X, PIVOT_Y, PIVOT_Z);
            poseStack.mulPose(new Quaternionf().rotateAxis(
                    (float) Math.toRadians(angle), WHEEL_AXIS.x, WHEEL_AXIS.y, WHEEL_AXIS.z));
            poseStack.translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z);
        }

        ModelBlockRenderer mbr = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        VertexConsumer vc = buffer.getBuffer(RenderType.solid());
        mbr.renderModel(poseStack.last(), vc, state, model, 1.0f, 1.0f, 1.0f,
                packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();


        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(orient.getRotation().getMatrix());
        poseStack.translate(-0.5, -0.5, -0.5);
        DashboardScreenRenderer.render(be, poseStack, buffer, partialTick);
        poseStack.popPose();
    }
}
