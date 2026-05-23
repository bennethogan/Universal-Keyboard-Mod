package dev.bennethogan.universalkeyboard.blockentity;

import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import dev.bennethogan.universalkeyboard.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, UniversalKeyboardMod.MOD_ID);

    public static final Supplier<BlockEntityType<LinkedKeyboardBlockEntity>> LINKED_KEYBOARD =
            BLOCK_ENTITIES.register("universal_keyboard", () ->
                    BlockEntityType.Builder.of(LinkedKeyboardBlockEntity::new,
                            ModBlocks.LINKED_KEYBOARD.get(),
                            ModBlocks.TRANS_KEYBOARD.get(),
                            ModBlocks.RAINBOW_KEYBOARD.get(),
                            ModBlocks.ACE_KEYBOARD.get(),
                            ModBlocks.BI_KEYBOARD.get()).build(null));

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        try {
            Class.forName("dan200.computercraft.api.peripheral.IPeripheral");
            registerCCCapabilities(event);
        } catch (ClassNotFoundException e) {
            UniversalKeyboardMod.LOGGER.info("CC:Tweaked not found, skipping peripheral registration.");
        }
    }

    private static void registerCCCapabilities(RegisterCapabilitiesEvent event) {
        try {
            var cap = net.neoforged.neoforge.capabilities.BlockCapability.createSided(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            "computercraft", "peripheral"),
                    dan200.computercraft.api.peripheral.IPeripheral.class
            );
            event.registerBlockEntity(cap, LINKED_KEYBOARD.get(),
                    (be, direction) -> (dan200.computercraft.api.peripheral.IPeripheral) be.getPeripheral());
        } catch (Exception e) {
            UniversalKeyboardMod.LOGGER.warn("Could not register CC peripheral capability: {}", e.getMessage());
        }
    }
}
