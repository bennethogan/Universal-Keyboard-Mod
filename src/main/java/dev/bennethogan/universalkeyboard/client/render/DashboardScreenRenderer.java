package dev.bennethogan.universalkeyboard.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.universalkeyboard.compat.SableCompat;
import dev.bennethogan.universalkeyboard.config.ModConfig;
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
import java.util.Set;


public final class DashboardScreenRenderer {

    private DashboardScreenRenderer() {}


    private static final float FACE_X    = 0.0f;
    private static final float CENTER_Y  = 15.5f;
    private static final float CENTER_Z  = 8.0f;
    private static final float SCREEN_W  = 11.5f;
    private static final float SCREEN_H  = 4.6f;

    private static final float FACE_YAW  = -90.0f;

    private static final float EPS        = 0.02f;
    private static final float MIN_SCALE  = 0.0065f;
    private static final float MAX_SCALE  = 0.0100f;
    private static final float COL_FILL   = 0.90f;
    private static final int   LINE_GAP   = 2;
    private static final int   SCREEN_LIGHT = 0xF000F0;

    private static final int COLOR_OFF      = 0xFF334433;
    private static final int COLOR_RPM      = 0xFFFF8844;
    private static final int COLOR_SPEED    = 0xFF44FFFF;
    private static final int COLOR_THROTTLE = 0xFFFFDD00;
    private static final int COLOR_GEAR     = 0xFF66FF99;
    private static final int COLOR_STARTUP  = 0xFFFFFFFF;

    private static final long STARTUP_TICKS = 40L;  // 2 s at 20 TPS

    // cache
    private record Line(String text, int color) {}
    private record Cached(long tick, List<List<Line>> columns) {}

    private static final Map<BlockPos, Cached>  CACHE          = new HashMap<>();
    private static final Map<BlockPos, Long>    ENGINE_ON_TICK = new HashMap<>();
    private static final Map<BlockPos, Boolean> PREV_ENGINE_ON = new HashMap<>();
    private static final int REFRESH_TICKS = 2;

    //  entry point
    public static void render(LinkedKeyboardBlockEntity be, PoseStack poseStack,
                              MultiBufferSource buffer, float partialTick) {
        Level level = be.getLevel();
        if (level == null) return;

        BlockPos pos = be.getBlockPos();

        List<List<Line>> columns;
        try {
            columns = cachedColumns(level, pos);
        } catch (Exception e) {
            columns = List.of(List.of(new Line("ERR", COLOR_OFF)));
        }
        if (columns.isEmpty()) return;

        float screenW = SCREEN_W / 16f;
        float screenH = SCREEN_H / 16f;
        float cy      = CENTER_Y  / 16f;
        float cz      = CENTER_Z  / 16f;
        float outX    = (FACE_X - EPS) / 16f;

        Font font  = Minecraft.getInstance().font;
        int  lineH = font.lineHeight + LINE_GAP;

        int nCols   = columns.size();
        int maxRows = 1;
        int widest  = 1;
        for (List<Line> col : columns) {
            maxRows = Math.max(maxRows, col.size());
            for (Line l : col) widest = Math.max(widest, font.width(l.text()));
        }

        float fitW  = (screenW * COL_FILL / nCols) / widest;
        float fitH  = screenH / (maxRows * (float) lineH);
        float scale = Math.max(MIN_SCALE, Math.min(Math.min(fitW, fitH), MAX_SCALE));

        poseStack.pushPose();
        poseStack.translate(outX, cy, cz);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(FACE_YAW));
        poseStack.scale(scale, -scale, scale);

        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource.BufferSource textBuffer = Minecraft.getInstance().renderBuffers().bufferSource();

        float totalWFont = screenW / scale;
        float colWFont   = totalWFont / nCols;
        for (int c = 0; c < nCols; c++) {
            List<Line> col = columns.get(c);
            float colCenterX = -totalWFont / 2f + colWFont * (c + 0.5f);
            float startY     = -(col.size() * lineH) / 2f;
            for (int i = 0; i < col.size(); i++) {
                Line  l  = col.get(i);
                float lx = colCenterX - font.width(l.text()) / 2f;
                float ly = startY + i * lineH;
                font.drawInBatch(l.text(), lx, ly, l.color(), false,
                        matrix, textBuffer, Font.DisplayMode.POLYGON_OFFSET, 0, SCREEN_LIGHT);
            }
        }
        textBuffer.endBatch();
        poseStack.popPose();
    }


    private static List<List<Line>> cachedColumns(Level level, BlockPos pos) {
        long now = level.getGameTime();
        Cached c = CACHE.get(pos);
        if (c != null && now - c.tick() < REFRESH_TICKS) return c.columns();
        if (CACHE.size() > 32) CACHE.clear();
        List<List<Line>> columns = buildColumns(level, pos, now);
        CACHE.put(pos.immutable(), new Cached(now, columns));
        return columns;
    }

    private static List<List<Line>> buildColumns(Level level, BlockPos pos, long now) {
        try {
            return buildColumnsInner(level, pos, now);
        } catch (Exception e) {
            return fallbackColumns();
        }
    }

    private static List<List<Line>> buildColumnsInner(Level level, BlockPos pos, long now) {

        int startStopRow = safeParseRow(safeGet(ModConfig.CLIENT.dashboardStartStopRow));
        boolean liveActive = LiveControlManager.isActive()
                && pos.equals(LiveControlManager.getKeyboardPos());

        boolean engineOn;
        if (startStopRow < 0) {
            engineOn = true;
        } else {
            engineOn = liveActive && LiveControlManager.isRowActive(startStopRow);
        }

        if (!engineOn) {
            PREV_ENGINE_ON.put(pos.immutable(), false);
            ENGINE_ON_TICK.remove(pos);
            return List.of(List.of(new Line("-- OFF --", COLOR_OFF)));
        }

        boolean wasOn = PREV_ENGINE_ON.getOrDefault(pos, false);
        PREV_ENGINE_ON.put(pos.immutable(), true);
        if (!wasOn) ENGINE_ON_TICK.put(pos.immutable(), now);

        long onSince = ENGINE_ON_TICK.getOrDefault(pos, now);
        boolean startup = (now - onSince) < STARTUP_TICKS;

        int    rpm      = startup ? 9999  : readRpm();
        double speed    = startup ? 999.9 : readSpeed(level, pos);
        int    throttle = startup ? 15    : readThrottle();
        String gear     = "N";   // placeholder — gear system not yet implemented

        int rpmColor  = startup ? COLOR_STARTUP : COLOR_RPM;
        int spdColor  = startup ? COLOR_STARTUP : COLOR_SPEED;
        int thrColor  = startup ? COLOR_STARTUP : COLOR_THROTTLE;
        int gearColor = startup ? COLOR_STARTUP : COLOR_GEAR;

        List<Line> left = new ArrayList<>(2);
        left.add(new Line(String.format("%4d RPM",   rpm),   rpmColor));
        left.add(new Line(String.format("%5.1f b/s", speed), spdColor));

        List<Line> right = new ArrayList<>(2);
        right.add(new Line(String.format("THR %2d", throttle), thrColor));
        right.add(new Line("GEAR " + gear,                     gearColor));

        return List.of(left, right);
    }

    private static List<List<Line>> fallbackColumns() {
        List<Line> left = new ArrayList<>(2);
        left.add(new Line("   0 RPM",  COLOR_RPM));
        left.add(new Line("  0.0 b/s", COLOR_SPEED));

        List<Line> right = new ArrayList<>(2);
        right.add(new Line("THR  0",   COLOR_THROTTLE));
        right.add(new Line("GEAR N",   COLOR_GEAR));

        return List.of(left, right);
    }


    private static int readRpm() {
        try {
            int lastIdx = LiveControlManager.getLastRpmBindingIdx();
            if (lastIdx >= 0) return LiveControlManager.getRpmValue(lastIdx);
            int cfgRow = safeParseRow(safeGet(ModConfig.CLIENT.dashboardRpmRow));
            return cfgRow >= 0 ? LiveControlManager.getRpmValue(cfgRow) : 0;
        } catch (Exception e) { return 0; }
    }

    private static double readSpeed(Level level, BlockPos pos) {
        try {
            if (!SableCompat.isPresent() || !SableCompat.isOnSublevel(level, pos)) return 0.0;
            double vx = SableCompat.getValue(level, pos, "velX");
            double vz = SableCompat.getValue(level, pos, "velZ");
            return Math.sqrt(vx * vx + vz * vz) / 1000.0;
        } catch (Exception e) { return 0.0; }
    }

    private static int readThrottle() {
        try {
            Set<Integer> rows = LiveControlManager.parseRowSet(
                    safeGet(ModConfig.CLIENT.dashboardThrottleRows));
            int max = 0;
            for (int row : rows) max = Math.max(max, LiveControlManager.getRowSignalStrength(row));
            return max;
        } catch (Exception e) { return 0; }
    }


    private static String safeGet(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> val) {
        try { return val == null ? "" : val.get(); } catch (Exception e) { return ""; }
    }

    private static int safeParseRow(String cfg) {
        if (cfg == null || cfg.isBlank()) return -1;
        try {
            int n = Integer.parseInt(cfg.trim());
            return (n >= 1 && n <= 40) ? n - 1 : -1;
        } catch (NumberFormatException e) { return -1; }
    }
}
