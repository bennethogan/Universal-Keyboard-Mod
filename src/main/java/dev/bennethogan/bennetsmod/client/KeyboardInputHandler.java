package dev.bennethogan.bennetsmod.client;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

public class KeyboardInputHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!KeyboardCaptureManager.isCapturing()) return;
        KeyboardCaptureManager.tickHud();
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!KeyboardCaptureManager.isCapturing()) return;

        int key    = event.getKey();
        int action = event.getAction();

        if (action == GLFW.GLFW_RELEASE) {
            if (KeyboardCaptureManager.isCCCapturing())
                KeyboardCaptureManager.forwardKeyUp(key);
            return;
        }

        suppressMovementKey(key, event.getScanCode());

        if (KeyboardCaptureManager.isCreateCapturing()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { KeyboardCaptureManager.exitCreateCapture(); return; }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                KeyboardCaptureManager.handleCreateChar('\n');
                return;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) { KeyboardCaptureManager.handleCreateChar((char) 8); return; }
            char ch = getCharForKey(key, event.getScanCode(), event.getModifiers());
            if (ch != '\0') KeyboardCaptureManager.handleCreateChar(ch);
            return;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) { KeyboardCaptureManager.exitCapture(); return; }

        boolean held = (action == GLFW.GLFW_REPEAT);
        KeyboardCaptureManager.forwardKeyPress(key, held);

        char ch = getCharForKey(key, event.getScanCode(), event.getModifiers());
        if (ch != '\0') KeyboardCaptureManager.forwardChar(ch);
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (KeyboardCaptureManager.isCapturing()) event.setCanceled(true);
    }

    private static void suppressMovementKey(int keyCode, int scanCode) {
        for (var mapping : Minecraft.getInstance().options.keyMappings) {
            if (mapping.matches(keyCode, scanCode)) {
                mapping.consumeClick();
                mapping.setDown(false);
                break;
            }
        }
    }

    private static char getCharForKey(int keyCode, int scanCode, int mods) {
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;

        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            char base = (char) ('a' + (keyCode - GLFW.GLFW_KEY_A));
            return shift ? Character.toUpperCase(base) : base;
        }
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            if (!shift) return (char) ('0' + (keyCode - GLFW.GLFW_KEY_0));
            return switch (keyCode) {
                case GLFW.GLFW_KEY_1 -> '!'; case GLFW.GLFW_KEY_2 -> '@';
                case GLFW.GLFW_KEY_3 -> '#'; case GLFW.GLFW_KEY_4 -> '$';
                case GLFW.GLFW_KEY_5 -> '%'; case GLFW.GLFW_KEY_6 -> '^';
                case GLFW.GLFW_KEY_7 -> '&'; case GLFW.GLFW_KEY_8 -> '*';
                case GLFW.GLFW_KEY_9 -> '('; case GLFW.GLFW_KEY_0 -> ')';
                default -> '\0';
            };
        }
        if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9)
            return (char) ('0' + (keyCode - GLFW.GLFW_KEY_KP_0));

        return switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE         -> ' ';
            case GLFW.GLFW_KEY_APOSTROPHE    -> shift ? '"'  : '\'';
            case GLFW.GLFW_KEY_COMMA         -> shift ? '<'  : ',';
            case GLFW.GLFW_KEY_MINUS         -> shift ? '_'  : '-';
            case GLFW.GLFW_KEY_PERIOD        -> shift ? '>'  : '.';
            case GLFW.GLFW_KEY_SLASH         -> shift ? '?'  : '/';
            case GLFW.GLFW_KEY_SEMICOLON     -> shift ? ':'  : ';';
            case GLFW.GLFW_KEY_EQUAL         -> shift ? '+'  : '=';
            case GLFW.GLFW_KEY_LEFT_BRACKET  -> shift ? '{'  : '[';
            case GLFW.GLFW_KEY_BACKSLASH     -> shift ? '|'  : '\\';
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> shift ? '}'  : ']';
            case GLFW.GLFW_KEY_GRAVE_ACCENT  -> shift ? '~'  : '`';
            case GLFW.GLFW_KEY_KP_DECIMAL    -> '.';
            case GLFW.GLFW_KEY_KP_DIVIDE     -> '/';
            case GLFW.GLFW_KEY_KP_MULTIPLY   -> '*';
            case GLFW.GLFW_KEY_KP_SUBTRACT   -> '-';
            case GLFW.GLFW_KEY_KP_ADD        -> '+';
            case GLFW.GLFW_KEY_KP_EQUAL      -> '=';
            case GLFW.GLFW_KEY_ENTER,
                 GLFW.GLFW_KEY_KP_ENTER      -> '\n';
            case GLFW.GLFW_KEY_TAB           -> '\t';
            default                          -> '\0';
        };
    }
}
