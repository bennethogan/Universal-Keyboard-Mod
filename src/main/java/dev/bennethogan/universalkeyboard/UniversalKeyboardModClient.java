package dev.bennethogan.universalkeyboard;

import dev.bennethogan.universalkeyboard.block.ModBlocks;
import dev.bennethogan.universalkeyboard.blockentity.ModBlockEntities;
import dev.bennethogan.universalkeyboard.client.ClientPacketHandlers;
import dev.bennethogan.universalkeyboard.client.KeyboardCaptureManager;
import dev.bennethogan.universalkeyboard.client.KeyboardInputHandler;
import dev.bennethogan.universalkeyboard.client.LinkingModeRenderer;
import dev.bennethogan.universalkeyboard.client.model.WirelessCopycatBaseModel;
import dev.bennethogan.universalkeyboard.client.model.WirelessCopycatPanelModel;
import dev.bennethogan.universalkeyboard.client.model.WirelessCopycatStepModel;
import dev.bennethogan.universalkeyboard.livecontrol.LiveControlManager;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.function.Function;

@Mod(value = UniversalKeyboardMod.MOD_ID, dist = Dist.CLIENT)
public class UniversalKeyboardModClient {

    public UniversalKeyboardModClient(IEventBus modEventBus) {
        modEventBus.addListener(ClientPacketHandlers::onRegisterClientPayloads);
        modEventBus.addListener(this::onRegisterCapabilities);
        modEventBus.addListener(this::onRegisterMenuScreens);
        if (ModList.get().isLoaded("create")) {
            modEventBus.addListener(this::onModelBake);
        }

        NeoForge.EVENT_BUS.addListener(KeyboardInputHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(KeyboardInputHandler::onKeyInput);
        NeoForge.EVENT_BUS.addListener(KeyboardInputHandler::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(KeyboardInputHandler::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(LinkingModeRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(UniversalKeyboardModClient::onPlayerLogout);
    }

    private void onModelBake(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        wrapBlock(models, ModBlocks.WIRELESS_COPYCAT,       WirelessCopycatBaseModel::new);
        wrapBlock(models, ModBlocks.WIRELESS_COPYCAT_PANEL, WirelessCopycatPanelModel::new);
        wrapBlock(models, ModBlocks.WIRELESS_COPYCAT_STEP,  WirelessCopycatStepModel::new);
    }

    private static void wrapBlock(java.util.Map<net.minecraft.client.resources.model.ModelResourceLocation,
            net.minecraft.client.resources.model.BakedModel> models,
            net.neoforged.neoforge.registries.DeferredBlock<?> deferredBlock,
            Function<net.minecraft.client.resources.model.BakedModel,
                    net.minecraft.client.resources.model.BakedModel> factory) {
        if (deferredBlock == null) return;
        Block block = deferredBlock.get();
        var blockId = BuiltInRegistries.BLOCK.getKey(block);
        block.getStateDefinition().getPossibleStates().forEach(state -> {
            var loc = BlockModelShaper.stateToModelLocation(blockId, state);
            var existing = models.get(loc);
            if (existing != null) models.put(loc, factory.apply(existing));
        });
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
