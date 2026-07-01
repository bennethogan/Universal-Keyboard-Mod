package dev.bennethogan.universalkeyboard.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.bennethogan.universalkeyboard.config.ModConfig;
import net.neoforged.neoforge.common.conditions.ICondition;

public record MakeRecipeHarderCondition(boolean expected) implements ICondition {

    public static final MapCodec<MakeRecipeHarderCondition> CODEC = RecordCodecBuilder.mapCodec(inst ->
            inst.group(
                    Codec.BOOL.optionalFieldOf("expected", Boolean.TRUE).forGetter(MakeRecipeHarderCondition::expected)
            ).apply(inst, MakeRecipeHarderCondition::new));

    @Override
    public boolean test(IContext context) {
        boolean harder;
        try {
            harder = ModConfig.COMMON.makeRecipeHarder.get();
        } catch (Exception e) {
            harder = false;
        }
        return harder == expected;
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
