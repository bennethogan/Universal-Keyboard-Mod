package dev.bennethogan.universalkeyboard.client.screen;

import dev.bennethogan.universalkeyboard.api.PeripheralControl;
import dev.bennethogan.universalkeyboard.api.PeripheralMenuConfig;
import dev.bennethogan.universalkeyboard.compat.KeyboardMode;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;


    // Config based peripheeral interface. Functionally it works the same as the old one, but redesigned with the
    // same style I was doing everywhere else
    // if I make a KubeJS compat mod, then I will add very detailed documentation here
public class PeripheralControlScreen extends Screen {

    private static final int PAD       = 10;
    private static final int TITLE_H   = 16;
    private static final int MAX_ROWS  = 7;   // visible rows before scrolling kicks in
    private static final int BASE_W    = 240;
    private static final int BASE_ROW  = 16;
    private static final int BASE_BTN  = 18;

    private final BlockPos keyboardPos;
    private final PeripheralMenuConfig config;
    private int currentChannel;

    // scaled metrics (computed in init from config scale)
    private int panelW, panelH, rowH, btnH;
    private int panelX, panelY;
    private int listTop, listBottom;
    private int scrollY = 0;

    private final boolean hasInput;
    private int   activeInput = -1; // index into controls of the selected INPUT row, or -1
    private EditBox inputBox;

    public PeripheralControlScreen(BlockPos keyboardPos, PeripheralMenuConfig config, int channel) {
        super(Component.empty());
        this.keyboardPos    = keyboardPos;
        this.config         = config;
        this.currentChannel = channel;
        this.hasInput = config.controls().stream().anyMatch(c -> c.kind() == PeripheralControl.Kind.INPUT);
    }

    private List<PeripheralControl> controls() { return config.controls(); }

    @Override
    protected void init() {
        float s = config.scaleClamped();
        panelW = Math.round(BASE_W   * s);
        rowH   = Math.round(BASE_ROW * s);
        btnH   = Math.round(BASE_BTN * s);

        int rows  = Math.max(1, Math.min(controls().size(), MAX_ROWS));
        int listH = rows * rowH;
        int footerBlock = hasInput ? (btnH + 8) : 0;

        panelH = PAD + TITLE_H + listH + 6 + footerBlock + btnH + PAD;
        panelH = Math.min(panelH, height - 16);
        panelX = (width  - panelW) / 2;
        panelY = (height - panelH) / 2;

        listTop    = panelY + PAD + TITLE_H;
        listBottom = listTop + listH;
        int footerY = listBottom + 6;
        int bottomY = footerY + footerBlock;

        // Wiki button on top right
        addRenderableWidget(DarkButton.make(Component.literal("?"),
                Component.translatable("gui.universalkeyboard.tooltip.wiki"),
                b -> Minecraft.getInstance().setScreen(new WikiScreen(this)),
                panelX + panelW - PAD - 16, panelY + 4, 16, 16));

        // Footer input row, only when at least one input control exists
        if (hasInput) {
            int editW = panelW - PAD * 2 - 44;
            inputBox = new EditBox(font, panelX + PAD, footerY, editW, btnH,
                    Component.literal(I18n.get("gui.universalkeyboard.label.value")));
            inputBox.setMaxLength(256);
            inputBox.setHint(Component.literal(I18n.get("gui.universalkeyboard.hint.select_control")));
            addRenderableWidget(inputBox);
            addRenderableWidget(DarkButton.make(Component.translatable("gui.universalkeyboard.btn.set"),
                    b -> submitInput(), panelX + PAD + editW + 4, footerY, 40, btnH));
        }

        // Bottom bar: [Channel N] [Refresh] [Close]
        int rowW   = panelW - PAD * 2;
        int thirdW = (rowW - 8) / 3;
        addRenderableWidget(DarkButton.make(
                Component.literal(I18n.get("gui.universalkeyboard.btn.channel", currentChannel)),
                b -> ModPackets.sendCycleChannelAndReopen(keyboardPos, KeyboardMode.CC_PERIPHERAL),
                panelX + PAD, bottomY, thirdW, btnH));
        addRenderableWidget(DarkButton.make(Component.translatable("gui.universalkeyboard.btn.refresh"),
                b -> requestRefresh(), panelX + PAD + thirdW + 4, bottomY, thirdW, btnH));
        addRenderableWidget(DarkButton.make(Component.translatable("gui.universalkeyboard.btn.close"),
                b -> onClose(), panelX + PAD + (thirdW + 4) * 2, bottomY, rowW - (thirdW + 4) * 2, btnH));
    }

    // render

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        int accent = config.accentOrDefault();

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF111111);
        drawBorder(g, panelX, panelY, panelW, panelH, accent);

        String title = config.title().isEmpty()
                ? I18n.get("gui.universalkeyboard.peripheral_control.untitled")
                : config.title();
        g.drawCenteredString(font, title, panelX + panelW / 2, panelY + PAD - 1, accent | 0xFF000000);

        scrollY = Math.max(0, Math.min(scrollY, maxScroll()));

        g.enableScissor(panelX + 1, listTop, panelX + panelW - 1, listBottom);
        if (controls().isEmpty()) {
            String msg = I18n.get("gui.universalkeyboard.peripheral_control.no_peripheral");
            g.drawCenteredString(font, "§7" + msg,
                    panelX + panelW / 2, listTop + (listBottom - listTop - 8) / 2, 0xFFAAAAAA);
        } else {
            int y = listTop - scrollY;
            for (int i = 0; i < controls().size(); i++) {
                if (y + rowH >= listTop && y <= listBottom) renderRow(g, i, y, mx, my);
                y += rowH;
            }
        }
        g.disableScissor();

        if (!controls().isEmpty() && maxScroll() > 0) {
            String arrows = (scrollY > 0 ? "▲ " : "  ") + (scrollY < maxScroll() ? "▼" : " ");
            g.drawString(font, arrows, panelX + panelW - PAD - 14, listBottom - 9, 0xFF666666, false);
        }

        for (var r : this.renderables) r.render(g, mx, my, pt);
    }

    private void renderRow(GuiGraphics g, int i, int y, int mx, int my) {
        PeripheralControl c = controls().get(i);
        int rowX = panelX + PAD;
        int rowW = panelW - PAD * 2;
        int textY = y + (rowH - 8) / 2;

        switch (c.kind()) {
            case READOUT -> g.drawString(font, "§7" + c.label() + ": §f" + c.value(),
                    rowX + 2, textY, 0xFFFFFFFF, false);
            case BUTTON  -> {
                boolean hov = inRow(mx, my, rowX, rowW, y);
                fillButton(g, rowX, y, rowW, hov, false);
                g.drawCenteredString(font, c.label(), rowX + rowW / 2, textY, 0xFFFFFFFF);
            }
            case INPUT   -> {
                boolean hov = inRow(mx, my, rowX, rowW, y);
                boolean sel = i == activeInput;
                fillButton(g, rowX, y, rowW, hov, sel);
                g.drawString(font, c.label(), rowX + 4, textY, 0xFFFFFFFF, false);
                String hint = "§8(" + c.argType() + ")";
                g.drawString(font, hint, rowX + rowW - 4 - font.width(hint), textY, 0xFF888888, false);
            }
        }
    }

    private boolean inRow(int mx, int my, int rowX, int rowW, int y) {
        return mx >= rowX && mx < rowX + rowW && my >= y && my < y + rowH
                && my >= listTop && my < listBottom;
    }

    private void fillButton(GuiGraphics g, int x, int y, int w, boolean hov, boolean sel) {
        int bg  = sel ? 0xFF24242E : (hov ? 0xFF2A2A3A : 0xFF1C1C1C);
        int brd = sel ? config.accentOrDefault() : 0xFF555555;
        g.fill(x, y + 1, x + w, y + rowH - 1, bg);
        drawBorder(g, x, y + 1, w, rowH - 2, brd);
    }

    //Input handling ----------

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && my >= listTop && my < listBottom
                && mx >= panelX + PAD && mx < panelX + panelW - PAD) {
            int idx = (int) ((my - listTop + scrollY) / rowH);
            if (idx >= 0 && idx < controls().size()) {
                PeripheralControl c = controls().get(idx);
                switch (c.kind()) {
                    case BUTTON -> ModPackets.sendCallPeripheralMethod(keyboardPos, c.method(), c.arg());
                    case INPUT  -> selectInput(idx);
                    case READOUT -> { /* not interactive */ }
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    private void selectInput(int idx) {
        activeInput = idx;
        if (inputBox != null) {
            PeripheralControl c = controls().get(idx);
            inputBox.setValue("");
            inputBox.setHint(Component.literal("§7" + c.label() + " (" + c.argType() + ")"));
            setFocused(inputBox);
            inputBox.setFocused(true);
        }
    }

    private void submitInput() {
        if (activeInput < 0 || activeInput >= controls().size()) return;
        PeripheralControl c = controls().get(activeInput);
        if (c.kind() != PeripheralControl.Kind.INPUT) return;
        String arg = inputBox != null ? inputBox.getValue().trim() : "";
        if (arg.isEmpty()) return;
        ModPackets.sendCallPeripheralMethod(keyboardPos, c.method(), arg);
    }

    private void requestRefresh() {
        ModPackets.sendCallPeripheralMethod(keyboardPos, "", "");
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (maxScroll() > 0 && my >= listTop && my < listBottom) {
            scrollY = Math.max(0, Math.min(maxScroll(), scrollY - (int) (dy * 12)));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (inputBox != null && inputBox.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            submitInput();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        boolean boxFocused = inputBox != null && inputBox.isFocused();
        if (!boxFocused && MenuNav.handleTabBack(this, keyCode, keyboardPos)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int maxScroll() {
        return Math.max(0, controls().size() * rowH - (listBottom - listTop));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }
}
