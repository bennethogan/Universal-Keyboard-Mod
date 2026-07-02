package dev.bennethogan.universalkeyboard.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.platform.NativeImage;
import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import dev.bennethogan.universalkeyboard.block.LinkedKeyboardBlock;
import dev.bennethogan.universalkeyboard.block.ModBlocks;
import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import dev.bennethogan.universalkeyboard.client.KeyboardCaptureManager;
import dev.bennethogan.universalkeyboard.client.gamepad.MouseLiveDriver;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlManager;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class KeyboardAnimations {

    private KeyboardAnimations() {}

    private static final int CLUSTER_SPLIT_COL = 8; //split at column 8 for a separate numpad animation
    // user configurable animation settings (Client -> Animation)
    static boolean animationsEnabled() {
        try { return dev.bennethogan.universalkeyboard.config.ModConfig.CLIENT.enableKeyboardAnimations.get(); }
        catch (Exception e) { return true; }
    }

    private static float waveSpeed() {
        try { return dev.bennethogan.universalkeyboard.config.ModConfig.CLIENT.mainWaveSpeed.get().floatValue(); }
        catch (Exception e) { return 0.15f; }
    }

    private static float clusterWaveSpeed() {
        try { return dev.bennethogan.universalkeyboard.config.ModConfig.CLIENT.clusterWaveSpeed.get().floatValue(); }
        catch (Exception e) { return 0.05f; }
    }
    // ------------

    private static final ResourceLocation WHITE_TEX =
            ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "textures/block/white.png");
    private static final ResourceLocation RAINBOW_TEX =
            ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "textures/block/keyboardtexture_rainbow.png");
    private static final ResourceLocation RAINBOW_MOUSE_TEX =
            ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "textures/block/mousetexture_rainbow.png");

    // separate mouse models for live polling based animation
    public static final List<ModelResourceLocation> MOUSE_MODELS = new ArrayList<>();
    private static final Map<String, ModelResourceLocation[]> MOUSE_BY_BLOCK = new HashMap<>();
    private static final String[] KEYBOARD_NAMES = {
            "universal_keyboard", "trans_keyboard", "rainbow_keyboard", "ace_keyboard", "bi_keyboard"
    };

    static {
        for (String name : KEYBOARD_NAMES) {
            ModelResourceLocation floor = ModelResourceLocation.standalone(
                    ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "block/" + name + "_mouse"));
            ModelResourceLocation wall = ModelResourceLocation.standalone(
                    ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "block/" + name + "_wall_mouse"));
            MOUSE_MODELS.add(floor);
            MOUSE_MODELS.add(wall);
            MOUSE_BY_BLOCK.put(name, new ModelResourceLocation[]{floor, wall});
        }
    }

    // Rainbow keyboard's animation area, and color code threshold for "plastic" vs "color" keys
    private static final int TOP_W = 24, TOP_H = 8;
    private static final int PLASTIC_MAX_CHANNEL = 0x40;
    private static int[] plasticGrid; // Per texel: ARGB color, or 0 if it's a key slot
    private static int[][] keyCols; // Per row: main-keyboard key columns, ascending
    private static int[][] keyColors; // per row: rest colors (ARGB) of main keys, same order as keyCols
    private static int[][] clusterKeyCols; //per row, numPad and other keys
    private static int[][] clusterKeyColors; //per row rest colors for other keys
    private static boolean rainbowLoaded, rainbowFailed;

    // rainbow mouse texture read, grabbing colored texels and cycling through them
    private static int[] mouseKeyColors;
    private static boolean mouseLoaded, mouseFailed;

    // Top-face texel colors per keyboard variant, for the press-darken effect
    // Loaded lazily so an empty array marks a texture that failed to load
    private static final Map<String, String> KEY_TEX_BY_BLOCK = Map.of(
            "universal_keyboard", "keyboard_black_texture",
            "trans_keyboard",     "keyboardtexture_trans",
            "rainbow_keyboard",   "keyboardtexture_rainbow",
            "ace_keyboard",       "keyboardtexture_ace",
            "bi_keyboard",        "keyboardtexture_bi");
    private static final Map<String, int[]> topGrids = new HashMap<>();

    private static int[] topGrid(String blockPath) {
        int[] cached = topGrids.get(blockPath);
        if (cached != null) return cached.length == 0 ? null : cached;
        String texName = KEY_TEX_BY_BLOCK.get(blockPath);
        if (texName == null) { topGrids.put(blockPath, new int[0]); return null; }
        int[] grid = new int[0];
        try (var res = Minecraft.getInstance().getResourceManager().open(
                ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "textures/block/" + texName + ".png"));
             NativeImage img = NativeImage.read(res)) {
            grid = new int[TOP_W * TOP_H];
            for (int ty = 0; ty < TOP_H; ty++) {
                for (int tx = 0; tx < TOP_W; tx++) {
                    int abgr = img.getPixelRGBA(tx, ty);
                    int r = abgr & 0xFF, g = (abgr >> 8) & 0xFF, b = (abgr >> 16) & 0xFF;
                    grid[ty * TOP_W + tx] = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
            }
        } catch (Exception e) {
            UniversalKeyboardMod.LOGGER.warn("Couldn't read keyboard texture {} for key-press animation: {}",
                    texName, e.getMessage());
        }
        topGrids.put(blockPath, grid);
        return grid.length == 0 ? null : grid;
    }

    private static boolean loadMouseData() {
        if (mouseLoaded) return !mouseFailed;
        mouseLoaded = true;
        try (var res = Minecraft.getInstance().getResourceManager().open(RAINBOW_MOUSE_TEX);
             NativeImage img = NativeImage.read(res)) {
            List<Integer> palette = new ArrayList<>();
            for (int ty = 0; ty < img.getHeight(); ty++) {
                for (int tx = 0; tx < img.getWidth(); tx++) {
                    int abgr = img.getPixelRGBA(tx, ty);
                    int a = (abgr >> 24) & 0xFF;
                    int r = abgr & 0xFF, g = (abgr >> 8) & 0xFF, b = (abgr >> 16) & 0xFF;
                    if (a == 0) continue;
                    if (r < PLASTIC_MAX_CHANNEL && g < PLASTIC_MAX_CHANNEL && b < PLASTIC_MAX_CHANNEL) continue;
                    palette.add(0xFF000000 | (r << 16) | (g << 8) | b);
                }
            }
            if (palette.isEmpty()) { mouseFailed = true; return false; }
            mouseKeyColors = palette.stream().mapToInt(Integer::intValue).toArray();
        } catch (Exception e) {
            UniversalKeyboardMod.LOGGER.warn("Couldn't read rainbow mouse texture for animation: {}", e.getMessage());
            mouseFailed = true;
        }
        return !mouseFailed;
    }

    private static boolean loadRainbowData() {
        if (rainbowLoaded) return !rainbowFailed;
        rainbowLoaded = true;
        try (var resource = Minecraft.getInstance().getResourceManager()
                .open(RAINBOW_TEX);
             NativeImage img = NativeImage.read(resource)) {
            plasticGrid      = new int[TOP_W * TOP_H];
            keyCols          = new int[TOP_H][];
            keyColors        = new int[TOP_H][];
            clusterKeyCols   = new int[TOP_H][];
            clusterKeyColors = new int[TOP_H][];
            for (int ty = 0; ty < TOP_H; ty++) {
                List<Integer> mainCols = new ArrayList<>(), mainColors = new ArrayList<>();
                List<Integer> clusCols = new ArrayList<>(), clusColors = new ArrayList<>();
                for (int tx = 0; tx < TOP_W; tx++) {
                    int abgr = img.getPixelRGBA(tx, ty);
                    int r = abgr & 0xFF, g = (abgr >> 8) & 0xFF, b = (abgr >> 16) & 0xFF;
                    int argb = 0xFF000000 | (r << 16) | (g << 8) | b;
                    if (r < PLASTIC_MAX_CHANNEL && g < PLASTIC_MAX_CHANNEL && b < PLASTIC_MAX_CHANNEL) {
                        plasticGrid[ty * TOP_W + tx] = argb;
                    } else if (tx < CLUSTER_SPLIT_COL) {
                        clusCols.add(tx);
                        clusColors.add(argb);
                    } else {
                        mainCols.add(tx);
                        mainColors.add(argb);
                    }
                }
                keyCols[ty]          = mainCols.stream().mapToInt(Integer::intValue).toArray();
                keyColors[ty]        = mainColors.stream().mapToInt(Integer::intValue).toArray();
                clusterKeyCols[ty]   = clusCols.stream().mapToInt(Integer::intValue).toArray();
                clusterKeyColors[ty] = clusColors.stream().mapToInt(Integer::intValue).toArray();
            }
        } catch (Exception e) {
            UniversalKeyboardMod.LOGGER.warn("Couldn't read rainbow keyboard texture for key animation: {}", e.getMessage());
            rainbowFailed = true;
        }
        return !rainbowFailed;
    }

    // Rendering ----------------------------

    static void render(LinkedKeyboardBlockEntity be, BlockState state, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        AttachFace face   = state.getValue(LinkedKeyboardBlock.FACE);
        Direction  facing = state.getValue(LinkedKeyboardBlock.FACING);
        boolean    wall   = face == AttachFace.WALL;

        poseStack.pushPose();
        BlockModelRotation orient = ControlWheelRenderer.orientationFor(face, facing);
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(orient.getRotation().getMatrix());
        poseStack.translate(-0.5, -0.5, -0.5);

        // animates only the mouse for the keyboard which the player is using
        float ox = 0f, oy = 0f;
        net.minecraft.core.BlockPos activePos =
                LiveControlManager.isActive()       ? LiveControlManager.getKeyboardPos()
              : KeyboardCaptureManager.isCCCapturing() ? KeyboardCaptureManager.getCapturedPos()
              : null;
        if (activePos != null && be.getBlockPos().equals(activePos)) {
            ox = MouseLiveDriver.modelOffsetX(partialTick);
            oy = MouseLiveDriver.modelOffsetY(partialTick);
        }

        // key press effects show on whichever keyboard the player is typing into
        net.minecraft.core.BlockPos typingPos =
                LiveControlManager.isActive()         ? LiveControlManager.getKeyboardPos()
              : KeyboardCaptureManager.isCapturing()  ? KeyboardCaptureManager.getCapturedPos()
              : null;
        boolean typing = typingPos != null && be.getBlockPos().equals(typingPos);

        renderMouse(state.getBlock(), wall, ox, oy, poseStack, buffer, packedLight);
        if (!animationsEnabled()) {
            // master switch off: the static block texture shows; mouse still renders & moves
            poseStack.popPose();
            return;
        }
        if (state.getBlock() == ModBlocks.RAINBOW_KEYBOARD.get()) {
            renderRainbowMouseOverlay(be, wall, ox, oy, partialTick, poseStack, buffer);
            renderRainbowKeys(be, wall, typing, partialTick, poseStack, buffer, packedLight);
        } else {
            // other variants: darken the texels under held keys (rainbow does this in its own pass).
            boolean[] held = typing ? PressedKeys.texelGrid()
                                    : RemoteKeyAnim.texelGrid(be.getBlockPos());
            if (held != null)
                renderPressedKeys(state.getBlock(), wall, held, poseStack, buffer, packedLight);
        }

        poseStack.popPose();
    }


    // overlay the 4 cycling colors with the same animation as the numpad
    private static void renderRainbowMouseOverlay(LinkedKeyboardBlockEntity be, boolean wall,
            float ox, float oy, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer) {
        if (!loadMouseData() || be.getLevel() == null) return;

        float time = (be.getLevel().getGameTime() % 24000L) + partialTick;
        float clusterShift = time * clusterWaveSpeed();
        int   clusterWhole = (int) Math.floor(clusterShift);
        float clusterFrac  = clusterShift - clusterWhole;

        int n = mouseKeyColors.length;
        int[] animColors = new int[4];
        for (int i = 0; i < 4; i++) {
            int c0 = mouseKeyColors[Math.floorMod(i + clusterWhole, n)];
            int c1 = mouseKeyColors[Math.floorMod(i + clusterWhole + 1, n)];
            animColors[i] = lerpColor(c0, c1, clusterFrac);
        }

        VertexConsumer vc = buffer.getBuffer(RenderType.text(WHITE_TEX));
        final float e = 0.002f; // lift off face

        poseStack.pushPose();
        if (wall) poseStack.translate(ox / 16f, -oy / 16f, 0);
        else      poseStack.translate(ox / 16f, 0, oy / 16f);

        // apply the same rotation as the model
        float rotX = wall ? 16f / 16f : 14f / 16f;
        float rotY = wall ? 7f / 16f : 0f;
        float rotZ = wall ? 0f : 7f / 16f;
        poseStack.translate(rotX, rotY, rotZ);
        if (wall) poseStack.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(22.5)));
        else      poseStack.mulPose(new Quaternionf().rotateY((float) Math.toRadians(22.5)));
        poseStack.translate(-rotX, -rotY, -rotZ);

        Matrix4f pose = poseStack.last().pose();
        int light = LightTexture.FULL_BRIGHT;

        // Texel mapping: (tx,ty) -> animColor[0], (1,0) = 1, (0,1) = 2, (1,1) = 3
        // UV is [2,3,0,0]. tx -> model_X = 16-tx; ty -> model z / y = 10-ty;

        if (!wall) {
            // Floor mouse, use y
            float y = 1f / 16f + e;
            emitMouseQuad(vc, pose, 15, 16, y, 9, 10, animColors[0], false, light); // (0,0)
            emitMouseQuad(vc, pose, 14, 15, y, 9, 10, animColors[1], false, light); // (1,0)
            emitMouseQuad(vc, pose, 15, 16, y, 8,  9, animColors[2], false, light); // (0,1)
            emitMouseQuad(vc, pose, 14, 15, y, 8,  9, animColors[3], false, light); // (1,1)
        } else {
            // Wall mouse: SOUTH face at z=1/16; model shifted to x=16-18
            float z = 1f / 16f + e;
            emitMouseQuad(vc, pose, 17, 18, z, 9, 10, animColors[0], true, light); // (0,0)
            emitMouseQuad(vc, pose, 16, 17, z, 9, 10, animColors[1], true, light); // (1,0)
            emitMouseQuad(vc, pose, 17, 18, z, 8,  9, animColors[2], true, light); // (0,1)
            emitMouseQuad(vc, pose, 16, 17, z, 8,  9, animColors[3], true, light); // (1,1)
        }

        poseStack.popPose();
    }


    private static void emitMouseQuad(VertexConsumer vc, Matrix4f pose,
            int xA, int xB, float fixed, int cdA, int cdB,
            int argb, boolean wallFace, int light) {
        float x1 = xA / 16f, x2 = xB / 16f;
        float d1 = cdA / 16f, d2 = cdB / 16f;
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        if (wallFace) {
            vertex(vc, pose, x1, d1, fixed, r, g, b, light);
            vertex(vc, pose, x2, d1, fixed, r, g, b, light);
            vertex(vc, pose, x2, d2, fixed, r, g, b, light);
            vertex(vc, pose, x1, d2, fixed, r, g, b, light);
        } else {
            vertex(vc, pose, x1, fixed, d1, r, g, b, light);
            vertex(vc, pose, x1, fixed, d2, r, g, b, light);
            vertex(vc, pose, x2, fixed, d2, r, g, b, light);
            vertex(vc, pose, x2, fixed, d1, r, g, b, light);
        }
    }

    private static void renderMouse(Block block, boolean wall, float ox, float oy,
                                    PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        ModelResourceLocation[] models = MOUSE_BY_BLOCK.get(id.getPath());
        if (models == null) return;
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(models[wall ? 1 : 0]);

        poseStack.pushPose();
        if (wall) {
            poseStack.translate(ox / 16f, -oy / 16f, 0);
        } else {
            poseStack.translate(ox / 16f, 0, oy / 16f);
        }
        ModelBlockRenderer mbr = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        VertexConsumer vc = buffer.getBuffer(RenderType.solid());
        mbr.renderModel(poseStack.last(), vc, null, model, 1.0f, 1.0f, 1.0f,
                packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private static void renderPressedKeys(Block block, boolean wall, boolean[] held,
                                          PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (held == null) return;
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
        int[] grid = topGrid(path);
        if (grid == null) return;

        VertexConsumer vc = buffer.getBuffer(RenderType.text(WHITE_TEX));
        Matrix4f pose = poseStack.last().pose();
        for (int ty = 0; ty < TOP_H; ty++)
            for (int tx = 0; tx < TOP_W; tx++)
                if (held[ty * TOP_W + tx])
                    emitTexel(vc, pose, tx, ty, darken(grid[ty * TOP_W + tx]), packedLight, wall);
    }

    // color code math for 50% darker
    private static int darken(int argb) {
        return 0xFF000000
                | ((((argb >> 16) & 0xFF) / 2) << 16)
                | ((((argb >> 8)  & 0xFF) / 2) << 8)
                |   ((argb        & 0xFF) / 2);
    }

    private static void renderRainbowKeys(LinkedKeyboardBlockEntity be, boolean wall, boolean typing,
                                          float partialTick,
                                          PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (!loadRainbowData() || be.getLevel() == null) return;

        float time = (be.getLevel().getGameTime() % 24000L) + partialTick;
        float shift = time * waveSpeed();
        int wholeShift = (int) Math.floor(shift);
        float frac = shift - wholeShift;
        double now = be.getLevel().getGameTime() + partialTick;
        KeyRipples ripples = typing ? KeyRipples.LOCAL : RemoteKeyAnim.ripples(be.getBlockPos());
        boolean doRipples = ripples != null && ripples.any(now);
        boolean[] held = typing ? PressedKeys.texelGrid() : RemoteKeyAnim.texelGrid(be.getBlockPos());

        VertexConsumer vc = buffer.getBuffer(RenderType.text(WHITE_TEX));
        Matrix4f pose = poseStack.last().pose();
        int keyLight = LightTexture.FULL_BRIGHT;

        float clusterShift = time * clusterWaveSpeed();
        int clusterWhole = (int) Math.floor(clusterShift);
        float clusterFrac = clusterShift - clusterWhole;

        for (int ty = 0; ty < TOP_H; ty++) {
            for (int tx = 0; tx < TOP_W; tx++) {
                int plastic = plasticGrid[ty * TOP_W + tx];
                if (plastic != 0) emitTexel(vc, pose, tx, ty, plastic, packedLight, wall);
            }

            // Main area row wave animation
            int[] cols = keyCols[ty], colors = keyColors[ty];
            int n = cols.length;
            for (int i = 0; i < n; i++) {
                int c0 = colors[Math.floorMod(i + wholeShift, n)];
                int c1 = colors[Math.floorMod(i + wholeShift + 1, n)];
                int col = lerpColor(c0, c1, frac);
                if (doRipples) col = ripples.apply(cols[i], ty, col, now);
                if (held != null && held[ty * TOP_W + cols[i]]) col = darken(col);
                emitTexel(vc, pose, cols[i], ty, col, keyLight, wall);
            }

            // numpad cluster gets its own animation
            int[] cCols = clusterKeyCols[ty], cColors = clusterKeyColors[ty];
            int nc = cCols.length;
            for (int i = 0; i < nc; i++) {
                int c0 = cColors[Math.floorMod(i + clusterWhole, nc)];
                int c1 = cColors[Math.floorMod(i + clusterWhole + 1, nc)];
                int col = lerpColor(c0, c1, clusterFrac);
                if (doRipples) col = ripples.apply(cCols[i], ty, col, now);
                if (held != null && held[ty * TOP_W + cCols[i]]) col = darken(col);
                emitTexel(vc, pose, cCols[i], ty, col, keyLight, wall);
            }
        }
    }

    // draw texel right above static model, mirror flipped uv mapping
    private static void emitTexel(VertexConsumer vc, Matrix4f pose, int tx, int ty,
                                  int argb, int light, boolean wall) {
        final float e = 0.0015f; // lift above the face to dodge z-fighting
        float x1 = (13f - (tx + 1) * 0.5f) / 16f;
        float x2 = (13f - tx * 0.5f) / 16f;
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        if (wall) {
            float y1 = (5f + ty * 0.5f) / 16f;
            float y2 = (5f + (ty + 1) * 0.5f) / 16f;
            float z = 1f / 16f + e;
            vertex(vc, pose, x1, y1, z, r, g, b, light);
            vertex(vc, pose, x2, y1, z, r, g, b, light);
            vertex(vc, pose, x2, y2, z, r, g, b, light);
            vertex(vc, pose, x1, y2, z, r, g, b, light);
        } else {
            float z1 = (9f - (ty + 1) * 0.5f) / 16f;
            float z2 = (9f - ty * 0.5f) / 16f;
            float y = 1f / 16f + e;
            vertex(vc, pose, x1, y, z1, r, g, b, light);
            vertex(vc, pose, x1, y, z2, r, g, b, light);
            vertex(vc, pose, x2, y, z2, r, g, b, light);
            vertex(vc, pose, x2, y, z1, r, g, b, light);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose,
                               float x, float y, float z, int r, int g, int b, int light) {
        vc.addVertex(pose, x, y, z)
          .setColor(r, g, b, 255)
          .setUv(0.5f, 0.5f)
          .setLight(light);
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000
                | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8)
                |  (int) (ab + (bb - ab) * t);
    }
}
