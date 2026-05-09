package dev.bennethogan.bennetsmod.config;

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
                             "Range: 1–256. Default: 16.")
                    .defineInRange("keyboardRange", 16, 1, 256);

            builder.pop();
        }
    }
}
