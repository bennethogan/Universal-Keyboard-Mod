package dev.bennethogan.universalkeyboard.compat;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import dev.bennethogan.universalkeyboard.blockentity.ModBlockEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// Loaded only when Create is present — never reference this class without a ClassNotFoundException guard.
public class ModDisplaySources {

    private static final DeferredRegister<DisplaySource> DISPLAY_SOURCES =
            DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, UniversalKeyboardMod.MOD_ID);

    private static final DeferredHolder<DisplaySource, KeyboardDisplaySource> KEYBOARD_SOURCE =
            DISPLAY_SOURCES.register("keyboard_peripheral", KeyboardDisplaySource::new);

    public static void init(IEventBus modEventBus) {
        DISPLAY_SOURCES.register(modEventBus);
    }

    // Called from FMLCommonSetupEvent (enqueueWork) — registries are frozen by then.
    public static void registerAssociations() {
        DisplaySource.BY_BLOCK_ENTITY.add(
                ModBlockEntities.LINKED_KEYBOARD.get(),
                KEYBOARD_SOURCE.get()
        );
        UniversalKeyboardMod.LOGGER.info("Registered keyboard peripheral display source.");
    }
}
