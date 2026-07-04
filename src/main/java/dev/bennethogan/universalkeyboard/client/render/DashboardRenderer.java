package dev.bennethogan.universalkeyboard.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
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


    private static final float TILT_MAX_DEG = 17.5f;   // fully-active tilt
    private static final float TILT_TICKS   = 10f;     // ease duration each way (~0.5 s)

    private static final class Tilt { long startTick; float startAngle; float target; }
    private static final java.util.Map<BlockPos, Tilt> TILTS = new java.util.HashMap<>();

    public static void render(LinkedKeyboardBlockEntity be, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (be.getLevel() == null) return;

        AttachFace face   = state.getValue(dev.bennethogan.universalkeyboard.block.LinkedKeyboardBlock.FACE);
        Direction  facing = state.getValue(dev.bennethogan.universalkeyboard.block.LinkedKeyboardBlock.FACING);

        BakedModel model = Minecraft.getInstance().getModelManager().getModel(WHEEL_MODEL);
        float angle = ControlWheelRenderer.currentAngle(be, partialTick);

        poseStack.pushPose();


        BlockModelRotation orient = dashboardOrient(face, facing);
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(orient.getRotation().getMatrix());
        poseStack.translate(-0.5, -0.5, -0.5);

        float tilt = currentTilt(be.getBlockPos(), be.getLevel(), partialTick);
        if (tilt != 0.0f) {
            poseStack.translate(PIVOT_X, PIVOT_Y, PIVOT_Z);
            poseStack.mulPose(new Quaternionf().rotateAxis((float) Math.toRadians(-tilt), 0f, 0f, 1f));
            poseStack.translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z);
        }

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

    private static float currentTilt(BlockPos pos, Level level, float partialTick) {
        if (level == null) return 0f;
        boolean active = LiveControlManager.isActive() && pos.equals(LiveControlManager.getKeyboardPos());
        float desired = active ? TILT_MAX_DEG : 0f;
        long  now     = level.getGameTime();

        Tilt s = TILTS.get(pos);
        if (s == null) {
            if (TILTS.size() > 32) TILTS.clear();
            s = new Tilt();
            s.startTick = now; s.startAngle = desired; s.target = desired;
            TILTS.put(pos.immutable(), s);
        }
        if (s.target != desired) {          // state changed -> re-launch from the current angle
            s.startAngle = tiltAt(s, now, partialTick);
            s.target     = desired;
            s.startTick  = now;
        }
        return tiltAt(s, now, partialTick);
    }

    private static float tiltAt(Tilt s, long now, float partialTick) {
        float elapsed = (now - s.startTick) + partialTick;
        float t = Math.max(0f, Math.min(1f, elapsed / TILT_TICKS));
        return s.startAngle + (s.target - s.startAngle) * LinkedKeyboardBlockEntity.wheelSmoothstep(t);
    }

    private static BlockModelRotation dashboardOrient(AttachFace face, Direction facing) {
        return switch (face) {
            case FLOOR   -> BlockModelRotation.by(0,   (ControlWheelRenderer.yFloor(facing) + 270) % 360);
            case CEILING -> BlockModelRotation.by(180, (ControlWheelRenderer.yFloor(facing) + 270) % 360);
            case WALL    -> BlockModelRotation.by(0,   (ControlWheelRenderer.yWall(facing)  + 270) % 360);
        };
    }
}
