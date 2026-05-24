package dev.bennethogan.universalkeyboard.compat;

import com.simibubi.create.api.schematic.nbt.PartialSafeNBT;
import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

public class CreateKeyboardBlockEntity extends LinkedKeyboardBlockEntity implements PartialSafeNBT {

    public CreateKeyboardBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
    }
}
