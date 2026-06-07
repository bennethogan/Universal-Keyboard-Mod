package dev.bennethogan.universalkeyboard.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LinkedControlWheelBlock extends LinkedKeyboardBlock {

    public static final MapCodec<LinkedControlWheelBlock> CODEC =
            simpleCodec(LinkedControlWheelBlock::new);

    //wall shapes
    private static final VoxelShape WALL_S = Block.box( 2.169,  8.91,  0.768, 13.861, 15.147,  2.253);
    private static final VoxelShape WALL_N = Block.box( 2.139,  8.91, 13.747, 13.831, 15.147, 15.232);
    private static final VoxelShape WALL_E = Block.box( 0.768,  8.91,  2.139,  2.253, 15.147, 13.831);
    private static final VoxelShape WALL_W = Block.box(13.747,  8.91,  2.169, 15.232, 15.147, 13.861);

    //floor shapes
    private static final VoxelShape FLOOR_N = Shapes.or(
            Block.box( 2.169,  8.91,  9.568, 13.861, 15.147, 11.053),
            Block.box( 3.060,  0.0,   0.064, 12.970, 13.662,  8.380));
    private static final VoxelShape FLOOR_E = Shapes.or(
            Block.box( 4.947,  8.91,  2.169,  6.432, 15.147, 13.861),
            Block.box( 7.620,  0.0,   3.060, 15.936, 13.662, 12.970));
    private static final VoxelShape FLOOR_S = Shapes.or(
            Block.box( 2.139,  8.91,  4.947, 13.831, 15.147,  6.432),
            Block.box( 3.030,  0.0,   7.620, 12.940, 13.662, 15.936));
    private static final VoxelShape FLOOR_W = Shapes.or(
            Block.box( 9.568,  8.91,  2.139, 11.053, 15.147, 13.831),
            Block.box( 0.064,  0.0,   3.030,  8.380, 13.662, 12.940));

    public LinkedControlWheelBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends LinkedControlWheelBlock> codec() {
        return CODEC;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return switch (state.getValue(FACE)) {
            case WALL -> switch (facing) {
                case NORTH -> WALL_N;
                case EAST  -> WALL_E;
                case WEST  -> WALL_W;
                default    -> WALL_S;
            };
            case FLOOR -> switch (facing) {
                case EAST  -> FLOOR_E;
                case SOUTH -> FLOOR_S;
                case WEST  -> FLOOR_W;
                default    -> FLOOR_N;
            };
            default -> super.getShape(state, level, pos, context);
        };
    }
}
