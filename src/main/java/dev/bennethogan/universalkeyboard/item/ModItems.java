package dev.bennethogan.universalkeyboard.item;

import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import dev.bennethogan.universalkeyboard.block.ModBlocks;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import javax.annotation.Nullable;

public class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(UniversalKeyboardMod.MOD_ID);

    public static final DeferredItem<LinkedKeyboardItem> LINKED_KEYBOARD =
            ITEMS.register("universal_keyboard", () ->
                    new LinkedKeyboardItem(ModBlocks.LINKED_KEYBOARD.get(),
                            new Item.Properties().stacksTo(1).fireResistant()));

    public static final DeferredItem<LinkedKeyboardItem> TRANS_KEYBOARD =
            ITEMS.register("trans_keyboard", () ->
                    new LinkedKeyboardItem(ModBlocks.TRANS_KEYBOARD.get(),
                            new Item.Properties().stacksTo(1).fireResistant()));

    public static final DeferredItem<LinkedKeyboardItem> RAINBOW_KEYBOARD =
            ITEMS.register("rainbow_keyboard", () ->
                    new LinkedKeyboardItem(ModBlocks.RAINBOW_KEYBOARD.get(),
                            new Item.Properties().stacksTo(1).fireResistant()));

    public static final DeferredItem<LinkedKeyboardItem> ACE_KEYBOARD =
            ITEMS.register("ace_keyboard", () ->
                    new LinkedKeyboardItem(ModBlocks.ACE_KEYBOARD.get(),
                            new Item.Properties().stacksTo(1).fireResistant()));

    public static final DeferredItem<LinkedKeyboardItem> BI_KEYBOARD =
            ITEMS.register("bi_keyboard", () ->
                    new LinkedKeyboardItem(ModBlocks.BI_KEYBOARD.get(),
                            new Item.Properties().stacksTo(1).fireResistant()));

    public static final DeferredItem<LinkedKeyboardItem> LINKED_CONTROLWHEEL =
            ITEMS.register("universal_controlwheel", () ->
                    new LinkedKeyboardItem(ModBlocks.LINKED_CONTROLWHEEL.get(),
                            new Item.Properties().stacksTo(1).fireResistant()));

    public static final DeferredItem<LinkedKeyboardItem> DASHBOARD =
            ITEMS.register("dashboard", () ->
                    new LinkedKeyboardItem(ModBlocks.DASHBOARD.get(),
                            new Item.Properties().stacksTo(1).fireResistant()));

    public static final DeferredItem<LinkingStickItem> LINKING_STICK =
            ITEMS.register("linking_stick", () ->
                    new LinkingStickItem(new Item.Properties().stacksTo(1)));

    @Nullable public static DeferredItem<net.minecraft.world.item.BlockItem> WIRELESS_COPYCAT;
    @Nullable public static DeferredItem<net.minecraft.world.item.BlockItem> WIRELESS_COPYCAT_PANEL;
    @Nullable public static DeferredItem<net.minecraft.world.item.BlockItem> WIRELESS_COPYCAT_STEP;

    public static void registerForCreate() {
        WIRELESS_COPYCAT = ITEMS.register("wireless_copycat", () ->
                new net.minecraft.world.item.BlockItem(ModBlocks.WIRELESS_COPYCAT.get(),
                        new Item.Properties().stacksTo(64)));
        WIRELESS_COPYCAT_PANEL = ITEMS.register("wireless_copycat_panel", () ->
                new net.minecraft.world.item.BlockItem(ModBlocks.WIRELESS_COPYCAT_PANEL.get(),
                        new Item.Properties().stacksTo(64)));
        WIRELESS_COPYCAT_STEP = ITEMS.register("wireless_copycat_step", () ->
                new net.minecraft.world.item.BlockItem(ModBlocks.WIRELESS_COPYCAT_STEP.get(),
                        new Item.Properties().stacksTo(64)));
    }
}
