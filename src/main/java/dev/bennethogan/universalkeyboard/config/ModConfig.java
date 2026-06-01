package dev.bennethogan.universalkeyboard.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ModConfig {

    public static final Common COMMON;
    public static final ModConfigSpec COMMON_SPEC;

    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        var pair = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        COMMON_SPEC = pair.getRight();

        var clientPair = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = clientPair.getLeft();
        CLIENT_SPEC = clientPair.getRight();
    }

    public static class Common {

        public final ModConfigSpec.IntValue keyboardRange;
        public final ModConfigSpec.IntValue copycatLocateDuration;

        Common(ModConfigSpec.Builder builder) {
            builder.comment("Universal Keyboard Settings").push("keyboard");

            keyboardRange = builder
                    .comment("Max wireless range in blocks between the keyboard and its linked target.",
                             "Events are dropped if the player exceeds this distance.",
                             "Set to a very large value for effectively unlimited range.",
                             "Range: 1–2147483647. Default: 32.")
                    .defineInRange("keyboardRange", 32, 1, Integer.MAX_VALUE);

            copycatLocateDuration = builder
                    .comment("Duration in seconds for the Wireless Copycat locate/test feature.",
                             "Applies to both the per-face Test button on the Copycat and the Locate button on the keyboard.",
                             "Range: 1–300. Default: 15.")
                    .defineInRange("copycatLocateDuration", 15, 1, 300);

            builder.pop();
        }
    }

    public static class Client {

        public final ModConfigSpec.BooleanValue enableGamepad;
        public final ModConfigSpec.DoubleValue  stickThreshold;
        public final ModConfigSpec.DoubleValue  triggerThreshold;
        public final ModConfigSpec.BooleanValue joystickScaling;

        Client(ModConfigSpec.Builder builder) {
            builder.comment("Universal Keyboard — Client Settings").push("gamepad");

            enableGamepad = builder
                    .comment("Allow a connected gamepad to drive Live Controller bindings.",
                             "When on, gamepad buttons/triggers/sticks can be bound just like keys.",
                             "Uses GLFW's standard gamepad mapping, so any recognized controller works.",
                             "Default: true.")
                    .define("enableGamepad", true);

            stickThreshold = builder
                    .comment("How far an analog stick must be pushed before it counts as a directional press.",
                             "Range: 0.1–1.0. Default: 0.5.")
                    .defineInRange("stickThreshold", 0.5, 0.1, 1.0);

            triggerThreshold = builder
                    .comment("How far a trigger (LT/RT) must be pulled before it counts as a press.",
                             "Range: 0.1–1.0. Default: 0.5.")
                    .defineInRange("triggerThreshold", 0.5, 0.1, 1.0);

            joystickScaling = builder
                    .comment("Scale a binding's output by how far the stick/trigger is pushed.",
                             "When on, an analog input bound to e.g. 50% power outputs proportionally:",
                             "pushing the stick halfway sends 25%, fully sends 50%.",
                             "Applies to RS, Thruster Power, Vector and Variable bindings in Hld/Tog modes.",
                             "Buttons and Inc/Overdrive bindings are never scaled.",
                             "Turn off to make analog inputs behave as plain on/off like buttons.",
                             "Default: true.")
                    .define("joystickScaling", true);

            builder.pop();
        }
    }
}
