package dev.bennethogan.universalkeyboard.block;

import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import javax.annotation.Nullable;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(UniversalKeyboardMod.MOD_ID);

    public static final DeferredBlock<LinkedKeyboardBlock> LINKED_KEYBOARD =
            BLOCKS.register("universal_keyboard", () -> new LinkedKeyboardBlock(
                    BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.STONE).noOcclusion()
            ));

    public static final DeferredBlock<LinkedKeyboardBlock> TRANS_KEYBOARD =
            BLOCKS.register("trans_keyboard", () -> new LinkedKeyboardBlock(
                    BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.STONE).noOcclusion()
            ));

    public static final DeferredBlock<LinkedKeyboardBlock> RAINBOW_KEYBOARD =
            BLOCKS.register("rainbow_keyboard", () -> new LinkedKeyboardBlock(
                    BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.STONE).noOcclusion()
            ));

    public static final DeferredBlock<LinkedKeyboardBlock> ACE_KEYBOARD =
            BLOCKS.register("ace_keyboard", () -> new LinkedKeyboardBlock(
                    BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.STONE).noOcclusion()
            ));

    public static final DeferredBlock<LinkedKeyboardBlock> BI_KEYBOARD =
            BLOCKS.register("bi_keyboard", () -> new LinkedKeyboardBlock(
                    BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.STONE).noOcclusion()
            ));

    @Nullable public static DeferredBlock<WirelessCopycatBlock> WIRELESS_COPYCAT;
    @Nullable public static DeferredBlock<WirelessCopycatPanelBlock> WIRELESS_COPYCAT_PANEL;
    @Nullable public static DeferredBlock<WirelessCopycatStepBlock> WIRELESS_COPYCAT_STEP;

    public static void registerForCreate() {
        BlockBehaviour.Properties props = BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.STONE).noOcclusion();
        WIRELESS_COPYCAT       = BLOCKS.register("wireless_copycat",       () -> new WirelessCopycatBlock(props));
        WIRELESS_COPYCAT_PANEL = BLOCKS.register("wireless_copycat_panel", () -> new WirelessCopycatPanelBlock(props));
        WIRELESS_COPYCAT_STEP  = BLOCKS.register("wireless_copycat_step",  () -> new WirelessCopycatStepBlock(props));
    }
}
