package dev.bennethogan.universalkeyboard.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Helpers for rendering translated text that may be too wide for a fixed layout. */
public final class GuiText {

    private GuiText() {}

    /** Lines a string wraps into at the given pixel width (handles §-codes). */
    public static List<FormattedCharSequence> wrap(Font font, String text, int maxWidth) {
        return font.split(FormattedText.of(text), maxWidth);
    }

    /** Pixel height a string occupies when wrapped to maxWidth. */
    public static int wrappedHeight(Font font, String text, int maxWidth) {
        return wrap(font, text, maxWidth).size() * font.lineHeight;
    }

    /**
     * Draws text horizontally centered on {@code centerX}, wrapping to {@code maxWidth},
     * with the first line's top at {@code y}. Returns the total height drawn.
     */
    public static int drawWrappedCentered(GuiGraphics g, Font font, String text,
                                          int centerX, int y, int maxWidth, int color) {
        List<FormattedCharSequence> lines = wrap(font, text, maxWidth);
        int ly = y;
        for (FormattedCharSequence line : lines) {
            int w = font.width(line);
            g.drawString(font, line, centerX - w / 2, ly, color, false);
            ly += font.lineHeight;
        }
        return lines.size() * font.lineHeight;
    }
}
