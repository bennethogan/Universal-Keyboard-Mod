package dev.bennethogan.bennetsmod.network;

import dev.bennethogan.bennetsmod.UniversalKeyboardMod;
import dev.bennethogan.bennetsmod.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.bennetsmod.compat.CreateValueHelper;
import dev.bennethogan.bennetsmod.compat.KeyboardMode;
import dev.bennethogan.bennetsmod.compat.PeripheralHelper;
import dev.bennethogan.bennetsmod.item.LinkedKeyboardItem;
import dev.bennethogan.bennetsmod.sequencer.SequencerStep;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
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

    // server → client: open peripheral method browser (channel included for button display)
    public record OpenPeripheralMenuPacket(
            BlockPos keyboardPos, String peripheralType,
            List<String[]> getters, List<String[]> setters,
            int channel
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
                    buf.writeInt(pkt.channel());
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
                    int channel = buf.readInt();
                    return new OpenPeripheralMenuPacket(pos, type, getters, setters, channel);
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

    // server → client: open the thruster control screen (channel included for button display)
    public record OpenThrusterControlPacket(
            BlockPos keyboardPos, String peripheralType,
            double targetVectorX, double targetVectorY,
            double currentVectorX, double currentVectorY,
            int thrust, int thrustConfig, double configMax,
            double currentThrustPn, double displayedThrustPn,
            double airflowMs, int obstruction,
            int fuelAmountMb, int fuelCapacityMb,
            int channel
    ) implements CustomPacketPayload {
        public static final Type<OpenThrusterControlPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "open_thruster_control"));
        public static final StreamCodec<FriendlyByteBuf, OpenThrusterControlPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> {
                    BlockPos.STREAM_CODEC.encode(buf, pkt.keyboardPos());
                    buf.writeUtf(pkt.peripheralType());
                    buf.writeDouble(pkt.targetVectorX()); buf.writeDouble(pkt.targetVectorY());
                    buf.writeDouble(pkt.currentVectorX()); buf.writeDouble(pkt.currentVectorY());
                    buf.writeInt(pkt.thrust()); buf.writeInt(pkt.thrustConfig()); buf.writeDouble(pkt.configMax());
                    buf.writeDouble(pkt.currentThrustPn()); buf.writeDouble(pkt.displayedThrustPn());
                    buf.writeDouble(pkt.airflowMs()); buf.writeInt(pkt.obstruction());
                    buf.writeInt(pkt.fuelAmountMb()); buf.writeInt(pkt.fuelCapacityMb());
                    buf.writeInt(pkt.channel());
                },
                buf -> new OpenThrusterControlPacket(
                        BlockPos.STREAM_CODEC.decode(buf), buf.readUtf(),
                        buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readDouble(),
                        buf.readInt(), buf.readInt(), buf.readDouble(),
                        buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readInt(),
                        buf.readInt(), buf.readInt(),
                        buf.readInt()
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

    // client → server: set both vector axes atomically (calls setVector(x,y) on the peripheral)
    public record SetThrusterVectorPacket(
            BlockPos keyboardPos, double x, double y
    ) implements CustomPacketPayload {
        public static final Type<SetThrusterVectorPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "set_thruster_vector"));
        public static final StreamCodec<FriendlyByteBuf, SetThrusterVectorPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> {
                    BlockPos.STREAM_CODEC.encode(buf, pkt.keyboardPos());
                    buf.writeDouble(pkt.x());
                    buf.writeDouble(pkt.y());
                },
                buf -> new SetThrusterVectorPacket(
                        BlockPos.STREAM_CODEC.decode(buf), buf.readDouble(), buf.readDouble()
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

    // ── Channel packets ──────────────────────────────────────────────────────

    // client → server: set active channel on a placed keyboard block entity
    public record SetActiveChannelPacket(BlockPos keyboardPos, int channel) implements CustomPacketPayload {
        public static final Type<SetActiveChannelPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "set_active_channel"));
        public static final StreamCodec<FriendlyByteBuf, SetActiveChannelPacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, SetActiveChannelPacket::keyboardPos,
                        ByteBufCodecs.INT,     SetActiveChannelPacket::channel,
                        SetActiveChannelPacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // server → client: inform client of the current active channel (for HUD display)
    public record ChannelChangedPacket(BlockPos keyboardPos, int channel) implements CustomPacketPayload {
        public static final Type<ChannelChangedPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "channel_changed"));
        public static final StreamCodec<FriendlyByteBuf, ChannelChangedPacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, ChannelChangedPacket::keyboardPos,
                        ByteBufCodecs.INT,     ChannelChangedPacket::channel,
                        ChannelChangedPacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // client → server: set active channel on the held keyboard item (linking mode scroll)
    public record SetLinkingChannelPacket(int channel) implements CustomPacketPayload {
        public static final Type<SetLinkingChannelPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "set_linking_channel"));
        public static final StreamCodec<FriendlyByteBuf, SetLinkingChannelPacket> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT, SetLinkingChannelPacket::channel,
                        SetLinkingChannelPacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // client → server: cycle active channel on a placed keyboard and re-open the given mode's screen
    public record CycleChannelAndReopenPacket(BlockPos keyboardPos, byte modeOrdinal) implements CustomPacketPayload {
        public static final Type<CycleChannelAndReopenPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "cycle_channel_reopen"));
        public static final StreamCodec<FriendlyByteBuf, CycleChannelAndReopenPacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, CycleChannelAndReopenPacket::keyboardPos,
                        ByteBufCodecs.BYTE,    CycleChannelAndReopenPacket::modeOrdinal,
                        CycleChannelAndReopenPacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ── End channel packets ──────────────────────────────────────────────────

    public static void onRegisterServerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // playToServer — handlers run on the server, safe to register from the common @Mod class
        registrar.playToServer(KeyInputPacket.TYPE,               KeyInputPacket.CODEC,               ModPackets::handleKeyInput);
        registrar.playToServer(KeyboardReleasePacket.TYPE,        KeyboardReleasePacket.CODEC,        ModPackets::handleKeyboardRelease);
        registrar.playToServer(SaveAutoTypeScriptPacket.TYPE,     SaveAutoTypeScriptPacket.CODEC,     ModPackets::handleSaveAutoTypeScript);
        registrar.playToServer(CallPeripheralMethodPacket.TYPE,   CallPeripheralMethodPacket.CODEC,   ModPackets::handleCallPeripheralMethod);
        registrar.playToServer(SelectModePacket.TYPE,             SelectModePacket.CODEC,             ModPackets::handleSelectMode);
        registrar.playToServer(SetThrusterValuePacket.TYPE,       SetThrusterValuePacket.CODEC,       ModPackets::handleSetThrusterValue);
        registrar.playToServer(SetThrusterVectorPacket.TYPE,      SetThrusterVectorPacket.CODEC,      ModPackets::handleSetThrusterVector);
        registrar.playToServer(SaveAndRunSequencerPacket.TYPE,    SaveAndRunSequencerPacket.CODEC,    ModPackets::handleSaveAndRunSequencer);
        registrar.playToServer(StopSequencerPacket.TYPE,          StopSequencerPacket.CODEC,          ModPackets::handleStopSequencer);
        registrar.playToServer(UnlinkKeyboardPacket.TYPE,         UnlinkKeyboardPacket.CODEC,         ModPackets::handleUnlinkKeyboard);
        registrar.playToServer(SetActiveChannelPacket.TYPE,       SetActiveChannelPacket.CODEC,       ModPackets::handleSetActiveChannel);
        registrar.playToServer(SetLinkingChannelPacket.TYPE,      SetLinkingChannelPacket.CODEC,      ModPackets::handleSetLinkingChannel);
        registrar.playToServer(CycleChannelAndReopenPacket.TYPE,  CycleChannelAndReopenPacket.CODEC,  ModPackets::handleCycleChannelAndReopen);

        // playToClient — the server must declare these channels so the handshake succeeds.
        // Real handlers are registered by ClientPacketHandlers (client only); skip here on client
        // to avoid double-registration. No-op lambdas contain no client-only class references.
        if (!net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            registrar.playToClient(KeyboardCapturePacket.TYPE,     KeyboardCapturePacket.CODEC,     (p, c) -> {});
            registrar.playToClient(OpenAutoTypeScreenPacket.TYPE,  OpenAutoTypeScreenPacket.CODEC,  (p, c) -> {});
            registrar.playToClient(StartCreateCapturePacket.TYPE,  StartCreateCapturePacket.CODEC,  (p, c) -> {});
            registrar.playToClient(OpenPeripheralMenuPacket.TYPE,  OpenPeripheralMenuPacket.CODEC,  (p, c) -> {});
            registrar.playToClient(OpenModeSelectionPacket.TYPE,   OpenModeSelectionPacket.CODEC,   (p, c) -> {});
            registrar.playToClient(OpenThrusterControlPacket.TYPE, OpenThrusterControlPacket.CODEC, (p, c) -> {});
            registrar.playToClient(OpenSequencerPacket.TYPE,       OpenSequencerPacket.CODEC,       (p, c) -> {});
            registrar.playToClient(ChannelChangedPacket.TYPE,      ChannelChangedPacket.CODEC,      (p, c) -> {});
        }
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

    static void handleSaveAutoTypeScript(SaveAutoTypeScriptPacket packet, IPayloadContext ctx) {
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

    private static void handleCallPeripheralMethod(CallPeripheralMethodPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity keyboard)) return;

            List<BlockPos> targets = keyboard.getLinkedTargetPositions();
            if (targets.isEmpty()) return;

            String methodName = packet.methodName();
            String argString  = packet.argString();

            String callError = null;
            if (!methodName.isEmpty()) {
                for (BlockPos targetPos : targets) {
                    Object peripheral = PeripheralHelper.getPeripheral(sp.serverLevel(), targetPos);
                    if (peripheral == null) continue;
                    String err = PeripheralHelper.callSetter(peripheral, methodName, argString);
                    if (err != null && callError == null) callError = err;
                }
            }

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
            sendOpenPeripheralMenu(sp, packet.keyboardPos(), result.type(), result.getters(),
                    result.setters(), keyboard.getActiveChannel());
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

            if (!mode.isAvailableAt(sp.serverLevel(), targetPos)) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§c[Keyboard] §fThat mode isn't available for this block."), true);
                return;
            }

            openModeForPlayer(sp, packet.keyboardPos(), keyboard, targetPos, mode);
        });
    }

    private static void openModeForPlayer(ServerPlayer sp, BlockPos keyboardPos,
                                           LinkedKeyboardBlockEntity keyboard,
                                           BlockPos targetPos, KeyboardMode mode) {
        switch (mode) {
            case CC_COMPUTER -> {
                keyboard.turnOnLinkedComputer();
                sendKeyboardCapturePacket(sp, keyboardPos, true);
                PacketDistributor.sendToPlayer(sp,
                        new ChannelChangedPacket(keyboardPos, keyboard.getActiveChannel()));
                sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§a[Universal Keyboard] §fNow typing — Channel §e" + keyboard.getActiveChannel() +
                        "§f. Scroll to change channel. Press §aESC §fto stop."), true);
            }
            case CC_PERIPHERAL -> {
                PeripheralHelper.ScanResult result = PeripheralHelper.scanAndCall(
                        sp.serverLevel(), targetPos, "", "");
                if (result == null) {
                    sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "§c[Keyboard] §fPeripheral not found."), true);
                    return;
                }
                sendOpenPeripheralMenu(sp, keyboardPos, result.type(), result.getters(),
                        result.setters(), keyboard.getActiveChannel());
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
                sendStartCreateCapture(sp, keyboardPos, current, min, max);
                PacketDistributor.sendToPlayer(sp,
                        new ChannelChangedPacket(keyboardPos, keyboard.getActiveChannel()));
            }
            case THRUSTER_CONTROL -> {
                PeripheralHelper.ThrusterState state =
                        PeripheralHelper.scanThruster(sp.serverLevel(), targetPos);
                if (state == null) {
                    sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "§c[Keyboard] §fThruster not found or CC:Tweaked not installed."), true);
                    return;
                }
                sendOpenThrusterControl(sp, keyboardPos, state, keyboard.getActiveChannel());
            }
            case PERIPHERAL_SEQUENCER -> sendOpenSequencer(sp, keyboardPos, keyboard);
        }
    }

    // ── Channel handlers ─────────────────────────────────────────────────────

    private static void handleSetActiveChannel(SetActiveChannelPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity keyboard)) return;
            keyboard.setActiveChannel(packet.channel());
            PacketDistributor.sendToPlayer(sp,
                    new ChannelChangedPacket(packet.keyboardPos(), keyboard.getActiveChannel()));
        });
    }

    private static void handleSetLinkingChannel(SetLinkingChannelPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            // Update active_channel on the held keyboard item (main hand or off hand)
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack held = sp.getItemInHand(hand);
                if (held.getItem() instanceof LinkedKeyboardItem) {
                    LinkedKeyboardItem.setActiveLinkingChannel(held, packet.channel());
                    // Trigger inventory sync so client sees updated channel in tooltip
                    sp.inventoryMenu.sendAllDataToRemote();
                    return;
                }
            }
        });
    }

    private static void handleCycleChannelAndReopen(CycleChannelAndReopenPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity keyboard)) return;

            keyboard.cycleActiveChannel();

            BlockPos targetPos = keyboard.getLinkedTargetPos();
            if (targetPos == null) {
                // No target on new channel — still notify client
                PacketDistributor.sendToPlayer(sp,
                        new ChannelChangedPacket(packet.keyboardPos(), keyboard.getActiveChannel()));
                sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§b[Keyboard] §fChannel §e" + keyboard.getActiveChannel() + "§f — no device linked."), true);
                return;
            }

            int idx = packet.modeOrdinal() & 0xFF;
            KeyboardMode[] modes = KeyboardMode.values();
            if (idx < 0 || idx >= modes.length) return;
            KeyboardMode mode = modes[idx];

            if (!mode.isAvailableAt(sp.serverLevel(), targetPos)) {
                // Channel has a device but it's not compatible with this mode — show mode selection
                int bits = KeyboardMode.availableBitfield(sp.serverLevel(), targetPos);
                String typeName = sp.serverLevel().getBlockState(targetPos).getBlock().getName().getString();
                sendOpenModeSelection(sp, packet.keyboardPos(), typeName, bits);
                return;
            }

            openModeForPlayer(sp, packet.keyboardPos(), keyboard, targetPos, mode);
        });
    }

    // ── Senders ──────────────────────────────────────────────────────────────

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
    public static void sendSetActiveChannel(BlockPos keyboardPos, int channel) {
        PacketDistributor.sendToServer(new SetActiveChannelPacket(keyboardPos, channel));
    }
    public static void sendSetLinkingChannel(int channel) {
        PacketDistributor.sendToServer(new SetLinkingChannelPacket(channel));
    }
    public static void sendCycleChannelAndReopen(BlockPos keyboardPos, KeyboardMode mode) {
        PacketDistributor.sendToServer(new CycleChannelAndReopenPacket(keyboardPos, (byte) mode.ordinal()));
    }

    // server → client senders
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
                                               String type, List<String[]> getters,
                                               List<String[]> setters, int channel) {
        PacketDistributor.sendToPlayer(player,
                new OpenPeripheralMenuPacket(keyboardPos, type, getters, setters, channel));
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

    // ── Sequencer packets ─────────────────────────────────────────────────────

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

    public record UnlinkKeyboardPacket(BlockPos keyboardPos) implements CustomPacketPayload {
        public static final Type<UnlinkKeyboardPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "unlink_keyboard"));
        public static final StreamCodec<FriendlyByteBuf, UnlinkKeyboardPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> BlockPos.STREAM_CODEC.encode(buf, pkt.keyboardPos()),
                buf -> new UnlinkKeyboardPacket(BlockPos.STREAM_CODEC.decode(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record StopSequencerPacket(BlockPos keyboardPos) implements CustomPacketPayload {
        public static final Type<StopSequencerPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "stop_sequencer"));
        public static final StreamCodec<FriendlyByteBuf, StopSequencerPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> BlockPos.STREAM_CODEC.encode(buf, pkt.keyboardPos()),
                buf -> new StopSequencerPacket(BlockPos.STREAM_CODEC.decode(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void sendOpenThrusterControl(ServerPlayer player, BlockPos keyboardPos,
                                                PeripheralHelper.ThrusterState state, int channel) {
        PacketDistributor.sendToPlayer(player, new OpenThrusterControlPacket(
                keyboardPos, state.type(),
                state.targetVectorX(), state.targetVectorY(),
                state.currentVectorX(), state.currentVectorY(),
                state.thrust(), state.thrustConfig(), state.configMax(),
                state.currentThrustPn(), state.displayedThrustPn(),
                state.airflowMs(), state.obstruction(),
                state.fuelAmountMb(), state.fuelCapacityMb(),
                channel
        ));
    }

    public static void sendSetThrusterValue(BlockPos keyboardPos, String methodName, double value) {
        PacketDistributor.sendToServer(new SetThrusterValuePacket(keyboardPos, methodName, value));
    }

    public static void sendUnlinkKeyboard(BlockPos keyboardPos) {
        PacketDistributor.sendToServer(new UnlinkKeyboardPacket(keyboardPos));
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
                sendOpenThrusterControl(sp, packet.keyboardPos(), state, keyboard.getActiveChannel());
            }
        });
    }

    private static void handleSetThrusterVector(SetThrusterVectorPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity keyboard)) return;

            List<BlockPos> targets = keyboard.getLinkedTargetPositions();
            if (targets.isEmpty()) return;

            for (BlockPos targetPos : targets) {
                Object peripheral = PeripheralHelper.getPeripheral(sp.serverLevel(), targetPos);
                if (peripheral == null) continue;
                PeripheralHelper.callVectorSetter(peripheral, packet.x(), packet.y());
            }

            PeripheralHelper.ThrusterState state =
                    PeripheralHelper.scanThruster(sp.serverLevel(), targets.get(0));
            if (state != null) {
                sendOpenThrusterControl(sp, packet.keyboardPos(), state, keyboard.getActiveChannel());
            }
        });
    }

    public static void sendSetThrusterVector(BlockPos keyboardPos, double x, double y) {
        PacketDistributor.sendToServer(new SetThrusterVectorPacket(keyboardPos, x, y));
    }

    // ── Sequencer handlers ────────────────────────────────────────────────────

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
