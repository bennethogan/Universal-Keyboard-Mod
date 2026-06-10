package dev.bennethogan.universalkeyboard.api;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;


/**
 * Controls a peripheral menu that I intend to make very modular for possible KubeJS compat
 * I will explain it all more, if I decide to go that route
 *
 */
public record PeripheralControl(
        Kind   kind,
        String method,
        String label,
        String argType,
        String arg,
        String value
) {
    public enum Kind { READOUT, BUTTON, INPUT }

    public static PeripheralControl readout(String method, String label, String value) {
        return new PeripheralControl(Kind.READOUT, method, label, "", "", value);
    }

    public static PeripheralControl button(String method, String label, String arg) {
        return new PeripheralControl(Kind.BUTTON, method, label, "", arg, "");
    }

    public static PeripheralControl input(String method, String label, String argType) {
        return new PeripheralControl(Kind.INPUT, method, label, argType, "", "");
    }

   // A readout with the live value swapped in, used when refreshing a config from a fresh scan
    public PeripheralControl withValue(String newValue) {
        return new PeripheralControl(kind, method, label, argType, arg, newValue);
    }

    public static final StreamCodec<FriendlyByteBuf, PeripheralControl> STREAM_CODEC = StreamCodec.of(
            (buf, c) -> {
                buf.writeEnum(c.kind);
                buf.writeUtf(c.method);
                buf.writeUtf(c.label);
                buf.writeUtf(c.argType);
                buf.writeUtf(c.arg);
                buf.writeUtf(c.value);
            },
            buf -> new PeripheralControl(
                    buf.readEnum(Kind.class),
                    buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf()
            ));
}
