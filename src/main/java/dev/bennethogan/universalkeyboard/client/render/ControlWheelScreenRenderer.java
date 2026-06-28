package dev.bennethogan.universalkeyboard.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.universalkeyboard.compat.SableCompat;
import dev.bennethogan.universalkeyboard.compat.VistaCompat;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class ControlWheelScreenRenderer {

    private ControlWheelScreenRenderer() {}

    private static final float FLOOR_X0 = 4.0f,  FLOOR_X1 = 11.7f;
    private static final float FLOOR_Y0 = 10.1f, FLOOR_Y1 = 14.0f;
    private static final float FLOOR_FACE_Z = 11.647f;


    private static final float WALL_X0 = 5.4f,  WALL_X1 = 10.6f;
    private static final float WALL_Y0 = 10.0f, WALL_Y1 = 12.6f;
    private static final float WALL_FACE_Z = -0.42f;

    private static final float EPS       = 0.002f;
    private static final float EPS_VISTA = 0.08f;
    private static final float MIN_SCALE = 0.0115f;
    private static final float MAX_SCALE = 0.014f;
    private static final int   LINE_GAP = 2;
    private static final int   SCREEN_LIGHT = 0xF000F0;

    private static final int COLOR_KEYS = 0xFFFFC04D;
    private static final int COLOR_INFO = 0xFF66FF99;
    private static final int COLOR_IDLE = 0xFF4D8C66;

    private static final int MAX_KEY_LABELS  = 4;
    private static final int KEYS_PER_LINE   = 2;
    private static final int REFRESH_TICKS   = 2;

    // Cache content to not query sable every frame
    private record Cached(long tick, List<Line> lines) {}
    record Line(String text, int color) {}
    private static final Map<BlockPos, Cached> CACHE = new HashMap<>();

    public static void render(LinkedKeyboardBlockEntity be, PoseStack poseStack,
                              MultiBufferSource buffer, boolean wall, float partialTick) {
        Level level = be.getLevel();
        if (level == null) return;

        BlockPos pos = be.getBlockPos();

        float x0    = wall ? WALL_X0    : FLOOR_X0;
        float x1    = wall ? WALL_X1    : FLOOR_X1;
        float y0    = wall ? WALL_Y0    : FLOOR_Y0;
        float y1    = wall ? WALL_Y1    : FLOOR_Y1;
        float faceZ = wall ? WALL_FACE_Z : FLOOR_FACE_Z;

        float cx      = (x0 + x1) / 2f / 16f;
        float cy      = (y0 + y1) / 2f / 16f;
        float screenW = (x1 - x0) / 16f;
        float screenH = (y1 - y0) / 16f;
        float outZ    = wall ? (faceZ - EPS) : (faceZ + EPS);

        if (VistaCompat.isPresent()) {
            float vistaOutZ = wall ? (faceZ - EPS_VISTA) : (faceZ + EPS_VISTA);
            poseStack.pushPose();
            poseStack.translate(cx, cy, vistaOutZ / 16f);
            if (wall) poseStack.mulPose(Axis.YP.rotationDegrees(180f));
            boolean drew = VistaCompat.tryDrawFeed(level, pos, poseStack, buffer,
                    partialTick, screenW / 2f, screenH / 2f, SCREEN_LIGHT);
            poseStack.popPose();
            if (drew) {
                Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
                return;
            }
        }

        List<Line> lines = cachedLines(level, pos);
        if (lines.isEmpty()) return;

        Font font = Minecraft.getInstance().font;

        int lineH = font.lineHeight + LINE_GAP;
        int widest = 1;
        for (Line l : lines) widest = Math.max(widest, font.width(l.text()));
        float fitW  = screenW / widest;
        float fitH  = screenH / (lines.size() * (float) lineH);
        float scale = Math.max(MIN_SCALE, Math.min(Math.min(fitW, fitH), MAX_SCALE));

        poseStack.pushPose();
        poseStack.translate(cx, cy, outZ / 16f);
        if (wall) poseStack.mulPose(Axis.YP.rotationDegrees(180f));
        poseStack.scale(scale, -scale, scale);

        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource.BufferSource textBuffer = Minecraft.getInstance().renderBuffers().bufferSource();

        float totalH = lines.size() * lineH;
        float startY = -totalH / 2f;
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            float lx = -font.width(l.text()) / 2f;
            float ly = startY + i * lineH;
            font.drawInBatch(l.text(), lx, ly, l.color(), false,
                    matrix, textBuffer, Font.DisplayMode.POLYGON_OFFSET, 0, SCREEN_LIGHT);
        }
        textBuffer.endBatch();
        poseStack.popPose();
    }

    static List<Line> cachedLines(Level level, BlockPos pos) {
        long now = level.getGameTime();
        Cached c = CACHE.get(pos);
        if (c != null && now - c.tick() < REFRESH_TICKS) return c.lines();

        if (CACHE.size() > 32) CACHE.clear();   // crude bound; only looked-at wheels populate it
        List<Line> lines = buildLines(level, pos);
        CACHE.put(pos.immutable(), new Cached(now, lines));
        return lines;
    }

    private static List<Line> buildLines(Level level, BlockPos pos) {
        List<Line> lines = new ArrayList<>();

        if (LiveControlManager.isActive() && pos.equals(LiveControlManager.getKeyboardPos())) {
            List<String> keys = LiveControlManager.screenKeyLabels(MAX_KEY_LABELS);
            if (keys.isEmpty()) {
                lines.add(new Line("LIVE", COLOR_KEYS));
            } else {
                for (int i = 0; i < keys.size(); i += KEYS_PER_LINE) {
                    StringBuilder sb = new StringBuilder();
                    for (int j = i; j < Math.min(i + KEYS_PER_LINE, keys.size()); j++) {
                        if (j > i) sb.append(' ');
                        sb.append(keys.get(j));
                    }
                    lines.add(new Line(sb.toString(), COLOR_KEYS));
                }
            }
        }

        lines.add(new Line(formatTime(level.getDayTime()), COLOR_INFO));

        if (SableCompat.isPresent() && SableCompat.isOnSublevel(level, pos)) {
            double vx = SableCompat.getValue(level, pos, "velX");
            double vy = SableCompat.getValue(level, pos, "velY");
            double vz = SableCompat.getValue(level, pos, "velZ");
            double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
            lines.add(new Line(String.format("%.1f m/s", speed), COLOR_INFO));
        }

        if (lines.size() == 1) {
            lines.add(new Line("- idle -", COLOR_IDLE));
        }
        return lines;
    }

    private static String formatTime(long dayTime) {
        long t = ((dayTime % 24000) + 24000) % 24000;
        int hours = (int) ((t / 1000 + 6) % 24);
        int minutes = (int) ((t % 1000) * 60 / 1000);
        return String.format("%02d:%02d", hours, minutes);
    }
}
