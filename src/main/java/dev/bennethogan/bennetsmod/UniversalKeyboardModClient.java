package dev.bennethogan.bennetsmod;

import dev.bennethogan.bennetsmod.blockentity.ModBlockEntities;
import dev.bennethogan.bennetsmod.client.ClientPacketHandlers;
import dev.bennethogan.bennetsmod.client.KeyboardInputHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = UniversalKeyboardMod.MOD_ID, dist = Dist.CLIENT)
public class UniversalKeyboardModClient {

    public UniversalKeyboardModClient(IEventBus modEventBus) {
        modEventBus.addListener(ClientPacketHandlers::onRegisterClientPayloads);
        modEventBus.addListener(this::onRegisterCapabilities);

        NeoForge.EVENT_BUS.addListener(KeyboardInputHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(KeyboardInputHandler::onKeyInput);
        NeoForge.EVENT_BUS.addListener(KeyboardInputHandler::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(KeyboardInputHandler::onMouseScroll);
    }

    private void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        ModBlockEntities.registerCapabilities(event);
    }
}
