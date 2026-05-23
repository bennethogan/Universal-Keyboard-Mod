package dev.bennethogan.universalkeyboard.block;

import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(UniversalKeyboardMod.MOD_ID);

    public static final DeferredBlock<LinkedKeyboardBlock> LINKED_KEYBOARD =
            BLOCKS.register("universal_keyboard", () -> new LinkedKeyboardBlock(
                    BlockBehaviour.Properties.of()
                            .strength(1.0f)
                            .sound(SoundType.STONE)
                            .noOcclusion()
            ));

    public static final DeferredBlock<LinkedKeyboardBlock> TRANS_KEYBOARD =
            BLOCKS.register("trans_keyboard", () -> new LinkedKeyboardBlock(
                    BlockBehaviour.Properties.of()
                            .strength(1.0f)
                            .sound(SoundType.STONE)
                            .noOcclusion()
            ));

    public static final DeferredBlock<LinkedKeyboardBlock> RAINBOW_KEYBOARD =
            BLOCKS.register("rainbow_keyboard", () -> new LinkedKeyboardBlock(
                    BlockBehaviour.Properties.of()
                            .strength(1.0f)
                            .sound(SoundType.STONE)
                            .noOcclusion()
            ));

    public static final DeferredBlock<LinkedKeyboardBlock> ACE_KEYBOARD =
            BLOCKS.register("ace_keyboard", () -> new LinkedKeyboardBlock(
                    BlockBehaviour.Properties.of()
                            .strength(1.0f)
                            .sound(SoundType.STONE)
                            .noOcclusion()
            ));

    public static final DeferredBlock<LinkedKeyboardBlock> BI_KEYBOARD =
            BLOCKS.register("bi_keyboard", () -> new LinkedKeyboardBlock(
                    BlockBehaviour.Properties.of()
                            .strength(1.0f)
                            .sound(SoundType.STONE)
                            .noOcclusion()
            ));
}
