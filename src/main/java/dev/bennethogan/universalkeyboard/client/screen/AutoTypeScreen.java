package dev.bennethogan.universalkeyboard.client.screen;

import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class AutoTypeScreen extends Screen {

    private static final int PADDING       = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH  = 80;
    private static final int LABEL_HEIGHT  = 12;

    private final BlockPos keyboardPos;
    private final String existingScript;

    private MultiLineEditBox scriptBox;

    public AutoTypeScreen(BlockPos keyboardPos, String existingScript) {
        super(Component.translatable("gui.universalkeyboard.screen.auto_type.title"));
        this.keyboardPos    = keyboardPos;
        this.existingScript = existingScript;
    }

    @Override
    protected void init() {
        int boxWidth  = Math.min(width - PADDING * 2, 400);
        int boxHeight = Math.min(height - PADDING * 4 - LABEL_HEIGHT - BUTTON_HEIGHT - 10, 200);
        int boxX      = (width - boxWidth) / 2;
        int boxY      = PADDING + LABEL_HEIGHT + 6;

        scriptBox = new MultiLineEditBox(
                font, boxX, boxY, boxWidth, boxHeight,
                Component.translatable("gui.universalkeyboard.hint.script_placeholder"),
                Component.translatable("gui.universalkeyboard.label.script")
        );
        scriptBox.setCharacterLimit(4096);
        scriptBox.setValue(existingScript);
        addRenderableWidget(scriptBox);

        int buttonsY = boxY + boxHeight + 8;
        int centerX  = width / 2;

        addRenderableWidget(DarkButton.make(Component.literal("?"),
                Component.translatable("gui.universalkeyboard.tooltip.wiki"),
                b -> minecraft.setScreen(new WikiScreen(this)),
                boxX + boxWidth - 16, PADDING - 4, 16, 16));

        addRenderableWidget(Button.builder(
                Component.translatable("gui.universalkeyboard.btn.save"),
                btn -> { ModPackets.sendSaveAutoTypeScript(keyboardPos, scriptBox.getValue()); onClose(); })
                .pos(centerX - BUTTON_WIDTH - 4, buttonsY)
                .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.universalkeyboard.btn.cancel"), btn -> onClose())
                .pos(centerX + 4, buttonsY)
                .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, PADDING, 0xFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("gui.universalkeyboard.hint.script_info"),
                width / 2, PADDING + LABEL_HEIGHT + 1, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
