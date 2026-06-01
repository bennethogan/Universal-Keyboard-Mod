package dev.bennethogan.universalkeyboard.client.model;

import com.simibubi.create.content.decoration.copycat.CopycatModel;
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

public class WirelessCopycatBaseModel extends CopycatModel {

    public WirelessCopycatBaseModel(BakedModel originalModel) {
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
        if (CopycatPreview.active(data) && CopycatPreview.isHighlightedFace(data, side)) {
            if (RenderType.solid().equals(renderType))
                return super.getQuads(state, side, rand, CopycatPreview.goldData(), renderType);
            return List.of();
        }
        return super.getQuads(state, side, rand, data, renderType);
    }

    @Override
    protected List<BakedQuad> getCroppedQuads(BlockState state, Direction side, RandomSource rand,
                                              BlockState material, ModelData wrappedData, RenderType renderType) {
        BakedModel materialModel = getModelOf(material);
        return materialModel.getQuads(material, side, rand, wrappedData, renderType);
    }
}
