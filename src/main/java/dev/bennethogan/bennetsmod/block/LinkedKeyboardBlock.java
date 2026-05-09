package dev.bennethogan.bennetsmod.block;

import dev.bennethogan.bennetsmod.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.bennetsmod.blockentity.ModBlockEntities;
import dev.bennethogan.bennetsmod.compat.KeyboardMode;
import dev.bennethogan.bennetsmod.network.ModPackets;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class LinkedKeyboardBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE_NS = Block.box(0, 0, 2, 16, 2, 14);
    private static final VoxelShape SHAPE_EW = Block.box(2, 0, 0, 14, 2, 16);

    public LinkedKeyboardBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(LinkedKeyboardBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return (facing == Direction.NORTH || facing == Direction.SOUTH) ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LinkedKeyboardBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.LINKED_KEYBOARD.get(),
                LinkedKeyboardBlockEntity::serverTick);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (level.getBlockEntity(pos) instanceof LinkedKeyboardBlockEntity be)
            be.onRedstoneChanged(powered);
    }

    @Override
    public boolean isSignalSource(BlockState state) { return false; }

    // shift+right-click: open autotype script editor
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            if (!(player instanceof ServerPlayer sp)) return ItemInteractionResult.CONSUME;
            if (level.getBlockEntity(pos) instanceof LinkedKeyboardBlockEntity be)
                ModPackets.sendOpenAutoTypeScreen(sp, pos, be.getAutoTypeScript());
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // right-click (empty hand): open the mode selection screen for the linked target
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.CONSUME;
        if (!(level.getBlockEntity(pos) instanceof LinkedKeyboardBlockEntity be))
            return InteractionResult.CONSUME;

        BlockPos targetPos = be.getLinkedTargetPos();
        if (targetPos == null) {
            player.displayClientMessage(Component.literal(
                    "§c[Universal Keyboard] §fNot linked. Hold the keyboard item and right-click " +
                    "the block you want to control first."), true);
            return InteractionResult.CONSUME;
        }
        if (!be.isTargetInRange()) {
            player.displayClientMessage(Component.literal(
                    "§c[Universal Keyboard] §fLinked target is out of range."), true);
            return InteractionResult.CONSUME;
        }

        int bits = KeyboardMode.availableBitfield(level, targetPos);
        if (bits == 0) {
            player.displayClientMessage(Component.literal(
                    "§c[Universal Keyboard] §fLinked block has no compatible modes (might have changed)."), true);
            return InteractionResult.CONSUME;
        }

        String typeName = level.getBlockState(targetPos).getBlock()
                .getName().getString();
        ModPackets.sendOpenModeSelection(sp, pos, typeName, bits);

        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()) {
            if (level.getBlockEntity(pos) instanceof LinkedKeyboardBlockEntity be)
                be.onRemoved();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
