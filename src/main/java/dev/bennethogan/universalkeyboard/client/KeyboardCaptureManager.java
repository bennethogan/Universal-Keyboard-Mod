package dev.bennethogan.universalkeyboard.client;

import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class KeyboardCaptureManager {

    public enum CaptureMode { NONE, CC, CREATE }

    private static CaptureMode mode = CaptureMode.NONE;
    private static @Nullable BlockPos capturedPos = null;
    private static int capturedChannel = 1;

    private static int createCurrentValue = 0;
    private static int createMinValue     = 0;
    private static int createMaxValue     = 256;

    private static final StringBuilder createInputBuffer = new StringBuilder();

    public static boolean isCapturing()       { return mode != CaptureMode.NONE; }
    public static boolean isCCCapturing()     { return mode == CaptureMode.CC; }
    public static boolean isCreateCapturing() { return mode == CaptureMode.CREATE; }
    public static CaptureMode getMode()       { return mode; }
    public static @Nullable BlockPos getCapturedPos() { return capturedPos; }
    public static int getCapturedChannel()    { return capturedChannel; }

    public static void setCapturedChannel(int ch) {
        capturedChannel = Math.max(1, Math.min(8, ch));
    }

    public static void setCaptureMode(@Nullable BlockPos keyboardPos, boolean capture) {
        if (capture && keyboardPos != null) {
            mode        = CaptureMode.CC;
            capturedPos = keyboardPos;
        } else {
            mode        = CaptureMode.NONE;
            capturedPos = null;
            capturedChannel = 1;
        }
    }

    public static void exitCapture() {
        if (capturedPos == null) return;
        BlockPos pos = capturedPos;
        mode        = CaptureMode.NONE;
        capturedPos = null;
        capturedChannel = 1;
        showDisconnected();
        ModPackets.sendKeyboardReleasePacket(pos);
    }

    public static void setCreateCaptureMode(BlockPos keyboardPos, int currentValue, int min, int max) {
        mode               = CaptureMode.CREATE;
        capturedPos        = keyboardPos;
        createCurrentValue = currentValue;
        createMinValue     = min;
        createMaxValue     = max;
        createInputBuffer.setLength(0);
    }

    public static void handleCreateChar(char ch) {
        if (mode != CaptureMode.CREATE || capturedPos == null) return;

        if (ch == '\n' || ch == '\r') {
            String pending = createInputBuffer.toString().trim();
            if (!pending.isEmpty()) {
                try {
                    if (pending.startsWith("+")) {
                        int delta = Integer.parseInt(pending.substring(1));
                        createCurrentValue = Math.min(createCurrentValue + delta, createMaxValue);
                    } else if (pending.startsWith("--")) {
                        int delta = Integer.parseInt(pending.substring(2));
                        createCurrentValue = Math.max(createCurrentValue - delta, createMinValue);
                    } else {
                        int parsed = Integer.parseInt(pending);
                        createCurrentValue = Math.max(createMinValue, Math.min(parsed, createMaxValue));
                    }
                } catch (NumberFormatException ignored) {}
            }
            if (capturedPos != null) ModPackets.sendCharPacket(capturedPos, '\n');
            createInputBuffer.setLength(0);

        } else if (ch == 8) {
            if (createInputBuffer.length() > 0) {
                createInputBuffer.deleteCharAt(createInputBuffer.length() - 1);
                ModPackets.sendCharPacket(capturedPos, (char) 8);
            }
        } else if (Character.isDigit(ch) || ch == '-' || ch == '+') {
            createInputBuffer.append(ch);
            ModPackets.sendCharPacket(capturedPos, ch);
        }
    }

    public static void exitCreateCapture() {
        if (capturedPos == null) return;
        BlockPos pos = capturedPos;
        mode        = CaptureMode.NONE;
        capturedPos = null;
        capturedChannel = 1;
        createInputBuffer.setLength(0);
        showDisconnected();
        ModPackets.sendKeyboardReleasePacket(pos);
    }

    public static String getCreateInputBuffer()  { return createInputBuffer.toString(); }
    public static int getCreateCurrentValue()    { return createCurrentValue; }
    public static int getCreateMinValue()        { return createMinValue; }
    public static int getCreateMaxValue()        { return createMaxValue; }

    public static void forwardKeyPress(int keyCode, boolean held) {
        if (capturedPos == null) return;
        ModPackets.sendKeyInputPacket(capturedPos, keyCode, held);
    }

    public static void forwardKeyUp(int keyCode) {
        if (capturedPos == null) return;
        ModPackets.sendKeyUpPacket(capturedPos, keyCode);
    }

    public static void forwardChar(char character) {
        if (capturedPos == null) return;
        ModPackets.sendCharPacket(capturedPos, character);
    }

    /** Called by scroll handler to cycle channel while in capture mode. */
    public static void scrollChannel(int delta) {
        if (capturedPos == null) return;
        int next = ((capturedChannel - 1 + delta) % 8 + 8) % 8 + 1;
        capturedChannel = next;
        ModPackets.sendSetActiveChannel(capturedPos, capturedChannel);
    }

    private static void showDisconnected() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.player.displayClientMessage(Component.literal("§c[Keyboard] §fDisconnected."), true);
    }

    public static void tickHud() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        switch (mode) {
            case CC -> mc.player.displayClientMessage(
                    Component.literal(
                            "§a[Keyboard] §fTyping — §bCh " + capturedChannel +
                            " §7| scroll: switch channel | ESC: stop"),
                    true);

            case CREATE -> {
                String buf = createInputBuffer.toString();
                String typed = buf.isEmpty()
                        ? "§7(type then press [enter])"
                        : "§e" + buf + "§7_";
                mc.player.displayClientMessage(
                        Component.literal(
                                "§b[Keyboard] §bCh " + capturedChannel +
                                " §7| " + createMinValue + "–" + createMaxValue +
                                " §fcur: §b" + createCurrentValue +
                                " §7| scroll: ch §f→  " + typed),
                        true);
            }

            case NONE -> {}
        }
    }
}
