package dev.bennethogan.universalkeyboard.client.screen;

import dev.bennethogan.universalkeyboard.blockentity.WirelessCopycatBlockEntity;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class WirelessCopycatScreen extends PanelScreen {

    private static final String[] FACE_NAMES = {"Down", "Up", "North", "South", "West", "East"};
    private static final int PANEL_W = 252;
    private static final int ROW_H   = 20;
    private static final int PAD     = 8;
    private static final int BTN     = 16;

    private static final int COL_ONOFF = PAD + 44;
    private static final int COL_BOX   = PAD + 64;
    private static final int COL_RND   = PAD + 132;
    private static final int COL_CPY   = PAD + 150;
    private static final int COL_PST   = PAD + 168;
    private static final int COL_TEST  = PAD + 186;

    private final BlockPos  blockPos;
    private final String[]  freqs;
    private final boolean[] enabledState;
    // Originals for Revert
    private final String[]  originalFreqs;
    private final boolean[] originalEnabled;

    private boolean suppressSave = true;
    private final EditBox[] freqBoxes = new EditBox[6];
    private ConfirmDialog confirmDialog;

    public WirelessCopycatScreen(BlockPos blockPos, String[] freqs, boolean[] enabled) {
        super(Component.literal("Wireless Copycat"));
        this.blockPos        = blockPos;
        this.freqs           = freqs.clone();
        this.enabledState    = enabled.clone();
        this.originalFreqs   = freqs.clone();
        this.originalEnabled = enabled.clone();
    }

    @Override
    protected int getPanelW() { return PANEL_W; }

    @Override
    protected int getPanelH() { return PAD + 14 + 4 + 6 * ROW_H + PAD + BTN + PAD; }

    @Override
    protected void init() {
        suppressSave = true;
        panelX = (width  - getPanelW()) / 2;
        panelY = (height - getPanelH()) / 2;
        int rowAreaY = panelY + PAD + 14 + 4;

        for (int i = 0; i < 6; i++) {
            int rowY = rowAreaY + i * ROW_H;
            final int fi = i;

            IconButton toggle = IconButton.make(
                    enabledState[fi] ? ModIcons.ACTIVE : ModIcons.PASSIVE,
                    Component.literal(enabledState[fi] ? "Enabled" : "Disabled"),
                    b -> {
                        enabledState[fi] = !enabledState[fi];
                        ((IconButton) b).setIcon(enabledState[fi] ? ModIcons.ACTIVE : ModIcons.PASSIVE);
                        b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.literal(enabledState[fi] ? "Enabled" : "Disabled")));
                        autoSave();
                    }, panelX + COL_ONOFF, rowY + 2, BTN);
            addRenderableWidget(toggle);

            EditBox box = new EditBox(font, panelX + COL_BOX, rowY + 5, 60, 10,
                    Component.literal(FACE_NAMES[i]));
            box.setMaxLength(6);
            box.setValue(freqs[fi] != null ? freqs[fi] : "");
            box.setFilter(s -> s.matches("[0-9A-Za-z]*"));
            freqBoxes[i] = box;
            addRenderableWidget(box);

            addRenderableWidget(IconButton.make(ModIcons.DICE, Component.literal("Randomize"),
                    b -> { freqBoxes[fi].setValue(WirelessCopycatBlockEntity.generateFreq()); autoSave(); },
                    panelX + COL_RND, rowY + 2, BTN));

            addRenderableWidget(IconButton.make(ModIcons.COPY, Component.literal("Copy"),
                    b -> Minecraft.getInstance().keyboardHandler.setClipboard(freqBoxes[fi].getValue()),
                    panelX + COL_CPY, rowY + 2, BTN));

            addRenderableWidget(IconButton.make(ModIcons.PASTE, Component.literal("Paste"), b -> {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null && clip.matches("[0-9A-Za-z]{1,6}")) {
                    freqBoxes[fi].setValue(clip.toUpperCase());
                    autoSave();
                }
            }, panelX + COL_PST, rowY + 2, BTN));

            addRenderableWidget(IconButton.make(ModIcons.PLAY, Component.literal("Test - briefly replace this side with Gold texture"),
                    b -> ModPackets.sendTestWirelessCopycatFace(blockPos, fi),
                    panelX + COL_TEST, rowY + 2, BTN));
        }

        int footY = panelY + getPanelH() - BTN - PAD;
        addRenderableWidget(DarkButton.make(
                Component.translatable("gui.universalkeyboard.btn.revert"),
                b -> confirmDialog.open(
                        "gui.universalkeyboard.dialog.revert_title",
                        "gui.universalkeyboard.dialog.revert_body",
                        "gui.universalkeyboard.btn.yes_revert",
                        this::doRevert),
                panelX + PAD, footY, 60, BTN));
        addRenderableWidget(DarkButton.make(Component.literal("x"),
                b -> onClose(), panelX + PANEL_W - PAD - BTN, footY, BTN, BTN));

        confirmDialog = new ConfirmDialog(font);
        confirmDialog.setParentBounds(panelX, panelY, PANEL_W, getPanelH());
        suppressSave = false;
    }

    private void autoSave() {
        if (suppressSave) return;
        String[] newFreqs = new String[6];
        for (int i = 0; i < 6; i++) newFreqs[i] = freqBoxes[i] != null ? freqBoxes[i].getValue() : freqs[i];
        ModPackets.sendSaveWirelessCopycatConfig(blockPos, newFreqs, enabledState);
    }

    private void doRevert() {
        suppressSave = true;
        for (int i = 0; i < 6; i++) {
            if (freqBoxes[i] != null) freqBoxes[i].setValue(originalFreqs[i] != null ? originalFreqs[i] : "");
            enabledState[i] = originalEnabled[i];
        }
        suppressSave = false;
        autoSave();
        init(); // refresh toggle icons
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
        if (confirmDialog != null) confirmDialog.render(g, mouseX, mouseY);
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
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        autoSave();
        super.onClose();
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float pt) {
        g.drawString(font, "Wireless Copycat", panelX + PAD, panelY + PAD, 0xFFFFFF, false);
        int rowAreaY = panelY + PAD + 14 + 4;
        for (int i = 0; i < 6; i++) {
            int rowY  = rowAreaY + i * ROW_H;
            int color = enabledState[i] ? 0xFFFFFF : 0x888888;
            g.drawString(font, FACE_NAMES[i], panelX + PAD, rowY + 6, color, false);
        }
    }
}
