package dev.bennethogan.universalkeyboard.client.screen;

import dev.bennethogan.universalkeyboard.client.gamepad.GamepadLiveDriver;
import dev.bennethogan.universalkeyboard.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.glfw.GLFW;

import java.util.List;


public class GamepadCalibrationScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 220;
    private static final int PAD     = 10;
    private static final int BTN_H   = 18;
    private static final int PAD_R   = 46; // gizmo radius

    private final Screen parent;
    private int panelX, panelY;

    private final double[] recorded = {1.0, 1.0, 1.0, 1.0};
    private boolean recording = false;
    private IconButton runStopBtn;

    public GamepadCalibrationScreen(Screen parent) {
        super(Component.translatable("gui.universalkeyboard.screen.calibration.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelX = (width  - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;

        try {
            List<? extends Double> cal = ModConfig.CLIENT.stickCalibration.get();
            for (int i = 0; i < 4 && i < cal.size(); i++) recorded[i] = cal.get(i);
        } catch (Exception ignored) {}

        addRenderableWidget(DarkButton.make(Component.literal("?"),
                Component.translatable("gui.universalkeyboard.tooltip.wiki"),
                b -> Minecraft.getInstance().setScreen(new WikiScreen(this)),
                panelX + PANEL_W - PAD - BTN_H, panelY + 4, BTN_H, BTN_H));

        int by = panelY + PANEL_H - PAD - BTN_H;

        addRenderableWidget(IconButton.make(ModIcons.PREV_PAGE,
                Component.translatable("gui.universalkeyboard.tooltip.back"),
                b -> onClose(),
                panelX + PAD, by, BTN_H));

        runStopBtn = IconButton.make(
                recording ? ModIcons.STOP : ModIcons.PLAY,
                Component.translatable(recording
                        ? "gui.universalkeyboard.tooltip.stop"
                        : "gui.universalkeyboard.tooltip.play"),
                b -> toggleRecording(),
                panelX + PANEL_W / 2 - 24, by, 48, BTN_H,
                recording ? -1 : 0xFF1E3A1E, 0);
        addRenderableWidget(runStopBtn);
    }

    private void toggleRecording() {
        recording = !recording;
        if (recording) {
            recorded[0] = recorded[1] = recorded[2] = recorded[3] = 0.0;
        } else {
            doSave(); // auto-save on stop
        }
        updateRunStopBtn();
    }

    private void doSave() {
        Double[] out = new Double[4];
        for (int i = 0; i < 4; i++) {
            out[i] = Math.max(0.4, Math.min(1.0, recorded[i] <= 0.0 ? 1.0 : recorded[i]));
        }
        try {
            ModConfig.CLIENT.stickCalibration.set(List.of(out));
            ModConfig.CLIENT.stickCalibration.save();
        } catch (Exception ignored) {}
    }

    private void updateRunStopBtn() {
        runStopBtn.setIcon(recording ? ModIcons.STOP : ModIcons.PLAY);
        runStopBtn.setAccentBg(recording ? -1 : 0xFF1E3A1E);
        runStopBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable(recording
                        ? "gui.universalkeyboard.tooltip.stop"
                        : "gui.universalkeyboard.tooltip.play")));
    }

    @Override
    public void tick() {
        super.tick();
        GamepadLiveDriver.pollOnce();
        if (!recording) return;
        for (int i = 0; i < 4; i++)
            recorded[i] = Math.max(recorded[i], Math.abs(GamepadLiveDriver.rawAxis(i)));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFF111111);
        drawBorder(g, panelX, panelY, PANEL_W, PANEL_H, 0xFF666666);

        g.drawCenteredString(font, I18n.get("gui.universalkeyboard.screen.calibration.title"),
                panelX + PANEL_W / 2, panelY + PAD, 0xFFFFFF);

        boolean connected = GamepadLiveDriver.hasGamepad();
        String hint = !connected
                ? "§c" + I18n.get("gui.universalkeyboard.label.cal_no_pad")
                : recording
                    ? "§a" + I18n.get("gui.universalkeyboard.label.cal_rotate")
                    : "§7" + I18n.get("gui.universalkeyboard.label.cal_idle");
        g.drawCenteredString(font, hint, panelX + PANEL_W / 2, panelY + PAD + 12, 0xFFFFFF);

        int gizmoY = panelY + 91;
        int lcx = panelX + PANEL_W / 4;
        int rcx = panelX + PANEL_W * 3 / 4;
        drawGizmo(g, lcx, gizmoY, "LS", 0, 1);
        drawGizmo(g, rcx, gizmoY, "RS", 2, 3);

        for (var r : this.renderables) r.render(g, mx, my, pt);
    }

    private void drawGizmo(GuiGraphics g, int cx, int cy, String label, int axX, int axY) {
        g.drawCenteredString(font, label, cx, cy - PAD_R - 12, 0xAAAAAA);

        g.fill(cx - PAD_R, cy, cx + PAD_R, cy + 1, 0xFF223344);
        g.fill(cx, cy - PAD_R, cx + 1, cy + PAD_R, 0xFF223344);
        drawCircle(g, cx, cy, PAD_R, 0xFF334455);

        double extent = (clampRec(recorded[axX]) + clampRec(recorded[axY])) / 2.0;
        drawCircle(g, cx, cy, (int) (PAD_R * extent), 0xFFBB8822);

        float rx = GamepadLiveDriver.rawAxis(axX);
        float ry = GamepadLiveDriver.rawAxis(axY);
        int dotX = (int) (cx + rx * PAD_R);
        int dotY = (int) (cy + ry * PAD_R);
        g.fill(dotX - 2, dotY - 2, dotX + 3, dotY + 3, 0xFF00FFAA);

        String xs = String.format("X %.2f", clampRec(recorded[axX]));
        String ys = String.format("Y %.2f", clampRec(recorded[axY]));
        g.drawCenteredString(font, "§e" + xs, cx, cy + PAD_R + 6,  0xFFFFFF);
        g.drawCenteredString(font, "§e" + ys, cx, cy + PAD_R + 16, 0xFFFFFF);
    }

    private static double clampRec(double v) {
        return v <= 0.0 ? 0.0 : Math.min(1.0, v);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_TAB) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static void drawCircle(GuiGraphics g, int cx, int cy, int r, int c) {
        if (r <= 0) return;
        int steps = Math.max(12, r * 6);
        for (int i = 0; i < steps; i++) {
            double a = 2 * Math.PI * i / steps;
            int px = (int) (cx + Math.cos(a) * r);
            int py = (int) (cy + Math.sin(a) * r);
            g.fill(px, py, px + 1, py + 1, c);
        }
    }
}
