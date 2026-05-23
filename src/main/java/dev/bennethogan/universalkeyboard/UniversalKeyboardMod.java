package dev.bennethogan.universalkeyboard;

import dev.bennethogan.universalkeyboard.block.ModBlocks;
import dev.bennethogan.universalkeyboard.blockentity.ModBlockEntities;
import dev.bennethogan.universalkeyboard.config.ModConfig;
import dev.bennethogan.universalkeyboard.item.ModItems;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
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
        dev.bennethogan.universalkeyboard.menu.ModMenus.MENUS.register(modEventBus);

        modContainer.registerConfig(Type.COMMON, ModConfig.COMMON_SPEC);

        modEventBus.addListener(ModPackets::onRegisterServerPayloads);
        modEventBus.addListener(this::onRegisterCapabilities);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onBuildCreativeTab);

        // Register Create display source if Create is present
        try {
            Class.forName("com.simibubi.create.api.behaviour.display.DisplaySource");
            dev.bennethogan.universalkeyboard.compat.ModDisplaySources.init(modEventBus);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            LOGGER.info("Create not present — display source skipped.");
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Register block entity → display source associations after registries are frozen
        try {
            Class.forName("com.simibubi.create.api.behaviour.display.DisplaySource");
            event.enqueueWork(dev.bennethogan.universalkeyboard.compat.ModDisplaySources::registerAssociations);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {}
    }

    private void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        ModBlockEntities.registerCapabilities(event);
    }

    private void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModItems.LINKED_KEYBOARD);
            event.accept(ModItems.TRANS_KEYBOARD);
            event.accept(ModItems.RAINBOW_KEYBOARD);
            event.accept(ModItems.ACE_KEYBOARD);
            event.accept(ModItems.BI_KEYBOARD);
        }
    }
}
