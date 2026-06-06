package dev.bennethogan.universalkeyboard.client;

import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.universalkeyboard.client.gamepad.GamepadLiveDriver;
import dev.bennethogan.universalkeyboard.item.LinkedKeyboardItem;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlManager;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

public class KeyboardInputHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (KeyboardCaptureManager.isCapturing()) {
            KeyboardCaptureManager.tickHud();
        }
        if (LiveControlManager.isActive()) {
            GamepadLiveDriver.pollLive();
            LiveControlManager.tick();
        } else {
            GamepadLiveDriver.resetLive();
        }
        // poll the animator every tick
        ControlWheelAnimator.tick();

        // Persistent linking-mode action bar
        if (mc.player == null || mc.screen != null) return;
        ItemStack held = mc.player.getMainHandItem();
        if (!(held.getItem() instanceof LinkedKeyboardItem)) {
            held = mc.player.getOffhandItem();
            if (!(held.getItem() instanceof LinkedKeyboardItem)) return;
        }
        if (!LinkedKeyboardItem.isLinkingMode(held)) return;

        int ch = LinkedKeyboardItem.getActiveLinkingChannel(held);
        java.util.Map<Integer, java.util.List<net.minecraft.core.BlockPos>> allTargets =
                LinkedKeyboardItem.getAllChannelTargets(held);
        int countOnChannel = allTargets.getOrDefault(ch, java.util.List.of()).size();
        int totalChannels = (int) allTargets.values().stream().filter(l -> !l.isEmpty()).count();

        String msg = "§bLinking §f| §eChannel " + ch + " §f(" + countOnChannel + " linked)";
        if (totalChannels > 1) msg += " §7[" + totalChannels + " channels used]";
        mc.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(msg), true);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        // Live control mode intercepts all keys (including before CC capture)
        if (LiveControlManager.isActive()) {
            // Running handleKey first so toggle/INC counter state is current when the animator polls it
            LiveControlManager.handleKey(event.getKey(), event.getAction());
            ControlWheelAnimator.onKey(event.getKey(), event.getAction());
            if (event.getAction() != GLFW.GLFW_RELEASE && !isSafePassthroughKey(event.getKey())) {
                suppressMovementKey(event.getKey(), event.getScanCode());
            }
            return;
        }
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


    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();

        // Case 1: scroll while in keyboard capture mode (CC or Create)
        if (KeyboardCaptureManager.isCapturing()) {
            double scrollY = event.getScrollDeltaY();
            if (scrollY != 0) {
                int delta = scrollY > 0 ? -1 : 1; // scroll up = previous channel, scroll down = next
                KeyboardCaptureManager.scrollChannel(delta);
                event.setCanceled(true);
            }
            return;
        }

        // Case 2: scroll while holding a linked keyboard item in linking mode
        if (mc.player == null || mc.screen != null) return;
        ItemStack held = mc.player.getMainHandItem();
        if (!(held.getItem() instanceof LinkedKeyboardItem)) {
            held = mc.player.getOffhandItem();
            if (!(held.getItem() instanceof LinkedKeyboardItem)) return;
        }
        if (!LinkedKeyboardItem.isLinkingMode(held)) return;

        double scrollY = event.getScrollDeltaY();
        if (scrollY == 0) return;

        int current = LinkedKeyboardItem.getActiveLinkingChannel(held);
        int delta = scrollY > 0 ? -1 : 1;
        int next = ((current - 1 + delta) % LinkedKeyboardBlockEntity.MAX_CHANNELS
                + LinkedKeyboardBlockEntity.MAX_CHANNELS) % LinkedKeyboardBlockEntity.MAX_CHANNELS + 1;

        // Optimistic client-side update for immediate tooltip feedback
        LinkedKeyboardItem.setActiveLinkingChannel(held, next);
        // Confirm with server (server will sync item back)
        ModPackets.sendSetLinkingChannel(next);
        event.setCanceled(true);
    }

    private static boolean isSafePassthroughKey(int keyCode) {
        return keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F12;
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
