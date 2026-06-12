package dev.bennethogan.universalkeyboard.client.screen;

import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.universalkeyboard.client.gamepad.GamepadLiveDriver;
import dev.bennethogan.universalkeyboard.config.ModConfig;
import dev.bennethogan.universalkeyboard.livecontrol.MouseCodes;
import dev.bennethogan.universalkeyboard.livecontrol.ChannelMode;
import dev.bennethogan.universalkeyboard.livecontrol.FavoriteScreen;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding.ActionType;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding.Mode;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlManager;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
    private final int                     rsLinkCount;
    private final boolean                 hasThrusters;
    private final boolean                 hasVectorThrusters;
    private final boolean                 hasRpm;
    private final List<LiveControlBinding> bindings;
    private final int[] currentLocalRs;
    private final int[] currentWirelessPowers;
    private final int[] currentThrusterPowers;
    private final double[] currentVarValues;
    private final int[] currentRpmValues;

    // Profile state
    private int activeProfile;
    private final List<List<LiveControlBinding>> allProfiles; // mutable; all 4 slots
    private final List<List<LiveControlBinding>> originalAllProfiles; // for per-profile revert
    private final IconButton[] profileButtons = new IconButton[4];

    // Autosave/revert state
    private List<LiveControlBinding>       savedSnapshot;    // last state pushed to the server
    private List<LiveControlBinding>       lastTickSnapshot; // previous tick, to debounce mid-edit autosaves

    private int panelX, panelY, panelH;
    private int firstRowY;
    private ConfirmDialog confirmDialog;
    private int listeningSlot     = -1;
    private int vectorOverlaySlot = -1;
    private int exclOverlaySlot   = -1;
    private String exclInput      = "";
    private boolean mouseCaptureArmed = false;
    private double  lastMoveX = Double.NaN, lastMoveY;
    private int page = 0; // 0 = slots 0–19, 1 = slots 20–39
    private int focusedSlot = -1;

    private String wFreqTooltip   = null;
    private int    wFreqTooltipX  = 0;
    private int    wFreqTooltipY  = 0;

    // Favorite / wiki buttons
    private boolean isFavorited = false;
    private DarkButton starBtn;

    public void onFavoriteSync(FavoriteScreen fav) {
        isFavorited = fav == FavoriteScreen.LIVE_CONTROL;
        if (starBtn != null) {
            starBtn.setAccentBg(isFavorited ? 0xFF3A3000 : -1);
            starBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(
                    isFavorited ? "gui.universalkeyboard.tooltip.unfavorite"
                                : "gui.universalkeyboard.tooltip.favorite")));
        }
    }

    // ── Constructor ───────────────────────────────────────────────────────────
    public LiveControlScreen(BlockPos keyboardPos,
                             int rsLinkCount,
                             boolean hasThrusters,
                             boolean hasVectorThrusters,
                             boolean hasRpm,
                             int[] currentLocalRs,
                             int[] currentWirelessPowers,
                             int[] currentThrusterPowers,
                             double[] currentVarValues,
                             int[] currentRpmValues,
                             int activeProfile,
                             List<List<LiveControlBinding>> allProfilesIn) {
        super(Component.empty());
        this.keyboardPos           = keyboardPos;
        this.rsLinkCount         = rsLinkCount;
        this.hasThrusters          = hasThrusters;
        this.hasVectorThrusters    = hasVectorThrusters;
        this.hasRpm                = hasRpm;
        this.currentLocalRs        = currentLocalRs;
        this.currentWirelessPowers = currentWirelessPowers;
        this.currentThrusterPowers = currentThrusterPowers;
        this.currentVarValues      = currentVarValues;
        this.currentRpmValues      = currentRpmValues;
        this.activeProfile         = Math.max(0, Math.min(3, activeProfile));

        // mutable copies of profile list
        this.allProfiles = new ArrayList<>(4);
        for (int p = 0; p < 4; p++) {
            List<LiveControlBinding> src = (p < allProfilesIn.size()) ? allProfilesIn.get(p) : List.of();
            this.allProfiles.add(new ArrayList<>(src));
        }

        // Active profile bindings fill the working list
        List<LiveControlBinding> activeData = this.allProfiles.get(this.activeProfile);
        this.bindings = new ArrayList<>(MAX_SLOTS);
        for (int i = 0; i < MAX_SLOTS; i++)
            this.bindings.add(i < activeData.size() ? copyBinding(activeData.get(i)) : new LiveControlBinding());

        // Per-profile originals for Revert
        this.originalAllProfiles = new ArrayList<>(4);
        for (List<LiveControlBinding> profile : this.allProfiles)
            this.originalAllProfiles.add(new ArrayList<>(deepCopy(profile)));

        this.savedSnapshot    = deepCopy(bindings);
        this.lastTickSnapshot = deepCopy(bindings);
    }

    private static List<LiveControlBinding> deepCopy(List<LiveControlBinding> src) {
        List<LiveControlBinding> out = new ArrayList<>(src.size());
        for (LiveControlBinding b : src) out.add(copyBinding(b));
        return out;
    }

    private static boolean bindingEquals(LiveControlBinding a, LiveControlBinding b) {
        return a.keyCode == b.keyCode
                && a.actionType == b.actionType
                && a.mode == b.mode
                && a.rsLinkIdx == b.rsLinkIdx
                && a.wirelessFreqIdx == b.wirelessFreqIdx
                && a.rsSide == b.rsSide
                && a.signalStrength == b.signalStrength
                && a.channel == b.channel
                && a.powerLevel == b.powerLevel
                && a.vectorX == b.vectorX
                && a.vectorY == b.vectorY
                && a.incPlus == b.incPlus
                && a.varIndex == b.varIndex
                && a.varOnValue == b.varOnValue
                && a.overdriveMultiplier == b.overdriveMultiplier
                && java.util.Objects.equals(a.odExcludes, b.odExcludes)
                && a.rpmTarget == b.rpmTarget
                && a.inverted == b.inverted;
    }

    private static boolean listsEqual(List<LiveControlBinding> a, List<LiveControlBinding> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++)
            if (!bindingEquals(a.get(i), b.get(i))) return false;
        return true;
    }

    private static LiveControlBinding copyBinding(LiveControlBinding src) {
        LiveControlBinding b = new LiveControlBinding();
        b.keyCode        = src.keyCode;
        b.actionType     = src.actionType;
        b.mode           = src.mode;
        b.rsLinkIdx    = src.rsLinkIdx;
        b.wirelessFreqIdx        = src.wirelessFreqIdx;
        b.rsSide         = src.rsSide;
        b.signalStrength = src.signalStrength;
        b.channel        = src.channel;
        b.powerLevel     = src.powerLevel;
        b.vectorX        = src.vectorX;
        b.vectorY        = src.vectorY;
        b.incPlus        = src.incPlus;
        b.varIndex       = src.varIndex;
        b.varOnValue          = src.varOnValue;
        b.overdriveMultiplier = src.overdriveMultiplier;
        b.odExcludes          = src.odExcludes;
        b.rpmTarget           = src.rpmTarget;
        b.inverted            = src.inverted;
        return b;
    }

    // ── Init ──────────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        panelH    = PAD + TITLE_H + 4 + ROWS * (ROW_H + ROW_GAP) + 4 + BTN_H + PAD;
        panelX    = (width  - PANEL_W) / 2;
        panelY    = (height - panelH)  / 2;
        firstRowY = panelY + PAD + TITLE_H + 4;

        confirmDialog = new ConfirmDialog(font);
        confirmDialog.setParentBounds(panelX, panelY, PANEL_W, panelH);

        // Top-right corner: wiki and star buttons
        int topBtnY = panelY + (PAD + TITLE_H - BTN_H) / 2 + 2;
        addRenderableWidget(DarkButton.make(Component.literal("?"),
                Component.translatable("gui.universalkeyboard.tooltip.wiki"),
                b -> net.minecraft.client.Minecraft.getInstance().setScreen(new WikiScreen(this)),
                panelX + PANEL_W - PAD - BTN_H * 2 - 2, topBtnY, BTN_H, BTN_H));
        isFavorited = MenuNav.currentFavorite == FavoriteScreen.LIVE_CONTROL;
        starBtn = DarkButton.make(Component.literal("★"),
                Component.translatable(isFavorited
                        ? "gui.universalkeyboard.tooltip.unfavorite"
                        : "gui.universalkeyboard.tooltip.favorite"),
                b -> toggleFavorite(),
                panelX + PANEL_W - PAD - BTN_H, topBtnY, BTN_H, BTN_H);
        starBtn.setAccentBg(isFavorited ? 0xFF3A3000 : -1);
        addRenderableWidget(starBtn);

        // Bottom row: Back (left) | gap | Start | Profile 1-4 | Revert | Prev | Next  (32×16 each, 2px gap)
        final int BTN_W   = 32;
        final int BTN_GAP = 2;
        final int NUM_BTNS = 8; // 1 start + 4 profiles + 1 revert + 2 nav
        int btnY  = panelY + panelH - PAD - BTN_H;
        int bx    = panelX + (PANEL_W - (NUM_BTNS * BTN_W + (NUM_BTNS - 1) * BTN_GAP)) / 2;

        // Back button — bottom left, isolated from the rest
        addRenderableWidget(IconButton.make(ModIcons.TAB_BACK,
                Component.translatable("gui.universalkeyboard.tooltip.back"),
                b -> goBack(),
                panelX + PAD, btnY, BTN_W, BTN_H));

        // Green start button
        addRenderableWidget(IconButton.make(ModIcons.PLAY,
                Component.translatable("gui.universalkeyboard.tooltip.start"),
                b -> doStart(),
                bx, btnY, BTN_W, BTN_H, 0xFF1E3A1E, 0));
        bx += BTN_W + BTN_GAP;

        // 4 profile buttons
        for (int i = 0; i < 4; i++) {
            final int profIdx = i;
            profileButtons[i] = IconButton.make(ModIcons.FOLDER,
                    Component.translatable("gui.universalkeyboard.tooltip.profile_n", i + 1),
                    b -> switchProfile(profIdx),
                    bx, btnY, BTN_W, BTN_H, -1, -6);
            addRenderableWidget(profileButtons[i]);
            bx += BTN_W + BTN_GAP;
        }

        // Revert
        addRenderableWidget(IconButton.make(ModIcons.REVERT,
                Component.translatable("gui.universalkeyboard.tooltip.revert"),
                b -> confirmDialog.open(
                        "gui.universalkeyboard.dialog.revert_title",
                        "gui.universalkeyboard.dialog.revert_body",
                        "gui.universalkeyboard.btn.yes_revert",
                        this::doRevert),
                bx, btnY, BTN_W, BTN_H));
        bx += BTN_W + BTN_GAP;

        // Prev Page
        addRenderableWidget(IconButton.make(ModIcons.PREV_PAGE,
                Component.translatable("gui.universalkeyboard.tooltip.prev_page"),
                b -> { page = 0; vectorOverlaySlot = -1; listeningSlot = -1; focusedSlot = -1; },
                bx, btnY, BTN_W, BTN_H));
        bx += BTN_W + BTN_GAP;

        // Next Page
        addRenderableWidget(IconButton.make(ModIcons.NEXT_PAGE,
                Component.translatable("gui.universalkeyboard.tooltip.next_page"),
                b -> { page = 1; vectorOverlaySlot = -1; listeningSlot = -1; focusedSlot = -1; },
                bx, btnY, BTN_W, BTN_H));
    }

    public BlockPos getKeyboardPos() { return keyboardPos; }

    private void goBack() {
        if (!MenuNav.back()) super.onClose();
    }

    private void toggleFavorite() {
        ModPackets.sendSetFavorite(keyboardPos, isFavorited ? FavoriteScreen.NONE : FavoriteScreen.LIVE_CONTROL);
    }

    /** Push the current bindings to the server if they differ from what was last saved. */
    private void autosave() {
        if (listsEqual(bindings, savedSnapshot)) return;
        ModPackets.sendSaveLiveBindings(keyboardPos, activeProfile, bindings);
        savedSnapshot = deepCopy(bindings);
    }

    /** Discard all edits to the current profile since the menu was opened. */
    private void doRevert() {
        bindings.clear();
        List<LiveControlBinding> orig = originalAllProfiles.get(activeProfile);
        for (int i = 0; i < MAX_SLOTS; i++)
            bindings.add(i < orig.size() ? copyBinding(orig.get(i)) : new LiveControlBinding());
        listeningSlot = vectorOverlaySlot = exclOverlaySlot = -1;
        autosave();
    }

    /** Switch to a different profile, auto-saving the current one first. */
    private void switchProfile(int idx) {
        if (idx == activeProfile) return;
        // Save current working state into the allProfiles list
        allProfiles.set(activeProfile, new ArrayList<>(bindings));
        autosave(); // flush current profile to server
        // Switch
        activeProfile = idx;
        List<LiveControlBinding> src = allProfiles.get(idx);
        bindings.clear();
        for (int i = 0; i < MAX_SLOTS; i++)
            bindings.add(i < src.size() ? copyBinding(src.get(i)) : new LiveControlBinding());
        savedSnapshot    = deepCopy(bindings);
        lastTickSnapshot = deepCopy(bindings);
        listeningSlot = vectorOverlaySlot = exclOverlaySlot = -1;
    }

    private void doStart() {
        // Unconditionally push the started profile to the server, maybe that will fix schematic printing issues
        // autosaves no change guardd was causing it to be skipping for NBT saving I think
        ModPackets.sendSaveLiveBindings(keyboardPos, activeProfile, bindings);
        savedSnapshot = deepCopy(bindings);
        LiveControlManager.activate(keyboardPos, bindings, currentLocalRs, currentWirelessPowers, currentThrusterPowers, currentVarValues, currentRpmValues);
        onClose();
    }

    // ── Rendering ─────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        wFreqTooltip = null;
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

        // active-profile underline and profile number
        for (int i = 0; i < 4; i++) {
            if (profileButtons[i] == null) continue;
            int bx = profileButtons[i].getX(), by = profileButtons[i].getY();
            g.drawCenteredString(font, String.valueOf(i + 1), bx + 24, by + 4, 0xFFFFFF);
            if (activeProfile == i)
                g.fill(bx + 1, by + 14, bx + 31, by + 16, 0xFF5599FF);
        }

        if (vectorOverlaySlot >= 0) renderVectorOverlay(g, mx, my, vectorOverlaySlot);
        if (exclOverlaySlot >= 0) renderExclOverlay(g, mx, my);

        if (confirmDialog != null) confirmDialog.render(g, mx, my);
        if (wFreqTooltip != null)
            g.renderTooltip(font, Component.literal(wFreqTooltip), wFreqTooltipX, wFreqTooltipY);
    }

    // ── Flagged-button visual helpers ─────────────────────────────────────────
    // For now its just for invert hold mode (red = inverted, green = normal)
    private static int modeBg(boolean hovered, boolean flagged) {
        return flagged ? (hovered ? 0xFF352525 : 0xFF261A1A)
                       : (hovered ? 0xFF253525 : 0xFF1A261A);
    }
    private static int modeBorder(boolean flagged) { return flagged ? 0xFF664444 : 0xFF446644; }
    private static String modeLabel(LiveControlBinding b) {
        String base = switch (b.mode) {
            case HLD -> I18n.get("gui.universalkeyboard.label.mode_hld");
            case TGL -> I18n.get("gui.universalkeyboard.label.mode_tog");
            case INC -> I18n.get("gui.universalkeyboard.label.mode_inc");
        };
        return (b.inverted && b.mode == Mode.HLD) ? "-" + base : base;
    }

    // ── Slot rendering ────────────────────────────────────────────────────────
    private void renderSlot(GuiGraphics g, int mx, int my, int idx, int rx, int ry) {
        LiveControlBinding b = bindings.get(idx);
        boolean listening = (listeningSlot == idx);

        // blue mark for a click-focused row
        if (focusedSlot == idx)
            g.fill(rx, ry, rx + 2, ry + ROW_H, 0xFF5599FF);

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
            case VARIABLE        -> I18n.get("gui.universalkeyboard.label.action_var");
            case OVERDRIVE       -> I18n.get("gui.universalkeyboard.label.action_od");
            default              -> I18n.get(ChannelMode.byType(b.actionType).labelKey); // channel modes (RPM family)
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
            case VARIABLE        -> renderVarConfig(g, mx, my, b, x, y);
            case OVERDRIVE       -> renderOdConfig(g, mx, my, b, x, y);
            default              -> renderChannelConfig(g, mx, my, b, x, y); // channel modes (RPM family)
        }
    }

    // OVERDRIVE config — [mode:20][gap:2][mult:40][gap:2][excl:26] = 90px
    private void renderOdConfig(GuiGraphics g, int mx, int my, LiveControlBinding b, int x, int y) {
        boolean mHov = isIn(mx, my, x, y, 20, ROW_H);
        boolean inv  = b.inverted && b.mode == Mode.HLD;
        g.fill(x, y, x + 20, y + ROW_H, modeBg(mHov, inv));
        drawBorder(g, x, y, 20, ROW_H, modeBorder(inv));
        g.drawCenteredString(font, modeLabel(b), x + 10, y + 4, 0xFFFFFF);
        x += 22;

        boolean vHov = isIn(mx, my, x, y, 40, ROW_H);
        g.fill(x, y, x + 40, y + ROW_H, vHov ? 0xFF402030 : 0xFF2A1525);
        drawBorder(g, x, y, 40, ROW_H, 0xFF884466);
        String mult = formatMultiplier(b.overdriveMultiplier);
        g.drawCenteredString(font, "§d" + mult, x + 20, y + 4, 0xFFFFFF);
        x += 42;

        boolean hasExcl = b.odExcludes != null && !b.odExcludes.isEmpty();
        boolean eHov = isIn(mx, my, x, y, 26, ROW_H);
        g.fill(x, y, x + 26, y + ROW_H, eHov ? 0xFF302030 : (hasExcl ? 0xFF221222 : 0xFF1A1020));
        drawBorder(g, x, y, 26, ROW_H, hasExcl ? 0xFF884488 : 0xFF553355);
        g.drawCenteredString(font, hasExcl ? "§dExcl" : "§8Excl", x + 13, y + 4, 0xFFFFFF);
    }

    private static String formatMultiplier(double m) {
        if (m == (int) m) return ((int) m) + "x";
        return m + "x";
    }

    // REDSTONE config — [side:50][mode:20][signal:20] = 90px
    private void renderRsConfig(GuiGraphics g, int mx, int my, LiveControlBinding b, int x, int y) {
        String sideLabel = b.wirelessFreqIdx > 0 ? "W" + b.wirelessFreqIdx
                : b.rsLinkIdx > 0 ? "L" + b.rsLinkIdx
                : b.rsSide.getSerializedName().toUpperCase();
        boolean sHov = isIn(mx, my, x, y, 50, ROW_H);
        g.fill(x, y, x + 50, y + ROW_H, sHov ? 0xFF252535 : 0xFF1A1A2A);
        drawBorder(g, x, y, 50, ROW_H, 0xFF333355);
        g.drawString(font, "§7◄", x + 1, y + 4, 0x999999, false);
        g.drawCenteredString(font, "§f" + sideLabel, x + 25, y + 4, 0xFFFFFF);
        g.drawString(font, "§7►", x + 41, y + 4, 0x999999, false);
        if (sHov && b.wirelessFreqIdx > 0) {
            String freq = getWirelessFreq(b.wirelessFreqIdx - 1);
            if (freq != null) { wFreqTooltip = freq; wFreqTooltipX = mx; wFreqTooltipY = my; }
        }
        x += 52;

        // Mode button cycles Hld -> Tog -> Inc; right-click on Hld toggles inversion
        boolean mHov = isIn(mx, my, x, y, 20, ROW_H);
        boolean inv  = b.inverted && b.mode == Mode.HLD;
        g.fill(x, y, x + 20, y + ROW_H, modeBg(mHov, inv));
        drawBorder(g, x, y, 20, ROW_H, modeBorder(inv));
        g.drawCenteredString(font, modeLabel(b), x + 10, y + 4, 0xFFFFFF);
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
        boolean inv  = b.inverted && b.mode == Mode.HLD;
        g.fill(x, y, x + 20, y + ROW_H, modeBg(mHov, inv));
        drawBorder(g, x, y, 20, ROW_H, modeBorder(inv));
        g.drawCenteredString(font, modeLabel(b), x + 10, y + 4, 0xFFFFFF);
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

    // Channel-mode config (RPM family) — [ch:40][mode:20][value:28] = 88px.
    // Generic: any registered ChannelMode renders here, value cell driven by the descriptor.
    private void renderChannelConfig(GuiGraphics g, int mx, int my, LiveControlBinding b, int x, int y) {
        ChannelMode m = ChannelMode.byType(b.actionType);

        boolean cHov = isIn(mx, my, x, y, 40, ROW_H);
        g.fill(x, y, x + 40, y + ROW_H, cHov ? 0xFF252535 : 0xFF1A1A2A);
        drawBorder(g, x, y, 40, ROW_H, 0xFF333355);
        g.drawString(font, "§7◄", x + 1, y + 4, 0x999999, false);
        g.drawCenteredString(font, "§fC" + b.channel, x + 20, y + 4, 0xFFFFFF);
        g.drawString(font, "§7►", x + 31, y + 4, 0x999999, false);
        x += 42;

        boolean mHov = isIn(mx, my, x, y, 20, ROW_H);
        boolean inv  = b.inverted && b.mode == Mode.HLD;
        g.fill(x, y, x + 20, y + ROW_H, modeBg(mHov, inv));
        drawBorder(g, x, y, 20, ROW_H, modeBorder(inv));
        g.drawCenteredString(font, modeLabel(b), x + 10, y + 4, 0xFFFFFF);
        x += 22;

        boolean vHov = isIn(mx, my, x, y, 28, ROW_H);
        g.fill(x, y, x + 28, y + ROW_H, vHov ? 0xFF1A3040 : 0xFF111F2A);
        if (b.mode == Mode.INC) {
            drawBorder(g, x, y, 28, ROW_H, b.incPlus ? 0xFF448844 : 0xFF884444);
            g.drawCenteredString(font, b.incPlus ? "§a++" : "§c--", x + 14, y + 4, 0xFFFFFF);
        } else {
            drawBorder(g, x, y, 28, ROW_H, m.valueColor);
            g.drawCenteredString(font, "§b" + m.targetValue(b), x + 14, y + 4, 0xFFFFFF);
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
        boolean inv  = b.inverted && b.mode == Mode.HLD;
        g.fill(x, y, x + 20, y + ROW_H, modeBg(mHov, inv));
        drawBorder(g, x, y, 20, ROW_H, modeBorder(inv));
        g.drawCenteredString(font, modeLabel(b), x + 10, y + 4, 0xFFFFFF);
        x += 22;

        boolean vHov = isIn(mx, my, x, y, 28, ROW_H) || vectorOverlaySlot == idx;
        g.fill(x, y, x + 28, y + ROW_H, vHov ? 0xFF1A3535 : 0xFF111F1F);
        drawBorder(g, x, y, 28, ROW_H, vHov ? 0xFF44AAAA : 0xFF334444);
        double mag = Math.sqrt(b.vectorX * b.vectorX + b.vectorY * b.vectorY);
        String vecLabel = mag < 0.01 ? "§7---" : vecArrow(b.vectorX, b.vectorY);
        g.drawCenteredString(font, vecLabel, x + 14, y + 4, 0xFFFFFF);
    }

    // VARIABLE config — [var:40][mode:20][value:28] = 88px
    private void renderVarConfig(GuiGraphics g, int mx, int my, LiveControlBinding b, int x, int y) {
        boolean vHov = isIn(mx, my, x, y, 40, ROW_H);
        g.fill(x, y, x + 40, y + ROW_H, vHov ? 0xFF353525 : 0xFF2A2A1A);
        drawBorder(g, x, y, 40, ROW_H, 0xFF555533);
        g.drawString(font, "§7◄", x + 1, y + 4, 0x999999, false);
        g.drawCenteredString(font, "§eV" + (b.varIndex + 1), x + 20, y + 4, 0xFFFFFF);
        g.drawString(font, "§7►", x + 31, y + 4, 0x999999, false);
        x += 42;

        boolean mHov = isIn(mx, my, x, y, 20, ROW_H);
        boolean inv  = b.inverted && b.mode == Mode.HLD;
        g.fill(x, y, x + 20, y + ROW_H, modeBg(mHov, inv));
        drawBorder(g, x, y, 20, ROW_H, modeBorder(inv));
        g.drawCenteredString(font, modeLabel(b), x + 10, y + 4, 0xFFFFFF);
        x += 22;

        boolean valHov = isIn(mx, my, x, y, 28, ROW_H);
        g.fill(x, y, x + 28, y + ROW_H, valHov ? 0xFF353520 : 0xFF26260F);
        if (b.mode == Mode.INC) {
            drawBorder(g, x, y, 28, ROW_H, b.incPlus ? 0xFF448844 : 0xFF884444);
            g.drawCenteredString(font, b.incPlus ? "§a++" : "§c--", x + 14, y + 4, 0xFFFFFF);
        } else {
            drawBorder(g, x, y, 28, ROW_H, 0xFF887733);
            g.drawCenteredString(font, "§e" + b.varOnValue, x + 14, y + 4, 0xFFFFFF);
        }
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

    // ── Excl overlay ─────────────────────────────────────────────────────────
    private static final int EXCL_OV_W = 220;
    private static final int EXCL_OV_H = 78;

    private void renderExclOverlay(GuiGraphics g, int mx, int my) {
        int ox = width  / 2 - EXCL_OV_W / 2;
        int oy = height / 2 - EXCL_OV_H / 2;
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(ox, oy, ox + EXCL_OV_W, oy + EXCL_OV_H, 0xFF0D0A14);
        drawBorder(g, ox, oy, EXCL_OV_W, EXCL_OV_H, 0xFF884488);
        g.drawCenteredString(font, "§dExclude Slots from OD", ox + EXCL_OV_W / 2, oy + 4, 0xFFFFFF);
        g.drawCenteredString(font, "§8slot numbers, comma-separated", ox + EXCL_OV_W / 2, oy + 13, 0x888888);

        int inX = ox + 8, inY = oy + 24, inW = EXCL_OV_W - 16, inH = 13;
        g.fill(inX, inY, inX + inW, inY + inH, 0xFF111111);
        drawBorder(g, inX, inY, inW, inH, 0xFF664466);
        g.drawString(font, exclInput + "§7|", inX + 3, inY + 3, 0xFFFFFF, false);

        int btnY = oy + EXCL_OV_H - 16, btnH = 13;
        boolean okH  = isIn(mx, my, ox + 16,            btnY, 72, btnH);
        boolean caH  = isIn(mx, my, ox + EXCL_OV_W - 88, btnY, 72, btnH);
        g.fill(ox + 16,            btnY, ox + 88,            btnY + btnH, okH ? 0xFF2A4A2A : 0xFF1E3A1E);
        g.fill(ox + EXCL_OV_W - 88, btnY, ox + EXCL_OV_W - 16, btnY + btnH, caH ? 0xFF4A2A2A : 0xFF3A1E1E);
        drawBorder(g, ox + 16,            btnY, 72, btnH, 0xFF448844);
        drawBorder(g, ox + EXCL_OV_W - 88, btnY, 72, btnH, 0xFF884444);
        g.drawCenteredString(font, "§aOK",     ox + 52,            btnY + 3, 0xFFFFFF);
        g.drawCenteredString(font, "§cCancel", ox + EXCL_OV_W - 52, btnY + 3, 0xFFFFFF);
        g.pose().popPose();
    }

    private void handleExclOverlayClick(int mx, int my) {
        int ox = width  / 2 - EXCL_OV_W / 2;
        int oy = height / 2 - EXCL_OV_H / 2;
        int btnY = oy + EXCL_OV_H - 16, btnH = 13;
        if (isIn(mx, my, ox + 16, btnY, 72, btnH)) {
            commitExclInput();
        } else {
            exclOverlaySlot = -1;
            exclInput = "";
        }
    }

    private void commitExclInput() {
        if (exclOverlaySlot >= 0 && exclOverlaySlot < bindings.size())
            bindings.get(exclOverlaySlot).odExcludes = normalizeExclInput(exclInput);
        exclOverlaySlot = -1;
        exclInput = "";
    }

    private String normalizeExclInput(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        java.util.LinkedHashSet<Integer> slots = new java.util.LinkedHashSet<>();
        for (String part : raw.split(",")) {
            try {
                int n = Integer.parseInt(part.trim());
                if (n >= 1 && n <= MAX_SLOTS) slots.add(n);
            } catch (NumberFormatException ignored) {}
        }
        StringBuilder sb = new StringBuilder();
        for (int slot : slots) {
            if (sb.length() > 0) sb.append(",");
            sb.append(slot);
        }
        return sb.toString();
    }

    // ── Mouse handling ────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (confirmDialog != null && confirmDialog.isOpen())
            return confirmDialog.mouseClicked(mx, my, btn);

        int imx = (int) mx, imy = (int) my;

        if (exclOverlaySlot >= 0) { handleExclOverlayClick(imx, imy); return true; }
        if (vectorOverlaySlot >= 0) { handleVectorOverlayClick(imx, imy, vectorOverlaySlot, btn); return true; }
        // key bind a mouse click when listening
        if (listeningSlot >= 0 && mouseCaptureArmed && mouseInputEnabled()
                && listeningSlot < bindings.size() && btn < MouseCodes.MAX_BUTTONS) {
            bindings.get(listeningSlot).keyCode = MouseCodes.button(btn);
            listeningSlot = -1;
            return true;
        }
        if (listeningSlot >= 0) listeningSlot = -1;
        int pageStart = page * (ROWS * 2);
        for (int pi = 0; pi < ROWS * 2; pi++) {
            int i = pageStart + pi;
            if (i >= MAX_SLOTS) break;
            int col = pi / ROWS, row = pi % ROWS;
            int rx = panelX + PAD + col * COL_OFFSET;
            int ry = firstRowY + row * (ROW_H + ROW_GAP);
            // click to focus row
            if (isIn(imx, imy, rx, ry, COL_W, ROW_H)) focusedSlot = i;
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
            double rawX = snapToGrid(dx / r, 15);
            double rawY = snapToGrid(dy / r, 15);
            double mag = Math.sqrt(rawX * rawX + rawY * rawY);
            if (mag > 1.0) { rawX /= mag; rawY /= mag; }
            b.vectorX = rawX;
            b.vectorY = rawY;
        }
    }

    private boolean handleSlotClick(int idx, int rx, int ry, int mx, int my, int btn) {
        LiveControlBinding b = bindings.get(idx);

        int kx = rx + NUM_W;
        if (isIn(mx, my, kx, ry, KEY_W, ROW_H)) {
            if (btn == 1) { b.keyCode = -1; listeningSlot = -1; }
            else {
                listeningSlot = (listeningSlot == idx) ? -1 : idx;
                if (listeningSlot >= 0) {
                    GamepadLiveDriver.beginCapture();
                    mouseCaptureArmed = false;
                    lastMoveX = Double.NaN;
                }
            }
            return true;
        }

        int tx = kx + KEY_W + 2;
        if (isIn(mx, my, tx, ry, TYPE_W, ROW_H)) {
            int dir = (btn == 1) ? -1 : 1;
            b.actionType = nextAvailableType(b.actionType, dir);
            // VEC and OD don't support INC — reset to HLD
            if (b.mode == Mode.INC
                    && (b.actionType == ActionType.THRUSTER_VECTOR || b.actionType == ActionType.OVERDRIVE))
                b.mode = Mode.HLD;
            // VARIABLE doesn't support inversion
            if (b.inverted && !canInvert(b.actionType)) b.inverted = false;
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
                    cycleModeOrInvert(b, right); return true;
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
                    cycleModeOrInvert(b, right); return true;
                }
                x += 22;
                if (isIn(mx, my, x, y, 28, ROW_H)) {
                    if (b.mode == Mode.INC) { b.incPlus = !b.incPlus; }
                    else b.powerLevel = stepPower(b.powerLevel, right ? -1 : 1);
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
                    cycleModeOrInvert(b, right); return true;
                }
                x += 22;
                if (isIn(mx, my, x, y, 28, ROW_H)) { vectorOverlaySlot = (vectorOverlaySlot == idx) ? -1 : idx; return true; }
            }
            case VARIABLE -> {
                if (isIn(mx, my, x, y, 40, ROW_H)) {
                    if (mx < x + 12)      b.varIndex = (b.varIndex + 15) % 16;
                    else if (mx > x + 28) b.varIndex = (b.varIndex + 1)  % 16;
                    return true;
                }
                x += 42;
                if (isIn(mx, my, x, y, 20, ROW_H)) {
                    cycleModeOrInvert(b, right); return true;
                }
                x += 22;
                if (isIn(mx, my, x, y, 28, ROW_H)) {
                    if (b.mode == Mode.INC) b.incPlus = !b.incPlus;
                    else b.varOnValue = Math.max(1, Math.min(100, b.varOnValue + (right ? -1 : 1)));
                    return true;
                }
            }
            case OVERDRIVE -> {
                if (isIn(mx, my, x, y, 20, ROW_H)) {
                    cycleModeOrInvert(b, right); return true;
                }
                x += 22;
                if (isIn(mx, my, x, y, 40, ROW_H)) {
                    b.overdriveMultiplier = cycleOverdrive(b.overdriveMultiplier, right ? -1 : 1);
                    return true;
                }
                x += 42;
                if (isIn(mx, my, x, y, 26, ROW_H)) {
                    listeningSlot = -1;
                    vectorOverlaySlot = -1;
                    focusedSlot = -1;
                    exclOverlaySlot = idx;
                    exclInput = b.odExcludes == null ? "" : b.odExcludes;
                    return true;
                }
            }
            default -> { // channel modes (RPM family) — [ch:40][mode:20][value:28]
                ChannelMode m = ChannelMode.byType(b.actionType);
                if (m == null) return false;
                if (isIn(mx, my, x, y, 40, ROW_H)) {
                    if (mx < x + 12) b.channel = b.channel == 1 ? LinkedKeyboardBlockEntity.MAX_CHANNELS : b.channel - 1;
                    else if (mx > x + 28) b.channel = b.channel == LinkedKeyboardBlockEntity.MAX_CHANNELS ? 1 : b.channel + 1;
                    return true;
                }
                x += 42;
                if (isIn(mx, my, x, y, 20, ROW_H)) {
                    cycleModeOrInvert(b, right); return true;
                }
                x += 22;
                if (isIn(mx, my, x, y, 28, ROW_H)) {
                    if (b.mode == Mode.INC) b.incPlus = !b.incPlus;
                    else m.stepTarget(b, right ? -1 : 1);
                    return true;
                }
            }
        }
        return false;
    }

    private static double cycleOverdrive(double current, int dir) {
        double[] vals = LiveControlBinding.OVERDRIVE_VALUES;
        int idx = 0;
        for (int i = 0; i < vals.length; i++) if (vals[i] == current) { idx = i; break; }
        return vals[((idx + dir) % vals.length + vals.length) % vals.length];
    }

    // ── Scroll wheel ──────────────────────────────────────────────────────────
    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        // scroll wheel is also a bindable input now
        if (listeningSlot >= 0 && mouseInputEnabled() && listeningSlot < bindings.size() && dy != 0) {
            bindings.get(listeningSlot).keyCode = dy > 0 ? MouseCodes.SCROLL_UP : MouseCodes.SCROLL_DOWN;
            listeningSlot = -1;
            return true;
        }
        if (dy == 0) return false;
        int dir = dy > 0 ? 1 : -1;

        // scroll behavior only on hovered cell
        int imx = (int) mx, imy = (int) my;
        int pageStart = page * (ROWS * 2);
        for (int pi = 0; pi < ROWS * 2; pi++) {
            int i = pageStart + pi;
            if (i >= MAX_SLOTS) break;
            int col = pi / ROWS, row = pi % ROWS;
            int rx = panelX + PAD + col * COL_OFFSET;
            int ry = firstRowY + row * (ROW_H + ROW_GAP);
            if (!isIn(imx, imy, rx, ry, COL_W, ROW_H)) continue;
            // same idea as the sequencer screen with scrolling being less easy
            if (i == focusedSlot) {
                scrollHoveredCell(i, rx, ry, imx, imy, dir);
            }
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    private void scrollHoveredCell(int idx, int rx, int ry, int mx, int my, int dir) {
        LiveControlBinding b = bindings.get(idx);

        // scroll Type box too
        int tx = rx + NUM_W + KEY_W + 2;
        if (isIn(mx, my, tx, ry, TYPE_W, ROW_H)) {
            b.actionType = nextAvailableType(b.actionType, dir);
            if (b.mode == Mode.INC
                    && (b.actionType == ActionType.THRUSTER_VECTOR || b.actionType == ActionType.OVERDRIVE))
                b.mode = Mode.HLD;
            if (b.inverted && !canInvert(b.actionType)) b.inverted = false;
            return;
        }

        int x = rx + CFG_OFF, y = ry;
        switch (b.actionType) {
            case REDSTONE -> {
                if (isIn(mx, my, x, y, 50, ROW_H)) { cycleSide(b, dir); return; }
                x += 52;
                if (isIn(mx, my, x, y, 20, ROW_H)) { cycleModeScroll(b, dir); return; }
                x += 22;
                if (isIn(mx, my, x, y, 20, ROW_H)) {
                    if (b.mode == Mode.INC) b.incPlus = !b.incPlus;
                    else b.signalStrength = Math.max(0, Math.min(15, b.signalStrength + dir));
                }
            }
            case THRUSTER_POWER -> {
                if (isIn(mx, my, x, y, 40, ROW_H)) { stepChannel(b, dir); return; }
                x += 42;
                if (isIn(mx, my, x, y, 20, ROW_H)) { cycleModeScroll(b, dir); return; }
                x += 22;
                if (isIn(mx, my, x, y, 28, ROW_H)) {
                    if (b.mode == Mode.INC) b.incPlus = !b.incPlus;
                    else b.powerLevel = stepPower(b.powerLevel, dir);
                }
            }
            case THRUSTER_VECTOR -> {
                if (isIn(mx, my, x, y, 40, ROW_H)) { stepChannel(b, dir); return; }
                x += 42;
                if (isIn(mx, my, x, y, 20, ROW_H)) cycleModeScroll(b, dir);

            }
            case VARIABLE -> {
                if (isIn(mx, my, x, y, 40, ROW_H)) { b.varIndex = ((b.varIndex + dir) % 16 + 16) % 16; return; }
                x += 42;
                if (isIn(mx, my, x, y, 20, ROW_H)) { cycleModeScroll(b, dir); return; }
                x += 22;
                if (isIn(mx, my, x, y, 28, ROW_H)) {
                    if (b.mode == Mode.INC) b.incPlus = !b.incPlus;
                    else b.varOnValue = Math.max(1, Math.min(100, b.varOnValue + dir));
                }
            }
            case OVERDRIVE -> {
                if (isIn(mx, my, x, y, 20, ROW_H)) { cycleModeScroll(b, dir); return; }
                x += 22;
                if (isIn(mx, my, x, y, 40, ROW_H))
                    b.overdriveMultiplier = cycleOverdrive(b.overdriveMultiplier, dir);
            }
            default -> { // channel modes (RPM family)
                ChannelMode m = ChannelMode.byType(b.actionType);
                if (m == null) return;
                if (isIn(mx, my, x, y, 40, ROW_H)) { stepChannel(b, dir); return; }
                x += 42;
                if (isIn(mx, my, x, y, 20, ROW_H)) { cycleModeScroll(b, dir); return; }
                x += 22;
                if (isIn(mx, my, x, y, 28, ROW_H)) {
                    if (b.mode == Mode.INC) b.incPlus = !b.incPlus;
                    else m.stepTarget(b, dir);
                }
            }
        }
    }

    private static void stepChannel(LiveControlBinding b, int dir) {
        b.channel += dir;
        if (b.channel < 1) b.channel = LinkedKeyboardBlockEntity.MAX_CHANNELS;
        if (b.channel > LinkedKeyboardBlockEntity.MAX_CHANNELS) b.channel = 1;
    }

    private static void cycleModeScroll(LiveControlBinding b, int dir) {
        b.mode = nextMode(b.actionType, b.mode, dir);
        if (b.mode != Mode.HLD) b.inverted = false;
    }

    private void stepPrimaryField(int idx, int dir) {
        if (idx < 0 || idx >= bindings.size()) return;
        LiveControlBinding b = bindings.get(idx);
        switch (b.actionType) {
            case REDSTONE -> cycleSide(b, dir);
            case VARIABLE -> b.varIndex = ((b.varIndex + dir) % 16 + 16) % 16;
            case OVERDRIVE -> b.overdriveMultiplier = cycleOverdrive(b.overdriveMultiplier, dir);
            default -> stepChannel(b, dir);
        }
    }

    @Override
    public void mouseMoved(double mx, double my) {
        if (listeningSlot >= 0 && mouseCaptureArmed && mouseInputEnabled()
                && listeningSlot < bindings.size()) {
            if (Double.isNaN(lastMoveX)) { lastMoveX = mx; lastMoveY = my; }
            double ddx = mx - lastMoveX, ddy = my - lastMoveY;
            lastMoveX = mx; lastMoveY = my;
            final double FLICK = 10.0;
            if (Math.abs(ddx) >= FLICK || Math.abs(ddy) >= FLICK) {
                int code = (Math.abs(ddx) >= Math.abs(ddy))
                        ? (ddx > 0 ? MouseCodes.AXIS_X_POS : MouseCodes.AXIS_X_NEG)
                        : (ddy > 0 ? MouseCodes.AXIS_Y_POS : MouseCodes.AXIS_Y_NEG);
                bindings.get(listeningSlot).keyCode = code;
                listeningSlot = -1;
            }
        }
        super.mouseMoved(mx, my);
    }

    private static boolean mouseInputEnabled() {
        try { return ModConfig.CLIENT.enableMouseInput.get(); }
        catch (Exception e) { return false; }
    }

    // ── Key handling ──────────────────────────────────────────────────────────
    @Override
    public boolean charTyped(char c, int modifiers) {
        if (exclOverlaySlot >= 0) {
            if ((Character.isDigit(c) || c == ',' || c == ' ') && exclInput.length() < 100)
                exclInput += c;
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmDialog != null && confirmDialog.isOpen())
            return confirmDialog.keyPressed(keyCode);
        if (exclOverlaySlot >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitExclInput();
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                exclOverlaySlot = -1; exclInput = "";
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !exclInput.isEmpty()) {
                exclInput = exclInput.substring(0, exclInput.length() - 1);
            }
            return true;
        }
        if (listeningSlot >= 0) {
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) bindings.get(listeningSlot).keyCode = keyCode;
            listeningSlot = -1;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (vectorOverlaySlot >= 0) { vectorOverlaySlot = -1; return true; }
            onClose(); return true;
        }
        // arrow keys nudge the quantized vector point
        if (vectorOverlaySlot >= 0) {
            final double STEP = 1.0 / 15.0;
            LiveControlBinding vb = bindings.get(vectorOverlaySlot);
            if (keyCode == GLFW.GLFW_KEY_LEFT)  { nudgeVector(vb, -STEP, 0);    return true; }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) { nudgeVector(vb, +STEP, 0);    return true; }
            if (keyCode == GLFW.GLFW_KEY_UP)    { nudgeVector(vb, 0,    +STEP); return true; }
            if (keyCode == GLFW.GLFW_KEY_DOWN)  { nudgeVector(vb, 0,    -STEP); return true; }
        }
        if (MenuNav.handleTabBack(this, keyCode, keyboardPos)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (listeningSlot >= 0 && exclOverlaySlot < 0) {
            mouseCaptureArmed = true;
            int code = GamepadLiveDriver.pollCapture();
            if (code >= 0 && listeningSlot < bindings.size()) {
                bindings.get(listeningSlot).keyCode = code;
                listeningSlot = -1;
            }
        } else {
            mouseCaptureArmed = false;
        }

        // Debounced autosave: flush one tick after the last edit, so continuous
        // edits don't spam a packet every tick.
        if (listsEqual(bindings, lastTickSnapshot)) autosave();
        else lastTickSnapshot = deepCopy(bindings);
    }

    @Override
    public void removed() {
        autosave(); // flush any edit made in the final tick before the menu closed
        super.removed();
    }

    @Override public boolean isPauseScreen() { return false; }

    // ── Type / mode cycling ───────────────────────────────────────────────────
    // Right click on HLD toggles inversion; any other click cycles mode and clears inversion on exit
    private static void cycleModeOrInvert(LiveControlBinding b, boolean right) {
        if (right && b.mode == Mode.HLD && canInvert(b.actionType)) {
            b.inverted = !b.inverted;
        } else {
            b.mode = nextMode(b.actionType, b.mode, right ? -1 : 1);
            if (b.mode != Mode.HLD) b.inverted = false;
        }
    }

    //variable mode cant invert, others might not be able to later
    private static boolean canInvert(ActionType t) {
        return t != ActionType.VARIABLE;
    }
    private boolean isTypeAvailable(ActionType t) {
        return switch (t) {
            case REDSTONE        -> true;
            case THRUSTER_POWER  -> hasThrusters;
            case THRUSTER_VECTOR -> hasVectorThrusters;
            case RPM_CONTROL     -> hasRpm;
            case VARIABLE        -> true;
            case OVERDRIVE       -> true;
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

    /** Cycle through modes. VEC and OD only support HLD and TGL (no INC). */
    private static Mode nextMode(ActionType type, Mode current, int dir) {
        Mode[] available = (type == ActionType.THRUSTER_VECTOR || type == ActionType.OVERDRIVE)
                ? new Mode[]{Mode.HLD, Mode.TGL}
                : Mode.values();
        int idx = 0;
        for (int i = 0; i < available.length; i++) if (available[i] == current) { idx = i; break; }
        return available[((idx + dir) % available.length + available.length) % available.length];
    }

    // ── Side cycling (RS target) ───────────────────────────────────────────────
    private int getWirelessFreqCount() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        var be = mc.level.getBlockEntity(keyboardPos);
        if (be instanceof dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity kb)
            return kb.getWirelessFreqCount();
        return 0;
    }

    private String getWirelessFreq(int zeroBasedIdx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        var be = mc.level.getBlockEntity(keyboardPos);
        if (be instanceof dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity kb)
            return kb.getWirelessFreq(zeroBasedIdx);
        return null;
    }

    private void cycleSide(LiveControlBinding b, int dir) {
        Direction[] dirs = Direction.values();
        int linkCount = getWirelessFreqCount();
        int total = dirs.length + rsLinkCount + linkCount;
        int current;
        if (b.wirelessFreqIdx > 0) current = dirs.length + rsLinkCount + (b.wirelessFreqIdx - 1);
        else if (b.rsLinkIdx > 0) current = dirs.length + (b.rsLinkIdx - 1);
        else current = b.rsSide.ordinal();
        current = ((current + dir) % total + total) % total;
        if (current < dirs.length) {
            b.rsLinkIdx = 0; b.wirelessFreqIdx = 0; b.rsSide = dirs[current];
        } else if (current < dirs.length + rsLinkCount) {
            b.rsLinkIdx = current - dirs.length + 1; b.wirelessFreqIdx = 0;
        } else {
            b.wirelessFreqIdx = current - dirs.length - rsLinkCount + 1; b.rsLinkIdx = 0;
        }
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

    private static double stepPower(double current, int dir) {
        int n = (int) Math.round(current * 15);
        return Math.max(0, Math.min(15, n + dir)) / 15.0;
    }

    private static double snapToGrid(double val, int divisions) {
        return Math.round(val * divisions) / (double) divisions;
    }

    private static void nudgeVector(LiveControlBinding b, double dx, double dy) {
        double nx = Math.max(-1.0, Math.min(1.0, snapToGrid(b.vectorX + dx, 15)));
        double ny = Math.max(-1.0, Math.min(1.0, snapToGrid(b.vectorY + dy, 15)));
        double mag = Math.sqrt(nx * nx + ny * ny);
        if (mag > 1.0) { nx /= mag; ny /= mag; }
        b.vectorX = nx;
        b.vectorY = ny;
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
