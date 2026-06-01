package dev.bennethogan.universalkeyboard;

import dev.bennethogan.universalkeyboard.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, UniversalKeyboardMod.MOD_ID);

    public static final Supplier<CreativeModeTab> TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .title(Component.translatable("itemGroup.universalkeyboard.main"))
                    .icon(() -> new ItemStack(ModItems.LINKED_KEYBOARD.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.LINKED_KEYBOARD.get());
                        output.accept(ModItems.TRANS_KEYBOARD.get());
                        output.accept(ModItems.RAINBOW_KEYBOARD.get());
                        output.accept(ModItems.ACE_KEYBOARD.get());
                        output.accept(ModItems.BI_KEYBOARD.get());
                        if (ModItems.WIRELESS_COPYCAT       != null) output.accept(ModItems.WIRELESS_COPYCAT.get());
                        if (ModItems.WIRELESS_COPYCAT_PANEL != null) output.accept(ModItems.WIRELESS_COPYCAT_PANEL.get());
                        if (ModItems.WIRELESS_COPYCAT_STEP  != null) output.accept(ModItems.WIRELESS_COPYCAT_STEP.get());
                    })
                    .build());
}
