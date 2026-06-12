package dev.bennethogan.universalkeyboard.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

// Needed to handle the toggle for 'mouse focus' during live controls, so the camera freezes
public final class ModKeyMappings {

    private ModKeyMappings() {}

    public static final String CATEGORY = "key.categories.universalkeyboard";

    public static final KeyMapping MOUSE_FOCUS = new KeyMapping(
            "key.universalkeyboard.mouse_focus",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            CATEGORY);

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(MOUSE_FOCUS);
    }

    public static boolean isMouseFocusKey(int keyCode, int scanCode) {
        return !MOUSE_FOCUS.isUnbound() && MOUSE_FOCUS.matches(keyCode, scanCode);
    }
}
