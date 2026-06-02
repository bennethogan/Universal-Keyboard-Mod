package dev.bennethogan.universalkeyboard.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side breadcrumb for the nested keyboard menus. Remembers enough context to re-open the
 * ModeSelectionScreen at the right page so that pressing Tab walks back up one layer.
 */
public final class MenuNav {

    private MenuNav() {}

    // Context captured whenever the mode-selection menu is built.
    public static BlockPos keyboardPos;
    public static String   targetTypeName = "";
    public static int      availableBits;


    public static ModeSelectionScreen.Page returnPage = ModeSelectionScreen.Page.ROOT;

    public static void remember(BlockPos pos, String type, int bits) {
        keyboardPos    = pos;
        targetTypeName = type;
        availableBits  = bits;
    }


    public static boolean back() {
        if (keyboardPos == null) return false;
        Minecraft.getInstance().setScreen(
                new ModeSelectionScreen(keyboardPos, targetTypeName, availableBits, returnPage));
        return true;
    }


    public static boolean handleTabBack(Screen screen, int keyCode) {
        return handleTabBack(screen, keyCode, null);
    }

    public static boolean handleTabBack(Screen screen, int keyCode, BlockPos leafPos) {
        if (keyCode != GLFW.GLFW_KEY_TAB) return false;
        if (screen.getFocused() instanceof EditBox) return false;
        if (leafPos != null && !leafPos.equals(keyboardPos)) return false;
        return back();
    }
}
