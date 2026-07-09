package dev.bennethogan.universalkeyboard.network;

import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.universalkeyboard.blockentity.WirelessCopycatBlockEntity;
import dev.bennethogan.universalkeyboard.compat.CreateValueHelper;
import dev.bennethogan.universalkeyboard.compat.KeyboardMode;
import dev.bennethogan.universalkeyboard.compat.PeripheralHelper;
import dev.bennethogan.universalkeyboard.compat.SableCompat;
import dev.bennethogan.universalkeyboard.livecontrol.FavoriteScreen;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
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

    public record CameraTogglePacket(BlockPos keyboardPos, boolean cameraOnly) implements CustomPacketPayload {
        public static final Type<CameraTogglePacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "camera_toggle"));
        public static final StreamCodec<FriendlyByteBuf, CameraTogglePacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, CameraTogglePacket::keyboardPos,
                        ByteBufCodecs.BOOL,    CameraTogglePacket::cameraOnly,
                        CameraTogglePacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CameraNudgePacket(BlockPos cameraPos, double dYaw, double dPitch, int dZoom)
            implements CustomPacketPayload {
        public static final Type<CameraNudgePacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "camera_nudge"));
        public static final StreamCodec<FriendlyByteBuf, CameraNudgePacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, CameraNudgePacket::cameraPos,
                        ByteBufCodecs.DOUBLE,  CameraNudgePacket::dYaw,
                        ByteBufCodecs.DOUBLE,  CameraNudgePacket::dPitch,
                        ByteBufCodecs.INT,     CameraNudgePacket::dZoom,
                        CameraNudgePacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UnlinkTargetPacket(BlockPos keyboardPos, int channel, BlockPos targetPos)
            implements CustomPacketPayload {
        public static final Type<UnlinkTargetPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "unlink_target"));
        public static final StreamCodec<FriendlyByteBuf, UnlinkTargetPacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, UnlinkTargetPacket::keyboardPos,
                        ByteBufCodecs.INT,     UnlinkTargetPacket::channel,
                        BlockPos.STREAM_CODEC, UnlinkTargetPacket::targetPos,
                        UnlinkTargetPacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // Move a linked target to a new channel/position (Peripheral Manager C#N# edit).
    public record MoveTargetPacket(BlockPos keyboardPos, BlockPos targetPos, int fromChannel,
                                   int toChannel, int newIndex) implements CustomPacketPayload {
        public static final Type<MoveTargetPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "move_target"));
        public static final StreamCodec<FriendlyByteBuf, MoveTargetPacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, MoveTargetPacket::keyboardPos,
                        BlockPos.STREAM_CODEC, MoveTargetPacket::targetPos,
                        ByteBufCodecs.INT,     MoveTargetPacket::fromChannel,
                        ByteBufCodecs.INT,     MoveTargetPacket::toChannel,
                        ByteBufCodecs.INT,     MoveTargetPacket::newIndex,
                        MoveTargetPacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // GUM mode aiming: user's ground target and fire power is shared so every linked cannon solved its own
    // trajectory to this point
    public record GunAimPacket(BlockPos keyboardPos, int channel, double tx, double ty, double tz, int power)
            implements CustomPacketPayload {
        public static final Type<GunAimPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "gun_aim"));
        public static final StreamCodec<FriendlyByteBuf, GunAimPacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, GunAimPacket::keyboardPos,
                        ByteBufCodecs.INT,     GunAimPacket::channel,
                        ByteBufCodecs.DOUBLE,  GunAimPacket::tx,
                        ByteBufCodecs.DOUBLE,  GunAimPacket::ty,
                        ByteBufCodecs.DOUBLE,  GunAimPacket::tz,
                        ByteBufCodecs.INT,     GunAimPacket::power,
                        GunAimPacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // GUN manual (MAN mode): move all cannons on the channel the same
    public record GunManualPacket(BlockPos keyboardPos, int channel, double dYaw, double dPitch, int dPower)
            implements CustomPacketPayload {
        public static final Type<GunManualPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "gun_manual"));
        public static final StreamCodec<FriendlyByteBuf, GunManualPacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, GunManualPacket::keyboardPos,
                        ByteBufCodecs.INT,     GunManualPacket::channel,
                        ByteBufCodecs.DOUBLE,  GunManualPacket::dYaw,
                        ByteBufCodecs.DOUBLE,  GunManualPacket::dPitch,
                        ByteBufCodecs.INT,     GunManualPacket::dPower,
                        GunManualPacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // GUN fire (cannons on the channel)
    public record GunFirePacket(BlockPos keyboardPos, int channel) implements CustomPacketPayload {
        public static final Type<GunFirePacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "gun_fire"));
        public static final StreamCodec<FriendlyByteBuf, GunFirePacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, GunFirePacket::keyboardPos,
                        ByteBufCodecs.INT,     GunFirePacket::channel,
                        GunFirePacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // GUN release — drop leases on the channel's cannons (channel 0 = all of the keyboard's cannons)
    public record GunReleasePacket(BlockPos keyboardPos, int channel) implements CustomPacketPayload {
        public static final Type<GunReleasePacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "gun_release"));
        public static final StreamCodec<FriendlyByteBuf, GunReleasePacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, GunReleasePacket::keyboardPos,
                        ByteBufCodecs.INT,     GunReleasePacket::channel,
                        GunReleasePacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // PAN — drive a Control Panels module: set the module at (moduleIndex) on the panel(s) linked on
    // (channel) to (value) (0-15), server resolves the index to a module name per panel
    public record PanSetPacket(BlockPos keyboardPos, int channel, int moduleIndex, int value)
            implements CustomPacketPayload {
        public static final Type<PanSetPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "pan_set"));
        public static final StreamCodec<FriendlyByteBuf, PanSetPacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, PanSetPacket::keyboardPos,
                        ByteBufCodecs.INT,     PanSetPacket::channel,
                        ByteBufCodecs.INT,     PanSetPacket::moduleIndex,
                        ByteBufCodecs.INT,     PanSetPacket::value,
                        PanSetPacket::new);
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

    // server → client: keyboard position changed since last use, ask the player why
    public record ShowPositionMovePacket(BlockPos kbPos) implements CustomPacketPayload {
        public static final Type<ShowPositionMovePacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "show_position_move"));
        public static final StreamCodec<FriendlyByteBuf, ShowPositionMovePacket> CODEC = StreamCodec.of(
                (buf, pkt) -> BlockPos.STREAM_CODEC.encode(buf, pkt.kbPos()),
                buf -> new ShowPositionMovePacket(BlockPos.STREAM_CODEC.decode(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // client → server: player chose how to resolve the position change
    public record ApplyPositionMovePacket(BlockPos kbPos, byte choice, boolean dontAsk) implements CustomPacketPayload {
        public static final Type<ApplyPositionMovePacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "apply_position_move"));
        public static final StreamCodec<FriendlyByteBuf, ApplyPositionMovePacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, ApplyPositionMovePacket::kbPos,
                        ByteBufCodecs.BYTE,    ApplyPositionMovePacket::choice,
                        ByteBufCodecs.BOOL,    ApplyPositionMovePacket::dontAsk,
                        ApplyPositionMovePacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void onRegisterServerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // playToServer — handlers run on the server, safe to register from the common @Mod class
        registrar.playToServer(KeyInputPacket.TYPE,               KeyInputPacket.CODEC,               ModPackets::handleKeyInput);
        registrar.playToServer(KeyboardReleasePacket.TYPE,        KeyboardReleasePacket.CODEC,        ModPackets::handleKeyboardRelease);
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
        registrar.playToServer(ControlWheelAnimatePacket.TYPE,      ControlWheelAnimatePacket.CODEC,      ModPackets::handleControlWheelAnimate);
        registrar.playToServer(KeyAnimPacket.TYPE,                  KeyAnimPacket.CODEC,                  ModPackets::handleKeyAnim);
        registrar.optional().playToServer(RequestThrusterRefreshPacket.TYPE,   RequestThrusterRefreshPacket.CODEC,   ModPackets::handleRequestThrusterRefresh);
        registrar.optional().playToServer(OpenWirelessConfigPacket.TYPE,       OpenWirelessConfigPacket.CODEC,       ModPackets::handleOpenWirelessConfig);
        registrar.optional().playToServer(WirelessAddRemovePacket.TYPE,        WirelessAddRemovePacket.CODEC,        ModPackets::handleWirelessAddRemove);
        registrar.optional().playToServer(WirelessGhostSetPacket.TYPE,         WirelessGhostSetPacket.CODEC,         ModPackets::handleWirelessGhostSet);
        registrar.optional().playToServer(SaveWirelessCopycatConfigPacket.TYPE,   SaveWirelessCopycatConfigPacket.STREAM_CODEC,   ModPackets::handleSaveWirelessCopycatConfig);
        registrar.optional().playToServer(TestWirelessCopycatFacePacket.TYPE,     TestWirelessCopycatFacePacket.STREAM_CODEC,     ModPackets::handleTestWirelessCopycatFace);
        registrar.optional().playToServer(LocateWirelessCopycatPacket.TYPE,       LocateWirelessCopycatPacket.STREAM_CODEC,       ModPackets::handleLocateWirelessCopycat);
        registrar.optional().playToServer(SaveLinkFreqsPacket.TYPE,               SaveLinkFreqsPacket.STREAM_CODEC,               ModPackets::handleSaveLinkFreqs);
        registrar.optional().playToServer(RequestLinkFreqScreenPacket.TYPE,       RequestLinkFreqScreenPacket.STREAM_CODEC,       ModPackets::handleRequestLinkFreqScreen);
        registrar.playToServer(SetFavoritePacket.TYPE,              SetFavoritePacket.CODEC,              ModPackets::handleSetFavorite);
        registrar.playToServer(SetKeyboardLockPacket.TYPE,          SetKeyboardLockPacket.CODEC,          ModPackets::handleSetKeyboardLock);
        registrar.optional().playToServer(UnlinkTargetPacket.TYPE,   UnlinkTargetPacket.CODEC,            ModPackets::handleUnlinkTarget);
        registrar.optional().playToServer(MoveTargetPacket.TYPE,     MoveTargetPacket.CODEC,              ModPackets::handleMoveTarget);
        registrar.optional().playToServer(CameraNudgePacket.TYPE,     CameraNudgePacket.CODEC,             ModPackets::handleCameraNudge);
        registrar.optional().playToServer(CameraTogglePacket.TYPE,    CameraTogglePacket.CODEC,            ModPackets::handleCameraToggle);
        registrar.optional().playToServer(GunAimPacket.TYPE,          GunAimPacket.CODEC,                  ModPackets::handleGunAim);
        registrar.optional().playToServer(GunManualPacket.TYPE,       GunManualPacket.CODEC,               ModPackets::handleGunManual);
        registrar.optional().playToServer(GunFirePacket.TYPE,         GunFirePacket.CODEC,                 ModPackets::handleGunFire);
        registrar.optional().playToServer(PanSetPacket.TYPE,          PanSetPacket.CODEC,                  ModPackets::handlePanSet);
        registrar.optional().playToServer(GunReleasePacket.TYPE,      GunReleasePacket.CODEC,              ModPackets::handleGunRelease);
        registrar.playToServer(ApplyPositionMovePacket.TYPE,        ApplyPositionMovePacket.CODEC,        ModPackets::handleApplyPositionMove);

        // playToClient — the server must declare these channels so the handshake succeeds.
        // Real handlers are registered by ClientPacketHandlers (client only); skip here on client
        // to avoid double-registration. No-op lambdas contain no client-only class references.
        // Server has to declate this
        if (!net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            registrar.playToClient(KeyboardCapturePacket.TYPE,     KeyboardCapturePacket.CODEC,     (p, c) -> {});
            registrar.playToClient(StartCreateCapturePacket.TYPE,  StartCreateCapturePacket.CODEC,  (p, c) -> {});
            registrar.playToClient(OpenPeripheralMenuPacket.TYPE,  OpenPeripheralMenuPacket.CODEC,  (p, c) -> {});
            registrar.playToClient(OpenModeSelectionPacket.TYPE,   OpenModeSelectionPacket.CODEC,   (p, c) -> {});
            registrar.playToClient(OpenThrusterControlPacket.TYPE, OpenThrusterControlPacket.CODEC, (p, c) -> {});
            registrar.playToClient(OpenSequencerPacket.TYPE,       OpenSequencerPacket.CODEC,       (p, c) -> {});
            registrar.playToClient(SequencerProgressPacket.TYPE,        SequencerProgressPacket.CODEC,        (p, c) -> {});
            registrar.playToClient(TypewriterImportOfferPacket.TYPE,  TypewriterImportOfferPacket.CODEC,  (p, c) -> {});
            registrar.playToClient(ChannelChangedPacket.TYPE,          ChannelChangedPacket.CODEC,          (p, c) -> {});
            registrar.playToClient(OpenLiveControlScreenPacket.TYPE, OpenLiveControlScreenPacket.CODEC, (p, c) -> {});
            registrar.playToClient(ControlWheelAnimateClientPacket.TYPE, ControlWheelAnimateClientPacket.CODEC, (p, c) -> {});
            registrar.playToClient(KeyAnimClientPacket.TYPE,             KeyAnimClientPacket.CODEC,             (p, c) -> {});
            registrar.playToClient(SyncFavoritePacket.TYPE,            SyncFavoritePacket.CODEC,            (p, c) -> {});
            registrar.playToClient(ShowPositionMovePacket.TYPE,         ShowPositionMovePacket.CODEC,         (p, c) -> {});
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
            case VISTA_CAMERA  -> openLiveControlForPlayer(sp, keyboardPos, keyboard, true);
            case GUN_CANNON    -> openLiveControlForPlayer(sp, keyboardPos, keyboard, true);
            case PANEL_CONTROL -> openLiveControlForPlayer(sp, keyboardPos, keyboard, true);
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
            // Update active_channel on the held linking tool (keyboard item or linking stick)
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack held = sp.getItemInHand(hand);
                if (held.getItem() instanceof LinkedKeyboardItem) {
                    LinkedKeyboardItem.setActiveLinkingChannel(held, packet.channel());
                    sp.inventoryMenu.sendAllDataToRemote();
                    return;
                }
                if (held.getItem() instanceof dev.bennethogan.universalkeyboard.item.LinkingStickItem) {
                    dev.bennethogan.universalkeyboard.item.LinkingStickItem.setActiveChannel(held, packet.channel());
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

            int idx = packet.modeOrdinal() & 0xFF;
            KeyboardMode[] modes = KeyboardMode.values();
            if (idx < 0 || idx >= modes.length) return;
            KeyboardMode mode = modes[idx];

            if (mode == KeyboardMode.CC_PERIPHERAL) {
                cycleCCPeripheralChannel(sp, packet.keyboardPos(), keyboard);
                return;
            }

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

    private static void cycleCCPeripheralChannel(ServerPlayer sp, BlockPos keyboardPos,
                                                  LinkedKeyboardBlockEntity keyboard) {
        int start = keyboard.getActiveChannel();
        int maxCh = LinkedKeyboardBlockEntity.MAX_CHANNELS;

        for (int i = 1; i < maxCh; i++) {
            int next = (start - 1 + i) % maxCh + 1;
            List<BlockPos> targets = keyboard.getLinkedTargetPositions(next);
            if (targets.isEmpty()) continue;
            BlockPos targetPos = targets.get(0);
            if (KeyboardMode.CC_PERIPHERAL.isAvailableAt(sp.serverLevel(), targetPos)) {
                keyboard.setActiveChannel(next);
                openModeForPlayer(sp, keyboardPos, keyboard, targetPos, KeyboardMode.CC_PERIPHERAL);
                return;
            }
        }

        // No other CC-peripheral channel, reopen the current channel's menu
        List<BlockPos> currentTargets = keyboard.getLinkedTargetPositions(start);
        if (!currentTargets.isEmpty()) {
            openModeForPlayer(sp, keyboardPos, keyboard, currentTargets.get(0), KeyboardMode.CC_PERIPHERAL);
        }
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
    public static void sendUnlinkTarget(BlockPos keyboardPos, int channel, BlockPos targetPos) {
        PacketDistributor.sendToServer(new UnlinkTargetPacket(keyboardPos, channel, targetPos));
    }
    public static void sendMoveTarget(BlockPos keyboardPos, BlockPos targetPos, int fromChannel, int toChannel, int newIndex) {
        PacketDistributor.sendToServer(new MoveTargetPacket(keyboardPos, targetPos, fromChannel, toChannel, newIndex));
    }
    public static void sendCameraNudge(BlockPos cameraPos, double dYaw, double dPitch, int dZoom) {
        PacketDistributor.sendToServer(new CameraNudgePacket(cameraPos, dYaw, dPitch, dZoom));
    }
    public static void sendCameraToggle(BlockPos keyboardPos, boolean cameraOnly) {
        PacketDistributor.sendToServer(new CameraTogglePacket(keyboardPos, cameraOnly));
    }
    public static void sendGunAim(BlockPos keyboardPos, int channel, net.minecraft.world.phys.Vec3 target, int power) {
        PacketDistributor.sendToServer(new GunAimPacket(keyboardPos, channel, target.x, target.y, target.z, power));
    }
    public static void sendGunManual(BlockPos keyboardPos, int channel, double dYaw, double dPitch, int dPower) {
        PacketDistributor.sendToServer(new GunManualPacket(keyboardPos, channel, dYaw, dPitch, dPower));
    }
    public static void sendGunFire(BlockPos keyboardPos, int channel) {
        PacketDistributor.sendToServer(new GunFirePacket(keyboardPos, channel));
    }
    public static void sendPanSet(BlockPos keyboardPos, int channel, int moduleIndex, int value) {
        PacketDistributor.sendToServer(new PanSetPacket(keyboardPos, channel, moduleIndex, value));
    }
    public static void sendGunRelease(BlockPos keyboardPos, int channel) {
        PacketDistributor.sendToServer(new GunReleasePacket(keyboardPos, channel));
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
        // Sync favorite so the ThrusterControlScreen can highlight its star button.
        BlockEntity kbe = player.serverLevel().getBlockEntity(keyboardPos);
        if (kbe instanceof LinkedKeyboardBlockEntity kb)
            PacketDistributor.sendToPlayer(player, new SyncFavoritePacket(keyboardPos, (byte) kb.getFavoriteScreen().ordinal()));
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

            if (freqs.size() > LinkedKeyboardBlockEntity.MAX_RSLINKS) {
                PacketDistributor.sendToPlayer(sp, new TypewriterImportOfferPacket(
                        packet.keyboardPos(), twPos, bindings.size(), freqs.size(),
                        "Too many unique wireless frequencies (" + freqs.size() + "). Max is " +
                        LinkedKeyboardBlockEntity.MAX_RSLINKS + ". Reduce bindings in the typewriter first."));
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
            // synthetic value panel setter
            if (kLevel != null && CreateValueHelper.hasScrollValue(kLevel.getBlockEntity(targets.get(0))))
                chSetters.add(new String[]{ CreateValueHelper.VALUE_PANEL_SETTER, "int" });
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
        PacketDistributor.sendToPlayer(player, new SyncFavoritePacket(keyboardPos, (byte) keyboard.getFavoriteScreen().ordinal()));
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
            if (!dev.bennethogan.universalkeyboard.compat.rslink.RsLinkPresence.isPresent()) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity)) return;
            sp.openMenu(new net.minecraft.world.MenuProvider() {
                @Override public net.minecraft.network.chat.Component getDisplayName() {
                    return net.minecraft.network.chat.Component.translatable("gui.universalkeyboard.screen.wireless_config.title");
                }
                @Override public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                        int id, net.minecraft.world.entity.player.Inventory inv,
                        net.minecraft.world.entity.player.Player player) {
                    return new dev.bennethogan.universalkeyboard.menu.RedstoneLinksMenu(id, inv, packet.keyboardPos());
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
                int n = kb.getRsLinkCount();
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
            int cols     = dev.bennethogan.universalkeyboard.menu.RedstoneLinksMenu.GHOST_COLS;
            int entryIdx = slotIdx / cols;
            if (entryIdx < 0 || entryIdx >= dev.bennethogan.universalkeyboard.menu.RedstoneLinksMenu.ROWS) return;
            // Auto-activate: create entries up to entryIdx when an item is placed
            if (!packet.item().isEmpty()) {
                while (kb.getRsLinkCount() <= entryIdx) {
                    if (kb.addWirelessEntry() < 0) break; // MAX_RSLINKS reached
                }
            }
            if (entryIdx >= kb.getRsLinkCount()) return; // couldn't create (already at max)
            boolean isFirst = (slotIdx % cols) == 0;
            kb.setWirelessFrequencyItem(entryIdx, isFirst, packet.item());
            // Blank+blank: if both frequency slots are now empty, deactivate the entry
            if (!kb.getWirelessEntries().get(entryIdx).hasFrequency()) {
                kb.removeWirelessEntry(entryIdx);
            }
            // Broadcast so the client stays in sync
            if (sp.containerMenu instanceof dev.bennethogan.universalkeyboard.menu.RedstoneLinksMenu wcm
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
            int rsLinkCount,
            boolean hasThrusters,
            boolean hasVectorThrusters,
            boolean hasRpm,
            int[] localRsOutputs,
            int[] rsLinkPowers,
            int[] thrusterPowers,
            double[] varValues,
            int[] rpmValues,
            int activeProfile,
            List<List<dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding>> allProfiles,
            boolean favoriteShortcut
    ) implements CustomPacketPayload {
        public static final Type<OpenLiveControlScreenPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "open_live_control_screen"));
        public static final StreamCodec<FriendlyByteBuf, OpenLiveControlScreenPacket> CODEC = StreamCodec.of(
                (buf, p) -> {
                    BlockPos.STREAM_CODEC.encode(buf, p.keyboardPos());
                    buf.writeInt(p.bindings().size());
                    for (var b : p.bindings()) b.encode(buf);
                    buf.writeInt(p.rsLinkCount());
                    buf.writeBoolean(p.hasThrusters());
                    buf.writeBoolean(p.hasVectorThrusters());
                    buf.writeBoolean(p.hasRpm());
                    buf.writeByteArray(toByteArray(p.localRsOutputs()));
                    buf.writeByteArray(toByteArray(p.rsLinkPowers()));
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
                    buf.writeBoolean(p.favoriteShortcut());
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
                    boolean favoriteShortcut = buf.readBoolean();
                    return new OpenLiveControlScreenPacket(pos, binds, wc, ht, hvt, hr, lrs, wp, tp, vars, rpmVals, activeProf, allProfs, favoriteShortcut);
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

    // Client to server handling for animation ---------------------------------
    public record ControlWheelAnimatePacket(BlockPos pos, int fractionPct) implements CustomPacketPayload {
        public static final Type<ControlWheelAnimatePacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "control_wheel_animate"));
        public static final StreamCodec<FriendlyByteBuf, ControlWheelAnimatePacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, ControlWheelAnimatePacket::pos,
                        ByteBufCodecs.INT,     ControlWheelAnimatePacket::fractionPct,
                        ControlWheelAnimatePacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ControlWheelAnimateClientPacket(BlockPos pos, int fractionPct) implements CustomPacketPayload {
        public static final Type<ControlWheelAnimateClientPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "control_wheel_animate_client"));
        public static final StreamCodec<FriendlyByteBuf, ControlWheelAnimateClientPacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, ControlWheelAnimateClientPacket::pos,
                        ByteBufCodecs.INT,     ControlWheelAnimateClientPacket::fractionPct,
                        ControlWheelAnimateClientPacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void sendControlWheelAnimate(BlockPos pos, float fraction) {
        int pct = Math.max(-100, Math.min(100, Math.round(fraction * 100f)));
        PacketDistributor.sendToServer(new ControlWheelAnimatePacket(pos, pct));
    }

    private static void handleControlWheelAnimate(ControlWheelAnimatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            ServerLevel level = sp.serverLevel();
            BlockEntity be = level.getBlockEntity(packet.pos());
            if (!(be instanceof LinkedKeyboardBlockEntity kb)) return;
            net.minecraft.world.level.block.Block block = level.getBlockState(packet.pos()).getBlock();
            if (!(block instanceof dev.bennethogan.universalkeyboard.block.LinkedControlWheelBlock)
                    && !(block instanceof dev.bennethogan.universalkeyboard.block.DashboardBlock)) return;
            kb.setStoredWheelFraction(packet.fractionPct() / 100.0f);
            PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(packet.pos()),
                    new ControlWheelAnimateClientPacket(packet.pos(), packet.fractionPct()));
        });
    }

    // --- Keypress animation sync (so other players see each others' typing) ---------------------------

    public record KeyAnimPacket(BlockPos pos, int key, boolean press) implements CustomPacketPayload {
        public static final Type<KeyAnimPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "key_anim"));
        public static final StreamCodec<FriendlyByteBuf, KeyAnimPacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC,  KeyAnimPacket::pos,
                        ByteBufCodecs.VAR_INT,  KeyAnimPacket::key,
                        ByteBufCodecs.BOOL,     KeyAnimPacket::press,
                        KeyAnimPacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record KeyAnimClientPacket(BlockPos pos, int key, boolean press) implements CustomPacketPayload {
        public static final Type<KeyAnimClientPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "key_anim_client"));
        public static final StreamCodec<FriendlyByteBuf, KeyAnimClientPacket> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC,  KeyAnimClientPacket::pos,
                        ByteBufCodecs.VAR_INT,  KeyAnimClientPacket::key,
                        ByteBufCodecs.BOOL,     KeyAnimClientPacket::press,
                        KeyAnimClientPacket::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void sendKeyAnim(BlockPos keyboardPos, int key, boolean press) {
        if (keyboardPos == null) return;
        PacketDistributor.sendToServer(new KeyAnimPacket(keyboardPos, key, press));
    }

    private static void handleKeyAnim(KeyAnimPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            ServerLevel level = sp.serverLevel();
            if (!sp.blockPosition().closerThan(packet.pos(), 64.0)) return;
            if (!(level.getBlockEntity(packet.pos()) instanceof LinkedKeyboardBlockEntity)) return;
            PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(packet.pos()),
                    new KeyAnimClientPacket(packet.pos(), packet.key(), packet.press()));
        });
    }

    public static void sendOpenLiveControl(BlockPos keyboardPos) {
        PacketDistributor.sendToServer(new OpenLiveControlPacket(keyboardPos));
    }

    public static void sendSetFavorite(BlockPos keyboardPos, FavoriteScreen fav) {
        PacketDistributor.sendToServer(new SetFavoritePacket(keyboardPos, (byte) fav.ordinal()));
    }

    public static void sendSetKeyboardLock(BlockPos keyboardPos, boolean lock) {
        PacketDistributor.sendToServer(new SetKeyboardLockPacket(keyboardPos, lock));
    }

    private static void handleCameraToggle(CameraTogglePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity kb)) return;
            if (!kb.isUsableBy(sp)) return;
            var level = sp.serverLevel();
            // deduped server-side count of linked viewfinders in sorted order
            java.util.LinkedHashSet<BlockPos> vfs = new java.util.LinkedHashSet<>();
            Map<Integer, List<BlockPos>> targets = kb.getAllChannelTargets();
            for (int ch = 1; ch <= LinkedKeyboardBlockEntity.MAX_CHANNELS; ch++) {
                List<BlockPos> list = targets.get(ch);
                if (list == null) continue;
                for (BlockPos tp : list)
                    if (dev.bennethogan.universalkeyboard.compat.VistaCamera.isViewfinder(level, tp)) vfs.add(tp);
            }
            int n = vfs.size();
            if (n <= 0) { kb.setActiveCameraIndex(-1); return; }
            int idx = kb.getActiveCameraIndex();
            if (packet.cameraOnly()) {
                idx = ((idx < 0 ? 0 : idx) + 1) % n;   // wrap, no off state
            } else {
                idx = idx + 1;
                if (idx >= n) idx = -1;                 // off -> cam0 -> ... -> camN-1 -> off
            }
            kb.setActiveCameraIndex(idx);
        });
    }

    private static void handleCameraNudge(CameraNudgePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            var level = sp.serverLevel();
            BlockPos cam = packet.cameraPos();
            if (!dev.bennethogan.universalkeyboard.compat.VistaCamera.isViewfinder(level, cam)) return;
            dev.bennethogan.universalkeyboard.compat.VistaCamera.nudgeAim(level, cam, packet.dYaw(), packet.dPitch());
            dev.bennethogan.universalkeyboard.compat.VistaCamera.nudgeZoom(level, cam, packet.dZoom());
        });
    }

    // Server-side cannons linked to a keyboard. channel <= 0 = every channel; else just that channel.
    private static List<BlockPos> cannonsServer(net.minecraft.server.level.ServerLevel level,
                                                LinkedKeyboardBlockEntity kb, int channel) {
        java.util.LinkedHashSet<BlockPos> out = new java.util.LinkedHashSet<>();
        Map<Integer, List<BlockPos>> targets = kb.getAllChannelTargets();
        int from = channel > 0 ? channel : 1;
        int to   = channel > 0 ? channel : LinkedKeyboardBlockEntity.MAX_CHANNELS;
        for (int ch = from; ch <= to; ch++) {
            List<BlockPos> list = targets.get(ch);
            if (list == null) continue;
            for (BlockPos tp : list)
                if (dev.bennethogan.universalkeyboard.compat.CannonControl.isCannon(level, tp)) out.add(tp);
        }
        return new ArrayList<>(out);
    }

    private static void handleGunAim(GunAimPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity kb)) return;
            if (!kb.isUsableBy(sp)) return;
            var level = sp.serverLevel();
            net.minecraft.world.phys.Vec3 target =
                    new net.minecraft.world.phys.Vec3(packet.tx(), packet.ty(), packet.tz());
            for (BlockPos cannon : cannonsServer(level, kb, packet.channel()))
                if (dev.bennethogan.universalkeyboard.compat.CannonLeases.claim(level, cannon, sp))
                    dev.bennethogan.universalkeyboard.compat.CannonControl.aimAt(level, cannon, target, packet.power());
        });
    }

    private static void handleGunManual(GunManualPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity kb)) return;
            if (!kb.isUsableBy(sp)) return;
            var level = sp.serverLevel();
            for (BlockPos cannon : cannonsServer(level, kb, packet.channel())) {
                if (!dev.bennethogan.universalkeyboard.compat.CannonLeases.claim(level, cannon, sp)) continue;
                if (packet.dYaw() != 0 || packet.dPitch() != 0)
                    dev.bennethogan.universalkeyboard.compat.CannonControl.nudgeAim(level, cannon, packet.dYaw(), packet.dPitch());
                if (packet.dPower() != 0)
                    dev.bennethogan.universalkeyboard.compat.CannonControl.stepPower(level, cannon, packet.dPower());
            }
        });
    }

    private static void handleGunFire(GunFirePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity kb)) return;
            if (!kb.isUsableBy(sp)) return;
            var level = sp.serverLevel();
            for (BlockPos cannon : cannonsServer(level, kb, packet.channel()))
                if (dev.bennethogan.universalkeyboard.compat.CannonLeases.claim(level, cannon, sp))
                    dev.bennethogan.universalkeyboard.compat.CannonControl.fire(level, cannon, sp);
        });
    }

    private static void handleGunRelease(GunReleasePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity kb)) return;
            var level = sp.serverLevel();
            dev.bennethogan.universalkeyboard.compat.CannonLeases.releaseAll(
                    level, sp.getUUID(), cannonsServer(level, kb, packet.channel()));
        });
    }

    // Control Panels panels linked on a specific channel (channel 0 = all channels).
    private static List<BlockPos> panelsServer(net.minecraft.server.level.ServerLevel level,
                                               LinkedKeyboardBlockEntity kb, int channel) {
        java.util.LinkedHashSet<BlockPos> out = new java.util.LinkedHashSet<>();
        Map<Integer, List<BlockPos>> targets = kb.getAllChannelTargets();
        int from = channel > 0 ? channel : 1;
        int to   = channel > 0 ? channel : LinkedKeyboardBlockEntity.MAX_CHANNELS;
        for (int ch = from; ch <= to; ch++) {
            List<BlockPos> list = targets.get(ch);
            if (list == null) continue;
            for (BlockPos tp : list)
                if (dev.bennethogan.universalkeyboard.compat.PanelControl.isPanel(level, tp)) out.add(tp);
        }
        return new ArrayList<>(out);
    }

    private static void handlePanSet(PanSetPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity kb)) return;
            if (!kb.isUsableBy(sp)) return;
            var level = sp.serverLevel();
            // Prefer modules point-linked onto this channel: the index maps into that list and the
            // resolved module name is driven on every panel on the channel
            List<String> linked = kb.getChannelModules(packet.channel());
            if (linked != null && !linked.isEmpty()) {
                if (packet.moduleIndex() < 0 || packet.moduleIndex() >= linked.size()) return;
                String moduleName = linked.get(packet.moduleIndex());
                for (BlockPos panel : panelsServer(level, kb, packet.channel()))
                    dev.bennethogan.universalkeyboard.compat.PanelControl.setValue(level, panel, moduleName, packet.value());
                return;
            }
            // Fallback
            for (BlockPos panel : panelsServer(level, kb, packet.channel())) {
                List<String> names = dev.bennethogan.universalkeyboard.compat.PanelControl.drivableModuleNames(level, panel);
                if (names.isEmpty() || packet.moduleIndex() < 0 || packet.moduleIndex() >= names.size()) continue;
                dev.bennethogan.universalkeyboard.compat.PanelControl.setValue(
                        level, panel, names.get(packet.moduleIndex()), packet.value());
            }
        });
    }

    private static void handleUnlinkTarget(UnlinkTargetPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity kb)) return;
            if (!kb.isUsableBy(sp)) return;
            ItemStack cass = kb.unlinkTarget(packet.channel(), packet.targetPos());
            if (!cass.isEmpty() && !sp.getInventory().add(cass)) sp.drop(cass, false);
        });
    }

    private static void handleMoveTarget(MoveTargetPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity kb)) return;
            if (!kb.isUsableBy(sp)) return;
            boolean ok = kb.moveTarget(packet.targetPos(), packet.fromChannel(), packet.toChannel(), packet.newIndex());
            if (!ok) sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§c[Universal Keyboard] §fCouldn't move — channel " + packet.toChannel()
                    + " may hold a different block type."), true);
        });
    }

    private static void handleSetKeyboardLock(SetKeyboardLockPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity kb)) return;
            if (!kb.isUsableBy(sp)) return;
            kb.setLocked(packet.lock(), sp);
            sp.displayClientMessage(net.minecraft.network.chat.Component.literal(packet.lock()
                    ? "§e[Universal Keyboard] §fLocked to §e" + sp.getName().getString() + "§f."
                    : "§a[Universal Keyboard] §fUnlocked — public use."), true);
        });
    }

    private static void handleSetFavorite(SetFavoritePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.keyboardPos());
            if (!(be instanceof LinkedKeyboardBlockEntity kb)) return;
            kb.setFavoriteScreen(FavoriteScreen.fromByte(packet.favorite()));
            // Confirm back to client so the star button updates immediately.
            PacketDistributor.sendToPlayer(sp, new SyncFavoritePacket(packet.keyboardPos(), packet.favorite()));
        });
    }

    private static void handleApplyPositionMove(ApplyPositionMovePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.serverLevel().getBlockEntity(packet.kbPos());
            if (!(be instanceof LinkedKeyboardBlockEntity kb)) return;
            kb.applyPositionChoice(packet.choice(), packet.dontAsk());
            openModeSelectionFallback(sp, packet.kbPos(), kb, sp.serverLevel());
        });
    }

    // Called from LinkedKeyboardBlock when a favorite shortcut is in effect
    public static void openFavoriteForPlayer(ServerPlayer sp, BlockPos keyboardPos,
            LinkedKeyboardBlockEntity kb) {
        var level = sp.serverLevel();
        FavoriteScreen fav = kb.getFavoriteScreen();
        switch (fav) {
            case LIVE_CONTROL -> {
                // Favorite shortcut: signal the client this is a quick-open and the client decides
                // whether to actually auto-start from its own config preference
                openLiveControlForPlayer(sp, keyboardPos, kb, true);
            }
            case SEQUENCER -> sendOpenSequencer(sp, keyboardPos, kb);
            case THRUSTER_CONTROL -> {
                BlockPos targetPos = kb.getLinkedTargetPos();
                if (targetPos == null || !kb.isTargetInRange()) {
                    openModeSelectionFallback(sp, keyboardPos, kb, level);
                    return;
                }
                PeripheralHelper.ThrusterState state = PeripheralHelper.scanThruster(level, targetPos);
                if (state == null) {
                    openModeSelectionFallback(sp, keyboardPos, kb, level);
                    return;
                }
                double[] snap = (SableCompat.isPresent() && SableCompat.isOnSublevel(level, keyboardPos))
                        ? SableCompat.getSnapshot(level, keyboardPos) : new double[0];
                sendOpenThrusterControl(sp, keyboardPos, state, kb.getActiveChannel(), snap);
            }
            case NONE -> openModeSelectionFallback(sp, keyboardPos, kb, level);
        }
    }

    private static void openModeSelectionFallback(ServerPlayer sp, BlockPos keyboardPos,
            LinkedKeyboardBlockEntity kb, Level level) {
        BlockPos targetPos = kb.getLinkedTargetPos();
        if (targetPos == null) {
            sendOpenModeSelection(sp, keyboardPos, "", 1 << KeyboardMode.PERIPHERAL_SEQUENCER.ordinal());
        } else {
            int bits = KeyboardMode.availableBitfield(level, targetPos)
                    | (1 << KeyboardMode.PERIPHERAL_SEQUENCER.ordinal());
            // getBlockState also force-loads chunks; skip if not already loaded
            String typeName = level.isLoaded(targetPos)
                    ? level.getBlockState(targetPos).getBlock().getName().getString()
                    : "";
            sendOpenModeSelection(sp, keyboardPos, typeName, bits);
        }
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
            openLiveControlForPlayer(sp, packet.keyboardPos(), kb, false);
        });
    }

    public static void openLiveControlForPlayer(ServerPlayer sp, BlockPos keyboardPos,
            LinkedKeyboardBlockEntity kb, boolean favoriteShortcut) {
        var level = sp.serverLevel();
        boolean hasThrusters = false, hasVector = false, hasRpm = false;

        int[] thrusterPowers = new int[LinkedKeyboardBlockEntity.MAX_CHANNELS + 1];
        int[] rpmValues      = new int[LinkedKeyboardBlockEntity.MAX_CHANNELS + 1];
        for (int ch = 1; ch <= LinkedKeyboardBlockEntity.MAX_CHANNELS; ch++) {
            for (BlockPos tp : kb.getLinkedTargetPositions(ch)) {
                Object p = PeripheralHelper.getPeripheral(level, tp);
                if (p != null) {
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
                } else {
                    // No CC peripheral but still allow RPM mode via the Create value panel on a creative
                    // motor / rotation speed controller
                    BlockEntity tbe = level.getBlockEntity(tp);
                    if (CreateValueHelper.isRpmScrollBlock(tbe)) {
                        hasRpm = true;
                        if (rpmValues[ch] == 0) {
                            int v = CreateValueHelper.getValue(tbe);
                            if (v != Integer.MIN_VALUE) rpmValues[ch] = v;
                        }
                    }
                }
            }
        }

        Direction[] dirs = Direction.values();
        int[] localRs = new int[dirs.length];
        for (Direction d : dirs) localRs[d.ordinal()] = kb.getRedstoneOutput(d);

        int wc = kb.getRsLinkCount();
        int[] rsLinkPowers = new int[wc];
        for (int i = 0; i < wc; i++) rsLinkPowers[i] = kb.getRsLinkOutput(i);

        PacketDistributor.sendToPlayer(sp, new OpenLiveControlScreenPacket(
                keyboardPos, kb.getLiveControlBindings(),
                wc, hasThrusters, hasVector, hasRpm, localRs, rsLinkPowers, thrusterPowers,
                kb.getSequencerVars(), rpmValues,
                kb.getActiveProfile(), kb.getAllProfileBindings(), favoriteShortcut));
        PacketDistributor.sendToPlayer(sp, new SyncFavoritePacket(keyboardPos, (byte) kb.getFavoriteScreen().ordinal()));
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
                dev.bennethogan.universalkeyboard.livecontrol.ChannelMode cm =
                        dev.bennethogan.universalkeyboard.livecontrol.ChannelMode.byOpcode(a.type());
                if (cm != null) {
                    int value = (int) Math.round(a.v1());
                    for (BlockPos tp : kb.getLinkedTargetPositions(a.target())) {
                        Object p = PeripheralHelper.getPeripheral(level, tp);
                        boolean applied = p != null && cm.server.apply(p, value);
                        // Fallback: no CC RPM peripheral, set the Create scroll value on a
                        // creative motor / rotation speed controller.
                        if (!applied && cm.type == dev.bennethogan.universalkeyboard.livecontrol.LiveControlBinding.ActionType.RPM_CONTROL) {
                            BlockEntity tbe = level.getBlockEntity(tp);
                            if (CreateValueHelper.isRpmScrollBlock(tbe)) CreateValueHelper.setValue(tbe, value);
                        }
                    }
                    continue;
                }
                switch (a.type()) {
                    case 0 -> { // local RS side
                        Direction[] dirs = Direction.values();
                        if (a.target() >= 0 && a.target() < dirs.length)
                            kb.setRedstoneOutput(dirs[a.target()], (int) Math.round(a.v1()));
                    }
                    case 1 -> // wireless RS
                        kb.setRsLinkOutput(a.target(), (int) Math.round(a.v1()));
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
                    case 5 -> // link channel broadcast (target = wirelessFreqIdx 0-based)
                        kb.broadcastWirelessFreq(a.target(), (int) Math.round(a.v1()));
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
                            String[] freqs = new String[LinkedKeyboardBlockEntity.MAX_WIRELESS_FREQS];
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
                            buf.writeInt(LinkedKeyboardBlockEntity.MAX_WIRELESS_FREQS);
                            for (int i = 0; i < LinkedKeyboardBlockEntity.MAX_WIRELESS_FREQS; i++) {
                                String f = (pkt.freqs() != null && i < pkt.freqs().length && pkt.freqs()[i] != null) ? pkt.freqs()[i] : "";
                                buf.writeUtf(f, 8);
                            }
                        },
                        buf -> {
                            BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                            int count = buf.readInt();
                            String[] freqs = new String[LinkedKeyboardBlockEntity.MAX_WIRELESS_FREQS];
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

    // Client -> Server: set/clear the favorite screen for a keyboard
    // Client -> Server: lock the keyboard to the sender (they become owner) or unlock it
    public record SetKeyboardLockPacket(BlockPos keyboardPos, boolean lock) implements CustomPacketPayload {
        public static final Type<SetKeyboardLockPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "set_keyboard_lock"));
        public static final StreamCodec<FriendlyByteBuf, SetKeyboardLockPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> { BlockPos.STREAM_CODEC.encode(buf, pkt.keyboardPos()); buf.writeBoolean(pkt.lock()); },
                buf -> new SetKeyboardLockPacket(BlockPos.STREAM_CODEC.decode(buf), buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SetFavoritePacket(BlockPos keyboardPos, byte favorite) implements CustomPacketPayload {
        public static final Type<SetFavoritePacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "set_favorite"));
        public static final StreamCodec<FriendlyByteBuf, SetFavoritePacket> CODEC = StreamCodec.of(
                (buf, pkt) -> { BlockPos.STREAM_CODEC.encode(buf, pkt.keyboardPos()); buf.writeByte(pkt.favorite()); },
                buf -> new SetFavoritePacket(BlockPos.STREAM_CODEC.decode(buf), buf.readByte()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // Server -> Client: sync the current favorite so favoritable screens can highlight the star
    public record SyncFavoritePacket(BlockPos keyboardPos, byte favorite) implements CustomPacketPayload {
        public static final Type<SyncFavoritePacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalKeyboardMod.MOD_ID, "sync_favorite"));
        public static final StreamCodec<FriendlyByteBuf, SyncFavoritePacket> CODEC = StreamCodec.of(
                (buf, pkt) -> { BlockPos.STREAM_CODEC.encode(buf, pkt.keyboardPos()); buf.writeByte(pkt.favorite()); },
                buf -> new SyncFavoritePacket(BlockPos.STREAM_CODEC.decode(buf), buf.readByte()));
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
