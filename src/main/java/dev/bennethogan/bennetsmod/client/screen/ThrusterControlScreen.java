package dev.bennethogan.bennetsmod.client.screen;

import dev.bennethogan.bennetsmod.compat.KeyboardMode;
import dev.bennethogan.bennetsmod.network.ModPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public class ThrusterControlScreen extends Screen {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final int PAD        = 8;
    private static final int TITLE_H    = 12;
    private static final int READOUT_H  = 74;  // phosphor display height
    private static final int LABEL_H    = 12;  // slider-label row below readout
    private static final int SLIDER_W   = 24;  // vertical throttle slider width
    private static final int BTN_H      = 18;
    private static final int INST_OUTER = 110; // instrument face widget outer size
    private static final int INST_BEZEL = 8;   // metallic bezel thickness

    // ── State (read-only telemetry — updated from server) ─────────────────────
    final  BlockPos keyboardPos;
    private String  peripheralType;

    private double targetVectorX, targetVectorY;
    private double currentVectorX, currentVectorY;
    private int    thrust;
    private int    thrustConfig;
    private double configMax;
    private double currentThrustPn;
    private double displayedThrustPn;
    private double airflowMs;
    private int    obstruction;
    private int    fuelAmountMb;
    private int    fuelCapacityMb;

    // ── Layout ────────────────────────────────────────────────────────────────
    private int panelX, panelY, panelW, panelH;
    private int readoutX, readoutY, readoutW;

    // ── Widgets ───────────────────────────────────────────────────────────────
    private InstrumentGridWidget grid;
    private ThrottleSlider       thrustSlider;
    private ThrottleSlider       configSlider;  // creative only
    private int                  currentChannel;

    // ── Constructor ───────────────────────────────────────────────────────────
    public ThrusterControlScreen(BlockPos keyboardPos, String peripheralType,
            double targetVX, double targetVY, double currentVX, double currentVY,
            int thrust, int thrustConfig, double configMax,
            double currentThrustPn, double displayedThrustPn,
            double airflowMs, int obstruction,
            int fuelAmountMb, int fuelCapacityMb,
            int channel) {
        super(Component.empty());
        this.keyboardPos       = keyboardPos;
        this.peripheralType    = peripheralType;
        this.targetVectorX     = targetVX;
        this.targetVectorY     = targetVY;
        this.currentVectorX    = currentVX;
        this.currentVectorY    = currentVY;
        this.thrust            = thrust;
        this.thrustConfig      = thrustConfig;
        this.configMax         = configMax;
        this.currentThrustPn   = currentThrustPn;
        this.displayedThrustPn = displayedThrustPn;
        this.airflowMs         = airflowMs;
        this.obstruction       = obstruction;
        this.fuelAmountMb      = fuelAmountMb;
        this.fuelCapacityMb    = fuelCapacityMb;
        this.currentChannel    = channel;
    }

    public BlockPos getKeyboardPos()  { return keyboardPos; }
    public int getCurrentChannel()    { return currentChannel; }

    // ── updateState — only updates telemetry, never slider positions ──────────
    public void updateState(String peripheralType,
            double targetVX, double targetVY, double currentVX, double currentVY,
            int thrust, int thrustConfig, double configMax,
            double currentThrustPn, double displayedThrustPn,
            double airflowMs, int obstruction,
            int fuelAmountMb, int fuelCapacityMb,
            int channel) {
        this.peripheralType    = peripheralType;
        this.targetVectorX     = targetVX;
        this.targetVectorY     = targetVY;
        this.currentVectorX    = currentVX;
        this.currentVectorY    = currentVY;
        // Sliders intentionally NOT synced — peripheral getter lags behind and would flicker.
        this.thrust            = thrust;
        this.thrustConfig      = thrustConfig;
        this.configMax         = configMax;
        this.currentThrustPn   = currentThrustPn;
        this.displayedThrustPn = displayedThrustPn;
        this.airflowMs         = airflowMs;
        this.obstruction       = obstruction;
        this.fuelAmountMb      = fuelAmountMb;
        this.fuelCapacityMb    = fuelCapacityMb;
        this.currentChannel    = channel;
        if (grid != null) grid.setTarget(targetVX, targetVY);
    }

    // ── init ──────────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        boolean isVector   = peripheralType != null && peripheralType.contains("vector");
        boolean isCreative = peripheralType != null && peripheralType.contains("creative");

        // Panel width: vector is wider to fit the direction grid nicely
        panelW = isVector ? 268 : 248;

        // Determine content layout
        int sliderCount  = isCreative ? 2 : 1;
        int slidersWidth = sliderCount * SLIDER_W + (sliderCount - 1) * PAD / 2;

        readoutW = panelW - PAD * 2 - slidersWidth - PAD / 2;
        // READOUT_H shared for all types; grid goes below for vector

        int contentH = READOUT_H + LABEL_H + (isVector ? PAD + INST_OUTER : 0);
        panelH = PAD + TITLE_H + PAD + contentH + PAD + BTN_H + PAD;

        panelX = (width  - panelW) / 2;
        panelY = (height - panelH) / 2;

        int contentY = panelY + PAD + TITLE_H + PAD;

        readoutX = panelX + PAD;
        readoutY = contentY;

        // Throttle slider (always present)
        int sliderY = contentY;
        int sliderX = readoutX + readoutW + PAD / 2;
        String thrustMethod = isVector ? "setThrust" : "setPower";
        thrustSlider = new ThrottleSlider(sliderX, sliderY, SLIDER_W, READOUT_H,
                "PWR", 0, 15, thrust,
                val -> ModPackets.sendSetThrusterValue(keyboardPos, thrustMethod, val));
        addRenderableWidget(thrustSlider);

        // Config slider (creative thrusters)
        // creative_vector_thruster uses setThrustOutput(pN); creative_thruster uses setThrustConfig(%).
        if (isCreative) {
            boolean isCreativeVector = "creative_vector_thruster".equals(peripheralType);
            int cfgSliderX = sliderX + SLIDER_W + PAD / 2;
            configSlider = new ThrottleSlider(cfgSliderX, sliderY, SLIDER_W, READOUT_H,
                    "CFG", 0, 100, thrustConfig,
                    val -> {
                        if (isCreativeVector) {
                            ModPackets.sendSetThrusterValue(keyboardPos, "setThrustOutput",
                                    val / 100.0 * configMax);
                        } else {
                            ModPackets.sendSetThrusterValue(keyboardPos, "setThrustConfig", val);
                        }
                    });
            addRenderableWidget(configSlider);
        }

        // Direction grid (vector thrusters)
        if (isVector) {
            int gridY = contentY + READOUT_H + LABEL_H + PAD;
            int gridX = panelX + (panelW - INST_OUTER) / 2;
            grid = new InstrumentGridWidget(gridX, gridY, targetVectorX, targetVectorY,
                    (vx, vy) -> ModPackets.sendSetThrusterVector(keyboardPos, vx, vy));
            addRenderableWidget(grid);
        }

        // Bottom row: [Channel N] [Close]
        int btnRowY   = panelY + panelH - PAD - BTN_H;
        int btnRowW   = panelW - PAD * 2;
        int channelBW = btnRowW / 2 - 2;
        int closeBW   = btnRowW - channelBW - 4;
        addRenderableWidget(Button.builder(
                Component.literal("Channel " + currentChannel),
                b -> ModPackets.sendCycleChannelAndReopen(keyboardPos, KeyboardMode.THRUSTER_CONTROL))
                .pos(panelX + PAD, btnRowY)
                .size(channelBW, BTN_H)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .pos(panelX + PAD + channelBW + 4, btnRowY)
                .size(closeBW, BTN_H)
                .build());
    }

    // ── render ────────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        renderPanelBackground(g);
        renderTitleBar(g);
        renderPhosphorDisplay(g);
        renderSliderLabels(g);
        for (var w : renderables) w.render(g, mx, my, pt);
    }

    private void renderPanelBackground(GuiGraphics g) {
        // Tile smooth_stone block texture
        TextureAtlasSprite stone = Minecraft.getInstance()
                .getModelManager()
                .getAtlas(TextureAtlas.LOCATION_BLOCKS)
                .getSprite(ResourceLocation.withDefaultNamespace("block/smooth_stone"));
        g.enableScissor(panelX, panelY, panelX + panelW, panelY + panelH);
        for (int tx = panelX; tx < panelX + panelW; tx += 16) {
            for (int ty = panelY; ty < panelY + panelH; ty += 16) {
                g.blit(tx, ty, 0, 16, 16, stone);
            }
        }
        g.disableScissor();
        // Darken for contrast
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xAA000000);
        // Metallic bevel: highlight top/left, shadow bottom/right
        g.fill(panelX,             panelY,             panelX + panelW, panelY + 2,            0xFF888888);
        g.fill(panelX,             panelY,             panelX + 2,      panelY + panelH,        0xFF888888);
        g.fill(panelX,             panelY + panelH - 2, panelX + panelW, panelY + panelH,       0xFF1A1A1A);
        g.fill(panelX + panelW - 2, panelY,            panelX + panelW, panelY + panelH,        0xFF1A1A1A);
    }

    private void renderTitleBar(GuiGraphics g) {
        // Slightly lighter strip at top
        g.fill(panelX + 2, panelY + 2, panelX + panelW - 2, panelY + 2 + TITLE_H + PAD / 2, 0x44FFFFFF);
        String title = friendlyTitle();
        g.drawCenteredString(font, "§e" + title, panelX + panelW / 2, panelY + PAD - 1, 0xFFFFFF);
    }

    private String friendlyTitle() {
        if (peripheralType == null) return "THRUSTER CONTROL";
        return switch (peripheralType) {
            case "thruster"                 -> "THRUSTER CONTROL";
            case "ion_thruster"             -> "ION THRUSTER";
            case "vector_thruster"          -> "VECTOR THRUSTER";
            case "liquid_vector_thruster"   -> "LIQUID VECTOR THRUSTER";
            case "creative_thruster"        -> "CREATIVE THRUSTER";
            case "creative_vector_thruster" -> "VECTOR THRUSTER (CREATIVE)";
            case "propulsion_thruster"      -> "THRUSTER CONTROL"; // legacy
            default                         -> peripheralType.replace('_', ' ').toUpperCase();
        };
    }

    private void renderPhosphorDisplay(GuiGraphics g) {
        int x = readoutX, y = readoutY, w = readoutW, h = READOUT_H;

        // Inset bezel (recessed panel effect)
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0A0A0A); // outer shadow
        g.fill(x,     y,     x + w,     y + h,     0xFF050508); // near-black screen
        // Subtle inner green tint
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x0A003300);

        boolean isVector   = peripheralType != null && peripheralType.contains("vector");
        boolean isCreative = peripheralType != null && peripheralType.contains("creative");

        int tx = x + 4, ty = y + 4;
        final int LINE = 11;
        final int GREEN = 0xFF33FF33;

        if (isVector) {
            drawPhosphorLine(g, tx, ty,      "THRUST : " + thrust); ty += LINE;
            drawPhosphorLine(g, tx, ty,      String.format("TGT VX : %+.2f", targetVectorX)); ty += LINE;
            drawPhosphorLine(g, tx, ty,      String.format("TGT VY : %+.2f", targetVectorY)); ty += LINE;
            drawPhosphorLine(g, tx, ty,      String.format("CUR VX : %+.2f", currentVectorX)); ty += LINE;
            drawPhosphorLine(g, tx, ty,      String.format("CUR VY : %+.2f", currentVectorY)); ty += LINE;
            drawPhosphorLine(g, tx, ty,      String.format("POWER  : %.3f pN", currentThrustPn / 1000.0));
        } else if (isCreative) {
            drawPhosphorLine(g, tx, ty,      "POWER  : " + thrust); ty += LINE;
            drawPhosphorLine(g, tx, ty,      "CONFIG : " + thrustConfig + "%"); ty += LINE;
            drawPhosphorLine(g, tx, ty,      String.format("THRUST : %.3f pN", displayedThrustPn / 1000.0)); ty += LINE;
            drawPhosphorLine(g, tx, ty,      String.format("AIRFLO : %.1f m/s", airflowMs)); ty += LINE;
            drawPhosphorLine(g, tx, ty,      "OBSTR  : " + obstruction);
        } else {
            drawPhosphorLine(g, tx, ty,      "POWER  : " + thrust); ty += LINE;
            drawPhosphorLine(g, tx, ty,      String.format("THRUST : %.3f pN", currentThrustPn / 1000.0)); ty += LINE;
            drawPhosphorLine(g, tx, ty,      String.format("AIRFLO : %.1f m/s", airflowMs)); ty += LINE;
            if (fuelCapacityMb > 0) {
                drawPhosphorLine(g, tx, ty,  String.format("FUEL   : %d/%d mB", fuelAmountMb, fuelCapacityMb));
                ty += LINE;
            }
            drawPhosphorLine(g, tx, ty,      "OBSTR  : " + obstruction);
        }
    }

    private void drawPhosphorLine(GuiGraphics g, int x, int y, String text) {
        // Dim green shadow for CRT glow
        g.drawString(font, text, x + 1, y + 1, 0x4400AA00, false);
        g.drawString(font, text, x,     y,     0xFF33FF33,  false);
    }

    private void renderSliderLabels(GuiGraphics g) {
        // Small label below each slider
        if (thrustSlider != null) {
            int lx = thrustSlider.getX() + thrustSlider.getWidth() / 2;
            int ly = readoutY + READOUT_H + 2;
            g.drawCenteredString(font, "§7PWR", lx, ly, 0xAAAAAA);
        }
        if (configSlider != null) {
            int lx = configSlider.getX() + configSlider.getWidth() / 2;
            int ly = readoutY + READOUT_H + 2;
            g.drawCenteredString(font, "§7CFG", lx, ly, 0xAAAAAA);
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }

    // ─────────────────────────────────────────────────────────────────────────
    // Vertical Throttle Slider
    // ─────────────────────────────────────────────────────────────────────────
    private static class ThrottleSlider extends AbstractWidget {

        private static final int KNOB_H = 10;

        private final String       label;
        private final int          minVal, maxVal;
        private int                currentVal;
        private final java.util.function.IntConsumer onChange;
        private boolean            dragging = false;

        ThrottleSlider(int x, int y, int w, int h, String label,
                       int minVal, int maxVal, int initVal,
                       java.util.function.IntConsumer onChange) {
            super(x, y, w, h, Component.empty());
            this.label      = label;
            this.minVal     = minVal;
            this.maxVal     = maxVal;
            this.currentVal = Mth.clamp(initVal, minVal, maxVal);
            this.onChange   = onChange;
        }

        int getValue() { return currentVal; }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();

            // Outer inset housing
            g.fill(x, y, x + w, y + h, 0xFF1C1C1C);
            // Shadow edges (recessed look)
            g.fill(x, y,         x + w, y + 1,     0xFF0A0A0A);
            g.fill(x, y,         x + 1, y + h,     0xFF0A0A0A);
            g.fill(x, y + h - 1, x + w, y + h,     0xFF505050);
            g.fill(x + w - 1, y, x + w, y + h,     0xFF505050);

            // Track groove (centered, 4px wide)
            int trackX = x + (w - 4) / 2;
            int trackTop    = y + KNOB_H / 2 + 2;
            int trackBottom = y + h - KNOB_H / 2 - 2;
            g.fill(trackX, trackTop, trackX + 4, trackBottom, 0xFF080808);
            g.fill(trackX, trackTop, trackX + 1, trackBottom, 0xFF1A1A1A); // left highlight

            // Green fill from bottom to knob (power level indicator)
            int knobY = computeKnobY(y, h);
            int fillTop = knobY + KNOB_H;
            if (fillTop < trackBottom) {
                g.fill(trackX + 1, fillTop, trackX + 3, trackBottom, 0xFF005522);
            }

            // Knob — raised fader
            int kx = x + 1;
            int ky = knobY;
            int kw = w - 2;
            // Main body
            g.fill(kx,      ky,          kx + kw,     ky + KNOB_H, 0xFF808080);
            // Top highlight
            g.fill(kx,      ky,          kx + kw,     ky + 1,      0xFFBBBBBB);
            // Bottom shadow
            g.fill(kx,      ky + KNOB_H - 1, kx + kw, ky + KNOB_H, 0xFF404040);
            // Right shadow
            g.fill(kx + kw - 1, ky,     kx + kw,     ky + KNOB_H, 0xFF404040);
            // Grip lines (3 horizontal lines through center)
            int mid = ky + KNOB_H / 2;
            for (int dl = -2; dl <= 2; dl += 2) {
                g.fill(kx + 2, mid + dl, kx + kw - 2, mid + dl + 1, 0xFF505050);
            }
        }

        private int computeKnobY(int y, int h) {
            int range = maxVal - minVal;
            float t   = range > 0 ? (float)(currentVal - minVal) / range : 0f;
            int travel = h - KNOB_H - 4; // total knob travel range
            return y + 2 + (int)((1f - t) * travel);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && isMouseOver(mx, my)) {
                dragging = true;
                updateFromMouse(my);
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (dragging && button == 0) { updateFromMouse(my); return true; }
            return false;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            if (dragging && button == 0) {
                dragging = false;
                updateFromMouse(my);
                onChange.accept(currentVal);
                return true;
            }
            return false;
        }

        private void updateFromMouse(double my) {
            int h      = getHeight();
            int travel = h - KNOB_H - 4;
            float t    = 1f - Mth.clamp((float)((my - getY() - 2) / travel), 0f, 1f);
            currentVal = Math.round(t * (maxVal - minVal)) + minVal;
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput out) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Direction Grid Widget (restyled JoystickWidget)
    // ─────────────────────────────────────────────────────────────────────────
    private static class InstrumentGridWidget extends AbstractWidget {

        private double valueX, valueY;
        private final java.util.function.BiConsumer<Double, Double> onRelease;
        private boolean dragging = false;

        // Inner active area: exclude bezel on all sides; bottom bezel is larger to fit coordinate label
        private static final int BEZEL_BOTTOM = 16; // extra bottom space for the readout label

        InstrumentGridWidget(int x, int y, double initX, double initY,
                             java.util.function.BiConsumer<Double, Double> onRelease) {
            super(x, y, INST_OUTER, INST_OUTER, Component.empty());
            this.valueX    = initX;
            this.valueY    = initY;
            this.onRelease = onRelease;
        }

        void setTarget(double x, double y) {
            if (!dragging) { this.valueX = x; this.valueY = y; }
        }

        private int innerX()  { return getX() + INST_BEZEL; }
        private int innerY()  { return getY() + INST_BEZEL; }
        private int innerW()  { return INST_OUTER - INST_BEZEL * 2; }
        private int innerH()  { return INST_OUTER - INST_BEZEL - BEZEL_BOTTOM; }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int ox = getX(), oy = getY(), ow = INST_OUTER, oh = INST_OUTER;

            // Outer housing: recessed inset metal bezel
            g.fill(ox, oy, ox + ow, oy + oh, 0xFF0D0D0D);
            g.fill(ox,           oy,           ox + ow,     oy + 2,      0xFF080808); // shadow top
            g.fill(ox,           oy,           ox + 2,      oy + oh,     0xFF080808); // shadow left
            g.fill(ox,           oy + oh - 2,  ox + ow,     oy + oh,     0xFF505050); // highlight bottom
            g.fill(ox + ow - 2,  oy,           ox + ow,     oy + oh,     0xFF505050); // highlight right

            // Inner instrument face (dark blue-black, avionics glass feel)
            int ix = innerX(), iy = innerY(), iw = innerW(), ih = innerH();
            g.fill(ix, iy, ix + iw, iy + ih, 0xFF070710);

            // Axis crosshair lines
            int cx = ix + iw / 2, cy = iy + ih / 2;
            g.fill(cx, iy, cx + 1, iy + ih, 0xFF1A1A3A);
            g.fill(ix, cy, ix + iw, cy + 1,  0xFF1A1A3A);

            // ±0.5 tick lines (subtle)
            g.fill(cx - iw / 4,     iy, cx - iw / 4 + 1,     iy + ih, 0xFF101020);
            g.fill(cx + iw / 4,     iy, cx + iw / 4 + 1,     iy + ih, 0xFF101020);
            g.fill(ix, cy - ih / 4, ix + iw, cy - ih / 4 + 1,          0xFF101020);
            g.fill(ix, cy + ih / 4, ix + iw, cy + ih / 4 + 1,          0xFF101020);

            // Moving dot with green glow
            int dotX = ix + Mth.clamp((int)((valueX * 0.5 + 0.5) * (iw - 1)), 0, iw - 1);
            int dotY = iy + Mth.clamp((int)((-valueY * 0.5 + 0.5) * (ih - 1)), 0, ih - 1);
            g.fill(dotX - 4, dotY - 4, dotX + 5, dotY + 5, 0x2200FF44); // glow outer
            g.fill(dotX - 2, dotY - 2, dotX + 3, dotY + 3, 0x5500FF44); // glow inner
            g.fill(dotX - 1, dotY - 1, dotX + 2, dotY + 2, 0xFF00FF44); // solid core

            // Coordinate readout in bottom bezel area
            var font = Minecraft.getInstance().font;
            g.drawCenteredString(font,
                    String.format("§a%+.2f §8/ §a%+.2f", valueX, valueY),
                    ox + ow / 2, iy + ih + (BEZEL_BOTTOM - 8) / 2 + 1, 0xAAAAAA);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && isInsideFace(mx, my)) {
                dragging = true;
                updateFromMouse(mx, my);
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (dragging && button == 0) { updateFromMouse(mx, my); return true; }
            return false;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            if (dragging && button == 0) {
                dragging = false;
                updateFromMouse(mx, my);
                onRelease.accept(valueX, valueY);
                return true;
            }
            return false;
        }

        private boolean isInsideFace(double mx, double my) {
            return mx >= innerX() && mx < innerX() + innerW()
                && my >= innerY() && my < innerY() + innerH();
        }

        private void updateFromMouse(double mx, double my) {
            int ix = innerX(), iy = innerY(), iw = innerW(), ih = innerH();
            valueX = Mth.clamp((mx - ix) / iw * 2.0 - 1.0, -1.0, 1.0);
            valueY = Mth.clamp(-((my - iy) / ih * 2.0 - 1.0), -1.0, 1.0);
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput out) {}
    }
}
