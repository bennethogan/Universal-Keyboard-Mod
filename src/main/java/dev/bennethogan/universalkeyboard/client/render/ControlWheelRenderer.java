package dev.bennethogan.universalkeyboard.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import dev.bennethogan.universalkeyboard.block.LinkedControlWheelBlock;
import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

public class ControlWheelRenderer implements BlockEntityRenderer<LinkedKeyboardBlockEntity> {

    public static final ModelResourceLocation WHEEL_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "block/universal_controlwheel_wheel"));
    public static final ModelResourceLocation WALL_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "block/universal_controlwheel_wall_spin"));

    private static final float MAX_ANGLE = 30.0f;  // degrees of full turn

    // pivot point from blockbench, floor/regular variant
    private static final float FLOOR_PX = 8.015f   / 16f;
    private static final float FLOOR_PY = 12.0285f / 16f;
    private static final float FLOOR_PZ = 10.3105f / 16f;
    private static final org.joml.Vector3f FLOOR_AXIS =
            new org.joml.Vector3f(0.0f, 0f, 1f).normalize();

    // wall variant pivot point
    private static final float WALL_PX = 8.015f   / 16f;
    private static final float WALL_PY = 12.0285f / 16f;
    private static final float WALL_PZ = 1.5105f  / 16f;
    private static final org.joml.Vector3f WALL_AXIS =
            new org.joml.Vector3f(0.0f, 0.0f, 1.0f).normalize();

    public ControlWheelRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public AABB getRenderBoundingBox(LinkedKeyboardBlockEntity be) {
        // avoid culling
        return new AABB(be.getBlockPos()).inflate(0.5, 0.75, 0.5);
    }

    @Override
    public void render(LinkedKeyboardBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (be.getLevel() == null) return;
        if (state.getBlock() instanceof dev.bennethogan.universalkeyboard.block.DashboardBlock) {
            DashboardRenderer.render(be, partialTick, poseStack, buffer, packedLight, packedOverlay);
            return;
        }
        if (!(state.getBlock() instanceof LinkedControlWheelBlock)) {
            // adding in Keyboard animation
            if (state.getBlock() instanceof dev.bennethogan.universalkeyboard.block.LinkedKeyboardBlock)
                KeyboardAnimations.render(be, state, partialTick, poseStack, buffer, packedLight);
            return;
        }

        AttachFace face   = state.getValue(LinkedControlWheelBlock.FACE);
        Direction  facing = state.getValue(LinkedControlWheelBlock.FACING);
        boolean    wall   = face == AttachFace.WALL;

        ModelResourceLocation modelLoc = wall ? WALL_MODEL : WHEEL_MODEL;
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(modelLoc);

        float angle = currentAngle(be, partialTick);

        poseStack.pushPose();

        // orient to blockstate
        BlockModelRotation orient = orientationFor(face, facing);
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(orient.getRotation().getMatrix());
        poseStack.translate(-0.5, -0.5, -0.5);

        // spin it babyy
        if (angle != 0.0f) {
            float px = wall ? WALL_PX : FLOOR_PX;
            float py = wall ? WALL_PY : FLOOR_PY;
            float pz = wall ? WALL_PZ : FLOOR_PZ;
            org.joml.Vector3f axis = wall ? WALL_AXIS : FLOOR_AXIS;
            poseStack.translate(px, py, pz);
            poseStack.mulPose(new Quaternionf().rotateAxis((float) Math.toRadians(angle), axis.x, axis.y, axis.z));
            poseStack.translate(-px, -py, -pz);
        }

        ModelBlockRenderer mbr = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        VertexConsumer vc = buffer.getBuffer(RenderType.solid());
        mbr.renderModel(poseStack.last(), vc, state, model, 1.0f, 1.0f, 1.0f,
                packedLight, OverlayTexture.NO_OVERLAY);

        // Dashboard overlay on the wheel's screen plate
        ControlWheelScreenRenderer.render(be, poseStack, buffer, wall, partialTick);

        poseStack.popPose();
    }

    static float currentAngle(LinkedKeyboardBlockEntity be, float partialTick) {
        return currentFraction(be, partialTick) * MAX_ANGLE;
    }

    private static float currentFraction(LinkedKeyboardBlockEntity be, float partialTick) {
        if (be.getLevel() == null || be.getWheelAnimStartTick() == Long.MIN_VALUE)
            return be.getWheelTargetFraction();
        float start   = be.getWheelStartFraction();
        float target  = be.getWheelTargetFraction();
        float delta   = target - start;
        if (delta == 0f) return target;
        float elapsed  = (be.getLevel().getGameTime() - be.getWheelAnimStartTick()) + partialTick;
        float duration = LinkedKeyboardBlockEntity.wheelDuration(start, target);
        float t = Math.max(0f, Math.min(1f, elapsed / duration));
        return start + delta * LinkedKeyboardBlockEntity.wheelSmoothstep(t);
    }

    static BlockModelRotation orientationFor(AttachFace face, Direction facing) {
        return switch (face) {
            case FLOOR   -> BlockModelRotation.by(0,   yFloor(facing));
            case CEILING -> BlockModelRotation.by(180, yFloor(facing));
            case WALL    -> BlockModelRotation.by(0,   yWall(facing));
        };
    }

    private static int yFloor(Direction facing) {
        return switch (facing) {
            case EAST  -> 90;
            case SOUTH -> 180;
            case WEST  -> 270;
            default    -> 0;   // NORTH
        };
    }

    private static int yWall(Direction facing) {
        return switch (facing) {
            case NORTH -> 180;
            case EAST  -> 270;
            case WEST  -> 90;
            default    -> 0;   // SOUTH
        };
    }
}
