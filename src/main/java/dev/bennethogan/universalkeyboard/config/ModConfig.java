package dev.bennethogan.universalkeyboard.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

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
        public final ModConfigSpec.BooleanValue makeRecipeHarder;

        Common(ModConfigSpec.Builder builder) {
            builder.comment("Universal Keyboard Settings").push("keyboard");

            keyboardRange = builder
                    .comment("Max wireless range in blocks between the keyboard and its linked target.",
                             "Events are dropped if the player exceeds this distance.",
                             "Set to greater than 2 million for range capable of reaching sub-levels",
                             "Range: 1–2147483647. Default: 32.")
                    .translation("config.universalkeyboard.keyboard.keyboardRange")
                    .defineInRange("keyboardRange", 32, 1, Integer.MAX_VALUE);

            copycatLocateDuration = builder
                    .comment("Duration in seconds for the Wireless Copycat test-face feature.",
                             "Applies to both the per-face Test button on the Copycat and the Locate button on the keyboard.",
                             "Range: 1–300. Default: 5.")
                    .translation("config.universalkeyboard.keyboard.copycatLocateDuration")
                    .defineInRange("copycatLocateDuration", 5, 1, 300);

            makeRecipeHarder = builder
                    .comment("Requires Aeronautics. Default: false.")
                    .translation("config.universalkeyboard.keyboard.makeRecipeHarder")
                    .define("makeRecipeHarder", false);

            builder.pop();
        }
    }

    public static class Client {

        public final ModConfigSpec.BooleanValue enableGamepad;
        public final ModConfigSpec.BooleanValue enableAdvancedInput;
        public final ModConfigSpec.DoubleValue  stickThreshold;
        public final ModConfigSpec.BooleanValue enableMouseInput;
        public final ModConfigSpec.BooleanValue mouseAbsoluteMode;
        public final ModConfigSpec.DoubleValue  mouseAxisSensitivity;
        public final ModConfigSpec.DoubleValue  triggerThreshold;
        public final ModConfigSpec.BooleanValue joystickScaling;
        public final ModConfigSpec.ConfigValue<List<? extends Double>> stickCalibration;
        public final ModConfigSpec.ConfigValue<String> controlWheelLeftRows;
        public final ModConfigSpec.ConfigValue<String> controlWheelRightRows;
        public final ModConfigSpec.BooleanValue        screenEnabled;
        public final ModConfigSpec.ConfigValue<String> screenVisualizedRows;
        public final ModConfigSpec.ConfigValue<String> vistaCameraToggleRow;
        public final ModConfigSpec.BooleanValue favoriteLiveControlAutoStart;
        public final ModConfigSpec.ConfigValue<String> liveControlIgnoredKeys;
        public final ModConfigSpec.BooleanValue redstoneWildcardWarningDismissed;
        public final ModConfigSpec.BooleanValue enableKeyboardAnimations;
        public final ModConfigSpec.BooleanValue doubleExplosion;
        public final ModConfigSpec.DoubleValue  mainWaveSpeed;
        public final ModConfigSpec.DoubleValue  clusterWaveSpeed;

        Client(ModConfigSpec.Builder builder) {
            builder.comment("Universal Keyboard — Client Settings").push("gamepad");

            enableGamepad = builder
                    .comment("Allow a connected gamepad to drive Live Controller bindings.",
                             "When on, gamepad buttons/triggers/sticks can be bound just like keys.",
                             "Uses GLFW's standard gamepad mapping, so any recognized controller works.",
                             "Multiple standard gamepads can be used at once; each is shown with its number.",
                             "Default: true.")
                    .translation("config.universalkeyboard.gamepad.enableGamepad")
                    .define("enableGamepad", true);

            enableAdvancedInput = builder
                    .comment("Read controllers as raw joysticks instead of mapped gamepads.",
                             "Turn this on for HOTAS / flight sticks / racing wheels and other devices that",
                             "GLFW does not recognise as standard gamepads. Every connected joystick is then",
                             "polled raw, exposing all of its axes, buttons and hat switches by number.",
                             "Bindings will show unmapped names (e.g. A3+, B12, H0↑) rather than A/B/RT/LS.",
                             "Leave off if you only use standard gamepads. Default: false.")
                    .translation("config.universalkeyboard.gamepad.enableAdvancedInput")
                    .define("enableAdvancedInput", false);

            stickThreshold = builder
                    .comment("How far an analog stick must be pushed before it counts as a directional press.",
                             "Range: 0.1–1.0. Default: 0.1.")
                    .translation("config.universalkeyboard.gamepad.stickThreshold")
                    .defineInRange("stickThreshold", 0.1, 0.0, 1.0);

            triggerThreshold = builder
                    .comment("How far a trigger (LT/RT) must be pulled before it counts as a press.",
                             "Range: 0.1–1.0. Default: 0.1.")
                    .translation("config.universalkeyboard.gamepad.triggerThreshold")
                    .defineInRange("triggerThreshold", 0.1, 0, 1.0);

            joystickScaling = builder
                    .comment("Scale a binding's output by how far the stick/trigger is pushed.",
                             "When on, an analog input bound to e.g. 50% power outputs proportionally:",
                             "pushing the stick halfway sends 25%, fully sends 50%.",
                             "Applies to RS, Thruster Power, Vector and Variable bindings in Hld/Tog modes.",
                             "Buttons and Inc/Overdrive bindings are never scaled.",
                             "Turn off to make analog inputs behave as plain on/off like buttons.",
                             "Default: true.")
                    .translation("config.universalkeyboard.gamepad.joystickScaling")
                    .define("joystickScaling", true);

            stickCalibration = builder
                    .comment("Per-axis maximum stick deflection, recorded by calibration.",
                             "Order: [Left X, Left Y, Right X, Right Y].",
                             "1.0 means the stick reaches the theoretical edge; a lower value means it",
                             "physically falls short, and analog output is rescaled so that the stick's real",
                             "maximum still maps to 100%. Fixes sticks that cap below full and asymmetric axes.",
                             "Set these with the Calibrate button in the Live Controller. Default: 1.0 each.")
                    .translation("config.universalkeyboard.gamepad.stickCalibration")
                    .defineList("stickCalibration",
                            List.of(1.0, 1.0, 1.0, 1.0),
                            () -> 1.0,
                            o -> o instanceof Double d && d >= 0.3 && d <= 1.0);

            builder.pop();

            builder.comment("Universal Keyboard — Mouse Input").push("mouse");

            enableMouseInput = builder
                    .comment("Allow the mouse to be bound in Live Controls",
                             "Default: false.")
                    .translation("config.universalkeyboard.mouse.enableMouseInput")
                    .define("enableMouseInput", false);

            mouseAbsoluteMode = builder
                    .comment("Choose two options for mouse movement",
                             "false (Velocity mode, default): mimics a real joystick, measures input speed as",
                             "the power, and reverts back to 0",
                             "true (Absolute mode): mimics the Aeroworks' joystick, which keeps the cursor locked, ",
                             "where you put it",
                             "Default: false.")
                    .translation("config.universalkeyboard.mouse.mouseAbsoluteMode")
                    .define("mouseAbsoluteMode", false);

            mouseAxisSensitivity = builder
                    .comment("Multiplier for how strongly mouse movement deflects its analog axes")
                    .translation("config.universalkeyboard.mouse.mouseAxisSensitivity")
                    .defineInRange("mouseAxisSensitivity", 1.0, 0.1, 10.0);

            builder.pop();

            builder.comment("Universal Keyboard: Animations").push("animations");

            enableKeyboardAnimations = builder
                    .comment("Turns off all keyboard animations if false",
                             "Default: true.")
                    .translation("config.universalkeyboard.animations.enableKeyboardAnimations")
                    .define("enableKeyboardAnimations", true);

            doubleExplosion = builder
                    .comment("Double explosion!!")
                    .translation("config.universalkeyboard.animations.doubleExplosion")
                    .define("doubleExplosion", false);

            mainWaveSpeed = builder
                    .comment("Keyboard main wave animation's speed",
                             "Range: 0.0–1.0. Default: 0.15.")
                    .translation("config.universalkeyboard.animations.mainWaveSpeed")
                    .defineInRange("mainWaveSpeed", 0.15, 0.0, 1.0);

            clusterWaveSpeed = builder
                    .comment("Keyboard cluster area animation speed",
                             "Range: 0.0–1.0. Default: 0.05.")
                    .translation("config.universalkeyboard.animations.clusterWaveSpeed")
                    .defineInRange("clusterWaveSpeed", 0.05, 0.0, 1.0);

            controlWheelLeftRows = builder
                    .comment("Comma-separated Live Controller row numbers (1–40) whose active state",
                             "animates the Control Wheel turning LEFT.",
                             "Example: \"1,3\" means rows 1 and 3 contribute to a left turn.",
                             "Leave blank to disable. Default: \"\".")
                    .translation("config.universalkeyboard.controlwheel.controlWheelLeftRows")
                    .define("controlWheelLeftRows", "");

            controlWheelRightRows = builder
                    .comment("Comma-separated Live Controller row numbers (1–40) whose active state",
                            "animates the Control Wheel turning RIGHT.",
                            "Example: \"2,4\" means rows 2 and 4 contribute to a right turn.",
                            "Leave blank to disable. Default: \"\".")
                    .translation("config.universalkeyboard.controlwheel.controlWheelRightRows")
                    .define("controlWheelRightRows", "");

            builder.pop();

            builder.comment("Universal Keyboard — Screens (dashboard + control wheel)").push("screens");

            screenEnabled = builder
                    .comment("Master on/off for the dashboard and control-wheel screens.",
                             "Set to false to leave them blank. Default: true.")
                    .translation("config.universalkeyboard.screens.screenEnabled")
                    .define("screenEnabled", true);

            screenVisualizedRows = builder
                    .comment("Comma-separated Live Controller row numbers (1–40) to visualize on the screens.",
                             "Each row is shown according to its binding type:",
                             "  RPM row      -> \"#### RPM\"",
                             "  Redstone row -> \"RS: N\"   (signal strength 0–15)",
                             "  Thruster row -> \"Thr: N\"  (thrust 0–15)",
                             "With more than one row, the display switches between the recently used one",
                             "Leave blank to show nothing. Default: \"\".")
                    .translation("config.universalkeyboard.screens.screenVisualizedRows")
                    .define("screenVisualizedRows", "");

            vistaCameraToggleRow = builder
                    .comment("Live Controller row number (1–40) whose key toggles the Vista camera",
                             "feed on the dashboard / control-wheel screens. Pressing that row's key",
                             "flips the feed on/off, regardless of what the row otherwise does.",
                             "Requires Vista mod. Leave blank to disable. Default: \"\".")
                    .translation("config.universalkeyboard.screens.vistaCameraToggleRow")
                    .define("vistaCameraToggleRow", "");

            builder.pop();

            builder.comment("Universal Keyboard — Controls").push("controls");

            favoriteLiveControlAutoStart = builder
                    .comment("Mark this option 'true' to start Live Controls immediately when you right-click keyboard",
                             "Live Controller menu must also be marked 'favorite'.",
                             "Shift-click the keyboard to open the main menu instead",
                             "Default: false.")
                    .translation("config.universalkeyboard.keyboard.favoriteLiveControlAutoStart")
                    .define("favoriteLiveControlAutoStart", false);

            liveControlIgnoredKeys = builder
                    .comment("Comma-separated keys that Live Control passes straight through to the game",
                             "instead of capturing, so other mods such as a map still respond while Live",
                             "Control is running. (F1–F12 and Esc are always free regardless of this setting.)",
                             "Use simple names: letters \"M\", digits \"5\", or names like \"space\", \"tab\",",
                             "\"left.shift\", \"semicolon\". Example: \"M, J, left.shift\".",
                             "Leave blank for none (default).")
                    .translation("config.universalkeyboard.controls.liveControlIgnoredKeys")
                    .define("liveControlIgnoredKeys", "");

            builder.pop();

            builder.comment("Universal Keyboard — Notices").push("notices");

            redstoneWildcardWarningDismissed = builder
                    .comment("Set by the 'Don't show this again' checkbox on the Create: Connected redstone-link",
                             "wildcard warning shown in the Redstone Links screen. When true, that popup is",
                             "suppressed. Reset to false to see the warning again. Default: false.")
                    .translation("config.universalkeyboard.notices.redstoneWildcardWarningDismissed")
                    .define("redstoneWildcardWarningDismissed", false);

            builder.pop();
        }
    }
}
