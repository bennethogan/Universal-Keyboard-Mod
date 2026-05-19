package dev.bennethogan.universalkeyboard.block;

import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.universalkeyboard.blockentity.ModBlockEntities;
import dev.bennethogan.universalkeyboard.compat.KeyboardMode;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
    public boolean isSignalSource(BlockState state) { return true; }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        if (level.getBlockEntity(pos) instanceof LinkedKeyboardBlockEntity be)
            return be.getRedstoneOutput(dir.getOpposite());
        return 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return getSignal(state, level, pos, dir);
    }

    // Sequencer TYPE_TEXT steps replace that functionality.

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
            // No linked device on this channel — show mode selection with only Sequencer available
            int bits = 1 << KeyboardMode.PERIPHERAL_SEQUENCER.ordinal();
            ModPackets.sendOpenModeSelection(sp, pos, "", bits);
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

    // keyboards aren't lost to accidental creative breaks (matches Create typewriter).
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()
                && level.getBlockEntity(pos) instanceof LinkedKeyboardBlockEntity lkbe
                && lkbe.hasData()) {
            ItemStack stack = new ItemStack(this.asItem());
            lkbe.saveToItem(stack, level.registryAccess());
            net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // Survival player breaks + non-player destruction (explosions, etc.).
    // Read the block entity directly from the Builder — calling builder.create()
    // first can silently fail and produce no drops.
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof LinkedKeyboardBlockEntity lkbe) {
            ItemStack stack = new ItemStack(this.asItem());
            lkbe.saveToItem(stack, builder.getLevel().registryAccess());
            return List.of(stack);
        }
        return super.getDrops(state, builder);
    }
}
