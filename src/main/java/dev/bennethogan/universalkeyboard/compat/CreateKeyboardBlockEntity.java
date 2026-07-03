package dev.bennethogan.universalkeyboard.compat;

import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.api.schematic.nbt.PartialSafeNBT;
import com.simibubi.create.content.contraptions.StructureTransform;
import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CreateKeyboardBlockEntity extends LinkedKeyboardBlockEntity
        implements TransformableBlockEntity, PartialSafeNBT {

    public CreateKeyboardBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public void transform(BlockEntity be, StructureTransform transform) {
        applyStructureRotation(transform.rotation, transform.rotationAxis);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
    }
}
