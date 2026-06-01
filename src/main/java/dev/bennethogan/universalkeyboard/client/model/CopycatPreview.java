package dev.bennethogan.universalkeyboard.client.model;

import com.simibubi.create.content.decoration.copycat.CopycatModel;
import dev.bennethogan.universalkeyboard.blockentity.WirelessCopycatBlockEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;


 // Shared logic for the test button that highlights the given face with gold block texture.

public final class CopycatPreview {

    public static final BlockState GOLD = Blocks.GOLD_BLOCK.defaultBlockState();

    private CopycatPreview() {}


    public static boolean active(ModelData data) {
        return data.get(WirelessCopycatBlockEntity.PREVIEW_FACE_PROPERTY) != null;
    }


    public static boolean isHighlightedFace(ModelData data, Direction side) {
        Integer face = data.get(WirelessCopycatBlockEntity.PREVIEW_FACE_PROPERTY);
        if (face == null) return false;
        return face == WirelessCopycatBlockEntity.LOCATE_ALL
                || (side != null && side.get3DDataValue() == face);
    }


    public static ModelData goldData() {
        return ModelData.builder().with(CopycatModel.MATERIAL_PROPERTY, GOLD).build();
    }


    public static ChunkRenderTypeSet withPreview(ChunkRenderTypeSet base, ModelData data) {
        if (!active(data)) return base;
        return ChunkRenderTypeSet.union(base, ChunkRenderTypeSet.of(RenderType.solid()));
    }


    public static void propagate(ModelData.Builder builder, ModelData blockEntityData) {
        Integer face = blockEntityData.get(WirelessCopycatBlockEntity.PREVIEW_FACE_PROPERTY);
        if (face != null) builder.with(WirelessCopycatBlockEntity.PREVIEW_FACE_PROPERTY, face);
    }
}
