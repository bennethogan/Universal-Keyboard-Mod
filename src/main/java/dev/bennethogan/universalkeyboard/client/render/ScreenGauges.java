package dev.bennethogan.universalkeyboard.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.universalkeyboard.compat.SableCompat;
import dev.bennethogan.universalkeyboard.config.ModConfig;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ScreenGauges {

    private ScreenGauges() {}

    public record Line(String text, int color) {}

    static final int COLOR_OFF     = 0xFF334433;
    static final int COLOR_DISPLAY = 0xFFCCE8FF;   // sequencer-written text
    static final int COLOR_RPM     = 0xFFFF8844;
    static final int COLOR_RS      = 0xFFFF5555;   // redstone signal
    static final int COLOR_THR     = 0xFFFFDD00;   // thruster power
    static final int COLOR_SPEED   = 0xFF44FFFF;
    static final int COLOR_POS     = 0xFF88FF88;   // world X/Z position

    static final int BG_COLOR = 0xE0202020;        // dark grey screen backing (when on)

    private static final int LINE_GAP = 2;
    private static final int MAX_CELL_CHARS = 10;   // each gauge cell is truncated to this

    private record Cached(long tick, List<List<Line>> columns) {}
    private static final Map<BlockPos, Cached> CACHE = new HashMap<>();
    private static final int REFRESH_TICKS = 2;

    // Content

    public static List<List<Line>> columns(Level level, BlockPos pos) {
        long now = level.getGameTime();
        Cached c = CACHE.get(pos);
        if (c != null && now - c.tick() < REFRESH_TICKS) return c.columns();
        if (CACHE.size() > 32) CACHE.clear();
        List<List<Line>> cols = build(level, pos);
        CACHE.put(pos.immutable(), new Cached(now, cols));
        return cols;
    }

    private static List<List<Line>> build(Level level, BlockPos pos) {
        try {
            return buildInner(level, pos);
        } catch (Exception e) {
            return fallback();
        }
    }

    private static List<List<Line>> buildInner(Level level, BlockPos pos) {
        if (dev.bennethogan.universalkeyboard.livecontrol.LiveControlManager.cameraOnly()) return List.of();

        String[] disp   = displayLines(level, pos);   // sequencer DISPLAY lines, or null
        boolean  enabled = safeBool(ModConfig.CLIENT.screenEnabled);

        if (!enabled && disp == null) return List.of();

        Line tl, tr, bl, br;
        if (enabled) {
            double speed = readSpeed(level, pos);
            long   posX  = Math.round(readWorldPos(level, pos, "posX"));
            long   posZ  = Math.round(readWorldPos(level, pos, "posZ"));
            tl = new Line(String.format("%5.1f b/s", speed), COLOR_SPEED);
            tr = visualizedLine();
            bl = new Line("X " + posX, COLOR_POS);
            br = new Line("Z " + posZ, COLOR_POS);
        } else {
            tl = tr = bl = br = new Line("", COLOR_OFF);
        }

        // Sequencer DISPLAY per-cell overrides: line 1->TL, 2->TR, 3->BL, 4->BR
        // A blank line reverts that cell to its gauge
        tl = cap(override(disp, 0, tl));
        tr = cap(override(disp, 1, tr));
        bl = cap(override(disp, 2, bl));
        br = cap(override(disp, 3, br));

        if (tl.text().isEmpty() && tr.text().isEmpty() && bl.text().isEmpty() && br.text().isEmpty())
            return List.of();

        return List.of(List.of(tl, bl), List.of(tr, br));
    }

    private static Line cap(Line l) {
        String t = l.text();
        return t.length() <= MAX_CELL_CHARS ? l : new Line(t.substring(0, MAX_CELL_CHARS), l.color());
    }

    private static Line override(String[] disp, int lineIdx, Line base) {
        if (disp == null || lineIdx >= disp.length) return base;
        String s = disp[lineIdx];
        if (s == null || s.isEmpty()) return base;
        return new Line(s, COLOR_DISPLAY);
    }

    private static String[] displayLines(Level level, BlockPos pos) {
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof LinkedKeyboardBlockEntity kbd) return kbd.getDisplayLines();  // null if none set
        return null;
    }

    private static List<List<Line>> fallback() {
        List<Line> left = new ArrayList<>(2);
        left.add(new Line("  0.0 b/s", COLOR_SPEED));
        left.add(new Line("X --", COLOR_POS));
        List<Line> right = new ArrayList<>(2);
        right.add(new Line("---", COLOR_OFF));
        right.add(new Line("Z --", COLOR_POS));
        return List.of(left, right);
    }


    private static Line visualizedLine() {
        int[] rows = parseRows(safeGet(ModConfig.CLIENT.screenVisualizedRows));
        if (rows.length == 0) return new Line("---", COLOR_OFF);
        int idx = LiveControlManager.mostRecentlyChosenAmong(rows);
        if (idx < 0) idx = rows[0];
        LiveControlBinding.ActionType type = LiveControlManager.getRowActionType(idx);
        if (type == null) return new Line("---", COLOR_OFF);
        return switch (type) {
            case RPM_CONTROL    -> new Line(String.format("%4d RPM", LiveControlManager.getRpmValue(idx)), COLOR_RPM);
            case REDSTONE       -> new Line("RS: "  + LiveControlManager.getRowSignalStrength(idx), COLOR_RS);
            case THRUSTER_POWER -> new Line("Thr: " + LiveControlManager.getThrusterValue(idx),     COLOR_THR);
            default             -> new Line("---", COLOR_OFF);
        };
    }

    private static double readSpeed(Level level, BlockPos pos) {
        try {
            if (!SableCompat.isPresent() || !SableCompat.isOnSublevel(level, pos)) return 0.0;
            double vx = SableCompat.getValue(level, pos, "velX");
            double vz = SableCompat.getValue(level, pos, "velZ");
            return Math.sqrt(vx * vx + vz * vz);   // getVelocity already returns blocks/second
        } catch (Exception e) { return 0.0; }
    }

    private static double readWorldPos(Level level, BlockPos pos, String getter) {
        try {
            if (SableCompat.isPresent() && SableCompat.isOnSublevel(level, pos))
                return SableCompat.getValue(level, pos, getter);
        } catch (Exception ignored) {}
        return ("posX".equals(getter) ? pos.getX() : pos.getZ()) + 0.5;
    }

    private static String safeGet(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> val) {
        try { return val == null ? "" : val.get(); } catch (Exception e) { return ""; }
    }

    private static boolean safeBool(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue val) {
        try { return val == null || val.get(); } catch (Exception e) { return true; }
    }

    private static int[] parseRows(String cfg) {
        if (cfg == null || cfg.isBlank()) return new int[0];
        List<Integer> out = new ArrayList<>();
        for (String p : cfg.split(",")) {
            try {
                int n = Integer.parseInt(p.trim());
                if (n >= 1 && n <= 40) out.add(n - 1);
            } catch (NumberFormatException ignored) {}
        }
        int[] arr = new int[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        return arr;
    }

    // Drawing
    public static void drawColumns(PoseStack poseStack, List<List<Line>> columns,
                                   float screenW, float screenH,
                                   float minScale, float maxScale, float colFill, int light) {
        if (columns.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        int lineH = font.lineHeight + LINE_GAP;

        int nCols = columns.size();
        int maxRows = 1, widest = 1;
        for (List<Line> col : columns) {
            maxRows = Math.max(maxRows, col.size());
            for (Line l : col) widest = Math.max(widest, font.width(l.text()));
        }
        float fitW  = (screenW * colFill / nCols) / widest;
        float fitH  = screenH / (maxRows * (float) lineH);
        float scale = Math.max(minScale, Math.min(Math.min(fitW, fitH), maxScale));

        poseStack.pushPose();
        poseStack.scale(scale, -scale, scale);
        Matrix4f matrix = poseStack.last().pose();

        MultiBufferSource.BufferSource src = Minecraft.getInstance().renderBuffers().bufferSource();

        float halfW = (screenW / scale) / 2f;
        float halfH = (screenH / scale) / 2f;
        VertexConsumer bg = src.getBuffer(RenderType.textBackground());
        bg.addVertex(matrix, -halfW, -halfH, 0).setColor(BG_COLOR).setLight(light);
        bg.addVertex(matrix, -halfW,  halfH, 0).setColor(BG_COLOR).setLight(light);
        bg.addVertex(matrix,  halfW,  halfH, 0).setColor(BG_COLOR).setLight(light);
        bg.addVertex(matrix,  halfW, -halfH, 0).setColor(BG_COLOR).setLight(light);
        bg.addVertex(matrix, -halfW, -halfH, 0).setColor(BG_COLOR).setLight(light);
        bg.addVertex(matrix,  halfW, -halfH, 0).setColor(BG_COLOR).setLight(light);
        bg.addVertex(matrix,  halfW,  halfH, 0).setColor(BG_COLOR).setLight(light);
        bg.addVertex(matrix, -halfW,  halfH, 0).setColor(BG_COLOR).setLight(light);

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
                        matrix, src, Font.DisplayMode.POLYGON_OFFSET, 0, light);
            }
        }
        src.endBatch();
        poseStack.popPose();
    }
}
