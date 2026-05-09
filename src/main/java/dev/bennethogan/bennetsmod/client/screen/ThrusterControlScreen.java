package dev.bennethogan.bennetsmod.client.screen;

import dev.bennethogan.bennetsmod.network.ModPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public class ThrusterControlScreen extends Screen {

    private static final int PAD    = 8;
    private static final int PANEL_W = 264;
    private static final int BTN_H  = 18;
    private static final int LINE_H = 11;

    final BlockPos keyboardPos;
    private String peripheralType;

    private double targetVectorX, targetVectorY;
    private double currentVectorX, currentVectorY;
    private int    thrust;
    private int    thrustConfig;
    private double currentThrustPn;
    private double displayedThrustPn;
    private double airflowMs;
    private int    obstruction;
    private int    fuelAmountMb;
    private int    fuelCapacityMb;

    private int panelX, panelY, panelH;
    private int statsY; // top of the text readout section

    private JoystickWidget joystick;
    private SliderWidget   thrustSlider;
    private SliderWidget   configSlider;

    public ThrusterControlScreen(BlockPos keyboardPos, String peripheralType,
            double targetVX, double targetVY, double currentVX, double currentVY,
            int thrust, int thrustConfig,
            double currentThrustPn, double displayedThrustPn,
            double airflowMs, int obstruction,
            int fuelAmountMb, int fuelCapacityMb) {
        super(Component.empty());
        this.keyboardPos      = keyboardPos;
        this.peripheralType   = peripheralType;
        this.targetVectorX    = targetVX;
        this.targetVectorY    = targetVY;
        this.currentVectorX   = currentVX;
        this.currentVectorY   = currentVY;
        this.thrust           = thrust;
        this.thrustConfig     = thrustConfig;
        this.currentThrustPn  = currentThrustPn;
        this.displayedThrustPn = displayedThrustPn;
        this.airflowMs        = airflowMs;
        this.obstruction      = obstruction;
        this.fuelAmountMb     = fuelAmountMb;
        this.fuelCapacityMb   = fuelCapacityMb;
    }

    public BlockPos getKeyboardPos() { return keyboardPos; }

    /** Called when an updated OpenThrusterControlPacket arrives while the screen is open. */
    public void updateState(String peripheralType,
            double targetVX, double targetVY, double currentVX, double currentVY,
            int thrust, int thrustConfig,
            double currentThrustPn, double displayedThrustPn,
            double airflowMs, int obstruction,
            int fuelAmountMb, int fuelCapacityMb) {
        this.peripheralType    = peripheralType;
        this.targetVectorX     = targetVX;
        this.targetVectorY     = targetVY;
        this.currentVectorX    = currentVX;
        this.currentVectorY    = currentVY;
        this.thrust            = thrust;
        this.thrustConfig      = thrustConfig;
        this.currentThrustPn   = currentThrustPn;
        this.displayedThrustPn = displayedThrustPn;
        this.airflowMs         = airflowMs;
        this.obstruction       = obstruction;
        this.fuelAmountMb      = fuelAmountMb;
        this.fuelCapacityMb    = fuelCapacityMb;
        if (joystick    != null) joystick.setValues(targetVX, targetVY);
        if (thrustSlider != null) thrustSlider.setValue(thrust);
        if (configSlider != null) configSlider.setValue(thrustConfig);
    }

    @Override
    protected void init() {
        boolean isVector   = "vector_thruster".equals(peripheralType);
        boolean isCreative = "creative_thruster".equals(peripheralType);

        int padSize    = isVector ? 120 : 0;
        int sliderRows = isCreative ? 2 : 1;
        int statLines  = isVector ? 2 : (isCreative ? 4 : 4);
        boolean showFuel = !isVector && fuelCapacityMb > 0;
        if (!isVector && !isCreative && fuelCapacityMb == 0) statLines = 3; // no fuel line

        panelH = PAD                          // top padding
               + 12 + PAD                    // title + gap
               + (padSize > 0 ? padSize + PAD : 0)   // joystick + gap
               + sliderRows * (BTN_H + PAD)  // slider rows
               + statLines * LINE_H + PAD    // stat readouts
               + BTN_H + PAD;               // close button

        panelX = (width  - PANEL_W) / 2;
        panelY = (height - panelH)  / 2;

        int y = panelY + PAD + 12 + PAD; // start below title

        if (isVector) {
            int padX = panelX + (PANEL_W - padSize) / 2;
            joystick = new JoystickWidget(padX, y, padSize, targetVectorX, targetVectorY,
                    (vx, vy) -> {
                        ModPackets.sendSetThrusterValue(keyboardPos, "setVectorX", vx);
                        ModPackets.sendSetThrusterValue(keyboardPos, "setVectorY", vy);
                    });
            addRenderableWidget(joystick);
            y += padSize + PAD;
        }

        // Thrust / power slider (0-15)
        String sliderLabel  = isVector ? "Thrust" : "Power";
        String sliderMethod = isVector ? "setThrust" : "setPower";
        thrustSlider = new SliderWidget(panelX + PAD, y, PANEL_W - PAD * 2, BTN_H,
                sliderLabel, 0, 15, thrust,
                val -> ModPackets.sendSetThrusterValue(keyboardPos, sliderMethod, val));
        addRenderableWidget(thrustSlider);
        y += BTN_H + PAD;

        if (isCreative) {
            configSlider = new SliderWidget(panelX + PAD, y, PANEL_W - PAD * 2, BTN_H,
                    "Config %", 0, 100, thrustConfig,
                    val -> ModPackets.sendSetThrusterValue(keyboardPos, "setThrustConfig", val));
            addRenderableWidget(configSlider);
            y += BTN_H + PAD;
        }

        statsY = y;

        // Close button at bottom
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .pos(panelX + PAD, panelY + panelH - PAD - BTN_H)
                .size(PANEL_W - PAD * 2, BTN_H)
                .build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);

        // Panel background
        g.fill(panelX,              panelY,              panelX + PANEL_W,  panelY + panelH,  0xFF111111);
        g.fill(panelX,              panelY,              panelX + PANEL_W,  panelY + 1,       0xFF666666);
        g.fill(panelX,              panelY + panelH - 1, panelX + PANEL_W,  panelY + panelH,  0xFF666666);
        g.fill(panelX,              panelY,              panelX + 1,        panelY + panelH,  0xFF666666);
        g.fill(panelX + PANEL_W - 1, panelY,             panelX + PANEL_W,  panelY + panelH,  0xFF666666);

        // Title
        g.drawCenteredString(font, "§b" + peripheralType, panelX + PANEL_W / 2, panelY + PAD, 0xFFFFFF);

        // Stats readouts
        int y = statsY;
        boolean isVector   = "vector_thruster".equals(peripheralType);
        boolean isCreative = "creative_thruster".equals(peripheralType);

        if (isVector) {
            g.drawString(font,
                    String.format("§7Target:  §fX=%.2f  Y=%.2f", targetVectorX, targetVectorY),
                    panelX + PAD, y, 0xFFFFFF, false);
            y += LINE_H;
            g.drawString(font,
                    String.format("§7Current: §fX=%.2f  Y=%.2f", currentVectorX, currentVectorY),
                    panelX + PAD, y, 0xFFFFFF, false);
        } else if (isCreative) {
            g.drawString(font,
                    String.format("§7Target:  §f%.1f pN", displayedThrustPn),
                    panelX + PAD, y, 0xFFFFFF, false);
            y += LINE_H;
            g.drawString(font,
                    String.format("§7Current: §f%.1f pN", currentThrustPn),
                    panelX + PAD, y, 0xFFFFFF, false);
            y += LINE_H;
            g.drawString(font,
                    String.format("§7Airflow: §f%.1f m/s", airflowMs),
                    panelX + PAD, y, 0xFFFFFF, false);
            y += LINE_H;
            g.drawString(font, "§7Obstruction: §f" + obstruction, panelX + PAD, y, 0xFFFFFF, false);
        } else {
            // propulsion_thruster
            g.drawString(font,
                    String.format("§7Thrust:  §f%.1f pN", currentThrustPn),
                    panelX + PAD, y, 0xFFFFFF, false);
            y += LINE_H;
            g.drawString(font,
                    String.format("§7Airflow: §f%.1f m/s", airflowMs),
                    panelX + PAD, y, 0xFFFFFF, false);
            y += LINE_H;
            if (fuelCapacityMb > 0) {
                g.drawString(font,
                        String.format("§7Fuel:    §f%d / %d mB", fuelAmountMb, fuelCapacityMb),
                        panelX + PAD, y, 0xFFFFFF, false);
                y += LINE_H;
            }
            g.drawString(font, "§7Obstruction: §f" + obstruction, panelX + PAD, y, 0xFFFFFF, false);
        }

        for (var renderable : this.renderables) renderable.render(g, mx, my, pt);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }

    // ─── Joystick Widget ─────────────────────────────────────────────────────

    private static class JoystickWidget extends AbstractWidget {

        private double valueX, valueY;
        private final java.util.function.BiConsumer<Double, Double> onRelease;
        private boolean dragging = false;

        JoystickWidget(int x, int y, int size, double initX, double initY,
                       java.util.function.BiConsumer<Double, Double> onRelease) {
            super(x, y, size, size, Component.empty());
            this.valueX    = initX;
            this.valueY    = initY;
            this.onRelease = onRelease;
        }

        void setValues(double x, double y) {
            if (!dragging) { this.valueX = x; this.valueY = y; }
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();

            // Background
            g.fill(x, y, x + w, y + h, 0xFF0D0D1A);

            // Axis lines
            g.fill(x + w / 2, y,        x + w / 2 + 1, y + h,     0xFF252550);
            g.fill(x,         y + h / 2, x + w,         y + h / 2 + 1, 0xFF252550);

            // Subtle quadrant ticks at ±0.5
            int q = w / 4;
            g.fill(x + q,     y + h / 2 - 3, x + q + 1,     y + h / 2 + 4, 0xFF1E1E40);
            g.fill(x + 3 * q, y + h / 2 - 3, x + 3 * q + 1, y + h / 2 + 4, 0xFF1E1E40);
            g.fill(x + w / 2 - 3, y + q,     x + w / 2 + 4, y + q + 1,     0xFF1E1E40);
            g.fill(x + w / 2 - 3, y + 3 * q, x + w / 2 + 4, y + 3 * q + 1, 0xFF1E1E40);

            // Border
            g.fill(x,         y,         x + w, y + 1,     0xFF4A4A8A);
            g.fill(x,         y + h - 1, x + w, y + h,     0xFF4A4A8A);
            g.fill(x,         y,         x + 1, y + h,     0xFF4A4A8A);
            g.fill(x + w - 1, y,         x + w, y + h,     0xFF4A4A8A);

            // Dot — map -1..1 to pixel space; Y axis is inverted (screen-down = -Y)
            int dotX = x + Mth.clamp((int) ((valueX * 0.5 + 0.5) * (w - 1)), 0, w - 1);
            int dotY = y + Mth.clamp((int) ((-valueY * 0.5 + 0.5) * (h - 1)), 0, h - 1);
            g.fill(dotX - 3, dotY - 3, dotX + 4, dotY + 4, 0xFF00FFAA);

            // Label
            var font = Minecraft.getInstance().font;
            g.drawCenteredString(font,
                    String.format("§7(%.2f, %.2f)", valueX, valueY),
                    x + w / 2, y + h + 2, 0xAAAAAA);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && isMouseOver(mx, my)) {
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

        private void updateFromMouse(double mx, double my) {
            valueX = Mth.clamp((mx - getX()) / getWidth()  * 2.0 - 1.0, -1.0, 1.0);
            valueY = Mth.clamp(-((my - getY()) / getHeight() * 2.0 - 1.0), -1.0, 1.0);
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput out) {}
    }

    // ─── Slider Widget ────────────────────────────────────────────────────────

    private static class SliderWidget extends AbstractWidget {

        private final String label;
        private final int    minVal, maxVal;
        private int          currentVal;
        private final java.util.function.IntConsumer onRelease;
        private boolean dragging = false;

        SliderWidget(int x, int y, int w, int h, String label,
                     int minVal, int maxVal, int initVal,
                     java.util.function.IntConsumer onRelease) {
            super(x, y, w, h, Component.empty());
            this.label      = label;
            this.minVal     = minVal;
            this.maxVal     = maxVal;
            this.currentVal = Mth.clamp(initVal, minVal, maxVal);
            this.onRelease  = onRelease;
        }

        void setValue(int val) {
            if (!dragging) currentVal = Mth.clamp(val, minVal, maxVal);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();

            // Track
            g.fill(x, y, x + w, y + h, 0xFF2A2A2A);
            g.fill(x, y,         x + w, y + 1,     0xFF555555);
            g.fill(x, y + h - 1, x + w, y + h,     0xFF555555);
            g.fill(x, y,         x + 1, y + h,     0xFF555555);
            g.fill(x + w - 1, y, x + w, y + h,     0xFF555555);

            // Fill bar
            int range = maxVal - minVal;
            float t = range > 0 ? (float) (currentVal - minVal) / range : 0f;
            int fillW = (int) (t * (w - 2));
            if (fillW > 0)
                g.fill(x + 1, y + 1, x + 1 + fillW, y + h - 1, 0xFF2E6EA0);

            // Label
            var font = Minecraft.getInstance().font;
            g.drawCenteredString(font, label + ": " + currentVal, x + w / 2, y + (h - 8) / 2, 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && isMouseOver(mx, my)) {
                dragging = true;
                updateFromMouse(mx);
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (dragging && button == 0) { updateFromMouse(mx); return true; }
            return false;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            if (dragging && button == 0) {
                dragging = false;
                updateFromMouse(mx);
                onRelease.accept(currentVal);
                return true;
            }
            return false;
        }

        private void updateFromMouse(double mx) {
            float t = Mth.clamp((float) ((mx - getX()) / getWidth()), 0f, 1f);
            currentVal = Math.round(t * (maxVal - minVal)) + minVal;
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput out) {}
    }
}
