package dev.bennethogan.bennetsmod.client;

import dev.bennethogan.bennetsmod.client.screen.AutoTypeScreen;
import dev.bennethogan.bennetsmod.client.screen.ModeSelectionScreen;
import dev.bennethogan.bennetsmod.client.screen.PeripheralMethodScreen;
import dev.bennethogan.bennetsmod.client.screen.SequencerScreen;
import dev.bennethogan.bennetsmod.client.screen.ThrusterControlScreen;
import dev.bennethogan.bennetsmod.network.ModPackets;
import dev.bennethogan.bennetsmod.network.ModPackets.*;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Client-side packet handlers for all playToClient packets.
 * Kept in the client package so this class is never loaded on a dedicated server.
 */
public class ClientPacketHandlers {

    public static void onRegisterClientPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(KeyboardCapturePacket.TYPE,      KeyboardCapturePacket.CODEC,      ClientPacketHandlers::handleKeyboardCapture);
        registrar.playToClient(OpenAutoTypeScreenPacket.TYPE,   OpenAutoTypeScreenPacket.CODEC,   ClientPacketHandlers::handleOpenAutoTypeScreen);
        registrar.playToClient(StartCreateCapturePacket.TYPE,   StartCreateCapturePacket.CODEC,   ClientPacketHandlers::handleStartCreateCapture);
        registrar.playToClient(OpenPeripheralMenuPacket.TYPE,   OpenPeripheralMenuPacket.CODEC,   ClientPacketHandlers::handleOpenPeripheralMenu);
        registrar.playToClient(OpenModeSelectionPacket.TYPE,    OpenModeSelectionPacket.CODEC,    ClientPacketHandlers::handleOpenModeSelection);
        registrar.playToClient(OpenThrusterControlPacket.TYPE,  OpenThrusterControlPacket.CODEC,  ClientPacketHandlers::handleOpenThrusterControl);
        registrar.playToClient(OpenSequencerPacket.TYPE,        OpenSequencerPacket.CODEC,        ClientPacketHandlers::handleOpenSequencer);
        registrar.playToClient(ChannelChangedPacket.TYPE,       ChannelChangedPacket.CODEC,       ClientPacketHandlers::handleChannelChanged);
    }

    private static void handleKeyboardCapture(KeyboardCapturePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                KeyboardCaptureManager.setCaptureMode(packet.keyboardPos(), packet.capture()));
    }

    private static void handleOpenAutoTypeScreen(OpenAutoTypeScreenPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                Minecraft.getInstance().setScreen(
                        new AutoTypeScreen(packet.keyboardPos(), packet.currentScript())));
    }

    private static void handleStartCreateCapture(StartCreateCapturePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                KeyboardCaptureManager.setCreateCaptureMode(
                        packet.keyboardPos(), packet.currentValue(),
                        packet.minValue(), packet.maxValue()));
    }

    private static void handleOpenPeripheralMenu(OpenPeripheralMenuPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                Minecraft.getInstance().setScreen(
                        new PeripheralMethodScreen(
                                packet.keyboardPos(), packet.peripheralType(),
                                packet.getters(), packet.setters(), packet.channel())));
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
                        packet.channel());
            } else {
                mc.setScreen(new ThrusterControlScreen(
                        packet.keyboardPos(), packet.peripheralType(),
                        packet.targetVectorX(), packet.targetVectorY(),
                        packet.currentVectorX(), packet.currentVectorY(),
                        packet.thrust(), packet.thrustConfig(), packet.configMax(),
                        packet.currentThrustPn(), packet.displayedThrustPn(),
                        packet.airflowMs(), packet.obstruction(),
                        packet.fuelAmountMb(), packet.fuelCapacityMb(),
                        packet.channel()));
            }
        });
    }

    private static void handleOpenSequencer(OpenSequencerPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof SequencerScreen existing
                    && existing.getKeyboardPos().equals(packet.keyboardPos())) {
                existing.updateState(packet.steps(), packet.running(), packet.currentStep(),
                        packet.availableGetterNames(), packet.availableSetters());
            } else {
                mc.setScreen(new SequencerScreen(
                        packet.keyboardPos(), packet.steps(), packet.running(), packet.currentStep(),
                        packet.peripheralType(), packet.availableGetterNames(), packet.availableSetters()));
            }
        });
    }

    private static void handleChannelChanged(ChannelChangedPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                KeyboardCaptureManager.setCapturedChannel(packet.channel()));
    }
}
