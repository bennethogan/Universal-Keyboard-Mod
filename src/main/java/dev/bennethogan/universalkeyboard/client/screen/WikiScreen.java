package dev.bennethogan.universalkeyboard.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


public class WikiScreen extends Screen {

    private static final int PANEL_W     = 380;
    private static final int PANEL_MAX_H = 240;
    private static final int PAD         = 10;
    private static final int LINE_H      = 10;
    private static final int HEADING_H   = 14;
    private static final int IMG_GAP     = 6;
    private static final int TITLE_BAR_H = 16;
    private static final int SIDEBAR_W   = 96;
    private static final int SIDE_ROW_H  = 14;
    private static final int SCROLL_SPEED = 12;
    private static final int TOOLTIP_W   = 180;

    // ── Content definition ───────────────────────────────────────────────────

    private record WikiHotspot(float nx0, float ny0, float nx1, float ny1, String title, String body) {}

    private record WikiEntry(String heading, String body, ResourceLocation image,
                             int imageW, int imageH, float scale, List<WikiHotspot> hotspots) {
        static WikiEntry heading(String text) {
            return new WikiEntry(text, null, null, 0, 0, 1.0f, List.of());
        }
        static WikiEntry text(String body, float scale) {
            return new WikiEntry(null, body, null, 0, 0, scale, List.of());
        }
        static WikiEntry image(ResourceLocation loc, int w, int h, List<WikiHotspot> hotspots) {
            return new WikiEntry(null, null, loc, w, h, 1.0f, hotspots);
        }
    }

    private record WikiPage(String label, List<WikiEntry> entries) {}

    // ── State ────────────────────────────────────────────────────────────────

    private final Screen returnScreen;
    private final List<WikiPage> pages = new ArrayList<>();
    private int currentPage = 0;
    private int scrollY = 0;
    private int contentHeight = 0;
    private int panelX, panelY, panelH;

    private final List<int[]> imageHitboxes   = new ArrayList<>(); // {x0,y0,x1,y1,entryIdx}
    private final List<int[]> hotspotScreenPos = new ArrayList<>(); // {x0,y0,x1,y1,entryIdx,spotIdx}
    private WikiEntry expandedImage = null;

    public WikiScreen(Screen returnScreen) {
        super(Component.translatable("gui.universalkeyboard.screen.wiki.title"));
        this.returnScreen = returnScreen;
    }

    // ── Loading ──────────────────────────────────────────────────────────────

    private void loadPages() {
        pages.clear();
        var rm = minecraft.getResourceManager();
        String lang = minecraft.options.languageCode;

        List<String> pageIds = new ArrayList<>();
        var manifestLoc = ResourceLocation.fromNamespaceAndPath("universalkeyboard", "wiki/manifest.json");
        var manifestOpt = rm.getResource(manifestLoc);
        if (manifestOpt.isEmpty()) return;
        try (var reader = new InputStreamReader(manifestOpt.get().open(), StandardCharsets.UTF_8)) {
            JsonArray arr = JsonParser.parseReader(reader).getAsJsonArray();
            for (var el : arr) pageIds.add(el.getAsString());
        } catch (Exception e) {
            return;
        }

        for (String id : pageIds) {
            WikiPage page = loadPage(rm, lang, id);
            if (page != null) pages.add(page);
        }

        currentPage = Math.min(currentPage, Math.max(0, pages.size() - 1));
    }

    private WikiPage loadPage(net.minecraft.server.packs.resources.ResourceManager rm, String lang, String id) {
        for (String locale : new String[]{lang, "en_us"}) {
            var loc = ResourceLocation.fromNamespaceAndPath("universalkeyboard", "wiki/" + locale + "/" + id + ".json");
            var opt = rm.getResource(loc);
            if (opt.isEmpty()) continue;
            try (var reader = new InputStreamReader(opt.get().open(), StandardCharsets.UTF_8)) {
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                String label = obj.has("label") ? obj.get("label").getAsString() : id;
                List<WikiEntry> entries = new ArrayList<>();
                if (obj.has("entries")) {
                    for (var el : obj.getAsJsonArray("entries")) {
                        WikiEntry entry = parseEntry(el.getAsJsonObject());
                        if (entry != null) entries.add(entry);
                    }
                }
                return new WikiPage(label, entries);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private WikiEntry parseEntry(JsonObject obj) {
        if (!obj.has("type")) return null;
        return switch (obj.get("type").getAsString()) {
            case "heading" -> WikiEntry.heading(obj.get("text").getAsString());
            case "text"    -> WikiEntry.text(obj.get("body").getAsString(),
                    obj.has("scale") ? obj.get("scale").getAsFloat() : 1.0f);
            case "image"   -> {
                int imgW = obj.get("width").getAsInt();
                int imgH = obj.get("height").getAsInt();
                List<WikiHotspot> spots = new ArrayList<>();
                if (obj.has("hotspots")) {
                    for (var el : obj.getAsJsonArray("hotspots")) {
                        JsonObject s = el.getAsJsonObject();
                        spots.add(new WikiHotspot(
                                s.get("px0").getAsFloat() / imgW,
                                s.get("py0").getAsFloat() / imgH,
                                s.get("px1").getAsFloat() / imgW,
                                s.get("py1").getAsFloat() / imgH,
                                s.has("title") ? s.get("title").getAsString() : "",
                                s.has("body")  ? s.get("body").getAsString()  : ""));
                    }
                }
                yield WikiEntry.image(
                        ResourceLocation.fromNamespaceAndPath("universalkeyboard", obj.get("texture").getAsString()),
                        imgW, imgH, spots);
            }
            default -> null;
        };
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        panelX = (width  - PANEL_W) / 2;
        panelH = Math.min(height - 20, PANEL_MAX_H);
        panelY = (height - panelH) / 2;
        loadPages();
        clampScroll();
    }

    private List<WikiEntry> entries() {
        if (pages.isEmpty()) return List.of();
        return pages.get(currentPage).entries();
    }

    private int contentX()      { return panelX + SIDEBAR_W + PAD; }
    private int contentW()      { return PANEL_W - SIDEBAR_W - PAD * 2; }
    private int contentTop()    { return panelY + TITLE_BAR_H + PAD; }
    private int contentBottom() { return panelY + panelH - PAD - 10; }

    private void clampScroll() {
        int avail = contentBottom() - contentTop();
        scrollY = Math.max(0, Math.min(scrollY, Math.max(0, contentHeight - avail)));
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xEE111111);
        drawBorder(g, panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xFF666666);

        g.fill(panelX, panelY, panelX + PANEL_W, panelY + TITLE_BAR_H, 0x44FFFFFF);
        g.drawCenteredString(font, getTitle(), panelX + PANEL_W / 2, panelY + 4, 0xFFFFFF);
        g.drawString(font, "§7[Esc]", panelX + PANEL_W - PAD - font.width("§7[Esc]"),
                panelY + 4, 0x888888, false);

        renderSidebar(g, mx, my);
        renderContent(g);
        renderHoveredHotspot(g, mx, my);

        for (var r : this.renderables) r.render(g, mx, my, pt);

        if (expandedImage != null) renderLightbox(g);
    }

    private void renderSidebar(GuiGraphics g, int mx, int my) {
        int sx0 = panelX;
        int sy0 = panelY + TITLE_BAR_H;
        int sx1 = panelX + SIDEBAR_W;
        int sy1 = panelY + panelH;

        g.fill(sx0, sy0, sx1, sy1, 0x55000000);
        g.fill(sx1 - 1, sy0, sx1, sy1, 0xFF555555);

        int rowY = sy0 + 6;
        for (int i = 0; i < pages.size(); i++) {
            int top = rowY + i * SIDE_ROW_H;
            int bot = top + SIDE_ROW_H;
            boolean active  = i == currentPage;
            boolean hovered = mx >= sx0 && mx < sx1 - 1 && my >= top && my < bot;

            if (active)       g.fill(sx0, top, sx1 - 1, bot, 0x55FFCC33);
            else if (hovered) g.fill(sx0, top, sx1 - 1, bot, 0x33FFFFFF);
            if (active)       g.fill(sx0, top, sx0 + 2, bot, 0xFFFFCC33);

            int color = active ? 0xFFDD44 : (hovered ? 0xFFFFFF : 0xBBBBBB);
            g.drawString(font, pages.get(i).label(), sx0 + 8, top + 3, color, false);
        }
    }

    private void renderContent(GuiGraphics g) {
        int cx = contentX(), cw = contentW();
        int cTop = contentTop(), cBot = contentBottom();

        g.enableScissor(cx, cTop, cx + cw, cBot);

        imageHitboxes.clear();
        hotspotScreenPos.clear();
        int y = cTop - scrollY;
        contentHeight = 0;
        List<WikiEntry> entries = entries();

        for (int idx = 0; idx < entries.size(); idx++) {
            WikiEntry entry = entries.get(idx);
            if (entry.image() != null) {
                int[] dim = fitImage(entry, cw);
                int dw = dim[0], dh = dim[1];
                if (y + dh > cTop && y < cBot) {
                    blitScaled(g, entry.image(), cx, y, dw, dh, entry.imageW(), entry.imageH());
                    drawBorder(g, cx, y, cx + dw, y + dh, 0xFF777777);

                    // Draw hotspot rectangles and record their screen positions
                    for (int si = 0; si < entry.hotspots().size(); si++) {
                        WikiHotspot spot = entry.hotspots().get(si);
                        int hx0 = cx + (int)(spot.nx0() * dw);
                        int hy0 = y  + (int)(spot.ny0() * dh);
                        int hx1 = cx + (int)(spot.nx1() * dw);
                        int hy1 = y  + (int)(spot.ny1() * dh);
                        hotspotScreenPos.add(new int[]{hx0, hy0, hx1, hy1, idx, si});
                    }
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
                float s = entry.scale();
                int scaledLineH = (int)(LINE_H * s);
                int scaledCw   = (int)(cw / s);
                List<FormattedCharSequence> lines = font.split(Component.literal(entry.body()), scaledCw);
                for (FormattedCharSequence line : lines) {
                    if (y + scaledLineH > cTop && y < cBot) {
                        g.pose().pushPose();
                        g.pose().translate(cx, y, 0);
                        g.pose().scale(s, s, 1.0f);
                        g.drawString(font, line, 0, 0, 0xCCCCCC, false);
                        g.pose().popPose();
                    }
                    y += scaledLineH;
                    contentHeight += scaledLineH;
                }
                y += 4;
                contentHeight += 4;
            }
        }

        g.disableScissor();

        int availH = cBot - cTop;
        if (contentHeight > availH) {
            int barH = Math.max(16, availH * availH / contentHeight);
            int barY = cTop + (int)((long)scrollY * (availH - barH) / (contentHeight - availH));
            g.fill(panelX + PANEL_W - 3, cTop, panelX + PANEL_W - 1, cBot, 0x22FFFFFF);
            g.fill(panelX + PANEL_W - 3, barY, panelX + PANEL_W - 1, barY + barH, 0x99AAAAAA);
        }
    }

    private void renderHoveredHotspot(GuiGraphics g, int mx, int my) {
        for (int[] pos : hotspotScreenPos) {
            if (!isInHotspot(mx, my, pos[0], pos[1], pos[2], pos[3])) continue;
            WikiHotspot spot = entries().get(pos[4]).hotspots().get(pos[5]);

            List<FormattedCharSequence> bodyLines =
                    font.split(Component.literal(spot.body()), TOOLTIP_W - PAD * 2);
            int panelH = PAD + HEADING_H + 3 + bodyLines.size() * LINE_H + PAD;
            int panelW = TOOLTIP_W;

            // Flip to the left of the cursor when on the right half of the screen,
            // so the tooltip never covers the mouse over the area it's describing.
            int px = (mx > width / 2) ? mx - 14 - panelW : mx + 14;
            int py = my - panelH / 2;
            px = Math.max(4, Math.min(px, width  - panelW - 4));
            py = Math.max(4, Math.min(py, height - panelH - 4));

            g.fill(px, py, px + panelW, py + panelH, 0xEE111111);
            drawBorder(g, px, py, px + panelW, py + panelH, 0xFFFFCC33);

            if (!spot.title().isEmpty())
                g.drawString(font, "§e§l" + spot.title(), px + PAD, py + PAD, 0xFFDD44, false);

            int ty = py + PAD + HEADING_H + 3;
            for (FormattedCharSequence line : bodyLines) {
                g.drawString(font, line, px + PAD, ty, 0xCCCCCC, false);
                ty += LINE_H;
            }
            break; // only one tooltip at a time
        }
    }

    private void renderLightbox(GuiGraphics g) {
        g.fill(0, 0, width, height, 0xCC000000);

        int maxW = width  - 40;
        int maxH = height - 40;
        double scale = Math.min((double) maxW / expandedImage.imageW(),
                                (double) maxH / expandedImage.imageH());
        int dw = (int)(expandedImage.imageW() * scale);
        int dh = (int)(expandedImage.imageH() * scale);
        int x = (width  - dw) / 2;
        int y = (height - dh) / 2;

        blitScaled(g, expandedImage.image(), x, y, dw, dh, expandedImage.imageW(), expandedImage.imageH());
        drawBorder(g, x - 1, y - 1, x + dw + 1, y + dh + 1, 0xFFAAAAAA);
        g.drawCenteredString(font, "§7[Esc] or [Tab] to return", width / 2, y + dh + 6, 0xAAAAAA);
    }

    // ── Drawing helpers ──────────────────────────────────────────────────────

    private static boolean isInHotspot(int mx, int my, int x0, int y0, int x1, int y1) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    private static void blitScaled(GuiGraphics g, ResourceLocation loc,
                                   int screenX, int screenY, int screenW, int screenH,
                                   int nativeW, int nativeH) {
        g.pose().pushPose();
        g.pose().translate(screenX, screenY, 0);
        g.pose().scale((float) screenW / nativeW, (float) screenH / nativeH, 1.0f);
        g.blit(loc, 0, 0, 0, 0, nativeW, nativeH, nativeW, nativeH);
        g.pose().popPose();
    }

    private static int[] fitImage(WikiEntry entry, int maxW) {
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

    // ── Input ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        if (expandedImage != null) { expandedImage = null; return true; }

        int sx0 = panelX, sx1 = panelX + SIDEBAR_W - 1;
        int rowY = panelY + TITLE_BAR_H + 6;
        if (mx >= sx0 && mx < sx1 && my >= rowY) {
            int row = (int)((my - rowY) / SIDE_ROW_H);
            if (row >= 0 && row < pages.size()) {
                if (row != currentPage) { currentPage = row; scrollY = 0; }
                return true;
            }
        }

        // fix this if I decide that clicking hotspots can enlarge the image
        for (int[] pos : hotspotScreenPos) {
            if (isInHotspot((int) mx, (int) my, pos[0], pos[1], pos[2], pos[3])) return true;
        }

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
        if (expandedImage != null
                && (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                 || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB)) {
            expandedImage = null;
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(returnScreen);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
