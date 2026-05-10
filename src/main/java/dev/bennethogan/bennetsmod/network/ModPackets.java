package dev.bennethogan.bennetsmod.network;

import dev.bennethogan.bennetsmod.UniversalKeyboardMod;
import dev.bennethogan.bennetsmod.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.bennetsmod.compat.CreateValueHelper;
import dev.bennethogan.bennetsmod.compat.KeyboardMode;
import dev.bennethogan.bennetsmod.compat.PeripheralHelper;
import dev.bennethogan.bennetsmod.sequencer.SequencerStep;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

public class ModPackets {

    public record KeyboardCapturePacket(BlockPos keyboardPos, boolean capture) implements CustomPacketPayload {
        public static final Type<KeyboardCapturePacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "keyboard_capture"));
        public static final StreamCodec<FriendlyByteBuf, KeyboardCapturePacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, KeyboardCapturePacket::keyboardPos,
                        ByteBufCodecs.BOOL,    KeyboardCapturePacket::capture,
                        KeyboardCapturePacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record KeyInputPacket(
            BlockPos keyboardPos, byte mode, int keyCode, boolean held, char character
    ) implements CustomPacketPayload {
        public static final Type<KeyInputPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "key_input"));
        public static final StreamCodec<FriendlyByteBuf, KeyInputPacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, KeyInputPacket::keyboardPos,
                        ByteBufCodecs.BYTE,    KeyInputPacket::mode,
                        ByteBufCodecs.INT,     KeyInputPacket::keyCode,
                        ByteBufCodecs.BOOL,    KeyInputPacket::held,
                        ByteBufCodecs.INT.map(i -> (char)(int)i, c -> (int)c), KeyInputPacket::character,
                        KeyInputPacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        public static KeyInputPacket keyPress(BlockPos pos, int keyCode, boolean held) {
            return new KeyInputPacket(pos, (byte) 0, keyCode, held, '\0');
        }
        public static KeyInputPacket keyUp(BlockPos pos, int keyCode) {
            return new KeyInputPacket(pos, (byte) 1, keyCode, false, '\0');
        }
        public static KeyInputPacket charEvent(BlockPos pos, char ch) {
            return new KeyInputPacket(pos, (byte) 2, 0, false, ch);
        }
    }

    public record KeyboardReleasePacket(BlockPos keyboardPos) implements CustomPacketPayload {
        public static final Type<KeyboardReleasePacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "keyboard_release"));
        public static final StreamCodec<FriendlyByteBuf, KeyboardReleasePacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, KeyboardReleasePacket::keyboardPos,
                        KeyboardReleasePacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SaveAutoTypeScriptPacket(BlockPos keyboardPos, String script) implements CustomPacketPayload {
        public static final Type<SaveAutoTypeScriptPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "save_autotype_script"));
        public static final StreamCodec<FriendlyByteBuf, SaveAutoTypeScriptPacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC,     SaveAutoTypeScriptPacket::keyboardPos,
                        ByteBufCodecs.STRING_UTF8, SaveAutoTypeScriptPacket::script,
                        SaveAutoTypeScriptPacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenAutoTypeScreenPacket(BlockPos keyboardPos, String currentScript) implements CustomPacketPayload {
        public static final Type<OpenAutoTypeScreenPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "open_autotype_screen"));
        public static final StreamCodec<FriendlyByteBuf, OpenAutoTypeScreenPacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC,     OpenAutoTypeScreenPacket::keyboardPos,
                        ByteBufCodecs.STRING_UTF8, OpenAutoTypeScreenPacket::currentScript,
                        OpenAutoTypeScreenPacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record StartCreateCapturePacket(
            BlockPos keyboardPos, int currentValue, int minValue, int maxValue
    ) implements CustomPacketPayload {
        public static final Type<StartCreateCapturePacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "start_create_capture"));
        public static final StreamCodec<FriendlyByteBuf, StartCreateCapturePacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, StartCreateCapturePacket::keyboardPos,
                        ByteBufCodecs.INT,     StartCreateCapturePacket::currentValue,
                        ByteBufCodecs.INT,     StartCreateCapturePacket::minValue,
                        ByteBufCodecs.INT,     StartCreateCapturePacket::maxValue,
                        StartCreateCapturePacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // server → client: open peripheral method browser
    public record OpenPeripheralMenuPacket(
            BlockPos keyboardPos, String peripheralType,
            List<String[]> getters, List<String[]> setters
    ) implements CustomPacketPayload {
        public static final Type<OpenPeripheralMenuPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "open_peripheral_menu"));
        public static final StreamCodec<FriendlyByteBuf, OpenPeripheralMenuPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> {
                    BlockPos.STREAM_CODEC.encode(buf, pkt.keyboardPos());
                    buf.writeUtf(pkt.peripheralType());
                    buf.writeInt(pkt.getters().size());
                    for (String[] e : pkt.getters()) { buf.writeUtf(e[0]); buf.writeUtf(e[1]); }
                    buf.writeInt(pkt.setters().size());
                    for (String[] e : pkt.setters()) { buf.writeUtf(e[0]); buf.writeUtf(e[1]); }
                },
                buf -> {
                    BlockPos pos  = BlockPos.STREAM_CODEC.decode(buf);
                    String type   = buf.readUtf();
                    int gc = buf.readInt();
                    List<String[]> getters = new ArrayList<>(gc);
                    for (int i = 0; i < gc; i++) getters.add(new String[]{ buf.readUtf(), buf.readUtf() });
                    int sc = buf.readInt();
                    List<String[]> setters = new ArrayList<>(sc);
                    for (int i = 0; i < sc; i++) setters.add(new String[]{ buf.readUtf(), buf.readUtf() });
                    return new OpenPeripheralMenuPacket(pos, type, getters, setters);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // client → server: call a peripheral setter method
    public record CallPeripheralMethodPacket(
            BlockPos keyboardPos, String methodName, String argString
    ) implements CustomPacketPayload {
        public static final Type<CallPeripheralMethodPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "call_peripheral_method"));
        public static final StreamCodec<FriendlyByteBuf, CallPeripheralMethodPacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC,     CallPeripheralMethodPacket::keyboardPos,
                        ByteBufCodecs.STRING_UTF8, CallPeripheralMethodPacket::methodName,
                        ByteBufCodecs.STRING_UTF8, CallPeripheralMethodPacket::argString,
                        CallPeripheralMethodPacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // server → client: open the thruster control screen
    public record OpenThrusterControlPacket(
            BlockPos keyboardPos, String peripheralType,
            double targetVectorX, double targetVectorY,
            double currentVectorX, double currentVectorY,
            int thrust, int thrustConfig,
            double currentThrustPn, double displayedThrustPn,
            double airflowMs, int obstruction,
            int fuelAmountMb, int fuelCapacityMb
    ) implements CustomPacketPayload {
        public static final Type<OpenThrusterControlPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "open_thruster_control"));
        public static final StreamCodec<FriendlyByteBuf, OpenThrusterControlPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> {
                    BlockPos.STREAM_CODEC.encode(buf, pkt.keyboardPos());
                    buf.writeUtf(pkt.peripheralType());
                    buf.writeDouble(pkt.targetVectorX()); buf.writeDouble(pkt.targetVectorY());
                    buf.writeDouble(pkt.currentVectorX()); buf.writeDouble(pkt.currentVectorY());
                    buf.writeInt(pkt.thrust()); buf.writeInt(pkt.thrustConfig());
                    buf.writeDouble(pkt.currentThrustPn()); buf.writeDouble(pkt.displayedThrustPn());
                    buf.writeDouble(pkt.airflowMs()); buf.writeInt(pkt.obstruction());
                    buf.writeInt(pkt.fuelAmountMb()); buf.writeInt(pkt.fuelCapacityMb());
                },
                buf -> new OpenThrusterControlPacket(
                        BlockPos.STREAM_CODEC.decode(buf), buf.readUtf(),
                        buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readDouble(),
                        buf.readInt(), buf.readInt(),
                        buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readInt(),
                        buf.readInt(), buf.readInt()
                ));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // client → server: set a thruster value (single double arg, auto-casted to int where needed)
    public record SetThrusterValuePacket(
            BlockPos keyboardPos, String methodName, double argValue
    ) implements CustomPacketPayload {
        public static final Type<SetThrusterValuePacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "set_thruster_value"));
        public static final StreamCodec<FriendlyByteBuf, SetThrusterValuePacket> CODEC = StreamCodec.of(
                (buf, pkt) -> {
                    BlockPos.STREAM_CODEC.encode(buf, pkt.keyboardPos());
                    buf.writeUtf(pkt.methodName());
                    buf.writeDouble(pkt.argValue());
                },
                buf -> new SetThrusterValuePacket(
                        BlockPos.STREAM_CODEC.decode(buf), buf.readUtf(), buf.readDouble()
                ));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // server → client: open the mode-selection screen
    public record OpenModeSelectionPacket(BlockPos keyboardPos, String targetTypeName, int availableBits)
            implements CustomPacketPayload {
        public static final Type<OpenModeSelectionPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "open_mode_selection"));
        public static final StreamCodec<FriendlyByteBuf, OpenModeSelectionPacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC,     OpenModeSelectionPacket::keyboardPos,
                        ByteBufCodecs.STRING_UTF8, OpenModeSelectionPacket::targetTypeName,
                        ByteBufCodecs.INT,         OpenModeSelectionPacket::availableBits,
                        OpenModeSelectionPacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // client → server: player picked a mode
    public record SelectModePacket(BlockPos keyboardPos, byte modeOrdinal)
            implements CustomPacketPayload {
        public static final Type<SelectModePacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "select_mode"));
        public static final StreamCodec<FriendlyByteBuf, SelectModePacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, SelectModePacket::keyboardPos,
                        ByteBufCodecs.BYTE,    SelectModePacket::modeOrdinal,
                        SelectModePacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(KeyboardCapturePacket.TYPE,    KeyboardCapturePacket.CODEC,    ModPackets::handleKeyboardCapture);
        registrar.playToServer(KeyInputPacket.TYPE,            KeyInputPacket.CODEC,            ModPackets::handleKeyInput);
        registrar.playToServer(KeyboardReleasePacket.TYPE,     KeyboardReleasePacket.CODEC,     ModPackets::handleKeyboardRelease);
        registrar.playToServer(SaveAutoTypeScriptPacket.TYPE,  SaveAutoTypeScriptPacket.CODEC,  ModPackets::handleSaveAutoTypeScript);
        registrar.playToClient(OpenAutoTypeScreenPacket.TYPE,  OpenAutoTypeScreenPacket.CODEC,  ModPackets::handleOpenAutoTypeScreen);
        registrar.playToClient(StartCreateCapturePacket.TYPE,  StartCreateCapturePacket.CODEC,  ModPackets::handleStartCreateCapture);
        registrar.playToClient(OpenPeripheralMenuPacket.TYPE,   OpenPeripheralMenuPacket.CODEC,   ModPackets::handleOpenPeripheralMenu);
        registrar.playToServer(CallPeripheralMethodPacket.TYPE, CallPeripheralMethodPacket.CODEC, ModPackets::handleCallPeripheralMethod);
        registrar.playToClient(OpenModeSelectionPacket.TYPE,    OpenModeSelectionPacket.CODEC,    ModPackets::handleOpenModeSelection);
        registrar.playToServer(SelectModePacket.TYPE,           SelectModePacket.CODEC,           ModPackets::handleSelectMode);
        registrar.playToClient(OpenThrusterControlPacket.TYPE,   OpenThrusterControlPacket.CODEC,   ModPackets::handleOpenThrusterControl);
        registrar.playToServer(SetThrusterValuePacket.TYPE,      SetThrusterValuePacket.CODEC,      ModPackets::handleSetThrusterValue);
        registrar.playToClient(OpenSequencerPacket.TYPE,         OpenSequencerPacket.CODEC,         ModPackets::handleOpenSequencer);
        registrar.playToServer(SaveAndRunSequencerPacket.TYPE,   SaveAndRunSequencerPacket.CODEC,   ModPackets::handleSaveAndRunSequencer);
        registrar.playToServer(StopSequencerPacket.TYPE,         StopSequencerPacket.CODEC,         ModPackets::handleStopSequencer);
        registrar.playToServer(UnlinkKeyboardPacket.TYPE,        UnlinkKeyboardPacket.CODEC,        ModPackets::handleUnlinkKeyboard);
    }

    private static void handleKeyboardCapture(KeyboardCapturePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                dev.bennethogan.bennetsmod.client.KeyboardCaptureManager.setCaptureMode(
                        packet.keyboardPos(), packet.capture()));
    }

    private static void handleKeyInput(KeyInputPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity keyboard)) return;

            if (keyboard.isInlineCapturing()) {
                switch (packet.mode()) {
                    case 2 -> keyboard.inlineCaptureChar(packet.character());
                    case 1 -> { if (packet.keyCode() == 256) keyboard.inlineCaptureEsc(); }
                    case 0 -> { if (packet.keyCode() == 257) keyboard.inlineCaptureChar('\n'); }
                }
                return;
            }

            switch (packet.mode()) {
                case 0 -> keyboard.sendKeyEvent(packet.keyCode(), packet.held());
                case 1 -> keyboard.sendKeyUpEvent(packet.keyCode());
                case 2 -> keyboard.sendCharEvent(packet.character());
            }
        });
    }

    private static void handleKeyboardRelease(KeyboardReleasePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (be instanceof LinkedKeyboardBlockEntity keyboard && keyboard.isInlineCapturing())
                keyboard.inlineCaptureEsc();
        });
    }

    private static void handleSaveAutoTypeScript(SaveAutoTypeScriptPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (sp.blockPosition().distSqr(packet.keyboardPos()) > 64) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity keyboard)) return;
            keyboard.setAutoTypeScript(packet.script());
            sp.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§aAuto-type script saved."), true);
        });
    }

    private static void handleOpenAutoTypeScreen(OpenAutoTypeScreenPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            mc.setScreen(new dev.bennethogan.bennetsmod.client.screen.AutoTypeScreen(
                    packet.keyboardPos(), packet.currentScript()));
        });
    }

    private static void handleStartCreateCapture(StartCreateCapturePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                dev.bennethogan.bennetsmod.client.KeyboardCaptureManager.setCreateCaptureMode(
                        packet.keyboardPos(),
                        packet.currentValue(),
                        packet.minValue(),
                        packet.maxValue()));
    }

    private static void handleOpenPeripheralMenu(OpenPeripheralMenuPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            mc.setScreen(new dev.bennethogan.bennetsmod.client.screen.PeripheralMethodScreen(
                    packet.keyboardPos(), packet.peripheralType(), packet.getters(), packet.setters()));
        });
    }

    private static void handleCallPeripheralMethod(CallPeripheralMethodPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity keyboard)) return;

            List<BlockPos> targets = keyboard.getLinkedTargetPositions();
            if (targets.isEmpty()) return;

            String methodName = packet.methodName();
            String argString  = packet.argString();

            // If this is a setter call, broadcast it to all mesh targets first
            String callError = null;
            if (!methodName.isEmpty()) {
                for (BlockPos targetPos : targets) {
                    Object peripheral = PeripheralHelper.getPeripheral(sp.serverLevel(), targetPos);
                    if (peripheral == null) continue;
                    String err = PeripheralHelper.callSetter(peripheral, methodName, argString);
                    if (err != null && callError == null) callError = err; // report first error
                }
            }

            // Re-scan primary target to refresh the menu
            BlockPos primary = targets.get(0);
            PeripheralHelper.ScanResult result = PeripheralHelper.scanAndCall(
                    sp.serverLevel(), primary, "", "");

            if (result == null) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§c[Keyboard] §fPeripheral not found or CC:Tweaked not installed."), true);
                return;
            }
            if (callError != null) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§c[Keyboard] §f" + callError), true);
            }
            sendOpenPeripheralMenu(sp, packet.keyboardPos(), result.type(), result.getters(), result.setters());
        });
    }

    private static void handleOpenModeSelection(OpenModeSelectionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            mc.setScreen(new dev.bennethogan.bennetsmod.client.screen.ModeSelectionScreen(
                    packet.keyboardPos(), packet.targetTypeName(), packet.availableBits()));
        });
    }

    private static void handleSelectMode(SelectModePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity keyboard)) return;
            BlockPos targetPos = keyboard.getLinkedTargetPos();
            if (targetPos == null) return;

            int idx = packet.modeOrdinal() & 0xFF;
            KeyboardMode[] modes = KeyboardMode.values();
            if (idx < 0 || idx >= modes.length) return;
            KeyboardMode mode = modes[idx];

            // Server-side authority: refuse unsupported modes
            if (!mode.isAvailableAt(sp.serverLevel(), targetPos)) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§c[Keyboard] §fThat mode isn't available for this block."), true);
                return;
            }

            switch (mode) {
                case CC_COMPUTER -> {
                    keyboard.turnOnLinkedComputer();
                    sendKeyboardCapturePacket(sp, packet.keyboardPos(), true);
                    sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "§a[Universal Keyboard] §fNow typing to linked computer. Press §aESC §fto stop."), true);
                }
                case CC_PERIPHERAL -> {
                    PeripheralHelper.ScanResult result = PeripheralHelper.scanAndCall(
                            sp.serverLevel(), targetPos, "", "");
                    if (result == null) {
                        sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                "§c[Keyboard] §fPeripheral not found."), true);
                        return;
                    }
                    sendOpenPeripheralMenu(sp, packet.keyboardPos(), result.type(), result.getters(), result.setters());
                }
                case VALUE_PANEL -> {
                    BlockEntity targetBe = sp.serverLevel().getBlockEntity(targetPos);
                    if (targetBe == null || !CreateValueHelper.hasScrollValue(targetBe)) {
                        sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                "§c[Keyboard] §fLinked block no longer has a scroll value."), true);
                        return;
                    }
                    int current = CreateValueHelper.getValue(targetBe);
                    int min     = CreateValueHelper.getMin(targetBe);
                    int max     = CreateValueHelper.getMax(targetBe);
                    keyboard.startInlineCapture();
                    sendStartCreateCapture(sp, packet.keyboardPos(), current, min, max);
                }
                case THRUSTER_CONTROL -> {
                    PeripheralHelper.ThrusterState state =
                            PeripheralHelper.scanThruster(sp.serverLevel(), targetPos);
                    if (state == null) {
                        sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                "§c[Keyboard] §fThruster not found or CC:Tweaked not installed."), true);
                        return;
                    }
                    sendOpenThrusterControl(sp, packet.keyboardPos(), state);
                }
                case PERIPHERAL_SEQUENCER -> sendOpenSequencer(sp, packet.keyboardPos(), keyboard);
            }
        });
    }

    // client → server
    public static void sendKeyInputPacket(BlockPos pos, int keyCode, boolean held) {
        PacketDistributor.sendToServer(KeyInputPacket.keyPress(pos, keyCode, held));
    }
    public static void sendKeyUpPacket(BlockPos pos, int keyCode) {
        PacketDistributor.sendToServer(KeyInputPacket.keyUp(pos, keyCode));
    }
    public static void sendCharPacket(BlockPos pos, char character) {
        PacketDistributor.sendToServer(KeyInputPacket.charEvent(pos, character));
    }
    public static void sendKeyboardReleasePacket(BlockPos pos) {
        PacketDistributor.sendToServer(new KeyboardReleasePacket(pos));
    }
    public static void sendSaveAutoTypeScript(BlockPos pos, String script) {
        PacketDistributor.sendToServer(new SaveAutoTypeScriptPacket(pos, script));
    }

    // server → client
    public static void sendKeyboardCapturePacket(ServerPlayer player, BlockPos pos, boolean capture) {
        PacketDistributor.sendToPlayer(player, new KeyboardCapturePacket(pos, capture));
    }
    public static void sendOpenAutoTypeScreen(ServerPlayer player, BlockPos pos, String currentScript) {
        PacketDistributor.sendToPlayer(player, new OpenAutoTypeScreenPacket(pos, currentScript));
    }
    public static void sendStartCreateCapture(ServerPlayer player, BlockPos keyboardPos,
                                               int currentValue, int min, int max) {
        PacketDistributor.sendToPlayer(player,
                new StartCreateCapturePacket(keyboardPos, currentValue, min, max));
    }
    public static void sendOpenPeripheralMenu(ServerPlayer player, BlockPos keyboardPos,
                                               String type, List<String[]> getters, List<String[]> setters) {
        PacketDistributor.sendToPlayer(player, new OpenPeripheralMenuPacket(keyboardPos, type, getters, setters));
    }
    public static void sendOpenModeSelection(ServerPlayer player, BlockPos keyboardPos,
                                              String targetTypeName, int availableBits) {
        PacketDistributor.sendToPlayer(player,
                new OpenModeSelectionPacket(keyboardPos, targetTypeName, availableBits));
    }
    public static void sendSelectMode(BlockPos keyboardPos, KeyboardMode mode) {
        PacketDistributor.sendToServer(new SelectModePacket(keyboardPos, (byte) mode.ordinal()));
    }
    public static void sendCallPeripheralMethod(BlockPos keyboardPos, String methodName, String argString) {
        PacketDistributor.sendToServer(new CallPeripheralMethodPacket(keyboardPos, methodName, argString));
    }

    // ── Sequencer packets ──────────────────────────────────────────────────────

    // server → client: open sequencer editor
    public record OpenSequencerPacket(
            BlockPos keyboardPos,
            List<SequencerStep> steps,
            boolean running,
            int currentStep,
            String peripheralType,
            List<String> availableGetterNames,
            List<String[]> availableSetters
    ) implements CustomPacketPayload {
        public static final Type<OpenSequencerPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "open_sequencer"));
        public static final StreamCodec<FriendlyByteBuf, OpenSequencerPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> {
                    BlockPos.STREAM_CODEC.encode(buf, pkt.keyboardPos());
                    buf.writeInt(pkt.steps().size());
                    for (SequencerStep s : pkt.steps()) s.encode(buf);
                    buf.writeBoolean(pkt.running());
                    buf.writeInt(pkt.currentStep());
                    buf.writeUtf(pkt.peripheralType());
                    buf.writeInt(pkt.availableGetterNames().size());
                    for (String g : pkt.availableGetterNames()) buf.writeUtf(g);
                    buf.writeInt(pkt.availableSetters().size());
                    for (String[] s : pkt.availableSetters()) { buf.writeUtf(s[0]); buf.writeUtf(s[1]); }
                },
                buf -> {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    int sc = buf.readInt();
                    List<SequencerStep> steps = new ArrayList<>(sc);
                    for (int i = 0; i < sc; i++) steps.add(SequencerStep.decode(buf));
                    boolean running    = buf.readBoolean();
                    int currentStep    = buf.readInt();
                    String pType       = buf.readUtf();
                    int gc = buf.readInt();
                    List<String> getters = new ArrayList<>(gc);
                    for (int i = 0; i < gc; i++) getters.add(buf.readUtf());
                    int stc = buf.readInt();
                    List<String[]> setters = new ArrayList<>(stc);
                    for (int i = 0; i < stc; i++) setters.add(new String[]{ buf.readUtf(), buf.readUtf() });
                    return new OpenSequencerPacket(pos, steps, running, currentStep, pType, getters, setters);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // client → server: save steps (and optionally start running)
    public record SaveAndRunSequencerPacket(
            BlockPos keyboardPos, List<SequencerStep> steps, boolean run
    ) implements CustomPacketPayload {
        public static final Type<SaveAndRunSequencerPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "save_and_run_sequencer"));
        public static final StreamCodec<FriendlyByteBuf, SaveAndRunSequencerPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> {
                    BlockPos.STREAM_CODEC.encode(buf, pkt.keyboardPos());
                    buf.writeInt(pkt.steps().size());
                    for (SequencerStep s : pkt.steps()) s.encode(buf);
                    buf.writeBoolean(pkt.run());
                },
                buf -> {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    int sc = buf.readInt();
                    List<SequencerStep> steps = new ArrayList<>(sc);
                    for (int i = 0; i < sc; i++) steps.add(SequencerStep.decode(buf));
                    return new SaveAndRunSequencerPacket(pos, steps, buf.readBoolean());
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // client → server: unlink keyboard from its target
    public record UnlinkKeyboardPacket(BlockPos keyboardPos) implements CustomPacketPayload {
        public static final Type<UnlinkKeyboardPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "unlink_keyboard"));
        public static final StreamCodec<FriendlyByteBuf, UnlinkKeyboardPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> BlockPos.STREAM_CODEC.encode(buf, pkt.keyboardPos()),
                buf -> new UnlinkKeyboardPacket(BlockPos.STREAM_CODEC.decode(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // client → server: stop sequencer
    public record StopSequencerPacket(BlockPos keyboardPos) implements CustomPacketPayload {
        public static final Type<StopSequencerPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "stop_sequencer"));
        public static final StreamCodec<FriendlyByteBuf, StopSequencerPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> BlockPos.STREAM_CODEC.encode(buf, pkt.keyboardPos()),
                buf -> new StopSequencerPacket(BlockPos.STREAM_CODEC.decode(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ── End sequencer packets ──────────────────────────────────────────────────

    public static void sendOpenThrusterControl(ServerPlayer player, BlockPos keyboardPos,
                                                PeripheralHelper.ThrusterState state) {
        PacketDistributor.sendToPlayer(player, new OpenThrusterControlPacket(
                keyboardPos, state.type(),
                state.targetVectorX(), state.targetVectorY(),
                state.currentVectorX(), state.currentVectorY(),
                state.thrust(), state.thrustConfig(),
                state.currentThrustPn(), state.displayedThrustPn(),
                state.airflowMs(), state.obstruction(),
                state.fuelAmountMb(), state.fuelCapacityMb()
        ));
    }

    public static void sendSetThrusterValue(BlockPos keyboardPos, String methodName, double value) {
        PacketDistributor.sendToServer(new SetThrusterValuePacket(keyboardPos, methodName, value));
    }

    public static void sendUnlinkKeyboard(BlockPos keyboardPos) {
        PacketDistributor.sendToServer(new UnlinkKeyboardPacket(keyboardPos));
    }

    private static void handleOpenThrusterControl(OpenThrusterControlPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.screen instanceof dev.bennethogan.bennetsmod.client.screen.ThrusterControlScreen existing
                    && existing.getKeyboardPos().equals(packet.keyboardPos())) {
                existing.updateState(
                        packet.peripheralType(),
                        packet.targetVectorX(), packet.targetVectorY(),
                        packet.currentVectorX(), packet.currentVectorY(),
                        packet.thrust(), packet.thrustConfig(),
                        packet.currentThrustPn(), packet.displayedThrustPn(),
                        packet.airflowMs(), packet.obstruction(),
                        packet.fuelAmountMb(), packet.fuelCapacityMb());
            } else {
                mc.setScreen(new dev.bennethogan.bennetsmod.client.screen.ThrusterControlScreen(
                        packet.keyboardPos(), packet.peripheralType(),
                        packet.targetVectorX(), packet.targetVectorY(),
                        packet.currentVectorX(), packet.currentVectorY(),
                        packet.thrust(), packet.thrustConfig(),
                        packet.currentThrustPn(), packet.displayedThrustPn(),
                        packet.airflowMs(), packet.obstruction(),
                        packet.fuelAmountMb(), packet.fuelCapacityMb()));
            }
        });
    }

    private static void handleSetThrusterValue(SetThrusterValuePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity keyboard)) return;

            List<BlockPos> targets = keyboard.getLinkedTargetPositions();
            if (targets.isEmpty()) return;

            for (BlockPos targetPos : targets) {
                Object peripheral = PeripheralHelper.getPeripheral(sp.serverLevel(), targetPos);
                if (peripheral == null) continue;
                PeripheralHelper.callMethodWithDouble(peripheral, packet.methodName(), packet.argValue());
            }

            PeripheralHelper.ThrusterState state =
                    PeripheralHelper.scanThruster(sp.serverLevel(), targets.get(0));
            if (state != null) {
                sendOpenThrusterControl(sp, packet.keyboardPos(), state);
            }
        });
    }

    // ── Sequencer handlers ────────────────────────────────────────────────────

    private static void handleOpenSequencer(OpenSequencerPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.screen instanceof dev.bennethogan.bennetsmod.client.screen.SequencerScreen existing
                    && existing.getKeyboardPos().equals(packet.keyboardPos())) {
                existing.updateState(packet.steps(), packet.running(), packet.currentStep(),
                        packet.availableGetterNames(), packet.availableSetters());
            } else {
                mc.setScreen(new dev.bennethogan.bennetsmod.client.screen.SequencerScreen(
                        packet.keyboardPos(), packet.steps(), packet.running(), packet.currentStep(),
                        packet.peripheralType(), packet.availableGetterNames(), packet.availableSetters()));
            }
        });
    }

    private static void handleSaveAndRunSequencer(SaveAndRunSequencerPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity keyboard)) return;
            keyboard.setSequencerSteps(packet.steps());
            if (packet.run()) keyboard.startSequencer();
            sendOpenSequencer(sp, packet.keyboardPos(), keyboard);
        });
    }

    private static void handleUnlinkKeyboard(UnlinkKeyboardPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (sp.serverLevel().getBlockEntity(packet.keyboardPos()) instanceof LinkedKeyboardBlockEntity be)
                be.unlink();
        });
    }

    private static void handleStopSequencer(StopSequencerPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (be instanceof LinkedKeyboardBlockEntity keyboard) {
                keyboard.stopSequencer();
                sendOpenSequencer(sp, packet.keyboardPos(), keyboard);
            }
        });
    }

    private static void sendOpenSequencer(ServerPlayer player, BlockPos keyboardPos,
                                           LinkedKeyboardBlockEntity keyboard) {
        BlockPos primary = keyboard.getLinkedTargetPos();
        String pType = "";
        List<String> getterNames = List.of();
        List<String[]> setters   = List.of();
        if (primary != null && !keyboard.isLinkedAsComputer()) {
            // Skip scan for CC computers — their methods (getID, isOn…) aren't useful
            // in the sequencer; TYPE_TEXT steps handle all CC computer interaction.
            PeripheralHelper.ScanResult result = PeripheralHelper.scanAndCall(
                    player.serverLevel(), primary, "", "");
            if (result != null) {
                pType       = result.type();
                getterNames = result.getters().stream().map(g -> g[0]).toList();
                setters     = result.setters();
            }
        } else if (primary != null) {
            pType = "CC Computer";
        }
        PacketDistributor.sendToPlayer(player, new OpenSequencerPacket(
                keyboardPos, keyboard.getSequencerSteps(),
                keyboard.isSequencerRunning(), keyboard.getSequencerCurrentStep(),
                pType, getterNames, setters));
    }

    public static void sendSaveAndRunSequencer(BlockPos keyboardPos, List<SequencerStep> steps, boolean run) {
        PacketDistributor.sendToServer(new SaveAndRunSequencerPacket(keyboardPos, steps, run));
    }

    public static void sendStopSequencer(BlockPos keyboardPos) {
        PacketDistributor.sendToServer(new StopSequencerPacket(keyboardPos));
    }
}
