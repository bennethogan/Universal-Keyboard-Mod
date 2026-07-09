package dev.bennethogan.universalkeyboard;

import dev.bennethogan.universalkeyboard.block.ModBlocks;
import dev.bennethogan.universalkeyboard.blockentity.ModBlockEntities;
import dev.bennethogan.universalkeyboard.config.ModConfig;
import dev.bennethogan.universalkeyboard.item.ModItems;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
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
        if (ModList.get().isLoaded("create")) {
            ModBlocks.registerForCreate();
            ModItems.registerForCreate();
            ModBlockEntities.registerForCreate();
        }

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        dev.bennethogan.universalkeyboard.menu.ModMenus.MENUS.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
        dev.bennethogan.universalkeyboard.recipe.ModRecipeConditions.register(modEventBus);

        modContainer.registerConfig(Type.COMMON, ModConfig.COMMON_SPEC);
        modContainer.registerConfig(Type.CLIENT, ModConfig.CLIENT_SPEC);

        modEventBus.addListener(ModPackets::onRegisterServerPayloads);
        modEventBus.addListener(this::onRegisterCapabilities);
        modEventBus.addListener(this::commonSetup);

        // cannon ownership leasing for GUN mode
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                dev.bennethogan.universalkeyboard.compat.CannonLeases::onServerTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                dev.bennethogan.universalkeyboard.compat.CannonLeases::onPlayerLogout);

        // Block right-click interactions while in linking mode
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                dev.bennethogan.universalkeyboard.item.LinkedKeyboardItem::suppressInteractionWhileLinking);

        try {
            Class.forName("com.simibubi.create.api.behaviour.display.DisplaySource");
            dev.bennethogan.universalkeyboard.compat.ModDisplaySources.init(modEventBus);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            LOGGER.info("Create not present — display source skipped.");
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        try {
            Class.forName("com.simibubi.create.api.behaviour.display.DisplaySource");
            event.enqueueWork(dev.bennethogan.universalkeyboard.compat.ModDisplaySources::registerAssociations);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {}
    }

    private void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        ModBlockEntities.registerCapabilities(event);
    }
}
