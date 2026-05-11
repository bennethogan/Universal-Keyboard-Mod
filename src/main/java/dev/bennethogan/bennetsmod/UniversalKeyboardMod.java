package dev.bennethogan.bennetsmod;

import dev.bennethogan.bennetsmod.block.ModBlocks;
import dev.bennethogan.bennetsmod.blockentity.ModBlockEntities;
import dev.bennethogan.bennetsmod.config.ModConfig;
import dev.bennethogan.bennetsmod.item.ModItems;
import dev.bennethogan.bennetsmod.network.ModPackets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(UniversalKeyboardMod.MOD_ID)
public class UniversalKeyboardMod {
    public static final String MOD_ID = "universalkeyboard";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public UniversalKeyboardMod(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);

        modContainer.registerConfig(Type.COMMON, ModConfig.COMMON_SPEC);

        modEventBus.addListener(ModPackets::onRegisterServerPayloads);
        modEventBus.addListener(this::onRegisterCapabilities);
        modEventBus.addListener(this::commonSetup);

        // Register Create display source if Create is present
        try {
            Class.forName("com.simibubi.create.api.behaviour.display.DisplaySource");
            dev.bennethogan.bennetsmod.compat.ModDisplaySources.init(modEventBus);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            LOGGER.info("Create not present — display source skipped.");
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Register block entity → display source associations after registries are frozen
        try {
            Class.forName("com.simibubi.create.api.behaviour.display.DisplaySource");
            event.enqueueWork(dev.bennethogan.bennetsmod.compat.ModDisplaySources::registerAssociations);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {}
    }

    private void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        ModBlockEntities.registerCapabilities(event);
    }
}
