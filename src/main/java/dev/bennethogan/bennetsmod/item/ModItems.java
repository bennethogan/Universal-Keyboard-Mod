package dev.bennethogan.bennetsmod.item;

import dev.bennethogan.bennetsmod.UniversalKeyboardMod;
import dev.bennethogan.bennetsmod.block.ModBlocks;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(UniversalKeyboardMod.MOD_ID);

    public static final DeferredItem<LinkedKeyboardItem> LINKED_KEYBOARD =
            ITEMS.register("universal_keyboard", () ->
                    new LinkedKeyboardItem(ModBlocks.LINKED_KEYBOARD.get(),
                            new Item.Properties().stacksTo(1)));

    public static final DeferredItem<LinkedKeyboardItem> TRANS_KEYBOARD =
            ITEMS.register("trans_keyboard", () ->
                    new LinkedKeyboardItem(ModBlocks.TRANS_KEYBOARD.get(),
                            new Item.Properties().stacksTo(1)));
}
