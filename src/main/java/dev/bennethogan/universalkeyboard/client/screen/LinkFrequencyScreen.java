package dev.bennethogan.universalkeyboard.client.screen;

import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LinkFrequencyScreen extends PanelScreen {

    private static final int PANEL_W = 252;
    private static final int VISIBLE = 8;
    private static final int ROW_H   = 18;
    private static final int PAD     = 8;
    private static final int BTN     = 16;
    private static final int HEADER_H = 28;
    private static final int MAX     = LinkedKeyboardBlockEntity.MAX_WIRELESS_FREQS;

    private static final int COL_DEL = PAD;
    private static final int COL_LBL = PAD + 18;
    private static final int COL_BOX = PAD + 44;
    private static final int COL_RND = PAD + 132;
    private static final int COL_CPY = PAD + 150;
    private static final int COL_PST = PAD + 168;

    private final BlockPos    keyboardPos;
    private final List<String> freqs;
    private final List<String> originalFreqs;
    private final Map<Integer, EditBox> boxMap = new HashMap<>();
    private int scrollOffset = 0;
    private ConfirmDialog confirmDialog;

    public LinkFrequencyScreen(BlockPos keyboardPos, String[] freqs) {
        super(Component.translatable("gui.universalkeyboard.screen.wireless_copycat.title"));
        this.keyboardPos   = keyboardPos;
        this.freqs         = new ArrayList<>();
        for (String f : freqs) if (f != null && !f.isEmpty()) this.freqs.add(f);
        this.originalFreqs = new ArrayList<>(this.freqs);
    }

    @Override
    protected int getPanelW() { return PANEL_W; }

    @Override
    protected int getPanelH() { return HEADER_H + VISIBLE * ROW_H + PAD; }

    @Override
    protected void init() {
        boxMap.clear();
        panelX = (width  - PANEL_W) / 2;
        panelY = Math.max(4, (height - getPanelH()) / 2);

        confirmDialog = new ConfirmDialog(font);
        confirmDialog.setParentBounds(panelX, panelY, PANEL_W, getPanelH());


        addRenderableWidget(DarkButton.make(Component.literal("?"),
                Component.translatable("gui.universalkeyboard.tooltip.wiki"),
                b -> Minecraft.getInstance().setScreen(new WikiScreen(this)),
                panelX + PANEL_W - PAD - BTN * 3 - 4, panelY + 6, BTN, BTN));
        addRenderableWidget(IconButton.make(ModIcons.REVERT,
                Component.translatable("gui.universalkeyboard.tooltip.revert"),
                b -> confirmDialog.open(
                        "gui.universalkeyboard.dialog.revert_title",
                        "gui.universalkeyboard.dialog.revert_body",
                        "gui.universalkeyboard.btn.yes_revert",
                        this::doRevert),
                panelX + PANEL_W - PAD - BTN * 2 - 2, panelY + 6, BTN));
        addRenderableWidget(IconButton.make(ModIcons.TAB_BACK,
                Component.translatable("gui.universalkeyboard.tooltip.back"),
                b -> goBack(),
                panelX + PANEL_W - PAD - BTN, panelY + 6, BTN));

        int totalItems = freqs.size() + (freqs.size() < MAX ? 1 : 0);
        scrollOffset   = Math.min(scrollOffset, Math.max(0, totalItems - VISIBLE));
        int rowAreaY   = panelY + HEADER_H;

        for (int vi = 0; vi < VISIBLE; vi++) {
            int fi   = scrollOffset + vi;
            int rowY = rowAreaY + vi * ROW_H;

            if (fi < freqs.size()) {
                final int fii = fi;
                addRenderableWidget(IconButton.make(ModIcons.TRASH, Component.literal("Remove"), b -> {
                    syncBoxesToFreqs();
                    freqs.remove(fii);
                    int total = freqs.size() + (freqs.size() < MAX ? 1 : 0);
                    scrollOffset = Math.min(scrollOffset, Math.max(0, total - VISIBLE));
                    rebuildWidgets();
                }, panelX + COL_DEL, rowY + 1, BTN));

                EditBox box = new EditBox(font, panelX + COL_BOX, rowY + 4, 84, 10, Component.empty());
                box.setMaxLength(6);
                box.setValue(freqs.get(fi));
                box.setFilter(s -> s.matches("[0-9A-Za-z]*"));
                boxMap.put(fi, box);
                addRenderableWidget(box);

                addRenderableWidget(IconButton.make(ModIcons.DICE, Component.literal("Randomize"),
                        b -> box.setValue(dev.bennethogan.universalkeyboard.wireless.WirelessFreqs.generate()),
                        panelX + COL_RND, rowY + 1, BTN));
                addRenderableWidget(IconButton.make(ModIcons.COPY, Component.literal("Copy"),
                        b -> Minecraft.getInstance().keyboardHandler.setClipboard(box.getValue()),
                        panelX + COL_CPY, rowY + 1, BTN));
                addRenderableWidget(IconButton.make(ModIcons.PASTE, Component.literal("Paste"), b -> {
                    String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                    if (clip != null && clip.matches("[0-9A-Za-z]{1,6}"))
                        box.setValue(clip.toUpperCase());
                }, panelX + COL_PST, rowY + 1, BTN));


            } else if (fi == freqs.size() && freqs.size() < MAX) {
                addRenderableWidget(DarkButton.make(
                        Component.literal("+ Add W" + (freqs.size() + 1)), b -> {
                            syncBoxesToFreqs();
                            freqs.add(dev.bennethogan.universalkeyboard.wireless.WirelessFreqs.generate());
                            rebuildWidgets();
                        }, panelX + COL_DEL, rowY + 1, PANEL_W - PAD * 2, 14));
            }
        }
    }

    private void goBack() {
        syncBoxesToFreqs();
        if (!MenuNav.back()) onClose();
    }

    private void syncBoxesToFreqs() {
        for (Map.Entry<Integer, EditBox> e : boxMap.entrySet()) {
            int fi = e.getKey();
            if (fi < freqs.size()) {
                String v = e.getValue().getValue().trim();
                if (!v.isEmpty()) freqs.set(fi, v);
            }
        }
    }

    private void autosave() {
        syncBoxesToFreqs();
        String[] arr = new String[MAX];
        for (int i = 0; i < freqs.size() && i < MAX; i++) arr[i] = freqs.get(i);
        ModPackets.sendSaveLinkFreqs(keyboardPos, arr);
    }

    private void doRevert() {
        freqs.clear();
        freqs.addAll(originalFreqs);
        scrollOffset = Math.min(scrollOffset, Math.max(0, freqs.size() + (freqs.size() < MAX ? 1 : 0) - VISIBLE));
        rebuildWidgets();
    }

    @Override
    public void removed() {
        autosave();
        super.removed();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (confirmDialog != null) confirmDialog.render(g, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (confirmDialog != null && confirmDialog.isOpen())
            return confirmDialog.mouseClicked(mx, my, btn);
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmDialog != null && confirmDialog.isOpen())
            return confirmDialog.keyPressed(keyCode);
        if (MenuNav.handleTabBack(this, keyCode, keyboardPos)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        syncBoxesToFreqs();
        int totalItems = freqs.size() + (freqs.size() < MAX ? 1 : 0);
        int maxScroll  = Math.max(0, totalItems - VISIBLE);
        scrollOffset   = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY));
        rebuildWidgets();
        return true;
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float pt) {
        int titleMaxW = (PANEL_W - PAD - BTN * 2 - 2 - 4) - PAD;
        drawScaled(g, getTitle().getString(), panelX + PAD, panelY + PAD, titleMaxW, 1.0f, 0xFFFFFF);
        drawScaled(g, "W1-W" + MAX, panelX + PAD, panelY + PAD + 11, titleMaxW, 0.75f, 0x888888);

        int rowAreaY = panelY + HEADER_H;
        for (int vi = 0; vi < VISIBLE; vi++) {
            int fi = scrollOffset + vi;
            if (fi < freqs.size()) {
                int rowY = rowAreaY + vi * ROW_H;
                g.drawString(font, "W" + (fi + 1), panelX + COL_LBL, rowY + 4, 0xAAAAAA, false);
            }
        }
    }

    // Ideally this will wrap long translations, will work on adding stuff like this everywhere if it works as intended
    private void drawScaled(GuiGraphics g, String text, int x, int y, int maxW, float baseScale, int color) {
        int w = font.width(text);
        float scale = baseScale;
        if (w * scale > maxW) scale = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }
}
