package dev.bennethogan.universalkeyboard.api;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes presentation style of the peripheral menu: title, accent color, layout style, and the
 * ordered set of PeriphalControls shown
 * This will be used later if I make a KubeJS comppat mod
 *
 */
public record PeripheralMenuConfig(
        String title,        // display title; blank → screen falls back to the peripheral type
        int accentColor,     // ARGB accent for title + border; 0 → DEFAULT_ACCENT
        float scale,         // layout scale multiplier (1.0 = default), clamped on use
        List<PeripheralControl> controls
) {
    public static final int DEFAULT_ACCENT = 0xFF666666;

    public int   accentOrDefault() { return accentColor == 0 ? DEFAULT_ACCENT : accentColor; }
    public float scaleClamped()    { return Math.max(0.75f, Math.min(2.0f, scale <= 0 ? 1.0f : scale)); }


    // hopefuly refactored the current CC peripheral menu correctly so this can be swapped in for the old one
    public static PeripheralMenuConfig defaultFrom(String type, List<String[]> getters, List<String[]> setters) {
        List<PeripheralControl> controls = new ArrayList<>();
        for (String[] g : getters) controls.add(PeripheralControl.readout(g[0], g[0], g[1]));
        for (String[] s : setters) {
            if ("call".equals(s[1])) controls.add(PeripheralControl.button(s[0], s[0], ""));
            else                     controls.add(PeripheralControl.input(s[0], s[0], s[1]));
        }
        return new PeripheralMenuConfig(type, 0, 1.0f, controls);
    }

    public static final StreamCodec<FriendlyByteBuf, PeripheralMenuConfig> STREAM_CODEC = StreamCodec.of(
            (buf, c) -> {
                buf.writeUtf(c.title);
                buf.writeInt(c.accentColor);
                buf.writeFloat(c.scale);
                buf.writeInt(c.controls.size());
                for (PeripheralControl pc : c.controls) PeripheralControl.STREAM_CODEC.encode(buf, pc);
            },
            buf -> {
                String title = buf.readUtf();
                int   accent = buf.readInt();
                float scale  = buf.readFloat();
                int n = buf.readInt();
                List<PeripheralControl> controls = new ArrayList<>(n);
                for (int i = 0; i < n; i++) controls.add(PeripheralControl.STREAM_CODEC.decode(buf));
                return new PeripheralMenuConfig(title, accent, scale, controls);
            });

    // builder to be used interally, or later by KubeJS

    public static Builder builder(String title) { return new Builder(title); }

    public static final class Builder {
        private final String title;
        private int   accent = 0;
        private float scale  = 1.0f;
        private final List<PeripheralControl> controls = new ArrayList<>();

        Builder(String title) { this.title = title == null ? "" : title; }

        public Builder accent(int argb) { this.accent = argb; return this; }
        public Builder scale(float s)   { this.scale  = s;    return this; }

        public Builder readout(String method, String label) {
            controls.add(PeripheralControl.readout(method, label, "")); return this;
        }
        public Builder button(String method, String label, String arg) {
            controls.add(PeripheralControl.button(method, label, arg)); return this;
        }
        public Builder input(String method, String label, String argType) {
            controls.add(PeripheralControl.input(method, label, argType)); return this;
        }

        public PeripheralMenuConfig build() {
            return new PeripheralMenuConfig(title, accent, scale, controls);
        }
    }
}
