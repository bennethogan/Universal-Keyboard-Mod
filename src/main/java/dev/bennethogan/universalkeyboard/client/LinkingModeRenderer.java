package dev.bennethogan.universalkeyboard.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.universalkeyboard.compat.CreateValueHelper;
import dev.bennethogan.universalkeyboard.compat.PeripheralHelper;
import dev.bennethogan.universalkeyboard.item.LinkedKeyboardItem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;
import java.util.Map;

public class LinkingModeRenderer {


    /** New separation in linking mode to separate blocks that can link only because they
     * are the "Create Value Panel" blocks. Those are mostly worthless connections, so I
     * dont want to get peoples hopes up, basically, by making those connections look the same.
     * Colors will be pink/purple instead of yellow/blue on the link bounding box
    */
     private static boolean isValuePanelOnly(Level level, BlockPos pos) {
        if (level == null) return false;
        if (!CreateValueHelper.hasScrollValue(level.getBlockEntity(pos))) return false;
        return !PeripheralHelper.hasPeripheral(level, pos);
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int activeChannel;
        Map<Integer, List<BlockPos>> allTargets;
        Map<Integer, List<String>> allModules;   // point-linked panel modules, for the yellow outlines
        BlockPos controllerPos = null;   // linking stick: the selected controller, drawn green

        ItemStack held = mc.player.getMainHandItem();
        ItemStack offhand = mc.player.getOffhandItem();
        ItemStack stick = held.getItem() instanceof dev.bennethogan.universalkeyboard.item.LinkingStickItem ? held
                : (offhand.getItem() instanceof dev.bennethogan.universalkeyboard.item.LinkingStickItem ? offhand : ItemStack.EMPTY);

        if (!stick.isEmpty()) {
            controllerPos = dev.bennethogan.universalkeyboard.item.LinkingStickItem.getTarget(stick);
            if (controllerPos == null) return;
            activeChannel = dev.bennethogan.universalkeyboard.item.LinkingStickItem.getActiveChannel(stick);
            if (mc.level != null
                    && mc.level.getBlockEntity(controllerPos) instanceof LinkedKeyboardBlockEntity kb) {
                allTargets = kb.getAllChannelTargets();
                allModules = kb.getAllChannelModules();
            } else {
                allTargets = Map.of();
                allModules = Map.of();
            }
        } else {
            ItemStack kbd = held.getItem() instanceof LinkedKeyboardItem ? held
                    : (offhand.getItem() instanceof LinkedKeyboardItem ? offhand : ItemStack.EMPTY);
            if (kbd.isEmpty() || !LinkedKeyboardItem.isLinkingMode(kbd)) return;
            activeChannel = LinkedKeyboardItem.getActiveLinkingChannel(kbd);
            allTargets = LinkedKeyboardItem.getAllChannelTargets(kbd);
            allModules = LinkedKeyboardItem.getAllChannelModules(kbd);
            if (allTargets.isEmpty()) return;
        }

        Camera camera = event.getCamera();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        poseStack.pushPose();

        // Translate so (0,0,0) in pose space = camera position in world
        poseStack.translate(
                -camera.getPosition().x,
                -camera.getPosition().y,
                -camera.getPosition().z);

        VertexConsumer lineBuffer = bufferSource.getBuffer(RenderType.lines());
        Level level = mc.level;

        for (Map.Entry<Integer, List<BlockPos>> entry : allTargets.entrySet()) {
            int ch = entry.getKey();
            List<BlockPos> positions = entry.getValue();
            if (positions.isEmpty()) continue;

            boolean isActive = (ch == activeChannel);
            float alpha = isActive ? 1.0f : 0.6f;

            for (BlockPos pos : positions) {
                boolean vpOnly = isValuePanelOnly(level, pos);
                float r, g, b;
                if (vpOnly) {
                    if (isActive) { r = 1.0f; g = 0.30f; b = 0.70f; } // pink
                    else          { r = 0.60f; g = 0.20f; b = 0.85f; } // purple
                } else if (isActive) {
                    r = 1.0f; g = 0.85f; b = 0.0f; // yellow
                } else {
                    r = 0.5f; g = 0.5f; b = 0.5f;   // grey
                }

                AABB box = new AABB(pos).inflate(0.002);
                LevelRenderer.renderLineBox(poseStack, lineBuffer,
                        box.minX, box.minY, box.minZ,
                        box.maxX, box.maxY, box.maxZ,
                        r, g, b, alpha);
            }
        }

        // linking stick: draw the selected controller green
        if (controllerPos != null) {
            AABB kbBox = new AABB(controllerPos).inflate(0.02);
            LevelRenderer.renderLineBox(poseStack, lineBuffer,
                    kbBox.minX, kbBox.minY, kbBox.minZ,
                    kbBox.maxX, kbBox.maxY, kbBox.maxZ,
                    0.1f, 1.0f, 0.25f, 1.0f); // green
        }

        // render per module bounding box outline with dashpanels compat
        renderLinkedModuleOutlines(poseStack, lineBuffer, level, allTargets, allModules, activeChannel);

        bufferSource.endBatch(RenderType.lines());

        // Draw floating channel labels
        for (Map.Entry<Integer, List<BlockPos>> entry : allTargets.entrySet()) {
            int ch = entry.getKey();
            List<BlockPos> positions = entry.getValue();
            if (positions.isEmpty()) continue;

            boolean isActive = (ch == activeChannel);
            String label = String.valueOf(ch);

            for (BlockPos pos : positions) {
                boolean vpOnly = isValuePanelOnly(level, pos);
                int color;
                if (vpOnly) color = isActive ? 0xFFFF4DB3 : 0xFF9933CC; // pink or purple
                else        color = isActive ? 0xFFFFD700 : 0xFFAAAAAA; // gold or grey

                double cx = pos.getX() + 0.5;
                double cy = pos.getY() + 0.5;
                double cz = pos.getZ() + 0.5;

                poseStack.pushPose();
                poseStack.translate(cx, cy, cz);
                poseStack.mulPose(camera.rotation());
                poseStack.scale(-0.025f, -0.025f, 0.025f);

                net.minecraft.client.gui.Font font = mc.font;
                MultiBufferSource.BufferSource labelBuffer = mc.renderBuffers().bufferSource();

                int halfW = font.width(label) / 2;
                font.drawInBatch(label, -halfW, -font.lineHeight / 2,
                        color, false,
                        poseStack.last().pose(), labelBuffer,
                        net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);

                labelBuffer.endBatch();
                poseStack.popPose();
            }
        }

        poseStack.popPose();
    }

    // Dashpanels per module bounding box rendering
    private static void renderLinkedModuleOutlines(PoseStack poseStack, VertexConsumer lineBuffer, Level level,
                                                   Map<Integer, List<BlockPos>> allTargets,
                                                   Map<Integer, List<String>> allModules, int activeChannel) {
        if (level == null || allModules == null || allModules.isEmpty()) return;
        for (Map.Entry<Integer, List<String>> entry : allModules.entrySet()) {
            int ch = entry.getKey();
            List<String> modules = entry.getValue();
            List<BlockPos> targets = allTargets.get(ch);
            if (modules == null || modules.isEmpty() || targets == null) continue;

            boolean isActive = (ch == activeChannel);
            // active channel = yellow, others = grey
            float r = isActive ? 1.0f  : 0.5f;
            float g = isActive ? 0.85f : 0.5f;
            float b = isActive ? 0.0f  : 0.5f;
            float alpha = isActive ? 1.0f : 0.6f;

            for (BlockPos panelPos : targets) {
                if (!dev.bennethogan.universalkeyboard.compat.PanelControl.isPanel(level, panelPos)) continue;
                net.minecraft.core.Direction facing = panelFacing(level.getBlockState(panelPos));
                for (String name : modules) {
                    var o = dev.bennethogan.universalkeyboard.compat.PanelControl
                            .getModuleOutline(level, panelPos, name);
                    if (o == null) continue;
                    poseStack.pushPose();
                    poseStack.translate(panelPos.getX(), panelPos.getY() + 0.75, panelPos.getZ());
                    poseStack.rotateAround(com.mojang.math.Axis.YP.rotationDegrees(
                            facing.toYRot() + (facing.getAxis() == net.minecraft.core.Direction.Axis.Z ? 0 : 180)),
                            0.5f, 0f, 0.5f);
                    poseStack.translate((o.x() + o.sizeX()) / 16f, 0f, (o.y() + o.sizeY()) / 16f);
                    poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
                    poseStack.translate(0f, 0f, -0.25f);
                    // draw the shape cause its private
                    for (AABB box : o.shape().toAabbs())
                        LevelRenderer.renderLineBox(poseStack, lineBuffer,
                                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, alpha);
                    poseStack.popPose();
                }
            }
        }
    }

    private static net.minecraft.core.Direction panelFacing(net.minecraft.world.level.block.state.BlockState state) {
        var prop = state.getBlock().getStateDefinition().getProperty("facing");
        if (prop instanceof net.minecraft.world.level.block.state.properties.DirectionProperty dp)
            return state.getValue(dp);
        return net.minecraft.core.Direction.NORTH;
    }
}
