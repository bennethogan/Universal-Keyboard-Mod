package dev.bennethogan.universalkeyboard;

import dev.bennethogan.universalkeyboard.blockentity.ModBlockEntities;
import dev.bennethogan.universalkeyboard.client.ClientPacketHandlers;
import dev.bennethogan.universalkeyboard.client.KeyboardCaptureManager;
import dev.bennethogan.universalkeyboard.client.KeyboardInputHandler;
import dev.bennethogan.universalkeyboard.client.LinkingModeRenderer;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = UniversalKeyboardMod.MOD_ID, dist = Dist.CLIENT)
public class UniversalKeyboardModClient {

    public UniversalKeyboardModClient(IEventBus modEventBus) {
        modEventBus.addListener(ClientPacketHandlers::onRegisterClientPayloads);
        modEventBus.addListener(this::onRegisterCapabilities);
        modEventBus.addListener(this::onRegisterMenuScreens);

        NeoForge.EVENT_BUS.addListener(KeyboardInputHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(KeyboardInputHandler::onKeyInput);
        NeoForge.EVENT_BUS.addListener(KeyboardInputHandler::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(KeyboardInputHandler::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(LinkingModeRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(UniversalKeyboardModClient::onPlayerLogout);
    }

    private void onRegisterMenuScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(dev.bennethogan.universalkeyboard.menu.ModMenus.WIRELESS_CONFIG_MENU.get(),
                dev.bennethogan.universalkeyboard.client.screen.WirelessConfigScreen::new);
    }

    private void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        ModBlockEntities.registerCapabilities(event);
    }

    private static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (LiveControlManager.isActive()) LiveControlManager.deactivate();
        if (KeyboardCaptureManager.isCapturing()) {
            KeyboardCaptureManager.setCaptureMode(null, false);
        }
    }
}
