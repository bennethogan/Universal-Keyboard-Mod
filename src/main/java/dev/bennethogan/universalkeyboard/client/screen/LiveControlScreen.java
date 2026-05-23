package dev.bennethogan.universalkeyboard.client.screen;

import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.universalkeyboard.compat.SableCompat;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding.ActionType;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding.Mode;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlManager;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;

public class LiveControlScreen extends Screen {

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int MAX_SLOTS  = 40;
    private static final int ROWS       = 10; // rows per page column (10×2 = 20 per page, 2 pages)
    private static final int PAD        = 8;
    private static final int TITLE_H    = 10;
    private static final int ROW_H      = 15;
    private static final int ROW_GAP    = 2;
    private static final int BTN_H      = 16;
    private static final int PANEL_W    = 400;
    // per-column: [num:12][key:38][gap:2][type:46][gap:2][cfg:90] = 190px
    private static final int COL_W      = 190;
    private static final int COL_GAP    = 4;
    private static final int COL_OFFSET = COL_W + COL_GAP;
    private static final int NUM_W      = 12;
    private static final int KEY_W      = 38;
    private static final int TYPE_W     = 46;
    private static final int CFG_OFF    = NUM_W + KEY_W + 2 + TYPE_W + 2; // 100
    private static final int CFG_W      = COL_W - CFG_OFF;                // 90
    // vector overlay
    private static final int VEC_OV_W   = 110;
    private static final int VEC_OV_H   = 140;

    // ── State ─────────────────────────────────────────────────────────────────
    private final BlockPos                keyboardPos;
    private final int                     wirelessCount;
    private final boolean                 hasThrusters;
    private final boolean                 hasVectorThrusters;
    private final List<LiveControlBinding> bindings;
    private final int[] currentLocalRs;
    private final int[] currentWirelessPowers;
    private final int[] currentThrusterPowers;

    private int panelX, panelY, panelH;
    private int firstRowY;
    private int listeningSlot   = -1;
    private int vectorOverlaySlot = -1;
    private int page = 0; // 0 = slots 0–19, 1 = slots 20–39

    // Typewriter import state
    private net.minecraft.core.BlockPos twOfferPos   = null; // non-null when offer is pending
    private int                         twBindCount  = 0;
    private int                         twFreqCount  = 0;
    private String                      twMessage    = null; // error or status shown briefly
    private int                         twMessageTick = 0;

    // ── Constructor ───────────────────────────────────────────────────────────
    public LiveControlScreen(BlockPos keyboardPos,
                             List<LiveControlBinding> inBindings,
                             int wirelessCount,
                             boolean hasThrusters,
                             boolean hasVectorThrusters,
                             int[] currentLocalRs,
                             int[] currentWirelessPowers,
                             int[] currentThrusterPowers) {
        super(Component.empty());
        this.keyboardPos           = keyboardPos;
        this.wirelessCount         = wirelessCount;
        this.hasThrusters          = hasThrusters;
        this.hasVectorThrusters    = hasVectorThrusters;
        this.currentLocalRs        = currentLocalRs;
        this.currentWirelessPowers = currentWirelessPowers;
        this.currentThrusterPowers = currentThrusterPowers;
        this.bindings = new ArrayList<>(MAX_SLOTS);
        for (int i = 0; i < MAX_SLOTS; i++)
            this.bindings.add(i < inBindings.size() ? copyBinding(inBindings.get(i)) : new LiveControlBinding());
    }

    private static LiveControlBinding copyBinding(LiveControlBinding src) {
        LiveControlBinding b = new LiveControlBinding();
        b.keyCode        = src.keyCode;
        b.actionType     = src.actionType;
        b.mode           = src.mode;
        b.wirelessIdx    = src.wirelessIdx;
        b.rsSide         = src.rsSide;
        b.signalStrength = src.signalStrength;
        b.channel        = src.channel;
        b.powerLevel     = src.powerLevel;
        b.vectorX        = src.vectorX;
        b.vectorY        = src.vectorY;
        b.incPlus        = src.incPlus;
        return b;
    }

    // ── Init ──────────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        panelH    = PAD + TITLE_H + 4 + ROWS * (ROW_H + ROW_GAP) + 4 + BTN_H + PAD;
        panelX    = (width  - PANEL_W) / 2;
        panelY    = (height - panelH)  / 2;
        firstRowY = panelY + PAD + TITLE_H + 4;

        int btnY = panelY + panelH - PAD - BTN_H;
        boolean sablePresent = SableCompat.isPresent();
        // Bottom button row: Save | Start | [Import Typewriter if Sable present]
        int bottomBtnCount = sablePresent ? 3 : 2;
        int btnW = (PANEL_W - PAD * 2 - 4 * (bottomBtnCount - 1)) / bottomBtnCount;
        addRenderableWidget(Button.builder(Component.translatable("gui.universalkeyboard.btn.save"),  b -> doSave())
                .pos(panelX + PAD, btnY).size(btnW, BTN_H).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.universalkeyboard.btn.start"), b -> doStart())
                .pos(panelX + PAD + (btnW + 4), btnY).size(btnW, BTN_H).build());
        if (sablePresent) {
            addRenderableWidget(Button.builder(Component.translatable("gui.universalkeyboard.btn.import_typewriter"), b -> doTypewriterScan())
                    .pos(panelX + PAD + (btnW + 4) * 2, btnY).size(btnW, BTN_H).build());
        }

        // Page navigation buttons at top-right of panel
        int pageBtnY = panelY + PAD;
        int pageBtnW = 80;
        addRenderableWidget(Button.builder(Component.translatable("gui.universalkeyboard.btn.next_page"),
                        b -> { page = 1; vectorOverlaySlot = -1; listeningSlot = -1; })
                .pos(panelX + PANEL_W - PAD - pageBtnW, pageBtnY).size(pageBtnW, 12).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.universalkeyboard.btn.prev_page"),
                        b -> { page = 0; vectorOverlaySlot = -1; listeningSlot = -1; })
                .pos(panelX + PANEL_W - PAD - pageBtnW * 2 - 4, pageBtnY).size(pageBtnW, 12).build());
    }

    public BlockPos getKeyboardPos() { return keyboardPos; }

    private void doSave()  { ModPackets.sendSaveLiveBindings(keyboardPos, bindings); }
    private void doStart() {
        ModPackets.sendSaveLiveBindings(keyboardPos, bindings);
        LiveControlManager.activate(keyboardPos, bindings, currentLocalRs, currentWirelessPowers, currentThrusterPowers);
        onClose();
    }

    private void doTypewriterScan() {
        twOfferPos = null;
        twMessage  = I18n.get("gui.universalkeyboard.msg.scanning");
        twMessageTick = 60;
        ModPackets.sendTypewriterScan(keyboardPos);
    }

    /** Called by ClientPacketHandlers when the server replies to our scan request. */
    public void handleTypewriterOffer(net.minecraft.core.BlockPos twPos, int bindCount, int freqCount, String error) {
        if (!error.isEmpty()) {
            twOfferPos = null;
            twMessage  = "§c" + error;
            twMessageTick = 120;
        } else {
            twOfferPos  = twPos;
            twBindCount = bindCount;
            twFreqCount = freqCount;
            twMessage   = null;
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xFF111111);
        drawBorder(g, panelX, panelY, PANEL_W, panelH, 0xFF666666);
        // Title in top-left so it doesn't conflict with the page-nav buttons on the right
        g.drawString(font, I18n.get("gui.universalkeyboard.screen.live_controller.title"), panelX + PAD, panelY + PAD + 1, 0xFFFFFF, false);

        if (vectorOverlaySlot < 0) {
            int pageStart = page * (ROWS * 2); // 20 slots per page
            for (int pi = 0; pi < ROWS * 2; pi++) {
                int i = pageStart + pi;
                if (i >= MAX_SLOTS) break;
                int col = pi / ROWS, row = pi % ROWS;
                int rx = panelX + PAD + col * COL_OFFSET;
                int ry = firstRowY + row * (ROW_H + ROW_GAP);
                renderSlot(g, mx, my, i, rx, ry);
            }
        }
        for (var r : this.renderables) r.render(g, mx, my, pt);
        if (vectorOverlaySlot >= 0) renderVectorOverlay(g, mx, my, vectorOverlaySlot);

        // Brief status/error message below title
        if (twMessage != null && twMessageTick > 0) {
            g.drawCenteredString(font, twMessage, panelX + PANEL_W / 2, panelY + PAD + TITLE_H + 2, 0xFFFFFF);
        }

        // Typewriter import confirmation dialog
        if (twOfferPos != null) {
            renderTypewriterDialog(g, mx, my);
        }
    }

    // ── Slot rendering ────────────────────────────────────────────────────────
    private void renderSlot(GuiGraphics g, int mx, int my, int idx, int rx, int ry) {
        LiveControlBinding b = bindings.get(idx);
        boolean listening = (listeningSlot == idx);

        g.drawString(font, "§8" + (idx + 1), rx, ry + 4, 0x888888, false);

        int kx = rx + NUM_W;
        String keyLabel = listening ? I18n.get("gui.universalkeyboard.label.listening") : LiveControlBinding.keyName(b.keyCode);
        int keyBg = listening ? 0xFF5500AA : (b.keyCode < 0 ? 0xFF1A1A1A : 0xFF1A2A1A);
        boolean kHov = isIn(mx, my, kx, ry, KEY_W, ROW_H);
        g.fill(kx, ry, kx + KEY_W, ry + ROW_H, kHov ? brighten(keyBg) : keyBg);
        drawBorder(g, kx, ry, KEY_W, ROW_H, listening ? 0xFF9955FF : 0xFF444444);
        drawCenteredTrimmed(g, keyLabel, kx, ry + 4, KEY_W);

        int tx = kx + KEY_W + 2;
        String typeLabel = switch (b.actionType) {
            case REDSTONE        -> I18n.get("gui.universalkeyboard.label.action_rs");
            case THRUSTER_POWER  -> I18n.get("gui.universalkeyboard.label.action_thr");
            case THRUSTER_VECTOR -> I18n.get("gui.universalkeyboard.label.action_vec");
        };
        boolean tHov = isIn(mx, my, tx, ry, TYPE_W, ROW_H);
        g.fill(tx, ry, tx + TYPE_W, ry + ROW_H, tHov ? 0xFF333355 : 0xFF1F1F3A);
        drawBorder(g, tx, ry, TYPE_W, ROW_H, 0xFF444466);
        g.drawCenteredString(font, typeLabel, tx + TYPE_W / 2, ry + 4, 0xFFFFFF);

        int cx = rx + CFG_OFF;
        renderConfig(g, mx, my, b, idx, cx, ry);
    }

    private void renderConfig(GuiGraphics g, int mx, int my,
                              LiveControlBinding b, int idx, int x, int y) {
        switch (b.actionType) {
            case REDSTONE        -> renderRsConfig(g, mx, my, b, x, y);
            case THRUSTER_POWER  -> renderPwrConfig(g, mx, my, b, x, y);
            case THRUSTER_VECTOR -> renderVecConfig(g, mx, my, b, idx, x, y);
        }
    }

    // REDSTONE config — [side:50][mode:20][signal:20] = 90px
    private void renderRsConfig(GuiGraphics g, int mx, int my, LiveControlBinding b, int x, int y) {
        String sideLabel = b.wirelessIdx == 0
                ? b.rsSide.getSerializedName().toUpperCase() : "W" + b.wirelessIdx;
        boolean sHov = isIn(mx, my, x, y, 50, ROW_H);
        g.fill(x, y, x + 50, y + ROW_H, sHov ? 0xFF252535 : 0xFF1A1A2A);
        drawBorder(g, x, y, 50, ROW_H, 0xFF333355);
        g.drawString(font, "§7◄", x + 1, y + 4, 0x999999, false);
        g.drawCenteredString(font, "§f" + sideLabel, x + 25, y + 4, 0xFFFFFF);
        g.drawString(font, "§7►", x + 41, y + 4, 0x999999, false);
        x += 52;

        // Mode button cycles Hld → Tog → Inc
        boolean mHov = isIn(mx, my, x, y, 20, ROW_H);
        String modeLabel = switch (b.mode) {
            case HLD -> I18n.get("gui.universalkeyboard.label.mode_hld");
            case TGL -> I18n.get("gui.universalkeyboard.label.mode_tog");
            case INC -> I18n.get("gui.universalkeyboard.label.mode_inc");
        };
        g.fill(x, y, x + 20, y + ROW_H, mHov ? 0xFF253525 : 0xFF1A261A);
        drawBorder(g, x, y, 20, ROW_H, 0xFF446644);
        g.drawCenteredString(font, modeLabel, x + 10, y + 4, 0xFFFFFF);
        x += 22;

        // Value widget: signal number (HLD/TGL) or ++/-- (INC)
        boolean vHov = isIn(mx, my, x, y, 20, ROW_H);
        if (b.mode == Mode.INC) {
            g.fill(x, y, x + 20, y + ROW_H, vHov ? 0xFF253525 : 0xFF1A1A1A);
            drawBorder(g, x, y, 20, ROW_H, b.incPlus ? 0xFF448844 : 0xFF884444);
            g.drawCenteredString(font, b.incPlus ? "§a++" : "§c--", x + 10, y + 4, 0xFFFFFF);
        } else {
            g.fill(x, y, x + 20, y + ROW_H, vHov ? 0xFF252015 : 0xFF1A1510);
            drawBorder(g, x, y, 20, ROW_H, 0xFF665533);
            g.drawCenteredString(font, "§e" + b.signalStrength, x + 10, y + 4, 0xFFFFFF);
        }
    }

    // THRUSTER_POWER config — [ch:40][mode:20][power:28] = 88px
    private void renderPwrConfig(GuiGraphics g, int mx, int my, LiveControlBinding b, int x, int y) {
        boolean cHov = isIn(mx, my, x, y, 40, ROW_H);
        g.fill(x, y, x + 40, y + ROW_H, cHov ? 0xFF252535 : 0xFF1A1A2A);
        drawBorder(g, x, y, 40, ROW_H, 0xFF333355);
        g.drawString(font, "§7◄", x + 1, y + 4, 0x999999, false);
        g.drawCenteredString(font, "§fC" + b.channel, x + 20, y + 4, 0xFFFFFF);
        g.drawString(font, "§7►", x + 31, y + 4, 0x999999, false);
        x += 42;

        boolean mHov = isIn(mx, my, x, y, 20, ROW_H);
        String modeLabel = switch (b.mode) {
            case HLD -> I18n.get("gui.universalkeyboard.label.mode_hld");
            case TGL -> I18n.get("gui.universalkeyboard.label.mode_tog");
            case INC -> I18n.get("gui.universalkeyboard.label.mode_inc");
        };
        g.fill(x, y, x + 20, y + ROW_H, mHov ? 0xFF253525 : 0xFF1A261A);
        drawBorder(g, x, y, 20, ROW_H, 0xFF446644);
        g.drawCenteredString(font, modeLabel, x + 10, y + 4, 0xFFFFFF);
        x += 22;

        int pct = (int) Math.round(b.powerLevel * 100);
        boolean vHov = isIn(mx, my, x, y, 28, ROW_H);
        g.fill(x, y, x + 28, y + ROW_H, vHov ? 0xFF203040 : 0xFF18202A);
        if (b.mode == Mode.INC) {
            drawBorder(g, x, y, 28, ROW_H, b.incPlus ? 0xFF448844 : 0xFF884444);
            g.drawCenteredString(font, b.incPlus ? "§a++" : "§c--", x + 14, y + 4, 0xFFFFFF);
        } else {
            drawBorder(g, x, y, 28, ROW_H, 0xFF335588);
            g.drawCenteredString(font, "§b" + pct + "%", x + 14, y + 4, 0xFFFFFF);
        }
    }

    // THRUSTER_VECTOR config — [ch:40][mode:20][vec:28] = 88px  (INC not supported for VEC)
    private void renderVecConfig(GuiGraphics g, int mx, int my,
                                 LiveControlBinding b, int idx, int x, int y) {
        boolean cHov = isIn(mx, my, x, y, 40, ROW_H);
        g.fill(x, y, x + 40, y + ROW_H, cHov ? 0xFF252535 : 0xFF1A1A2A);
        drawBorder(g, x, y, 40, ROW_H, 0xFF333355);
        g.drawString(font, "§7◄", x + 1, y + 4, 0x999999, false);
        g.drawCenteredString(font, "§fC" + b.channel, x + 20, y + 4, 0xFFFFFF);
        g.drawString(font, "§7►", x + 31, y + 4, 0x999999, false);
        x += 42;

        boolean mHov = isIn(mx, my, x, y, 20, ROW_H);
        String modeLabel = b.mode == Mode.TGL ? I18n.get("gui.universalkeyboard.label.mode_tog") : I18n.get("gui.universalkeyboard.label.mode_hld");
        g.fill(x, y, x + 20, y + ROW_H, mHov ? 0xFF253525 : 0xFF1A261A);
        drawBorder(g, x, y, 20, ROW_H, 0xFF446644);
        g.drawCenteredString(font, modeLabel, x + 10, y + 4, 0xFFFFFF);
        x += 22;

        boolean vHov = isIn(mx, my, x, y, 28, ROW_H) || vectorOverlaySlot == idx;
        g.fill(x, y, x + 28, y + ROW_H, vHov ? 0xFF1A3535 : 0xFF111F1F);
        drawBorder(g, x, y, 28, ROW_H, vHov ? 0xFF44AAAA : 0xFF334444);
        double mag = Math.sqrt(b.vectorX * b.vectorX + b.vectorY * b.vectorY);
        String vecLabel = mag < 0.01 ? "§7---" : vecArrow(b.vectorX, b.vectorY);
        g.drawCenteredString(font, vecLabel, x + 14, y + 4, 0xFFFFFF);
    }

    // ── Vector overlay ────────────────────────────────────────────────────────
    private void renderVectorOverlay(GuiGraphics g, int mx, int my, int idx) {
        LiveControlBinding b = bindings.get(idx);
        int ovX = width  / 2 - VEC_OV_W / 2;
        int ovY = height / 2 - VEC_OV_H / 2;

        g.fill(ovX, ovY, ovX + VEC_OV_W, ovY + VEC_OV_H, 0xFF070710);
        drawBorder(g, ovX, ovY, VEC_OV_W, VEC_OV_H, 0xFF6688BB);
        g.drawCenteredString(font, I18n.get("gui.universalkeyboard.label.vector_title"), ovX + VEC_OV_W / 2, ovY + 4, 0xFFFFFF);
        g.drawCenteredString(font, "§7Slot " + (idx + 1), ovX + VEC_OV_W / 2, ovY + 13, 0x888888);

        int cx = ovX + VEC_OV_W / 2;
        int cy = ovY + 22 + 38;
        int r  = 34;
        g.fill(cx - r, cy, cx + r, cy + 1, 0xFF223344);
        g.fill(cx, cy - r, cx + 1, cy + r, 0xFF223344);
        drawCircleOutline(g, cx, cy, r, 0xFF334455);

        int dotX = (int) (cx + b.vectorX * r);
        int dotY = (int) (cy - b.vectorY * r);
        double mag = Math.sqrt(b.vectorX * b.vectorX + b.vectorY * b.vectorY);
        if (mag > 0.01) drawLine(g, cx, cy, dotX, dotY, 0xFF00FFAA);
        g.fill(dotX - 2, dotY - 2, dotX + 3, dotY + 3, 0xFF00FFAA);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);

        double dispMag = Math.sqrt(b.vectorX * b.vectorX + b.vectorY * b.vectorY);
        String coords = String.format("§7X:§f%.2f §7Y:§f%.2f §8(%.0f%%)", b.vectorX, b.vectorY, dispMag * 100);
        g.drawCenteredString(font, coords, ovX + VEC_OV_W / 2, ovY + 22 + r * 2 + 6, 0xFFFFFF);
        g.drawCenteredString(font, I18n.get("gui.universalkeyboard.label.vector_hint"), ovX + VEC_OV_W / 2, ovY + VEC_OV_H - 22, 0x666666);

        int okX = ovX + VEC_OV_W / 2 - 18;
        int okY = ovY + VEC_OV_H - 14;
        boolean okHov = isIn(mx, my, okX, okY, 36, 12);
        g.fill(okX, okY, okX + 36, okY + 12, okHov ? 0xFF335533 : 0xFF224422);
        drawBorder(g, okX, okY, 36, 12, 0xFF448844);
        g.drawCenteredString(font, I18n.get("gui.universalkeyboard.btn.ok"), okX + 18, okY + 2, 0xFFFFFF);
    }

    // ── Mouse handling ────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int imx = (int) mx, imy = (int) my;

        // Typewriter import dialog intercepts all clicks
        if (twOfferPos != null) {
            int[] L = twDialogLayout();
            int dx = L[0], btnY = L[4], btnH = L[5];
            if (imx >= dx + 20 && imx < dx + 120 && imy >= btnY && imy < btnY + btnH) {
                // Confirm
                ModPackets.sendTypewriterConfirm(keyboardPos, twOfferPos);
                twOfferPos = null;
            } else {
                // Cancel or click outside
                twOfferPos = null;
            }
            return true;
        }

        if (vectorOverlaySlot >= 0) { handleVectorOverlayClick(imx, imy, vectorOverlaySlot, btn); return true; }
        if (listeningSlot >= 0) listeningSlot = -1;
        int pageStart = page * (ROWS * 2);
        for (int pi = 0; pi < ROWS * 2; pi++) {
            int i = pageStart + pi;
            if (i >= MAX_SLOTS) break;
            int col = pi / ROWS, row = pi % ROWS;
            int rx = panelX + PAD + col * COL_OFFSET;
            int ry = firstRowY + row * (ROW_H + ROW_GAP);
            if (handleSlotClick(i, rx, ry, imx, imy, btn)) return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    private void handleVectorOverlayClick(int mx, int my, int idx, int btn) {
        LiveControlBinding b = bindings.get(idx);
        int ovX = width  / 2 - VEC_OV_W / 2;
        int ovY = height / 2 - VEC_OV_H / 2;
        int okX = ovX + VEC_OV_W / 2 - 18;
        int okY = ovY + VEC_OV_H - 14;
        if (isIn(mx, my, okX, okY, 36, 12)) { vectorOverlaySlot = -1; return; }
        int cx = ovX + VEC_OV_W / 2, cy = ovY + 22 + 34, r = 34;
        double dx = mx - cx, dy = cy - my;
        if (Math.sqrt(dx * dx + dy * dy) <= r + 6) {
            b.vectorX = dx / r;
            b.vectorY = dy / r;
            double mag = Math.sqrt(b.vectorX * b.vectorX + b.vectorY * b.vectorY);
            if (mag > 1.0) { b.vectorX /= mag; b.vectorY /= mag; }
        }
    }

    private boolean handleSlotClick(int idx, int rx, int ry, int mx, int my, int btn) {
        LiveControlBinding b = bindings.get(idx);

        int kx = rx + NUM_W;
        if (isIn(mx, my, kx, ry, KEY_W, ROW_H)) {
            if (btn == 1) { b.keyCode = -1; listeningSlot = -1; }
            else listeningSlot = (listeningSlot == idx) ? -1 : idx;
            return true;
        }

        int tx = kx + KEY_W + 2;
        if (isIn(mx, my, tx, ry, TYPE_W, ROW_H)) {
            int dir = (btn == 1) ? -1 : 1;
            b.actionType = nextAvailableType(b.actionType, dir);
            // VEC doesn't support INC — reset to HLD
            if (b.mode == Mode.INC && b.actionType == ActionType.THRUSTER_VECTOR) b.mode = Mode.HLD;
            return true;
        }

        int cx = rx + CFG_OFF;
        return handleConfigClick(b, idx, cx, ry, mx, my, btn);
    }

    private boolean handleConfigClick(LiveControlBinding b, int idx,
                                      int x, int y, int mx, int my, int btn) {
        boolean right = (btn == 1);
        switch (b.actionType) {
            case REDSTONE -> {
                if (isIn(mx, my, x, y, 50, ROW_H)) {
                    if (mx < x + 12) cycleSide(b, -1); else if (mx > x + 38) cycleSide(b, +1);
                    return true;
                }
                x += 52;
                if (isIn(mx, my, x, y, 20, ROW_H)) {
                    b.mode = nextMode(b.actionType, b.mode, right ? -1 : 1); return true;
                }
                x += 22;
                if (isIn(mx, my, x, y, 20, ROW_H)) {
                    if (b.mode == Mode.INC) { b.incPlus = !b.incPlus; }
                    else b.signalStrength = Math.max(0, Math.min(15, b.signalStrength + (right ? -1 : 1)));
                    return true;
                }
            }
            case THRUSTER_POWER -> {
                if (isIn(mx, my, x, y, 40, ROW_H)) {
                    if (mx < x + 12) b.channel = b.channel == 1 ? LinkedKeyboardBlockEntity.MAX_CHANNELS : b.channel - 1;
                    else if (mx > x + 28) b.channel = b.channel == LinkedKeyboardBlockEntity.MAX_CHANNELS ? 1 : b.channel + 1;
                    return true;
                }
                x += 42;
                if (isIn(mx, my, x, y, 20, ROW_H)) {
                    b.mode = nextMode(b.actionType, b.mode, right ? -1 : 1); return true;
                }
                x += 22;
                if (isIn(mx, my, x, y, 28, ROW_H)) {
                    if (b.mode == Mode.INC) { b.incPlus = !b.incPlus; }
                    else { double step = right ? -0.05 : 0.05;
                        b.powerLevel = Math.max(0.0, Math.min(1.0, Math.round((b.powerLevel + step) * 20) / 20.0)); }
                    return true;
                }
            }
            case THRUSTER_VECTOR -> {
                if (isIn(mx, my, x, y, 40, ROW_H)) {
                    if (mx < x + 12) b.channel = b.channel == 1 ? LinkedKeyboardBlockEntity.MAX_CHANNELS : b.channel - 1;
                    else if (mx > x + 28) b.channel = b.channel == LinkedKeyboardBlockEntity.MAX_CHANNELS ? 1 : b.channel + 1;
                    return true;
                }
                x += 42;
                if (isIn(mx, my, x, y, 20, ROW_H)) {
                    b.mode = nextMode(b.actionType, b.mode, right ? -1 : 1); return true;
                }
                x += 22;
                if (isIn(mx, my, x, y, 28, ROW_H)) { vectorOverlaySlot = (vectorOverlaySlot == idx) ? -1 : idx; return true; }
            }
        }
        return false;
    }

    // ── Scroll wheel ──────────────────────────────────────────────────────────
    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int imx = (int) mx, imy = (int) my;
        int dir = dy > 0 ? 1 : -1;
        int pageStart = page * (ROWS * 2);
        for (int pi = 0; pi < ROWS * 2; pi++) {
            int i = pageStart + pi;
            if (i >= MAX_SLOTS) break;
            int col = pi / ROWS, row = pi % ROWS;
            int rx = panelX + PAD + col * COL_OFFSET;
            int ry = firstRowY + row * (ROW_H + ROW_GAP);
            if (!isIn(imx, imy, rx, ry, COL_W, ROW_H)) continue;
            LiveControlBinding b = bindings.get(i);
            int cfgX = rx + CFG_OFF;
            if (isIn(imx, imy, cfgX, ry, CFG_W, ROW_H) && b.mode != Mode.INC) {
                switch (b.actionType) {
                    case REDSTONE       -> b.signalStrength = Math.max(0, Math.min(15, b.signalStrength + dir));
                    case THRUSTER_POWER -> b.powerLevel = Math.max(0.0, Math.min(1.0,
                            Math.round((b.powerLevel + dir * 0.05) * 20) / 20.0));
                    default -> {}
                }
                return true;
            }
            return false;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    // ── Key handling ──────────────────────────────────────────────────────────
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listeningSlot >= 0) {
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) bindings.get(listeningSlot).keyCode = keyCode;
            listeningSlot = -1;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (twOfferPos != null) { twOfferPos = null; return true; }
            if (vectorOverlaySlot >= 0) { vectorOverlaySlot = -1; return true; }
            onClose(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        super.tick();
        if (twMessageTick > 0) { if (--twMessageTick == 0) twMessage = null; }
    }

    /** Layout for the typewriter dialog: {dx, dy, dw, dh, btnY, btnH}. */
    private int[] twDialogLayout() {
        int dw = 300, pad = 8, gap = 3, btnH = 14;
        int textW = dw - pad * 2;
        int textBlock = GuiText.wrappedHeight(font, I18n.get("gui.universalkeyboard.dialog.tw_import_title"), textW) + gap
                + GuiText.wrappedHeight(font, I18n.get("gui.universalkeyboard.dialog.tw_import_summary", twBindCount, twFreqCount), textW) + gap
                + GuiText.wrappedHeight(font, I18n.get("gui.universalkeyboard.dialog.tw_import_warn1"), textW)
                + GuiText.wrappedHeight(font, I18n.get("gui.universalkeyboard.dialog.tw_import_warn2"), textW);
        int dh = pad + textBlock + gap + 4 + btnH + pad;
        int dx = panelX + (PANEL_W - dw) / 2;
        int dy = panelY + (panelH  - dh) / 2;
        int btnY = dy + dh - pad - btnH;
        return new int[]{dx, dy, dw, dh, btnY, btnH};
    }

    private void renderTypewriterDialog(GuiGraphics g, int mx, int my) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        // Dim the whole panel
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xAA000000);

        int[] L = twDialogLayout();
        int dx = L[0], dy = L[1], dw = L[2], dh = L[3], btnY = L[4], btnH = L[5];
        int pad = 8, gap = 3, textW = dw - pad * 2, cx = dx + dw / 2;

        g.fill(dx - 1, dy - 1, dx + dw + 1, dy + dh + 1, 0xFF666666);
        g.fill(dx, dy, dx + dw, dy + dh, 0xFF1A1A1A);

        int y = dy + pad;
        y += GuiText.drawWrappedCentered(g, font, I18n.get("gui.universalkeyboard.dialog.tw_import_title"), cx, y, textW, 0xFFFFFF) + gap;
        y += GuiText.drawWrappedCentered(g, font, I18n.get("gui.universalkeyboard.dialog.tw_import_summary", twBindCount, twFreqCount), cx, y, textW, 0xFFFFFF) + gap;
        y += GuiText.drawWrappedCentered(g, font, I18n.get("gui.universalkeyboard.dialog.tw_import_warn1"), cx, y, textW, 0xAAAAAA);
        GuiText.drawWrappedCentered(g, font, I18n.get("gui.universalkeyboard.dialog.tw_import_warn2"), cx, y, textW, 0xAAAAAA);

        boolean yh = mx >= dx + 20  && mx < dx + 120 && my >= btnY && my < btnY + btnH;
        boolean nh = mx >= dx + 180 && mx < dx + 280 && my >= btnY && my < btnY + btnH;
        g.fill(dx + 20,  btnY, dx + 120, btnY + btnH, yh ? 0xFF2A4A2A : 0xFF1E3A1E);
        g.fill(dx + 180, btnY, dx + 280, btnY + btnH, nh ? 0xFF4A2A2A : 0xFF3A1E1E);
        g.drawCenteredString(font, I18n.get("gui.universalkeyboard.btn.import"), dx + 70,  btnY + 3, yh ? 0x88FF88 : 0x66CC66);
        g.drawCenteredString(font, I18n.get("gui.universalkeyboard.btn.cancel"), dx + 230, btnY + 3, nh ? 0xFF8888 : 0xCC6666);
        g.pose().popPose();
    }

    // ── Type / mode cycling ───────────────────────────────────────────────────
    private boolean isTypeAvailable(ActionType t) {
        return switch (t) {
            case REDSTONE        -> true;
            case THRUSTER_POWER  -> hasThrusters;
            case THRUSTER_VECTOR -> hasVectorThrusters;
        };
    }

    private ActionType nextAvailableType(ActionType current, int dir) {
        ActionType[] all = ActionType.values();
        int idx = current.ordinal();
        for (int i = 1; i <= all.length; i++) {
            int next = ((idx + dir * i) % all.length + all.length) % all.length;
            if (isTypeAvailable(all[next])) return all[next];
        }
        return current;
    }

    /** Cycle through modes. VEC only supports HLD and TGL (no INC). */
    private static Mode nextMode(ActionType type, Mode current, int dir) {
        Mode[] available = (type == ActionType.THRUSTER_VECTOR)
                ? new Mode[]{Mode.HLD, Mode.TGL}
                : Mode.values();
        int idx = 0;
        for (int i = 0; i < available.length; i++) if (available[i] == current) { idx = i; break; }
        return available[((idx + dir) % available.length + available.length) % available.length];
    }

    // ── Side cycling (RS target) ───────────────────────────────────────────────
    private void cycleSide(LiveControlBinding b, int dir) {
        Direction[] dirs  = Direction.values();
        int total   = dirs.length + wirelessCount;
        int current = b.wirelessIdx > 0 ? dirs.length + (b.wirelessIdx - 1) : b.rsSide.ordinal();
        current = ((current + dir) % total + total) % total;
        if (current < dirs.length) { b.wirelessIdx = 0; b.rsSide = dirs[current]; }
        else                       { b.wirelessIdx = current - dirs.length + 1; }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static boolean isIn(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int brighten(int color) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, ((color >> 16) & 0xFF) + 25);
        int g = Math.min(255, ((color >>  8) & 0xFF) + 25);
        int b = Math.min(255, ( color        & 0xFF) + 25);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x,         y,         x + w,     y + 1,     c);
        g.fill(x,         y + h - 1, x + w,     y + h,     c);
        g.fill(x,         y,         x + 1,     y + h,     c);
        g.fill(x + w - 1, y,         x + w,     y + h,     c);
    }

    private static void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2, int c) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int steps = Math.max(dx, dy);
        if (steps == 0) return;
        for (int i = 0; i <= steps; i++) {
            int px = x1 + (x2 - x1) * i / steps;
            int py = y1 + (y2 - y1) * i / steps;
            g.fill(px, py, px + 1, py + 1, c);
        }
    }

    private static void drawCircleOutline(GuiGraphics g, int cx, int cy, int r, int c) {
        int steps = r * 6;
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            int px = (int) (cx + Math.cos(angle) * r);
            int py = (int) (cy + Math.sin(angle) * r);
            g.fill(px, py, px + 1, py + 1, c);
        }
    }

    private void drawCenteredTrimmed(GuiGraphics g, String text, int x, int y, int maxW) {
        String plain = text.replaceAll("§.", "");
        int textW = font.width(plain);
        if (textW <= maxW) g.drawCenteredString(font, text, x + maxW / 2, y, 0xFFFFFF);
        else               g.drawString(font, text, x + 1, y, 0xFFFFFF, false);
    }

    private static String vecArrow(double vx, double vy) {
        double angle = Math.toDegrees(Math.atan2(vy, vx));
        if (angle >= -22.5  && angle < 22.5)   return "§f→";
        if (angle >= 22.5   && angle < 67.5)   return "§f↗";
        if (angle >= 67.5   && angle < 112.5)  return "§f↑";
        if (angle >= 112.5  && angle < 157.5)  return "§f↖";
        if (angle >= 157.5  || angle < -157.5) return "§f←";
        if (angle >= -157.5 && angle < -112.5) return "§f↙";
        if (angle >= -112.5 && angle < -67.5)  return "§f↓";
        return "§f↘";
    }
}
