package dev.bennethogan.universalkeyboard.network;

import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.universalkeyboard.blockentity.WirelessCopycatBlockEntity;
import dev.bennethogan.universalkeyboard.compat.CreateValueHelper;
import dev.bennethogan.universalkeyboard.compat.KeyboardMode;
import dev.bennethogan.universalkeyboard.compat.PeripheralHelper;
import dev.bennethogan.universalkeyboard.compat.SableCompat;
import dev.bennethogan.universalkeyboard.wireless.rs.WirelessRSNetwork;
import net.minecraft.world.level.Level;
import dev.bennethogan.universalkeyboard.item.LinkedKeyboardItem;
import dev.bennethogan.universalkeyboard.sequencer.SequencerStep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
            int channel,
            double[] sublevelSnapshot
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
                    double[] snap = pkt.sublevelSnapshot();
                    buf.writeInt(snap.length);
                    for (double d : snap) buf.writeDouble(d);
                },
                buf -> {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    String type = buf.readUtf();
                    double tvx = buf.readDouble(), tvy = buf.readDouble();
                    double cvx = buf.readDouble(), cvy = buf.readDouble();
                    int thrust = buf.readInt(), thrustCfg = buf.readInt();
                    double cfgMax = buf.readDouble();
                    double curPn = buf.readDouble(), dispPn = buf.readDouble();
                    double airflow = buf.readDouble(); int obstr = buf.readInt();
                    int fuelAmt = buf.readInt(), fuelCap = buf.readInt();
                    int channel = buf.readInt();
                    int snapLen = buf.readInt();
                    double[] snap = new double[snapLen];
                    for (int i = 0; i < snapLen; i++) snap[i] = buf.readDouble();
                    return new OpenThrusterControlPacket(pos, type, tvx, tvy, cvx, cvy,
                            thrust, thrustCfg, cfgMax, curPn, dispPn, airflow, obstr,
                            fuelAmt, fuelCap, channel, snap);
                });
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

    // client → server: open the wireless redstone config menu for this keyboard
    public record OpenWirelessConfigPacket(BlockPos keyboardPos) implements CustomPacketPayload {
        public static final Type<OpenWirelessConfigPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "open_wireless_config"));
        public static final StreamCodec<FriendlyByteBuf, OpenWirelessConfigPacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, OpenWirelessConfigPacket::keyboardPos,
                        OpenWirelessConfigPacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // client → server: add/remove a wireless entry
    public record WirelessAddRemovePacket(BlockPos keyboardPos, boolean add) implements CustomPacketPayload {
        public static final Type<WirelessAddRemovePacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "wireless_add_remove"));
        public static final StreamCodec<FriendlyByteBuf, WirelessAddRemovePacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC,  WirelessAddRemovePacket::keyboardPos,
                        ByteBufCodecs.BOOL,     WirelessAddRemovePacket::add,
                        WirelessAddRemovePacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // Client → server: set a single ghost slot without using the vanilla container-click state-ID system
    public record WirelessGhostSetPacket(BlockPos keyboardPos, int slotIdx, ItemStack item) implements CustomPacketPayload {
        public static final Type<WirelessGhostSetPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "wireless_ghost_set"));
        // ItemStack.OPTIONAL_STREAM_CODEC requires RegistryFriendlyByteBuf (needs registry context for item lookup)
        public static final StreamCodec<RegistryFriendlyByteBuf, WirelessGhostSetPacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC,           WirelessGhostSetPacket::keyboardPos,
                        ByteBufCodecs.INT,               WirelessGhostSetPacket::slotIdx,
                        ItemStack.OPTIONAL_STREAM_CODEC, WirelessGhostSetPacket::item,
                        WirelessGhostSetPacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // client → server: re-read thruster state and push updated OpenThrusterControlPacket
    public record RequestThrusterRefreshPacket(BlockPos keyboardPos) implements CustomPacketPayload {
        public static final Type<RequestThrusterRefreshPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "request_thruster_refresh"));
        public static final StreamCodec<FriendlyByteBuf, RequestThrusterRefreshPacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, RequestThrusterRefreshPacket::keyboardPos,
                        RequestThrusterRefreshPacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

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
        registrar.playToServer(SequencerWatchPacket.TYPE,             SequencerWatchPacket.CODEC,             ModPackets::handleSequencerWatch);
        registrar.playToServer(TypewriterScanPacket.TYPE,            TypewriterScanPacket.CODEC,            ModPackets::handleTypewriterScan);
        registrar.playToServer(TypewriterImportConfirmPacket.TYPE,   TypewriterImportConfirmPacket.CODEC,   ModPackets::handleTypewriterConfirm);
        registrar.playToServer(UnlinkKeyboardPacket.TYPE,            UnlinkKeyboardPacket.CODEC,            ModPackets::handleUnlinkKeyboard);
        registrar.optional().playToServer(ResetLinksPacket.TYPE,                ResetLinksPacket.CODEC,                ModPackets::handleResetLinks);
        registrar.playToServer(SetActiveChannelPacket.TYPE,       SetActiveChannelPacket.CODEC,       ModPackets::handleSetActiveChannel);
        registrar.playToServer(SetLinkingChannelPacket.TYPE,      SetLinkingChannelPacket.CODEC,      ModPackets::handleSetLinkingChannel);
        registrar.playToServer(CycleChannelAndReopenPacket.TYPE,    CycleChannelAndReopenPacket.CODEC,    ModPackets::handleCycleChannelAndReopen);
        registrar.playToServer(OpenLiveControlPacket.TYPE,          OpenLiveControlPacket.CODEC,          ModPackets::handleOpenLiveControl);
        registrar.playToServer(SaveLiveBindingsPacket.TYPE,         SaveLiveBindingsPacket.CODEC,         ModPackets::handleSaveLiveBindings);
        registrar.playToServer(LiveActionPacket.TYPE,               LiveActionPacket.CODEC,               ModPackets::handleLiveAction);
        registrar.optional().playToServer(RequestThrusterRefreshPacket.TYPE,   RequestThrusterRefreshPacket.CODEC,   ModPackets::handleRequestThrusterRefresh);
        registrar.optional().playToServer(OpenWirelessConfigPacket.TYPE,       OpenWirelessConfigPacket.CODEC,       ModPackets::handleOpenWirelessConfig);
        registrar.optional().playToServer(WirelessAddRemovePacket.TYPE,        WirelessAddRemovePacket.CODEC,        ModPackets::handleWirelessAddRemove);
        registrar.optional().playToServer(WirelessGhostSetPacket.TYPE,         WirelessGhostSetPacket.CODEC,         ModPackets::handleWirelessGhostSet);
        registrar.optional().playToServer(SaveWirelessCopycatConfigPacket.TYPE,   SaveWirelessCopycatConfigPacket.STREAM_CODEC,   ModPackets::handleSaveWirelessCopycatConfig);
        registrar.optional().playToServer(TestWirelessCopycatFacePacket.TYPE,     TestWirelessCopycatFacePacket.STREAM_CODEC,     ModPackets::handleTestWirelessCopycatFace);
        registrar.optional().playToServer(LocateWirelessCopycatPacket.TYPE,       LocateWirelessCopycatPacket.STREAM_CODEC,       ModPackets::handleLocateWirelessCopycat);
        registrar.optional().playToServer(SaveLinkFreqsPacket.TYPE,               SaveLinkFreqsPacket.STREAM_CODEC,               ModPackets::handleSaveLinkFreqs);
        registrar.optional().playToServer(RequestLinkFreqScreenPacket.TYPE,       RequestLinkFreqScreenPacket.STREAM_CODEC,       ModPackets::handleRequestLinkFreqScreen);

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
            registrar.playToClient(SequencerProgressPacket.TYPE,        SequencerProgressPacket.CODEC,        (p, c) -> {});
            registrar.playToClient(TypewriterImportOfferPacket.TYPE,  TypewriterImportOfferPacket.CODEC,  (p, c) -> {});
            registrar.playToClient(ChannelChangedPacket.TYPE,          ChannelChangedPacket.CODEC,          (p, c) -> {});
            registrar.playToClient(OpenLiveControlScreenPacket.TYPE, OpenLiveControlScreenPacket.CODEC, (p, c) -> {});
            registrar.optional().playToClient(OpenWirelessCopycatScreenPacket.TYPE, OpenWirelessCopycatScreenPacket.STREAM_CODEC, (p, c) -> {});
            registrar.optional().playToClient(OpenLinkFreqScreenPacket.TYPE,        OpenLinkFreqScreenPacket.STREAM_CODEC,        (p, c) -> {});
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
            if (targetPos == null) {
                // No linked target — sequencer works standalone (redstone / Sable getters)
                int idx2 = packet.modeOrdinal() & 0xFF;
                KeyboardMode[] modes2 = KeyboardMode.values();
                if (idx2 >= 0 && idx2 < modes2.length && modes2[idx2] == KeyboardMode.PERIPHERAL_SEQUENCER)
                    sendOpenSequencer(sp, packet.keyboardPos(), keyboard);
                return;
            }

            int idx = packet.modeOrdinal() & 0xFF;
            KeyboardMode[] modes = KeyboardMode.values();
            if (idx < 0 || idx >= modes.length) return;
            KeyboardMode mode = modes[idx];

            if (mode != KeyboardMode.PERIPHERAL_SEQUENCER
                    && !mode.isAvailableAt(sp.serverLevel(), targetPos)) {
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
                Level kLevel = keyboard.getLevel();
                double[] snap = (SableCompat.isPresent() && kLevel != null && SableCompat.isOnSublevel(kLevel, keyboard.getBlockPos()))
                        ? SableCompat.getSnapshot(kLevel, keyboard.getBlockPos()) : new double[0];
                sendOpenThrusterControl(sp, keyboardPos, state, keyboard.getActiveChannel(), snap);
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

            keyboard.cycleActiveChannelSmart();

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
            // channel 1 lists kept for backward-compat / fallback
            List<String> availableGetterNames,
            List<String[]> availableSetters,
            // per-channel maps (key = channel 1-16)
            Map<Integer, List<String>>   gettersByChannel,
            Map<Integer, List<String[]>> settersByChannel
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
                    buf.writeInt(pkt.availableGetterNames().size());
                    for (String g : pkt.availableGetterNames()) buf.writeUtf(g);
                    buf.writeInt(pkt.availableSetters().size());
                    for (String[] s : pkt.availableSetters()) { buf.writeUtf(s[0]); buf.writeUtf(s[1]); }
                    buf.writeInt(pkt.gettersByChannel().size());
                    for (Map.Entry<Integer, List<String>> e : pkt.gettersByChannel().entrySet()) {
                        buf.writeInt(e.getKey());
                        buf.writeInt(e.getValue().size());
                        for (String g : e.getValue()) buf.writeUtf(g);
                    }
                    buf.writeInt(pkt.settersByChannel().size());
                    for (Map.Entry<Integer, List<String[]>> e : pkt.settersByChannel().entrySet()) {
                        buf.writeInt(e.getKey());
                        buf.writeInt(e.getValue().size());
                        for (String[] s : e.getValue()) { buf.writeUtf(s[0]); buf.writeUtf(s[1]); }
                    }
                },
                buf -> {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    int sc = buf.readInt();
                    List<SequencerStep> steps = new ArrayList<>(sc);
                    for (int i = 0; i < sc; i++) steps.add(SequencerStep.decode(buf));
                    boolean running   = buf.readBoolean();
                    int currentStep   = buf.readInt();
                    int gc = buf.readInt();
                    List<String> getters = new ArrayList<>(gc);
                    for (int i = 0; i < gc; i++) getters.add(buf.readUtf());
                    int stc = buf.readInt();
                    List<String[]> setters = new ArrayList<>(stc);
                    for (int i = 0; i < stc; i++) setters.add(new String[]{ buf.readUtf(), buf.readUtf() });
                    int chgc = buf.readInt();
                    Map<Integer, List<String>> gettersByChannel = new HashMap<>(chgc);
                    for (int i = 0; i < chgc; i++) {
                        int ch = buf.readInt();
                        int cnt = buf.readInt();
                        List<String> gl = new ArrayList<>(cnt);
                        for (int j = 0; j < cnt; j++) gl.add(buf.readUtf());
                        gettersByChannel.put(ch, gl);
                    }
                    int chsc = buf.readInt();
                    Map<Integer, List<String[]>> settersByChannel = new HashMap<>(chsc);
                    for (int i = 0; i < chsc; i++) {
                        int ch = buf.readInt();
                        int cnt = buf.readInt();
                        List<String[]> sl = new ArrayList<>(cnt);
                        for (int j = 0; j < cnt; j++) sl.add(new String[]{ buf.readUtf(), buf.readUtf() });
                        settersByChannel.put(ch, sl);
                    }
                    return new OpenSequencerPacket(pos, steps, running, currentStep,
                            getters, setters, gettersByChannel, settersByChannel);
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

    public record ResetLinksPacket(BlockPos keyboardPos) implements CustomPacketPayload {
        public static final Type<ResetLinksPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "reset_links"));
        public static final StreamCodec<FriendlyByteBuf, ResetLinksPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> BlockPos.STREAM_CODEC.encode(buf, pkt.keyboardPos()),
                buf -> new ResetLinksPacket(BlockPos.STREAM_CODEC.decode(buf)));
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

    public record SequencerWatchPacket(BlockPos keyboardPos, boolean subscribe) implements CustomPacketPayload {
        public static final Type<SequencerWatchPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "sequencer_watch"));
        public static final StreamCodec<FriendlyByteBuf, SequencerWatchPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> { BlockPos.STREAM_CODEC.encode(buf, pkt.keyboardPos()); buf.writeBoolean(pkt.subscribe()); },
                buf -> new SequencerWatchPacket(BlockPos.STREAM_CODEC.decode(buf), buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SequencerProgressPacket(BlockPos keyboardPos, boolean running, int currentStep)
            implements CustomPacketPayload {
        public static final Type<SequencerProgressPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "sequencer_progress"));
        public static final StreamCodec<FriendlyByteBuf, SequencerProgressPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> {
                    BlockPos.STREAM_CODEC.encode(buf, pkt.keyboardPos());
                    buf.writeBoolean(pkt.running());
                    buf.writeInt(pkt.currentStep());
                },
                buf -> new SequencerProgressPacket(BlockPos.STREAM_CODEC.decode(buf), buf.readBoolean(), buf.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void sendOpenThrusterControl(ServerPlayer player, BlockPos keyboardPos,
                                                PeripheralHelper.ThrusterState state, int channel,
                                                double[] sublevelSnapshot) {
        PacketDistributor.sendToPlayer(player, new OpenThrusterControlPacket(
                keyboardPos, state.type(),
                state.targetVectorX(), state.targetVectorY(),
                state.currentVectorX(), state.currentVectorY(),
                state.thrust(), state.thrustConfig(), state.configMax(),
                state.currentThrustPn(), state.displayedThrustPn(),
                state.airflowMs(), state.obstruction(),
                state.fuelAmountMb(), state.fuelCapacityMb(),
                channel, sublevelSnapshot
        ));
    }

    public static void sendSetThrusterValue(BlockPos keyboardPos, String methodName, double value) {
        PacketDistributor.sendToServer(new SetThrusterValuePacket(keyboardPos, methodName, value));
    }

    public static void sendUnlinkKeyboard(BlockPos keyboardPos) {
        PacketDistributor.sendToServer(new UnlinkKeyboardPacket(keyboardPos));
    }

    public static void sendResetLinks(BlockPos keyboardPos) {
        PacketDistributor.sendToServer(new ResetLinksPacket(keyboardPos));
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
                Level kLvl = keyboard.getLevel();
                double[] snap = (SableCompat.isPresent() && kLvl != null && SableCompat.isOnSublevel(kLvl, keyboard.getBlockPos()))
                        ? SableCompat.getSnapshot(kLvl, keyboard.getBlockPos()) : new double[0];
                sendOpenThrusterControl(sp, packet.keyboardPos(), state, keyboard.getActiveChannel(), snap);
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
                Level kLvl2 = keyboard.getLevel();
                double[] snap2 = (SableCompat.isPresent() && kLvl2 != null && SableCompat.isOnSublevel(kLvl2, keyboard.getBlockPos()))
                        ? SableCompat.getSnapshot(kLvl2, keyboard.getBlockPos()) : new double[0];
                sendOpenThrusterControl(sp, packet.keyboardPos(), state, keyboard.getActiveChannel(), snap2);
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
                be.resetData();
        });
    }

    private static void handleResetLinks(ResetLinksPacket packet, IPayloadContext ctx) {
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

    private static void handleSequencerWatch(SequencerWatchPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity kb)) return;
            if (packet.subscribe()) kb.addSequencerViewer(sp.getUUID());
            else                    kb.removeSequencerViewer(sp.getUUID());
        });
    }

    public static void broadcastSequencerProgress(LinkedKeyboardBlockEntity kb) {
        if (kb.getSequencerViewers().isEmpty()) return;
        Level level = kb.getLevel();
        if (!(level instanceof net.minecraft.server.level.ServerLevel svl)) return;
        SequencerProgressPacket pkt = new SequencerProgressPacket(
                kb.getBlockPos(), kb.isSequencerRunning(), kb.getSequencerCurrentStep());
        for (UUID uuid : kb.getSequencerViewers()) {
            ServerPlayer sp = svl.getServer().getPlayerList().getPlayer(uuid);
            if (sp != null) PacketDistributor.sendToPlayer(sp, pkt);
        }
    }

    public static void sendSequencerWatch(BlockPos keyboardPos, boolean subscribe) {
        PacketDistributor.sendToServer(new SequencerWatchPacket(keyboardPos, subscribe));
    }

    // ── Typewriter import packets ─────────────────────────────────────────────

    /** Client requests a typewriter scan near the placed keyboard. */
    public record TypewriterScanPacket(BlockPos keyboardPos) implements CustomPacketPayload {
        public static final Type<TypewriterScanPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "typewriter_scan"));
        public static final StreamCodec<FriendlyByteBuf, TypewriterScanPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> BlockPos.STREAM_CODEC.encode(buf, pkt.keyboardPos()),
                buf -> new TypewriterScanPacket(BlockPos.STREAM_CODEC.decode(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Server's reply to the scan.
     * error != "" means failure (show the string). Otherwise bindingCount/freqCount are valid
     * and typewriterPos holds the found typewriter position (for the confirm step).
     */
    public record TypewriterImportOfferPacket(
            BlockPos keyboardPos, BlockPos typewriterPos,
            int bindingCount, int freqCount, String error)
            implements CustomPacketPayload {
        public static final Type<TypewriterImportOfferPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "typewriter_offer"));
        public static final StreamCodec<FriendlyByteBuf, TypewriterImportOfferPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> {
                    BlockPos.STREAM_CODEC.encode(buf, pkt.keyboardPos());
                    BlockPos.STREAM_CODEC.encode(buf, pkt.typewriterPos());
                    buf.writeInt(pkt.bindingCount()); buf.writeInt(pkt.freqCount()); buf.writeUtf(pkt.error());
                },
                buf -> new TypewriterImportOfferPacket(
                        BlockPos.STREAM_CODEC.decode(buf), BlockPos.STREAM_CODEC.decode(buf),
                        buf.readInt(), buf.readInt(), buf.readUtf()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client confirmed the import. */
    public record TypewriterImportConfirmPacket(BlockPos keyboardPos, BlockPos typewriterPos)
            implements CustomPacketPayload {
        public static final Type<TypewriterImportConfirmPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "typewriter_confirm"));
        public static final StreamCodec<FriendlyByteBuf, TypewriterImportConfirmPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> {
                    BlockPos.STREAM_CODEC.encode(buf, pkt.keyboardPos());
                    BlockPos.STREAM_CODEC.encode(buf, pkt.typewriterPos());
                },
                buf -> new TypewriterImportConfirmPacket(
                        BlockPos.STREAM_CODEC.decode(buf), BlockPos.STREAM_CODEC.decode(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void sendTypewriterScan(BlockPos keyboardPos) {
        PacketDistributor.sendToServer(new TypewriterScanPacket(keyboardPos));
    }

    public static void sendTypewriterConfirm(BlockPos keyboardPos, BlockPos typewriterPos) {
        PacketDistributor.sendToServer(new TypewriterImportConfirmPacket(keyboardPos, typewriterPos));
    }

    private static void handleTypewriterScan(TypewriterScanPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            net.minecraft.server.level.ServerLevel level = sp.serverLevel();
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity kb)) return;

            // Scan a 20-block cube around the keyboard for a typewriter
            BlockPos twPos = findNearbyTypewriter(level, packet.keyboardPos());
            if (twPos == null) {
                PacketDistributor.sendToPlayer(sp, new TypewriterImportOfferPacket(
                        packet.keyboardPos(), BlockPos.ZERO, 0, 0, "No typewriter found within 20 blocks."));
                return;
            }

            // Read bindings and validate
            List<dev.bennethogan.universalkeyboard.compat.TypewriterHelper.Binding> bindings =
                    dev.bennethogan.universalkeyboard.compat.TypewriterHelper.readBindings(level, twPos, level.registryAccess());
            if (bindings.isEmpty()) {
                PacketDistributor.sendToPlayer(sp, new TypewriterImportOfferPacket(
                        packet.keyboardPos(), twPos, 0, 0,
                        "Typewriter found but no bindings could be read. Check the log for details."));
                return;
            }

            // Count unique frequencies
            List<net.minecraft.world.item.ItemStack[]> freqs = new ArrayList<>();
            for (var b : bindings) {
                boolean dup = false;
                for (var f : freqs)
                    if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(f[0], b.firstItem())
                            && net.minecraft.world.item.ItemStack.isSameItemSameComponents(f[1], b.secondItem())) {
                        dup = true; break;
                    }
                if (!dup) freqs.add(new net.minecraft.world.item.ItemStack[]{b.firstItem(), b.secondItem()});
            }

            if (freqs.size() > LinkedKeyboardBlockEntity.MAX_WIRELESS) {
                PacketDistributor.sendToPlayer(sp, new TypewriterImportOfferPacket(
                        packet.keyboardPos(), twPos, bindings.size(), freqs.size(),
                        "Too many unique wireless frequencies (" + freqs.size() + "). Max is " +
                        LinkedKeyboardBlockEntity.MAX_WIRELESS + ". Reduce bindings in the typewriter first."));
                return;
            }

            // Everything looks good — offer the import
            PacketDistributor.sendToPlayer(sp, new TypewriterImportOfferPacket(
                    packet.keyboardPos(), twPos, bindings.size(), freqs.size(), ""));
        });
    }

    private static void handleTypewriterConfirm(TypewriterImportConfirmPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            net.minecraft.server.level.ServerLevel level = sp.serverLevel();
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity kb)) return;

            if (!dev.bennethogan.universalkeyboard.compat.TypewriterHelper.isTypewriter(level, packet.typewriterPos())) {
                sp.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§c[Keyboard] Typewriter no longer found at that position."), true);
                return;
            }

            List<dev.bennethogan.universalkeyboard.compat.TypewriterHelper.Binding> bindings =
                    dev.bennethogan.universalkeyboard.compat.TypewriterHelper.readBindings(level, packet.typewriterPos(), level.registryAccess());

            String err = kb.applyTypewriterImport(bindings, level.registryAccess());
            if (err != null) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[Keyboard] " + err), true);
                return;
            }

            sp.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§a[Keyboard] Imported " + bindings.size() + " binding(s) from typewriter."), true);
            // Re-send the live control screen so the client sees the updated bindings
            handleOpenLiveControl(new OpenLiveControlPacket(packet.keyboardPos()), ctx);
        });
    }


    /** Searches a 20-block cube around keyboardPos for any typewriter peripheral. */
    private static BlockPos findNearbyTypewriter(net.minecraft.server.level.ServerLevel level, BlockPos center) {
        int radius = 20;
        for (int dx = -radius; dx <= radius; dx++)
            for (int dy = -radius; dy <= radius; dy++)
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (level.getBlockEntity(p) == null) continue; // fast-skip empty positions
                    if (dev.bennethogan.universalkeyboard.compat.TypewriterHelper.isTypewriter(level, p)) return p;
                }
        return null;
    }

    public static void sendOpenSequencer(ServerPlayer player, BlockPos keyboardPos,
                                          LinkedKeyboardBlockEntity keyboard) {
        boolean isComputer = keyboard.isLinkedAsComputer();
        Level kLevel = keyboard.getLevel();

        // Sable sublevel getters are not channel-specific
        List<String> sableGetters = new ArrayList<>();
        if (SableCompat.isPresent() && kLevel != null) {
            BlockPos kPos = keyboard.getBlockPos();
            boolean onSub = SableCompat.isOnSublevel(kLevel, kPos);
            UniversalKeyboardMod.LOGGER.debug("SableCompat check: keyboard @ {} onSublevel={} level={}",
                    kPos, onSub, kLevel.getClass().getSimpleName());
            if (onSub) java.util.Collections.addAll(sableGetters, SableCompat.GETTER_NAMES);
        }

        // Scan every populated channel for its peripheral getters/setters
        Map<Integer, List<String>>   gettersByChannel = new HashMap<>();
        Map<Integer, List<String[]>> settersByChannel = new HashMap<>();
        for (int ch = 1; ch <= LinkedKeyboardBlockEntity.MAX_CHANNELS; ch++) {
            List<net.minecraft.core.BlockPos> targets = keyboard.getLinkedTargetPositions(ch);
            if (targets.isEmpty()) continue;
            List<String> chGetters = new ArrayList<>(sableGetters);
            List<String[]> chSetters = new ArrayList<>();
            if (!isComputer) {
                PeripheralHelper.ScanResult result = PeripheralHelper.scanAndCall(
                        player.serverLevel(), targets.get(0), "", "");
                if (result != null) {
                    result.getters().stream().map(g -> g[0]).forEach(chGetters::add);
                    chSetters.addAll(result.setters());
                }
            }
            gettersByChannel.put(ch, chGetters);
            settersByChannel.put(ch, chSetters);
        }

        // Channel-1 lists kept as the screen's top-level fallback
        List<String>   ch1Getters = gettersByChannel.getOrDefault(1, sableGetters);
        List<String[]> ch1Setters = settersByChannel.getOrDefault(1, List.of());

        PacketDistributor.sendToPlayer(player, new OpenSequencerPacket(
                keyboardPos, keyboard.getSequencerSteps(),
                keyboard.isSequencerRunning(), keyboard.getSequencerCurrentStep(),
                ch1Getters, ch1Setters, gettersByChannel, settersByChannel));
    }

    public static void sendSaveAndRunSequencer(BlockPos keyboardPos, List<SequencerStep> steps, boolean run) {
        PacketDistributor.sendToServer(new SaveAndRunSequencerPacket(keyboardPos, steps, run));
    }

    public static void sendStopSequencer(BlockPos keyboardPos) {
        PacketDistributor.sendToServer(new StopSequencerPacket(keyboardPos));
    }

    public static void sendRequestThrusterRefresh(BlockPos keyboardPos) {
        PacketDistributor.sendToServer(new RequestThrusterRefreshPacket(keyboardPos));
    }

    private static void handleRequestThrusterRefresh(RequestThrusterRefreshPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity keyboard)) return;
            List<BlockPos> targets = keyboard.getLinkedTargetPositions();
            if (targets.isEmpty()) return;
            PeripheralHelper.ThrusterState state = PeripheralHelper.scanThruster(sp.serverLevel(), targets.get(0));
            if (state == null) return;
            Level kLvl = keyboard.getLevel();
            double[] snap = (SableCompat.isPresent() && kLvl != null && SableCompat.isOnSublevel(kLvl, keyboard.getBlockPos()))
                    ? SableCompat.getSnapshot(kLvl, keyboard.getBlockPos()) : new double[0];
            sendOpenThrusterControl(sp, packet.keyboardPos(), state, keyboard.getActiveChannel(), snap);
        });
    }

    // ── Wireless config menu ─────────────────────────────────────────────────

    public static void sendOpenWirelessConfig(BlockPos keyboardPos) {
        PacketDistributor.sendToServer(new OpenWirelessConfigPacket(keyboardPos));
    }

    public static void sendWirelessAddRemove(BlockPos keyboardPos, boolean add) {
        PacketDistributor.sendToServer(new WirelessAddRemovePacket(keyboardPos, add));
    }

    private static void handleOpenWirelessConfig(OpenWirelessConfigPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!dev.bennethogan.universalkeyboard.compat.wireless.WirelessPresence.isPresent()) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity)) return;
            sp.openMenu(new net.minecraft.world.MenuProvider() {
                @Override public net.minecraft.network.chat.Component getDisplayName() {
                    return net.minecraft.network.chat.Component.translatable("gui.universalkeyboard.screen.wireless_config.title");
                }
                @Override public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                        int id, net.minecraft.world.entity.player.Inventory inv,
                        net.minecraft.world.entity.player.Player player) {
                    return new dev.bennethogan.universalkeyboard.menu.WirelessConfigMenu(id, inv, packet.keyboardPos());
                }
            }, buf -> buf.writeBlockPos(packet.keyboardPos()));
        });
    }

    private static void handleWirelessAddRemove(WirelessAddRemovePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity kb)) return;
            if (packet.add()) {
                kb.addWirelessEntry();
            } else {
                int n = kb.getWirelessCount();
                if (n > 0) kb.removeWirelessEntry(n - 1);
            }
        });
    }

    public static void sendWirelessGhostSet(BlockPos keyboardPos, int slotIdx, ItemStack item) {
        PacketDistributor.sendToServer(new WirelessGhostSetPacket(keyboardPos, slotIdx, item));
    }

    private static void handleWirelessGhostSet(WirelessGhostSetPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity kb)) return;
            int slotIdx  = packet.slotIdx();
            int cols     = dev.bennethogan.universalkeyboard.menu.WirelessConfigMenu.GHOST_COLS;
            int entryIdx = slotIdx / cols;
            if (entryIdx < 0 || entryIdx >= dev.bennethogan.universalkeyboard.menu.WirelessConfigMenu.ROWS) return;
            // Auto-activate: create entries up to entryIdx when an item is placed
            if (!packet.item().isEmpty()) {
                while (kb.getWirelessCount() <= entryIdx) {
                    if (kb.addWirelessEntry() < 0) break; // MAX_WIRELESS reached
                }
            }
            if (entryIdx >= kb.getWirelessCount()) return; // couldn't create (already at max)
            boolean isFirst = (slotIdx % cols) == 0;
            kb.setWirelessFrequencyItem(entryIdx, isFirst, packet.item());
            // Blank+blank: if both frequency slots are now empty, deactivate the entry
            if (!kb.getWirelessEntries().get(entryIdx).hasFrequency()) {
                kb.removeWirelessEntry(entryIdx);
            }
            // Broadcast so the client stays in sync
            if (sp.containerMenu instanceof dev.bennethogan.universalkeyboard.menu.WirelessConfigMenu wcm
                    && wcm.getKeyboardPos().equals(packet.keyboardPos())) {
                wcm.broadcastChanges();
            }
        });
    }


    // ══════════════════════════════════════════════════════════════════════════
    // Live Control packets
    // ══════════════════════════════════════════════════════════════════════════

    /** Client → Server: open the live control screen for this keyboard. */
    public record OpenLiveControlPacket(BlockPos keyboardPos) implements CustomPacketPayload {
        public static final Type<OpenLiveControlPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "open_live_control"));
        public static final StreamCodec<FriendlyByteBuf, OpenLiveControlPacket> CODEC = StreamCodec.of(
                (buf, p) -> BlockPos.STREAM_CODEC.encode(buf, p.keyboardPos()),
                buf -> new OpenLiveControlPacket(BlockPos.STREAM_CODEC.decode(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server → Client: open the live control screen with current bindings. */
    public record OpenLiveControlScreenPacket(
            BlockPos keyboardPos,
            List<dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding> bindings,
            int wirelessCount,
            boolean hasThrusters,
            boolean hasVectorThrusters,
            boolean hasRpm,
            int[] localRsOutputs,
            int[] wirelessPowers,
            int[] thrusterPowers,
            double[] varValues,
            int[] rpmValues,
            int activeProfile,
            List<List<dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding>> allProfiles
    ) implements CustomPacketPayload {
        public static final Type<OpenLiveControlScreenPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "open_live_control_screen"));
        public static final StreamCodec<FriendlyByteBuf, OpenLiveControlScreenPacket> CODEC = StreamCodec.of(
                (buf, p) -> {
                    BlockPos.STREAM_CODEC.encode(buf, p.keyboardPos());
                    buf.writeInt(p.bindings().size());
                    for (var b : p.bindings()) b.encode(buf);
                    buf.writeInt(p.wirelessCount());
                    buf.writeBoolean(p.hasThrusters());
                    buf.writeBoolean(p.hasVectorThrusters());
                    buf.writeBoolean(p.hasRpm());
                    buf.writeByteArray(toByteArray(p.localRsOutputs()));
                    buf.writeByteArray(toByteArray(p.wirelessPowers()));
                    buf.writeByteArray(toByteArray(p.thrusterPowers()));
                    buf.writeInt(p.varValues().length);
                    for (double v : p.varValues()) buf.writeDouble(v);
                    buf.writeShort(p.rpmValues().length);
                    for (int v : p.rpmValues()) buf.writeShort(v);
                    buf.writeByte(p.activeProfile());
                    List<List<dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding>> ap = p.allProfiles();
                    buf.writeByte(ap.size());
                    for (List<dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding> profile : ap) {
                        buf.writeInt(profile.size());
                        for (var b : profile) b.encode(buf);
                    }
                },
                buf -> {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    int cnt = buf.readInt();
                    List<dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding> binds = new ArrayList<>(cnt);
                    for (int i = 0; i < cnt; i++)
                        binds.add(dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding.decode(buf));
                    int wc  = buf.readInt();
                    boolean ht  = buf.readBoolean();
                    boolean hvt = buf.readBoolean();
                    boolean hr  = buf.readBoolean();
                    int[] lrs = fromByteArray(buf.readByteArray());
                    int[] wp  = fromByteArray(buf.readByteArray());
                    int[] tp  = fromByteArray(buf.readByteArray());
                    int vlen = buf.readInt();
                    double[] vars = new double[vlen];
                    for (int i = 0; i < vlen; i++) vars[i] = buf.readDouble();
                    int rlen = buf.readShort() & 0xFFFF;
                    int[] rpmVals = new int[rlen];
                    for (int i = 0; i < rlen; i++) rpmVals[i] = buf.readShort(); // signed: motors can run reverse (negative)
                    int activeProf = buf.readByte() & 0xFF;
                    int numProfiles = buf.readByte() & 0xFF;
                    List<List<dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding>> allProfs = new ArrayList<>(numProfiles);
                    for (int p = 0; p < numProfiles; p++) {
                        int pc = buf.readInt();
                        List<dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding> profile = new ArrayList<>(pc);
                        for (int i = 0; i < pc; i++)
                            profile.add(dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding.decode(buf));
                        allProfs.add(profile);
                    }
                    return new OpenLiveControlScreenPacket(pos, binds, wc, ht, hvt, hr, lrs, wp, tp, vars, rpmVals, activeProf, allProfs);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        private static byte[] toByteArray(int[] arr) {
            byte[] b = new byte[arr.length]; for (int i = 0; i < arr.length; i++) b[i] = (byte) arr[i]; return b;
        }
        private static int[] fromByteArray(byte[] b) {
            int[] a = new int[b.length]; for (int i = 0; i < b.length; i++) a[i] = b[i] & 0xFF; return a;
        }
    }

    /** Client → Server: save updated bindings for a specific profile slot. */
    public record SaveLiveBindingsPacket(
            BlockPos keyboardPos,
            int profileIdx,
            List<dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding> bindings
    ) implements CustomPacketPayload {
        public static final Type<SaveLiveBindingsPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "save_live_bindings"));
        public static final StreamCodec<FriendlyByteBuf, SaveLiveBindingsPacket> CODEC = StreamCodec.of(
                (buf, p) -> {
                    BlockPos.STREAM_CODEC.encode(buf, p.keyboardPos());
                    buf.writeByte(p.profileIdx());
                    buf.writeInt(p.bindings().size());
                    for (var b : p.bindings()) b.encode(buf);
                },
                buf -> {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    int profileIdx = buf.readByte() & 0xFF;
                    int cnt = buf.readInt();
                    List<dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding> binds = new ArrayList<>(cnt);
                    for (int i = 0; i < cnt; i++)
                        binds.add(dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding.decode(buf));
                    return new SaveLiveBindingsPacket(pos, profileIdx, binds);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** A single live-control action to apply on the server. */
    public record LiveAction(byte type, int target, double v1, double v2) {}

    /** Client → Server: apply one or more live control actions. */
    public record LiveActionPacket(
            BlockPos keyboardPos,
            List<LiveAction> actions
    ) implements CustomPacketPayload {
        public static final Type<LiveActionPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "live_action"));
        public static final StreamCodec<FriendlyByteBuf, LiveActionPacket> CODEC = StreamCodec.of(
                (buf, p) -> {
                    BlockPos.STREAM_CODEC.encode(buf, p.keyboardPos());
                    buf.writeInt(p.actions().size());
                    for (LiveAction a : p.actions()) {
                        buf.writeByte(a.type());
                        buf.writeInt(a.target());
                        buf.writeDouble(a.v1());
                        buf.writeDouble(a.v2());
                    }
                },
                buf -> {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    int cnt = buf.readInt();
                    List<LiveAction> acts = new ArrayList<>(cnt);
                    for (int i = 0; i < cnt; i++)
                        acts.add(new LiveAction(buf.readByte(), buf.readInt(), buf.readDouble(), buf.readDouble()));
                    return new LiveActionPacket(pos, acts);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void sendOpenLiveControl(BlockPos keyboardPos) {
        PacketDistributor.sendToServer(new OpenLiveControlPacket(keyboardPos));
    }

    public static void sendSaveLiveBindings(BlockPos keyboardPos, int profileIdx,
            List<dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding> bindings) {
        PacketDistributor.sendToServer(new SaveLiveBindingsPacket(keyboardPos, profileIdx, bindings));
    }

    public static void sendLiveAction(BlockPos keyboardPos, List<LiveAction> actions) {
        if (!actions.isEmpty() && net.minecraft.client.Minecraft.getInstance().getConnection() != null)
            PacketDistributor.sendToServer(new LiveActionPacket(keyboardPos, actions));
    }

    private static void handleOpenLiveControl(OpenLiveControlPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity kb)) return;
            var level = sp.serverLevel();
            boolean hasThrusters = false, hasVector = false, hasRpm = false;

            // Current thruster power by channel (1-16); index 0 unused
            int[] thrusterPowers = new int[LinkedKeyboardBlockEntity.MAX_CHANNELS + 1];
            int[] rpmValues      = new int[LinkedKeyboardBlockEntity.MAX_CHANNELS + 1];
            for (int ch = 1; ch <= LinkedKeyboardBlockEntity.MAX_CHANNELS; ch++) {
                for (BlockPos tp : kb.getLinkedTargetPositions(ch)) {
                    Object p = PeripheralHelper.getPeripheral(level, tp);
                    if (p == null) continue;
                    String type = PeripheralHelper.getPeripheralType(p);
                    if (PeripheralHelper.isThrusterType(type)) {
                        hasThrusters = true;
                        if (type.contains("vector")) hasVector = true;
                        if (thrusterPowers[ch] == 0)
                            thrusterPowers[ch] = PeripheralHelper.getThrusterPower(level, tp);
                    }
                    if (PeripheralHelper.isRpmCapable(p)) {
                        hasRpm = true;
                        if (rpmValues[ch] == 0)
                            rpmValues[ch] = PeripheralHelper.getRpmValue(level, tp);
                    }
                }
            }

            // Current local RS outputs (by Direction ordinal)
            Direction[] dirs = Direction.values();
            int[] localRs = new int[dirs.length];
            for (Direction d : dirs) localRs[d.ordinal()] = kb.getRedstoneOutput(d);

            // Current wireless powers (by slot index 0-based)
            int wc = kb.getWirelessCount();
            int[] wirelessPowers = new int[wc];
            for (int i = 0; i < wc; i++) wirelessPowers[i] = kb.getWirelessOutput(i);

            PacketDistributor.sendToPlayer(sp, new OpenLiveControlScreenPacket(
                    packet.keyboardPos(), kb.getLiveControlBindings(),
                    wc, hasThrusters, hasVector, hasRpm, localRs, wirelessPowers, thrusterPowers,
                    kb.getSequencerVars(), rpmValues,
                    kb.getActiveProfile(), kb.getAllProfileBindings()));
        });
    }

    private static void handleSaveLiveBindings(SaveLiveBindingsPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (be instanceof LinkedKeyboardBlockEntity kb) {
                kb.saveProfileBindings(packet.profileIdx(), packet.bindings());
            }
        });
    }

    private static void handleLiveAction(LiveActionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity kb)) return;
            var level = sp.serverLevel();
            for (LiveAction a : packet.actions()) {
                switch (a.type()) {
                    case 0 -> { // local RS side
                        Direction[] dirs = Direction.values();
                        if (a.target() >= 0 && a.target() < dirs.length)
                            kb.setRedstoneOutput(dirs[a.target()], (int) Math.round(a.v1()));
                    }
                    case 1 -> // wireless RS
                        kb.setWirelessOutput(a.target(), (int) Math.round(a.v1()));
                    case 2 -> { // thruster power (channel = target) — apply to all targets on channel
                        for (BlockPos tp : kb.getLinkedTargetPositions(a.target())) {
                            Object p = PeripheralHelper.getPeripheral(level, tp);
                            if (p != null)
                                PeripheralHelper.callMethodWithDouble(p, "setPower", Math.round(a.v1() * 15));
                        }
                    }
                    case 3 -> { // thruster vector (channel = target) — apply to all targets on channel
                        for (BlockPos tp : kb.getLinkedTargetPositions(a.target())) {
                            Object p = PeripheralHelper.getPeripheral(level, tp);
                            if (p != null) PeripheralHelper.callVectorSetter(p, a.v1(), a.v2());
                        }
                    }
                    case 4 -> // sequencer variable (target = varIndex 0-15)
                        kb.setSequencerVariable(a.target(), a.v1());
                    case 5 -> // link channel broadcast (target = linkIdx 0-based)
                        kb.broadcastLinkChannel(a.target(), (int) Math.round(a.v1()));
                    case 6 -> { // RPM control (channel = target)
                        for (BlockPos tp : kb.getLinkedTargetPositions(a.target())) {
                            Object p = PeripheralHelper.getPeripheral(level, tp);
                            if (p != null && PeripheralHelper.isRpmCapable(p))
                                PeripheralHelper.callRpmSetter(p, (int) Math.round(a.v1()));
                        }
                    }
                }
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Wireless Copycat packets
    // ══════════════════════════════════════════════════════════════════════════

    public record OpenWirelessCopycatScreenPacket(BlockPos pos, String[] freqs, boolean[] enabled)
            implements CustomPacketPayload {
        public static final Type<OpenWirelessCopycatScreenPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "open_wireless_copycat_screen"));
        public static final StreamCodec<FriendlyByteBuf, OpenWirelessCopycatScreenPacket> STREAM_CODEC =
                StreamCodec.of(
                        (buf, pkt) -> {
                            BlockPos.STREAM_CODEC.encode(buf, pkt.pos());
                            for (int i = 0; i < 6; i++) buf.writeUtf(pkt.freqs()[i] != null ? pkt.freqs()[i] : "", 8);
                            byte mask = 0;
                            for (int i = 0; i < 6; i++) if (pkt.enabled()[i]) mask |= (byte)(1 << i);
                            buf.writeByte(mask);
                        },
                        buf -> {
                            BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                            String[] freqs = new String[6];
                            for (int i = 0; i < 6; i++) freqs[i] = buf.readUtf(8);
                            byte mask = buf.readByte();
                            boolean[] enabled = new boolean[6];
                            for (int i = 0; i < 6; i++) enabled[i] = (mask & (1 << i)) != 0;
                            return new OpenWirelessCopycatScreenPacket(pos, freqs, enabled);
                        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SaveWirelessCopycatConfigPacket(BlockPos pos, String[] freqs, boolean[] enabled)
            implements CustomPacketPayload {
        public static final Type<SaveWirelessCopycatConfigPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "save_wireless_copycat_config"));
        public static final StreamCodec<FriendlyByteBuf, SaveWirelessCopycatConfigPacket> STREAM_CODEC =
                StreamCodec.of(
                        (buf, pkt) -> {
                            BlockPos.STREAM_CODEC.encode(buf, pkt.pos());
                            for (int i = 0; i < 6; i++) buf.writeUtf(pkt.freqs()[i] != null ? pkt.freqs()[i] : "", 8);
                            byte mask = 0;
                            for (int i = 0; i < 6; i++) if (pkt.enabled()[i]) mask |= (byte)(1 << i);
                            buf.writeByte(mask);
                        },
                        buf -> {
                            BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                            String[] freqs = new String[6];
                            for (int i = 0; i < 6; i++) freqs[i] = buf.readUtf(8);
                            byte mask = buf.readByte();
                            boolean[] enabled = new boolean[6];
                            for (int i = 0; i < 6; i++) enabled[i] = (mask & (1 << i)) != 0;
                            return new SaveWirelessCopycatConfigPacket(pos, freqs, enabled);
                        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TestWirelessCopycatFacePacket(BlockPos pos, int faceIdx) implements CustomPacketPayload {
        public static final Type<TestWirelessCopycatFacePacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "test_wireless_copycat_face"));
        public static final StreamCodec<FriendlyByteBuf, TestWirelessCopycatFacePacket> STREAM_CODEC =
                StreamCodec.of(
                        (buf, pkt) -> { BlockPos.STREAM_CODEC.encode(buf, pkt.pos()); buf.writeByte(pkt.faceIdx()); },
                        buf -> new TestWirelessCopycatFacePacket(BlockPos.STREAM_CODEC.decode(buf), buf.readByte() & 0xFF));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record LocateWirelessCopycatPacket(String freq) implements CustomPacketPayload {
        public static final Type<LocateWirelessCopycatPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "locate_wireless_copycat"));
        public static final StreamCodec<FriendlyByteBuf, LocateWirelessCopycatPacket> STREAM_CODEC =
                StreamCodec.of(
                        (buf, pkt) -> buf.writeUtf(pkt.freq(), 8),
                        buf -> new LocateWirelessCopycatPacket(buf.readUtf(8)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenLinkFreqScreenPacket(BlockPos pos, String[] freqs) implements CustomPacketPayload {
        public static final Type<OpenLinkFreqScreenPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "open_link_freq_screen"));
        public static final StreamCodec<FriendlyByteBuf, OpenLinkFreqScreenPacket> STREAM_CODEC =
                StreamCodec.of(
                        (buf, pkt) -> {
                            BlockPos.STREAM_CODEC.encode(buf, pkt.pos());
                            int count = 0;
                            for (String f : pkt.freqs()) if (f != null && !f.isEmpty()) count++;
                            buf.writeInt(count);
                            int written = 0;
                            for (String f : pkt.freqs()) {
                                if (f != null && !f.isEmpty()) { buf.writeUtf(f, 8); written++; }
                                if (written >= count) break;
                            }
                        },
                        buf -> {
                            BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                            int count = buf.readInt();
                            String[] freqs = new String[LinkedKeyboardBlockEntity.MAX_LINK_FREQS];
                            for (int i = 0; i < count && i < freqs.length; i++) freqs[i] = buf.readUtf(8);
                            return new OpenLinkFreqScreenPacket(pos, freqs);
                        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SaveLinkFreqsPacket(BlockPos pos, String[] freqs) implements CustomPacketPayload {
        public static final Type<SaveLinkFreqsPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "save_link_freqs"));
        public static final StreamCodec<FriendlyByteBuf, SaveLinkFreqsPacket> STREAM_CODEC =
                StreamCodec.of(
                        (buf, pkt) -> {
                            BlockPos.STREAM_CODEC.encode(buf, pkt.pos());
                            buf.writeInt(LinkedKeyboardBlockEntity.MAX_LINK_FREQS);
                            for (int i = 0; i < LinkedKeyboardBlockEntity.MAX_LINK_FREQS; i++) {
                                String f = (pkt.freqs() != null && i < pkt.freqs().length && pkt.freqs()[i] != null) ? pkt.freqs()[i] : "";
                                buf.writeUtf(f, 8);
                            }
                        },
                        buf -> {
                            BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                            int count = buf.readInt();
                            String[] freqs = new String[LinkedKeyboardBlockEntity.MAX_LINK_FREQS];
                            for (int i = 0; i < count; i++) {
                                String v = buf.readUtf(8);
                                if (i < freqs.length) freqs[i] = v.isEmpty() ? null : v;
                            }
                            return new SaveLinkFreqsPacket(pos, freqs);
                        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RequestLinkFreqScreenPacket(BlockPos keyboardPos) implements CustomPacketPayload {
        public static final Type<RequestLinkFreqScreenPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "request_link_freq_screen"));
        public static final StreamCodec<FriendlyByteBuf, RequestLinkFreqScreenPacket> STREAM_CODEC =
                StreamCodec.of(
                        (buf, pkt) -> BlockPos.STREAM_CODEC.encode(buf, pkt.keyboardPos()),
                        buf -> new RequestLinkFreqScreenPacket(BlockPos.STREAM_CODEC.decode(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void handleSaveWirelessCopycatConfig(SaveWirelessCopycatConfigPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            net.minecraft.world.level.block.entity.BlockEntity be = sp.serverLevel().getBlockEntity(pkt.pos());
            if (be instanceof WirelessCopycatBlockEntity cb) cb.setConfig(pkt.freqs(), pkt.enabled());
        });
    }

    private static void handleTestWirelessCopycatFace(TestWirelessCopycatFacePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            net.minecraft.world.level.block.entity.BlockEntity be = sp.serverLevel().getBlockEntity(pkt.pos());
            if (!(be instanceof WirelessCopycatBlockEntity cb)) return;
            int i = pkt.faceIdx();
            if (i < 0 || i >= 6) return;
            cb.startPreview(i);
        });
    }

    private static void handleLocateWirelessCopycat(LocateWirelessCopycatPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            dev.bennethogan.universalkeyboard.wireless.rs.WirelessRSNetwork
                    .getPositions(sp.serverLevel(), pkt.freq())
                    .forEach(pos -> {
                        net.minecraft.world.level.block.entity.BlockEntity be = sp.serverLevel().getBlockEntity(pos);
                        if (be instanceof WirelessCopycatBlockEntity cb) cb.startLocate();
                    });
        });
    }

    private static void handleSaveLinkFreqs(SaveLinkFreqsPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            net.minecraft.world.level.block.entity.BlockEntity be = sp.serverLevel().getBlockEntity(pkt.pos());
            if (be instanceof LinkedKeyboardBlockEntity kb) kb.setLinkFreqs(pkt.freqs());
        });
    }

    private static void handleRequestLinkFreqScreen(RequestLinkFreqScreenPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            net.minecraft.world.level.block.entity.BlockEntity be = sp.serverLevel().getBlockEntity(pkt.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity kb)) return;
            sendOpenLinkFreqScreen(sp, pkt.keyboardPos(), kb.getLinkFreqs());
        });
    }

    public static void sendOpenWirelessCopycatScreen(ServerPlayer player, BlockPos pos, String[] freqs, boolean[] enabled) {
        PacketDistributor.sendToPlayer(player, new OpenWirelessCopycatScreenPacket(pos, freqs, enabled));
    }

    public static void sendSaveWirelessCopycatConfig(BlockPos pos, String[] freqs, boolean[] enabled) {
        PacketDistributor.sendToServer(new SaveWirelessCopycatConfigPacket(pos, freqs, enabled));
    }

    public static void sendTestWirelessCopycatFace(BlockPos pos, int faceIdx) {
        PacketDistributor.sendToServer(new TestWirelessCopycatFacePacket(pos, faceIdx));
    }

    public static void sendLocateWirelessCopycat(String freq) {
        PacketDistributor.sendToServer(new LocateWirelessCopycatPacket(freq));
    }

    public static void sendOpenLinkFreqScreen(ServerPlayer player, BlockPos pos, String[] freqs) {
        PacketDistributor.sendToPlayer(player, new OpenLinkFreqScreenPacket(pos, freqs));
    }

    public static void sendSaveLinkFreqs(BlockPos pos, String[] freqs) {
        PacketDistributor.sendToServer(new SaveLinkFreqsPacket(pos, freqs));
    }

    public static void sendRequestLinkFreqScreen(BlockPos keyboardPos) {
        PacketDistributor.sendToServer(new RequestLinkFreqScreenPacket(keyboardPos));
    }
}
