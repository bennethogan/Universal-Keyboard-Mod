package dev.bennethogan.universalkeyboard.compat;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KeyboardDisplaySource extends DisplaySource {

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity be = context.getSourceBlockEntity();
        if (!(be instanceof LinkedKeyboardBlockEntity kbd)) return EMPTY;

        // sequencer's display line gets priority with the old behavior of CC getters, as fallback
        String[] seqLines = kbd.getDisplayLines();
        if (seqLines != null) {
            int last = 0;
            for (int i = 0; i < seqLines.length; i++)
                if (seqLines[i] != null && !seqLines[i].isEmpty()) last = i;
            List<MutableComponent> lines = new ArrayList<>();
            for (int i = 0; i <= last && lines.size() < stats.maxRows(); i++)
                lines.add(truncate(seqLines[i] == null ? "" : seqLines[i], stats.maxColumns()));
            return lines;
        }

        Map<String, String> values = kbd.getCachedGetterValues();
        String type = kbd.getCachedPeripheralType();

        if (values.isEmpty()) {
            String msg = type.isEmpty()
                    ? "No peripheral linked"
                    : "[" + type + "] No data";
            return List.of(Component.literal(msg));
        }

        List<MutableComponent> lines = new ArrayList<>();
        int maxCols = stats.maxColumns();

        if (!type.isEmpty()) {
            lines.add(truncate("[" + type + "]", maxCols));
        }

        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (lines.size() >= stats.maxRows()) break;
            lines.add(truncate(entry.getKey() + ": " + entry.getValue(), maxCols));
        }
        return lines;
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 20; // refresh display every second
    }

    private static MutableComponent truncate(String text, int maxCols) {
        if (maxCols > 0 && text.length() > maxCols) text = text.substring(0, maxCols);
        return Component.literal(text);
    }
}
