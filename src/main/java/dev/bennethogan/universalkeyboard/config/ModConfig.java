package dev.bennethogan.universalkeyboard.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ModConfig {

    public static final Common COMMON;
    public static final ModConfigSpec COMMON_SPEC;

    static {
        var pair = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        COMMON_SPEC = pair.getRight();
    }

    public static class Common {

        public final ModConfigSpec.IntValue keyboardRange;

        Common(ModConfigSpec.Builder builder) {
            builder.comment("Universal Keyboard Settings").push("keyboard");

            keyboardRange = builder
                    .comment("Max wireless range in blocks between the keyboard and its linked target.",
                             "Events are dropped if the player exceeds this distance.",
                             "Set to a very large value for effectively unlimited range.",
                             "Range: 1–2147483647. Default: 32.")
                    .defineInRange("keyboardRange", 32, 1, Integer.MAX_VALUE);

            builder.pop();
        }
    }
}
