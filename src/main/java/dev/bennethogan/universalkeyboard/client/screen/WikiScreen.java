package dev.bennethogan.universalkeyboard.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;


public class WikiScreen extends Screen {

    private static final int PANEL_W    = 380;
    private static final int PANEL_MAX_H = 240;
    private static final int PAD        = 10;
    private static final int LINE_H     = 10;
    private static final int HEADING_H  = 14;
    private static final int IMG_GAP    = 6;
    private static final int TITLE_BAR_H = 16;
    private static final int SIDEBAR_W  = 96;
    private static final int SIDE_ROW_H = 14;
    private static final int SCROLL_SPEED = 12;

    // ── Content definition ───────────────────────────────────────────────────


    private record WikiEntry(String heading, String body, ResourceLocation image, int imageW, int imageH) {
        static WikiEntry heading(String text) { return new WikiEntry(text, null, null, 0, 0); }
        static WikiEntry text(String body)    { return new WikiEntry(null, body, null, 0, 0); }
        static WikiEntry image(ResourceLocation loc, int imageW, int imageH) {
            return new WikiEntry(null, null, loc, imageW, imageH);
        }
    }

    private record WikiPage(String label, List<WikiEntry> entries) {
        static WikiPage of(String label, WikiEntry... entries) {
            return new WikiPage(label, List.of(entries));
        }
    }

    private static final List<WikiPage> PAGES = new ArrayList<>();

    static {
        PAGES.add(WikiPage.of("Overview",
                WikiEntry.heading("Overview"),
                WikiEntry.text("Wiki not done")));

        PAGES.add(WikiPage.of("Keyboard",
                WikiEntry.heading("Keyboard"),
                WikiEntry.text("Wiki not done")));

        PAGES.add(WikiPage.of("Live Control",
                WikiEntry.heading("Live Control"),
                WikiEntry.text("Wiki not done")));

        PAGES.add(WikiPage.of("Sequencer",
                WikiEntry.heading("Sequencer"),
                WikiEntry.text("Wiki not done")));

        PAGES.add(WikiPage.of("Thruster",
                WikiEntry.heading("Thruster Control"),
                WikiEntry.text("Wiki not done")));

        PAGES.add(WikiPage.of("Favorites",
                WikiEntry.heading("Favorites"),
                WikiEntry.text("Wiki not done")));

        PAGES.add(WikiPage.of("Gamepad",
                WikiEntry.heading("Gamepad & Joystick"),
                WikiEntry.text("Wiki not done")));

        PAGES.add(WikiPage.of("Copycats",
                WikiEntry.heading("Copycats"),
                WikiEntry.text("Wiki not done")));
    }

    // -- State ----------------------------

    private final Screen returnScreen;
    private int currentPage = 0;
    private int scrollY = 0;
    private int contentHeight = 0;
    private int panelX, panelY, panelH;


    private final List<int[]> imageHitboxes = new ArrayList<>(); // {x0,y0,x1,y1,entryIndex}


    private WikiEntry expandedImage = null;

    public WikiScreen(Screen returnScreen) {
        super(Component.translatable("gui.universalkeyboard.screen.wiki.title"));
        this.returnScreen = returnScreen;
    }

    @Override
    protected void init() {
        panelX = (width  - PANEL_W) / 2;
        panelH = Math.min(height - 20, PANEL_MAX_H);
        panelY = (height - panelH) / 2;
        clampScroll();
    }

    private List<WikiEntry> entries() { return PAGES.get(currentPage).entries(); }

    private int contentX()      { return panelX + SIDEBAR_W + PAD; }
    private int contentW()      { return PANEL_W - SIDEBAR_W - PAD * 2; }
    private int contentTop()    { return panelY + TITLE_BAR_H + PAD; }
    private int contentBottom() { return panelY + panelH - PAD - 10; }

    private void clampScroll() {
        int avail = contentBottom() - contentTop();
        scrollY = Math.max(0, Math.min(scrollY, Math.max(0, contentHeight - avail)));
    }

    // ---- Render -------------------------------

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // Panel frame
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xEE111111);
        drawBorder(g, panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xFF666666);

        // Title bar
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + TITLE_BAR_H, 0x44FFFFFF);
        g.drawCenteredString(font, getTitle(), panelX + PANEL_W / 2, panelY + 4, 0xFFFFFF);
        g.drawString(font, "§7[Esc]", panelX + PANEL_W - PAD - font.width("§7[Esc]"),
                panelY + 4, 0x888888, false);

        renderSidebar(g, mx, my);
        renderContent(g);

        for (var r : this.renderables) r.render(g, mx, my, pt);

        if (expandedImage != null) renderLightbox(g);
    }

    private void renderSidebar(GuiGraphics g, int mx, int my) {
        int sx0 = panelX;
        int sy0 = panelY + TITLE_BAR_H;
        int sx1 = panelX + SIDEBAR_W;
        int sy1 = panelY + panelH;

        // Sidebar background + divider
        g.fill(sx0, sy0, sx1, sy1, 0x55000000);
        g.fill(sx1 - 1, sy0, sx1, sy1, 0xFF555555);

        int rowY = sy0 + 6;
        for (int i = 0; i < PAGES.size(); i++) {
            int top = rowY + i * SIDE_ROW_H;
            int bot = top + SIDE_ROW_H;
            boolean active  = i == currentPage;
            boolean hovered = mx >= sx0 && mx < sx1 - 1 && my >= top && my < bot;

            if (active)       g.fill(sx0, top, sx1 - 1, bot, 0x55FFCC33);
            else if (hovered) g.fill(sx0, top, sx1 - 1, bot, 0x33FFFFFF);
            if (active)       g.fill(sx0, top, sx0 + 2, bot, 0xFFFFCC33);

            int color = active ? 0xFFDD44 : (hovered ? 0xFFFFFF : 0xBBBBBB);
            g.drawString(font, PAGES.get(i).label(), sx0 + 8, top + 3, color, false);
        }
    }

    private void renderContent(GuiGraphics g) {
        int cx = contentX(), cw = contentW();
        int cTop = contentTop(), cBot = contentBottom();

        g.enableScissor(cx, cTop, cx + cw, cBot);

        imageHitboxes.clear();
        int y = cTop - scrollY;
        contentHeight = 0;
        List<WikiEntry> entries = entries();

        for (int idx = 0; idx < entries.size(); idx++) {
            WikiEntry entry = entries.get(idx);
            if (entry.image() != null) {
                int[] dim = fitImage(entry, cw);
                int dw = dim[0], dh = dim[1];
                if (y + dh > cTop && y < cBot) {
                    g.blit(entry.image(), cx, y, 0, 0, dw, dh, dw, dh);
                    drawBorder(g, cx, y, cx + dw, y + dh, 0xFF777777);
                }
                imageHitboxes.add(new int[]{cx, y, cx + dw, y + dh, idx});
                y += dh + IMG_GAP;
                contentHeight += dh + IMG_GAP;
            } else if (entry.heading() != null) {
                y += 4;
                if (y > cTop - HEADING_H && y < cBot)
                    g.drawString(font, "§e§l" + entry.heading(), cx, y, 0xFFDD44, false);
                y += HEADING_H;
                if (y > cTop - 2 && y < cBot)
                    g.fill(cx, y, cx + cw, y + 1, 0xFF555533);
                y += 4;
                contentHeight += 4 + HEADING_H + 4;
            } else if (entry.body() != null) {
                List<FormattedCharSequence> lines = font.split(Component.literal(entry.body()), cw);
                for (FormattedCharSequence line : lines) {
                    if (y > cTop - LINE_H && y < cBot)
                        g.drawString(font, line, cx, y, 0xCCCCCC, false);
                    y += LINE_H;
                    contentHeight += LINE_H;
                }
                y += 4;
                contentHeight += 4;
            }
        }

        g.disableScissor();

        // Scroll indicator
        int availH = cBot - cTop;
        if (contentHeight > availH) {
            int barH = Math.max(16, availH * availH / contentHeight);
            int barY = cTop + (int) ((long) scrollY * (availH - barH) / (contentHeight - availH));
            g.fill(panelX + PANEL_W - 3, cTop, panelX + PANEL_W - 1, cBot, 0x22FFFFFF);
            g.fill(panelX + PANEL_W - 3, barY, panelX + PANEL_W - 1, barY + barH, 0x99AAAAAA);
        }
    }

    private void renderLightbox(GuiGraphics g) {
        g.fill(0, 0, width, height, 0xCC000000);

        int maxW = width  - 40;
        int maxH = height - 40;
        double scale = Math.min((double) maxW / expandedImage.imageW(),
                                (double) maxH / expandedImage.imageH());
        int dw = (int) (expandedImage.imageW() * scale);
        int dh = (int) (expandedImage.imageH() * scale);
        int x = (width - dw) / 2;
        int y = (height - dh) / 2;

        g.blit(expandedImage.image(), x, y, 0, 0, dw, dh, dw, dh);
        drawBorder(g, x - 1, y - 1, x + dw + 1, y + dh + 1, 0xFFAAAAAA);
        g.drawCenteredString(font, "§7[Esc] or [Tab] to return", width / 2, y + dh + 6, 0xAAAAAA);
    }


    private int[] fitImage(WikiEntry entry, int maxW) {
        int nw = Math.max(1, entry.imageW());
        int nh = Math.max(1, entry.imageH());
        int dw = Math.min(maxW, nw);
        int dh = dw * nh / nw;
        return new int[]{dw, dh};
    }

    private static void drawBorder(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    // ----- Input ------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        if (expandedImage != null) { expandedImage = null; return true; }

        // Sidebar page selection
        int sx0 = panelX, sx1 = panelX + SIDEBAR_W - 1;
        int rowY = panelY + TITLE_BAR_H + 6;
        if (mx >= sx0 && mx < sx1 && my >= rowY) {
            int row = (int) ((my - rowY) / SIDE_ROW_H);
            if (row >= 0 && row < PAGES.size()) {
                if (row != currentPage) { currentPage = row; scrollY = 0; }
                return true;
            }
        }

        // Image click -> expand into lightbox
        for (int[] box : imageHitboxes) {
            if (mx >= box[0] && mx < box[2] && my >= box[1] && my < box[3]) {
                expandedImage = entries().get(box[4]);
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double dY) {
        if (expandedImage != null) return true;
        int maxScroll = Math.max(0, contentHeight - (contentBottom() - contentTop()));
        this.scrollY = (int) Math.max(0, Math.min(maxScroll, this.scrollY - dY * SCROLL_SPEED));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Esc and Tab both return to the wiki rather than closing the screen
        if (expandedImage != null
                && (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                 || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB)) {
            expandedImage = null;
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(returnScreen);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
