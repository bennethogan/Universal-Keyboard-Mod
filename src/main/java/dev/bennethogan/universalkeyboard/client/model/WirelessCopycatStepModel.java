package dev.bennethogan.universalkeyboard.client.model;

import com.simibubi.create.content.decoration.copycat.CopycatModel;
import com.simibubi.create.content.decoration.copycat.CopycatStepModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.List;

public class WirelessCopycatStepModel extends CopycatStepModel {

    public WirelessCopycatStepModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    protected ModelData.Builder gatherModelData(ModelData.Builder builder, BlockAndTintGetter world,
            BlockPos pos, BlockState state, ModelData blockEntityData) {
        builder = super.gatherModelData(builder, world, pos, state, blockEntityData);
        CopycatPreview.propagate(builder, blockEntityData);
        return builder;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        BlockState material = CopycatModel.getMaterial(data);
        ChunkRenderTypeSet base = getModelOf(material).getRenderTypes(material, rand, ModelData.EMPTY);
        return CopycatPreview.withPreview(base, data);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand,
                                    ModelData data, RenderType renderType) {
        if (CopycatPreview.active(data)) {
            // Render as full cube during the gold-face test — couldnt figure it out otherwise
            if (CopycatPreview.isHighlightedFace(data, side)) {
                if (RenderType.solid().equals(renderType))
                    return getModelOf(CopycatPreview.GOLD).getQuads(CopycatPreview.GOLD, side, rand, ModelData.EMPTY, renderType);
                return List.of();
            }
            BlockState material = CopycatModel.getMaterial(data);
            return getModelOf(material).getQuads(material, side, rand, ModelData.EMPTY, renderType);
        }
        return super.getQuads(state, side, rand, data, renderType);
    }
}
