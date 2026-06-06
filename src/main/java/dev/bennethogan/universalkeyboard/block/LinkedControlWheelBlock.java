package dev.bennethogan.universalkeyboard.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class LinkedControlWheelBlock extends LinkedKeyboardBlock {

    public static final MapCodec<LinkedControlWheelBlock> CODEC =
            simpleCodec(LinkedControlWheelBlock::new);

    public LinkedControlWheelBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends LinkedControlWheelBlock> codec() {
        return CODEC;
    }
}
