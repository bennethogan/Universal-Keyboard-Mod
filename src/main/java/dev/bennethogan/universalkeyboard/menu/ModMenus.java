package dev.bennethogan.universalkeyboard.menu;

import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, UniversalKeyboardMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<RedstoneLinksMenu>> REDSTONE_LINKS_MENU =
            MENUS.register("wireless_config",
                    () -> IMenuTypeExtension.create(RedstoneLinksMenu::fromNetwork));
}
