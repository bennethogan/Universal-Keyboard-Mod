package dev.bennethogan.universalkeyboard.recipe;

import com.mojang.serialization.MapCodec;
import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModRecipeConditions {

    private ModRecipeConditions() {}

    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, UniversalKeyboardMod.MOD_ID);

    public static final Supplier<MapCodec<MakeRecipeHarderCondition>> MAKE_RECIPE_HARDER =
            CONDITION_CODECS.register("make_recipe_harder", () -> MakeRecipeHarderCondition.CODEC);

    public static void register(IEventBus modEventBus) {
        CONDITION_CODECS.register(modEventBus);
    }
}
