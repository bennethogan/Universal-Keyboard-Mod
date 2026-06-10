package dev.bennethogan.universalkeyboard.client;

import dev.bennethogan.universalkeyboard.api.PeripheralMenuConfig;
import dev.bennethogan.universalkeyboard.client.screen.LinkFrequencyScreen;
import dev.bennethogan.universalkeyboard.client.screen.LiveControlScreen;
import dev.bennethogan.universalkeyboard.client.screen.MenuNav;
import dev.bennethogan.universalkeyboard.client.screen.ModeSelectionScreen;
import dev.bennethogan.universalkeyboard.client.screen.PeripheralControlScreen;
import dev.bennethogan.universalkeyboard.client.screen.SequencerScreen;
import dev.bennethogan.universalkeyboard.client.screen.ThrusterControlScreen;
import dev.bennethogan.universalkeyboard.client.screen.WikiScreen;
import dev.bennethogan.universalkeyboard.client.screen.WirelessCopycatScreen;
import dev.bennethogan.universalkeyboard.livecontrol.FavoriteScreen;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlManager;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import dev.bennethogan.universalkeyboard.network.ModPackets.*;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;


public class ClientPacketHandlers {

    public static void onRegisterClientPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(KeyboardCapturePacket.TYPE,      KeyboardCapturePacket.CODEC,      ClientPacketHandlers::handleKeyboardCapture);
        registrar.playToClient(StartCreateCapturePacket.TYPE,   StartCreateCapturePacket.CODEC,   ClientPacketHandlers::handleStartCreateCapture);
        registrar.playToClient(OpenPeripheralMenuPacket.TYPE,   OpenPeripheralMenuPacket.CODEC,   ClientPacketHandlers::handleOpenPeripheralMenu);
        registrar.playToClient(OpenModeSelectionPacket.TYPE,    OpenModeSelectionPacket.CODEC,    ClientPacketHandlers::handleOpenModeSelection);
        registrar.playToClient(OpenThrusterControlPacket.TYPE,  OpenThrusterControlPacket.CODEC,  ClientPacketHandlers::handleOpenThrusterControl);
        registrar.playToClient(OpenSequencerPacket.TYPE,           OpenSequencerPacket.CODEC,           ClientPacketHandlers::handleOpenSequencer);
        registrar.playToClient(ModPackets.SequencerProgressPacket.TYPE,       ModPackets.SequencerProgressPacket.CODEC,       ClientPacketHandlers::handleSequencerProgress);
        registrar.playToClient(ModPackets.TypewriterImportOfferPacket.TYPE,   ModPackets.TypewriterImportOfferPacket.CODEC,   ClientPacketHandlers::handleTypewriterOffer);
        registrar.playToClient(ChannelChangedPacket.TYPE,          ChannelChangedPacket.CODEC,          ClientPacketHandlers::handleChannelChanged);
        registrar.playToClient(OpenLiveControlScreenPacket.TYPE, OpenLiveControlScreenPacket.CODEC, ClientPacketHandlers::handleOpenLiveControlScreen);
        registrar.playToClient(ModPackets.ControlWheelAnimateClientPacket.TYPE, ModPackets.ControlWheelAnimateClientPacket.CODEC, ClientPacketHandlers::handleControlWheelAnimate);
        registrar.playToClient(ModPackets.SyncFavoritePacket.TYPE, ModPackets.SyncFavoritePacket.CODEC, ClientPacketHandlers::handleSyncFavorite);
        registrar.optional().playToClient(ModPackets.OpenWirelessCopycatScreenPacket.TYPE, ModPackets.OpenWirelessCopycatScreenPacket.STREAM_CODEC, ClientPacketHandlers::handleOpenWirelessCopycatScreen);
        registrar.optional().playToClient(ModPackets.OpenLinkFreqScreenPacket.TYPE,        ModPackets.OpenLinkFreqScreenPacket.STREAM_CODEC,        ClientPacketHandlers::handleOpenLinkFreqScreen);
    }

    private static void handleKeyboardCapture(KeyboardCapturePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                KeyboardCaptureManager.setCaptureMode(packet.keyboardPos(), packet.capture()));
    }

    private static void handleStartCreateCapture(StartCreateCapturePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                KeyboardCaptureManager.setCreateCaptureMode(
                        packet.keyboardPos(), packet.currentValue(),
                        packet.minValue(), packet.maxValue()));
    }

    private static void handleOpenPeripheralMenu(OpenPeripheralMenuPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            PeripheralMenuConfig cfg = PeripheralMenuConfig.defaultFrom(
                    packet.peripheralType(), packet.getters(), packet.setters());
            Minecraft.getInstance().setScreen(
                    new PeripheralControlScreen(packet.keyboardPos(), cfg, packet.channel()));
        });
    }

    private static void handleOpenModeSelection(OpenModeSelectionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                Minecraft.getInstance().setScreen(
                        new ModeSelectionScreen(
                                packet.keyboardPos(), packet.targetTypeName(), packet.availableBits())));
    }

    private static void handleOpenThrusterControl(OpenThrusterControlPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ThrusterControlScreen existing
                    && existing.getKeyboardPos().equals(packet.keyboardPos())
                    && existing.getCurrentChannel() == packet.channel()) {
                existing.updateState(
                        packet.peripheralType(),
                        packet.targetVectorX(), packet.targetVectorY(),
                        packet.currentVectorX(), packet.currentVectorY(),
                        packet.thrust(), packet.thrustConfig(), packet.configMax(),
                        packet.currentThrustPn(), packet.displayedThrustPn(),
                        packet.airflowMs(), packet.obstruction(),
                        packet.fuelAmountMb(), packet.fuelCapacityMb(),
                        packet.channel(), packet.sublevelSnapshot());
            } else {
                mc.setScreen(new ThrusterControlScreen(
                        packet.keyboardPos(), packet.peripheralType(),
                        packet.targetVectorX(), packet.targetVectorY(),
                        packet.currentVectorX(), packet.currentVectorY(),
                        packet.thrust(), packet.thrustConfig(), packet.configMax(),
                        packet.currentThrustPn(), packet.displayedThrustPn(),
                        packet.airflowMs(), packet.obstruction(),
                        packet.fuelAmountMb(), packet.fuelCapacityMb(),
                        packet.channel(), packet.sublevelSnapshot()));
            }
        });
    }

    private static void handleOpenSequencer(OpenSequencerPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof SequencerScreen existing
                    && existing.getKeyboardPos().equals(packet.keyboardPos())) {
                existing.updateState(packet.steps(), packet.running(), packet.currentStep(),
                        packet.availableGetterNames(), packet.availableSetters(),
                        packet.gettersByChannel(), packet.settersByChannel());
            } else {
                mc.setScreen(new SequencerScreen(
                        packet.keyboardPos(), packet.steps(), packet.running(), packet.currentStep(),
                        packet.availableGetterNames(), packet.availableSetters(),
                        packet.gettersByChannel(), packet.settersByChannel()));
            }
        });
    }

    private static void handleTypewriterOffer(ModPackets.TypewriterImportOfferPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ModeSelectionScreen screen
                    && screen.getKeyboardPos().equals(packet.keyboardPos()))
                screen.handleTypewriterOffer(packet.typewriterPos(), packet.bindingCount(),
                        packet.freqCount(), packet.error());
        });
    }

    private static void handleSequencerProgress(ModPackets.SequencerProgressPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof SequencerScreen screen
                    && screen.getKeyboardPos().equals(packet.keyboardPos()))
                screen.updateProgress(packet.running(), packet.currentStep());
        });
    }

    private static void handleChannelChanged(ChannelChangedPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                KeyboardCaptureManager.setCapturedChannel(packet.channel()));
    }

    private static void handleOpenLiveControlScreen(OpenLiveControlScreenPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (packet.autoStart()) {
                // Favorite shortcut with autoStart=true: activate live controls without opening UI.
                LiveControlManager.activate(
                        packet.keyboardPos(), packet.bindings(),
                        packet.localRsOutputs(), packet.rsLinkPowers(), packet.thrusterPowers(),
                        packet.varValues(), packet.rpmValues());
            } else {
                Minecraft.getInstance().setScreen(
                        new LiveControlScreen(
                                packet.keyboardPos(), packet.rsLinkCount(),
                                packet.hasThrusters(), packet.hasVectorThrusters(), packet.hasRpm(),
                                packet.localRsOutputs(), packet.rsLinkPowers(), packet.thrusterPowers(),
                                packet.varValues(), packet.rpmValues(),
                                packet.activeProfile(), packet.allProfiles()));
            }
        });
    }

    private static void handleControlWheelAnimate(ModPackets.ControlWheelAnimateClientPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            if (mc.level.getBlockEntity(packet.pos())
                    instanceof dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity kb) {
                kb.setWheelTarget(packet.fractionPct() / 100.0f);
            }
        });
    }

    private static void handleSyncFavorite(ModPackets.SyncFavoritePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            FavoriteScreen fav = FavoriteScreen.fromByte(packet.favorite());
            MenuNav.currentFavorite = fav;
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof LiveControlScreen lcs && lcs.getKeyboardPos().equals(packet.keyboardPos()))
                lcs.onFavoriteSync(fav);
            else if (mc.screen instanceof SequencerScreen ss && ss.getKeyboardPos().equals(packet.keyboardPos()))
                ss.onFavoriteSync(fav);
            else if (mc.screen instanceof ThrusterControlScreen ts && ts.getKeyboardPos().equals(packet.keyboardPos()))
                ts.onFavoriteSync(fav);
        });
    }

    private static void handleOpenWirelessCopycatScreen(ModPackets.OpenWirelessCopycatScreenPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                Minecraft.getInstance().setScreen(
                        new WirelessCopycatScreen(packet.pos(), packet.freqs(), packet.enabled())));
    }

    private static void handleOpenLinkFreqScreen(ModPackets.OpenLinkFreqScreenPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                Minecraft.getInstance().setScreen(
                        new LinkFrequencyScreen(packet.pos(), packet.freqs())));
    }
}
