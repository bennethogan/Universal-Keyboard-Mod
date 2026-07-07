package dev.bennethogan.universalkeyboard.client.screen;

import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.universalkeyboard.compat.CreateValueHelper;
import dev.bennethogan.universalkeyboard.compat.MonitorHelper;
import dev.bennethogan.universalkeyboard.compat.PeripheralHelper;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


/**
 * Peripheral Manager screen is going to be expanded more, but will be the dedicated overview
 * and managing area for peripheral connections. Click a row to expand enables adding lots of
 * features to it later. Linking mode is confusing right nowm and will be improved later.
 *
 * This is all purely client-side, so the block names and connection types are being read live
 * from the world, and will show "unloaded block" if the chunk isnt loaded
 */
public class PeripheralManagerScreen extends Screen {

    private static final int PANEL_W = 240;
    private static final int PANEL_H = 220;
    private static final int PAD     = 10;
    private static final int BTN_H   = 18;
    private static final int ROW_H   = 16;
    private static final int LABEL_W = 56; // width reserved for the C#N# tag column

    private static final int COL_TAG        = 0xFFFFD700; // gold
    private static final int COL_TAG_VP     = 0xFFFF4DB3; // pink (for value-panel only connections cause that matches linking view)
    private static final int COL_NAME       = 0xFFFFFFFF;
    private static final int COL_NAME_OFF   = 0xFF888888;
    private static final int COL_DETAIL     = 0xFFBBBBBB;
    private static final int COL_DETAIL_DIM = 0xFF888888;

    private final BlockPos keyboardPos;
    private final Screen   parent;
    private int panelX, panelY;
    private int listTop, listBottom;

    private final List<Entry> entries = new ArrayList<>();
    private int expanded = -1; // index into entries, or -1
    private int scrollY  = 0;

    private static final int EJECT_W = 44;   // vista cassette eject

    private record Entry(int channel, int indexInChannel, BlockPos pos) {
        String tag() { return "C" + channel + "N" + indexInChannel; }
    }

    public PeripheralManagerScreen(BlockPos keyboardPos, Screen parent) {
        super(Component.translatable("gui.universalkeyboard.screen.peripheral_manager.title"));
        this.keyboardPos = keyboardPos;
        this.parent      = parent;
    }

    @Override
    protected void init() {
        panelX = (width  - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        listTop    = panelY + PAD + 14;
        listBottom = panelY + PANEL_H - PAD - BTN_H - 4;

        addRenderableWidget(DarkButton.make(Component.literal("?"),
                Component.translatable("gui.universalkeyboard.tooltip.wiki"),
                b -> Minecraft.getInstance().setScreen(new WikiScreen(this)),
                panelX + PANEL_W - PAD - BTN_H, panelY + 4, BTN_H, BTN_H));

        addRenderableWidget(IconButton.make(ModIcons.TAB_BACK,
                Component.translatable("gui.universalkeyboard.tooltip.back"),
                b -> onClose(),
                panelX + PAD, panelY + PANEL_H - PAD - BTN_H, BTN_H));

        rebuildEntries();
    }

    private boolean isViewfinderEntry(Level level, BlockPos pos) {
        return isLoaded(level, pos)
                && dev.bennethogan.universalkeyboard.compat.VistaCamera.isViewfinder(level.getBlockEntity(pos));
    }

    private void rebuildEntries() {
        entries.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockEntity be = mc.level.getBlockEntity(keyboardPos);
        if (!(be instanceof LinkedKeyboardBlockEntity kb)) return;

        // Sorted by channel so the C#N# ordering is stable and ascending
        Map<Integer, List<BlockPos>> sorted = new TreeMap<>(kb.getAllChannelTargets());
        for (Map.Entry<Integer, List<BlockPos>> e : sorted.entrySet()) {
            List<BlockPos> positions = e.getValue();
            if (positions == null || positions.isEmpty()) continue;
            int ch = e.getKey();
            for (int i = 0; i < positions.size(); i++) {
                entries.add(new Entry(ch, i + 1, positions.get(i)));
            }
        }
        if (expanded >= entries.size()) expanded = -1;
    }

    // World info (read live each frame) -----

    private boolean isLoaded(Level level, BlockPos pos) {
        return level != null && level.isLoaded(pos);
    }

    private String blockName(Level level, BlockPos pos) {
        if (!isLoaded(level, pos)) return I18n.get("gui.universalkeyboard.peripheral_manager.unloaded");
        return level.getBlockState(pos).getBlock().getName().getString();
    }

    // -1 unknown/unloaded, 0 none, 1 value-panel only, 2 CC peripheral, 3 both
    private int connectionType(Level level, BlockPos pos) {
        if (!isLoaded(level, pos)) return -1;
        boolean cc = PeripheralHelper.hasPeripheral(level, pos);
        boolean vp = CreateValueHelper.hasScrollValue(level.getBlockEntity(pos));
        if (cc && vp) return 3;
        if (cc)       return 2;
        if (vp)       return 1;
        return 0;
    }

    private String connectionLabel(int type) {
        return switch (type) {
            case 3  -> I18n.get("gui.universalkeyboard.peripheral_manager.conn_both");
            case 2  -> I18n.get("gui.universalkeyboard.peripheral_manager.conn_cc");
            case 1  -> I18n.get("gui.universalkeyboard.peripheral_manager.conn_vp");
            case 0  -> I18n.get("gui.universalkeyboard.peripheral_manager.conn_none");
            default -> I18n.get("gui.universalkeyboard.peripheral_manager.unloaded");
        };
    }

    private String specialCompat(Level level, BlockPos pos) {
        if (!isLoaded(level, pos)) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (dev.bennethogan.universalkeyboard.compat.VistaCamera.isViewfinder(be))
            return I18n.get("gui.universalkeyboard.peripheral_manager.compat_vista");
        if (MonitorHelper.isMonitor(be))
            return I18n.get("gui.universalkeyboard.peripheral_manager.compat_typing");
        if (PeripheralHelper.isCCPresent()) {
            Object p = PeripheralHelper.getPeripheral(level, pos);
            if (p != null) {
                String type = PeripheralHelper.getPeripheralType(p);
                if (PeripheralHelper.isThrusterType(type)) {
                    return type.contains("vector")
                            ? I18n.get("gui.universalkeyboard.peripheral_manager.compat_thr_vec")
                            : I18n.get("gui.universalkeyboard.peripheral_manager.compat_thr");
                }
                if (PeripheralHelper.isRpmCapable(p)) {
                    return I18n.get("gui.universalkeyboard.peripheral_manager.compat_rpm");
                }
            }
        }
        return I18n.get("gui.universalkeyboard.peripheral_manager.compat_none");
    }

    private int rowHeight(int i) {
        return ROW_H + (i == expanded ? expandedExtra() : 0);
    }

    private int expandedExtra() {
        // for now theres just 3 detail lines
        return font.lineHeight * 3 + 8;
    }

    private int totalContentHeight() {
        int h = 0;
        for (int i = 0; i < entries.size(); i++) h += rowHeight(i);
        return h;
    }

    private int viewportH() { return listBottom - listTop; }

    private int maxScroll() { return Math.max(0, totalContentHeight() - viewportH()); }

    // Render--------------------------------------

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        rebuildEntries();   // keep the list live so links/unlinks reflect after sync
        renderBackground(g, mx, my, pt);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFF111111);
        drawBorder(g, panelX, panelY, PANEL_W, PANEL_H, 0xFF666666);

        g.drawCenteredString(font, I18n.get("gui.universalkeyboard.screen.peripheral_manager.title"),
                panelX + PANEL_W / 2, panelY + PAD - 2, 0xFFFFFF);

        scrollY = Math.max(0, Math.min(scrollY, maxScroll()));

        if (entries.isEmpty()) {
            g.drawCenteredString(font, I18n.get("gui.universalkeyboard.peripheral_manager.empty"),
                    panelX + PANEL_W / 2, listTop + viewportH() / 2 - 4, 0xFF888888);
            for (var r : this.renderables) r.render(g, mx, my, pt);
            return;
        }

        Level level = Minecraft.getInstance().level;

        g.enableScissor(panelX + 1, listTop, panelX + PANEL_W - 1, listBottom);
        int y = listTop - scrollY;
        for (int i = 0; i < entries.size(); i++) {
            int rh = rowHeight(i);
            // cull rows fully outside the viewport
            if (y + rh >= listTop && y <= listBottom) {
                renderRow(g, level, i, y, mx, my);
            }
            y += rh;
        }
        g.disableScissor();

        // scroll hint
        if (maxScroll() > 0) {
            String arrows = (scrollY > 0 ? "▲ " : "  ") + (scrollY < maxScroll() ? "▼" : " ");
            g.drawString(font, arrows, panelX + PANEL_W - PAD - 14, listBottom + 2, 0xFF666666, false);
        }

        for (var r : this.renderables) r.render(g, mx, my, pt);
    }

    private void renderRow(GuiGraphics g, Level level, int i, int y, int mx, int my) {
        Entry e = entries.get(i);
        int rowX = panelX + PAD;
        int rowW = PANEL_W - PAD * 2;

        boolean headerHov = mx >= rowX && mx < rowX + rowW
                && my >= y && my < y + ROW_H
                && my >= listTop && my < listBottom;

        int type = connectionType(level, e.pos());
        boolean vpOnly = type == 1;

        // header background
        int bg = (i == expanded) ? 0xFF24242E : (headerHov ? 0xFF1F1F28 : 0xFF181818);
        g.fill(rowX, y + 1, rowX + rowW, y + ROW_H - 1, bg);

        // number tag
        g.drawString(font, e.tag(), rowX + 3, y + 4, vpOnly ? COL_TAG_VP : COL_TAG, false);

        // block name
        String name = blockName(level, e.pos());
        int nameX = rowX + 3 + LABEL_W;
        int avail = rowW - 6 - LABEL_W;
        String shown = trimToWidth(name, avail);
        int nameColor = isLoaded(level, e.pos()) ? COL_NAME : COL_NAME_OFF;
        g.drawString(font, shown, nameX, y + 4, nameColor, false);

        // expanded detail
        if (i == expanded) {
            int dy = y + ROW_H + 2;
            String posLine = I18n.get("gui.universalkeyboard.peripheral_manager.pos",
                    e.pos().getX() + ", " + e.pos().getY() + ", " + e.pos().getZ());
            g.drawString(font, posLine, rowX + 10, dy, COL_DETAIL, false);
            dy += font.lineHeight;
            String connLine = I18n.get("gui.universalkeyboard.peripheral_manager.conn",
                    connectionLabel(type));
            int connColor = type == -1 ? COL_DETAIL_DIM : COL_DETAIL;
            g.drawString(font, connLine, rowX + 10, dy, connColor, false);
            dy += font.lineHeight;
            String compat = specialCompat(level, e.pos());
            if (compat != null) {
                String compatLine = I18n.get("gui.universalkeyboard.peripheral_manager.compat", compat);
                g.drawString(font, compatLine, rowX + 10, dy, COL_DETAIL, false);
            }
            // Vista cassette eject button
            if (isViewfinderEntry(level, e.pos())) {
                int ex = rowX + rowW - EJECT_W - 2, ey = y + ROW_H + 3, eh = 14;
                boolean eHov = mx >= ex && mx < ex + EJECT_W && my >= ey && my < ey + eh
                        && my >= listTop && my < listBottom;
                g.fill(ex, ey, ex + EJECT_W, ey + eh, eHov ? 0xFF553333 : 0xFF3A2222);
                drawBorder(g, ex, ey, EJECT_W, eh, 0xFF885555);
                g.drawCenteredString(font,
                        I18n.get("gui.universalkeyboard.peripheral_manager.eject"),
                        ex + EJECT_W / 2, ey + 3, 0xFFDDAAAA);
            }
        }
    }

    private String trimToWidth(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        String ell = "…";
        while (!s.isEmpty() && font.width(s + ell) > maxW) s = s.substring(0, s.length() - 1);
        return s + ell;
    }

    //Input ----------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && my >= listTop && my < listBottom) {
            Level level = Minecraft.getInstance().level;
            int rowX = panelX + PAD, rowW = PANEL_W - PAD * 2;
            int y = listTop - scrollY;
            for (int i = 0; i < entries.size(); i++) {
                int rh = rowHeight(i);
                Entry e = entries.get(i);
                // eject button for vista cassette
                if (i == expanded && level != null && isViewfinderEntry(level, e.pos())) {
                    int ex = rowX + rowW - EJECT_W - 2, ey = y + ROW_H + 3, eh = 14;
                    if (mx >= ex && mx < ex + EJECT_W && my >= ey && my < ey + eh) {
                        ModPackets.sendUnlinkTarget(keyboardPos, e.channel(), e.pos());
                        expanded = -1;
                        return true;
                    }
                }
                if (my >= y && my < y + ROW_H
                        && mx >= rowX && mx < panelX + PANEL_W - PAD) {
                    expanded = (expanded == i) ? -1 : i;
                    return true;
                }
                y += rh;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (maxScroll() > 0) {
            scrollY = Math.max(0, Math.min(maxScroll(), scrollY - (int) (dy * 12)));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
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
}
