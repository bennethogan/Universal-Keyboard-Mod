package dev.bennethogan.universalkeyboard.client.screen;

import dev.bennethogan.universalkeyboard.compat.KeyboardMode;
import dev.bennethogan.universalkeyboard.compat.PeripheralHelper;
import dev.bennethogan.universalkeyboard.compat.wireless.WirelessPresence;
import dev.bennethogan.universalkeyboard.item.ModItems;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;


public class ModeSelectionScreen extends Screen {

    public enum Page { ROOT, SETUP, OTHER, RESET }

    private static Page parentOf(Page p) {
        return switch (p) {
            case ROOT  -> Page.ROOT;
            case SETUP -> Page.ROOT;
            case OTHER -> Page.ROOT;
            case RESET -> Page.SETUP;
        };
    }

    private static final int PAD     = 12;
    private static final int TITLE_H = 20;
    private static final int BOX     = 70;
    private static final int GAP     = 10;

    private static final int BORDER       = 0xFF555555;
    private static final int BORDER_HOVER = 0xFF999999;
    private static final int BORDER_OFF   = 0xFF333333;
    private static final int BORDER_DANGER= 0xFFCC4444;

    private final BlockPos keyboardPos;
    private final String   targetTypeName;
    private final int      availableBits;

    private Page page;
    private int  panelX, panelY, panelW, panelH;
    private final List<Box> boxes = new ArrayList<>();

    private ConfirmDialog confirmDialog;

    public ModeSelectionScreen(BlockPos keyboardPos, String targetTypeName, int availableBits) {
        this(keyboardPos, targetTypeName, availableBits, Page.ROOT);
    }

    public ModeSelectionScreen(BlockPos keyboardPos, String targetTypeName, int availableBits, Page initialPage) {
        super(Component.empty());
        this.keyboardPos    = keyboardPos;
        this.targetTypeName = targetTypeName;
        this.availableBits  = availableBits;
        this.page           = initialPage;
        MenuNav.remember(keyboardPos, targetTypeName, availableBits);
    }

    // ── A single clickable box ─────────────────────────────────────────────────

    private enum IconKind { GAMEPAD, COMPUTER, WIFI, ARROW, TRASH, ITEM, TEXT }

    private static final class Box {
        int x, y, w, h;
        IconKind kind;
        ItemStack item = ItemStack.EMPTY;
        boolean wifiBadge;
        String label;
        boolean enabled = true;
        int accent = BORDER;
        List<Component> tooltip = List.of();
        Runnable onClick = () -> {};
    }

    // ── Layout ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        confirmDialog = new ConfirmDialog(font);
        relayout();
    }

    private void setPage(Page p) {
        page = p;
        MenuNav.returnPage = p;
        relayout();
    }

    private void relayout() {
        boxes.clear();
        buildBoxes();

        int cols = switch (page) {
            case SETUP -> 3;
            case RESET -> 2;
            default    -> 2;
        };
        int n    = boxes.size();
        int rows = Math.max(1, (n + cols - 1) / cols);

        int gridW = cols * BOX + (cols - 1) * GAP;
        int gridH = rows * BOX + (rows - 1) * GAP;
        panelW = gridW + PAD * 2;
        panelH = PAD + TITLE_H + gridH + PAD;
        panelX = (width  - panelW) / 2;
        panelY = (height - panelH) / 2;

        int gridX = panelX + PAD;
        int gridY = panelY + PAD + TITLE_H;
        for (int i = 0; i < boxes.size(); i++) {
            Box b = boxes.get(i);
            int r = i / cols, c = i % cols;
            b.x = gridX + c * (BOX + GAP);
            b.y = gridY + r * (BOX + GAP);
            b.w = BOX;
            b.h = BOX;
        }

        if (confirmDialog != null) confirmDialog.setParentBounds(panelX, panelY, panelW, panelH);
    }

    private void buildBoxes() {
        switch (page) {
            case ROOT  -> buildRoot();
            case SETUP -> buildSetup();
            case OTHER -> buildOther();
            case RESET -> buildReset();
        }
    }

    private Box add() {
        Box b = new Box();
        boxes.add(b);
        return b;
    }

    private void buildRoot() {
        // Live Controller
        Box live = add();
        live.kind = IconKind.GAMEPAD;
        live.tooltip = List.of(Component.translatable("gui.universalkeyboard.box.live_controller"));
        live.onClick = () -> { MenuNav.returnPage = Page.ROOT; ModPackets.sendOpenLiveControl(keyboardPos); onClose(); };

        // Sequencer (PERIPHERAL_SEQUENCER)
        Box seq = add();
        seq.kind = IconKind.COMPUTER;
        boolean seqOk = bitAvailable(KeyboardMode.PERIPHERAL_SEQUENCER);
        seq.enabled = seqOk;
        if (seqOk) {
            seq.tooltip = List.of(Component.translatable("gui.universalkeyboard.box.sequencer"));
            seq.onClick = () -> { MenuNav.returnPage = Page.ROOT; selectMode(KeyboardMode.PERIPHERAL_SEQUENCER); };
        } else {
            seq.tooltip = unavailableTooltip(KeyboardMode.PERIPHERAL_SEQUENCER);
        }

        // Setup folder
        Box setup = add();
        setup.kind = IconKind.WIFI;
        setup.tooltip = List.of(Component.translatable("gui.universalkeyboard.box.setup"));
        setup.onClick = () -> setPage(Page.SETUP);

        // Other Modes folder
        Box other = add();
        other.kind = IconKind.ARROW;
        other.tooltip = List.of(Component.translatable("gui.universalkeyboard.box.other_modes"));
        other.onClick = () -> setPage(Page.OTHER);
    }

    private void buildSetup() {
        boolean create = WirelessPresence.isPresent();

        // Redstone Links (Create wireless link entries, L-channels)
        Box link = add();
        if (create) {
            link.kind = IconKind.ITEM;
            link.item = stackOf("create", "redstone_link");
            link.tooltip = List.of(Component.translatable("gui.universalkeyboard.box.redstone_links"));
            link.onClick = () -> { MenuNav.returnPage = Page.SETUP; ModPackets.sendOpenWirelessConfig(keyboardPos); onClose(); };
        } else {
            link.kind = IconKind.WIFI;
            link.enabled = false;
            link.tooltip = List.of(Component.translatable("gui.universalkeyboard.tooltip.need_create"));
        }

        // Wireless Copycat (keyboard W-channels)
        Box copy = add();
        if (create) {
            copy.kind = IconKind.ITEM;
            copy.item = ModItems.WIRELESS_COPYCAT != null
                    ? new ItemStack(ModItems.WIRELESS_COPYCAT.get()) : ItemStack.EMPTY;
            copy.wifiBadge = true;
            copy.tooltip = List.of(Component.translatable("gui.universalkeyboard.box.wireless_copycat"));
            copy.onClick = () -> { MenuNav.returnPage = Page.SETUP; ModPackets.sendRequestLinkFreqScreen(keyboardPos); onClose(); };
        } else {
            copy.kind = IconKind.WIFI;
            copy.enabled = false;
            copy.tooltip = List.of(Component.translatable("gui.universalkeyboard.tooltip.need_create"));
        }

        // Reset Data
        Box reset = add();
        reset.kind = IconKind.TRASH;
        reset.tooltip = List.of(Component.translatable("gui.universalkeyboard.box.reset_data"));
        reset.onClick = () -> setPage(Page.RESET);
    }

    private void buildOther() {
        addModeBox(KeyboardMode.THRUSTER_CONTROL);
        addModeBox(KeyboardMode.VALUE_PANEL);
        addModeBox(KeyboardMode.CC_PERIPHERAL);
        addModeBox(KeyboardMode.CC_COMPUTER);
    }

    private void addModeBox(KeyboardMode mode) {
        Box b = add();
        b.kind  = IconKind.TEXT;
        b.label = I18n.get(mode.displayName);
        boolean ok = bitAvailable(mode);
        b.enabled = ok;
        if (ok) {
            b.tooltip = List.of(Component.translatable(mode.description));
            b.onClick = () -> { MenuNav.returnPage = Page.OTHER; selectMode(mode); };
        } else {
            b.tooltip = unavailableTooltip(mode);
        }
    }

    private void buildReset() {
        Box del = add();
        del.kind = IconKind.TEXT;
        del.label = I18n.get("gui.universalkeyboard.btn.delete_all_data");
        del.accent = BORDER_DANGER;
        del.tooltip = List.of(Component.translatable("gui.universalkeyboard.dialog.delete_all_body"));
        del.onClick = () -> confirmDialog.open(
                "gui.universalkeyboard.dialog.delete_all_title",
                "gui.universalkeyboard.dialog.delete_all_body",
                "gui.universalkeyboard.btn.yes_reset",
                this::doDeleteAll);

        Box links = add();
        links.kind = IconKind.TEXT;
        links.label = I18n.get("gui.universalkeyboard.btn.reset_links");
        links.accent = BORDER_DANGER;
        links.tooltip = List.of(Component.translatable("gui.universalkeyboard.dialog.reset_links_body"));
        links.onClick = () -> confirmDialog.open(
                "gui.universalkeyboard.dialog.reset_links_title",
                "gui.universalkeyboard.dialog.reset_links_body",
                "gui.universalkeyboard.btn.yes_reset",
                this::doResetLinks);
    }

    // ── Helpers for mode availability ────────────────────────────────────────────

    private boolean bitAvailable(KeyboardMode mode) {
        return (availableBits & (1 << mode.ordinal())) != 0;
    }

    private List<Component> unavailableTooltip(KeyboardMode mode) {
        boolean ccMissing = !PeripheralHelper.isCCPresent() && mode.requiresCC();
        List<Component> lines = new ArrayList<>();
        if (ccMissing) {
            lines.add(Component.translatable("gui.universalkeyboard.msg.cc_not_installed"));
            lines.add(Component.translatable("gui.universalkeyboard.tooltip.cc_modrinth_1"));
            lines.add(Component.translatable("gui.universalkeyboard.tooltip.cc_modrinth_2"));
        } else {
            lines.add(Component.translatable(mode.unavailableReason()));
        }
        return lines;
    }

    private static ItemStack stackOf(String ns, String path) {
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(ns, path);
        return BuiltInRegistries.ITEM.getOptional(rl).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void doDeleteAll() { ModPackets.sendUnlinkKeyboard(keyboardPos); onClose(); }
    private void doResetLinks() { ModPackets.sendResetLinks(keyboardPos); onClose(); }
    private void selectMode(KeyboardMode mode) { ModPackets.sendSelectMode(keyboardPos, mode); onClose(); }

    private String pageTitle() {
        return switch (page) {
            case ROOT  -> I18n.get("gui.universalkeyboard.screen.mode_selection.title");
            case SETUP -> I18n.get("gui.universalkeyboard.box.setup");
            case OTHER -> I18n.get("gui.universalkeyboard.box.other_modes");
            case RESET -> I18n.get("gui.universalkeyboard.box.reset_data");
        };
    }

    // ── Render ──────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF111111);
        drawBorder(g, panelX, panelY, panelW, panelH, 0xFF666666);

        g.drawCenteredString(font, pageTitle(), panelX + panelW / 2, panelY + PAD - 2, 0xFFFFFF);
        if (page != Page.ROOT)
            g.drawString(font, "§8< Tab", panelX + PAD - 4, panelY + PAD - 2, 0x666666, false);

        List<Component> hoverTip = null;
        for (Box b : boxes) {
            boolean hov = inBox(b, mx, my);
            int border = !b.enabled ? BORDER_OFF : (hov ? BORDER_HOVER : b.accent);
            int bg     = (b.enabled && hov) ? 0xFF24242E : 0xFF1A1A1A;
            g.fill(b.x, b.y, b.x + b.w, b.y + b.h, bg);
            drawBorder(g, b.x, b.y, b.w, b.h, border);

            drawIcon(g, b);

            if (hov && !b.tooltip.isEmpty()) hoverTip = b.tooltip;
        }

        if (confirmDialog != null) confirmDialog.render(g, mx, my);
        if (hoverTip != null && (confirmDialog == null || !confirmDialog.isOpen()))
            g.renderComponentTooltip(font, hoverTip, mx, my);
    }

    private void drawIcon(GuiGraphics g, Box b) {
        int cx = b.x + b.w / 2;
        int cy = b.y + b.h / 2;
        boolean on = b.enabled;
        switch (b.kind) {
            case GAMEPAD  -> MenuGlyphs.gamepad(g, cx, cy, on);
            case COMPUTER -> MenuGlyphs.computer(g, cx, cy, on);
            case WIFI     -> MenuGlyphs.wifi(g, cx, cy, on);
            case ARROW    -> MenuGlyphs.arrowRight(g, cx, cy, on);
            case TRASH    -> ModIcons.TRASH.renderCentered(g, cx, cy, on);
            case ITEM     -> {
                MenuGlyphs.item(g, b.item, cx, cy + (b.wifiBadge ? 4 : 0), 2.0f);
                if (b.wifiBadge) MenuGlyphs.wifiBadge(g, cx + 14, b.y + 14, on);
            }
            case TEXT     -> {
                int color = on ? 0xFFFFFF : 0xFF777777;
                drawWrapped(g, b.label, cx, cy, b.w - 8, color);
            }
        }
    }

    private void drawWrapped(GuiGraphics g, String text, int cx, int cy, int maxW, int color) {
        List<net.minecraft.util.FormattedCharSequence> lines =
                font.split(Component.literal(text), maxW);
        int total = lines.size() * font.lineHeight;
        int y = cy - total / 2;
        for (var line : lines) {
            int w = font.width(line);
            g.drawString(font, line, cx - w / 2, y, color, false);
            y += font.lineHeight;
        }
    }

    private boolean inBox(Box b, int mx, int my) {
        return mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h;
    }

    // ── Input ───────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        if (confirmDialog != null && confirmDialog.isOpen())
            return confirmDialog.mouseClicked(mx, my, btn);
        for (Box b : boxes) {
            if (b.enabled && inBox(b, (int) mx, (int) my)) { b.onClick.run(); return true; }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmDialog != null && confirmDialog.isOpen()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) return confirmDialog.keyPressed(keyCode);
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        // Tab (and Esc on a sub-page) walk back up one layer; Esc at ROOT closes.
        if (keyCode == GLFW.GLFW_KEY_TAB || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (page != Page.ROOT) { setPage(parentOf(page)); return true; }
            if (keyCode == GLFW.GLFW_KEY_TAB) { onClose(); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
