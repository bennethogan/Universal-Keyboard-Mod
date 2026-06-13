package dev.bennethogan.universalkeyboard.block;

import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.universalkeyboard.blockentity.ModBlockEntities;
import dev.bennethogan.universalkeyboard.compat.KeyboardMode;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
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
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LinkedKeyboardBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;

    private static final VoxelShape SHAPE_FLOOR_NS   = Block.box( 0,  0,  2, 16,  2, 14);
    private static final VoxelShape SHAPE_FLOOR_EW   = Block.box( 2,  0,  0, 14,  2, 16);
    private static final VoxelShape SHAPE_CEIL_NS    = Block.box( 0, 14,  2, 16, 16, 14);
    private static final VoxelShape SHAPE_CEIL_EW    = Block.box( 2, 14,  0, 14, 16, 16);
    private static final VoxelShape SHAPE_WALL_N     = Block.box( 0,  5, 14, 16, 10, 16);
    private static final VoxelShape SHAPE_WALL_S     = Block.box( 0,  5,  0, 16, 10,  2);
    private static final VoxelShape SHAPE_WALL_E     = Block.box( 0,  5,  0,  2, 10, 16);
    private static final VoxelShape SHAPE_WALL_W     = Block.box(14,  5,  0, 16, 10, 16);

    public LinkedKeyboardBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FACE, AttachFace.FLOOR));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(LinkedKeyboardBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        AttachFace face;
        Direction facing;
        if (clicked == Direction.UP) {
            face = AttachFace.FLOOR;
            facing = context.getHorizontalDirection();
        } else if (clicked == Direction.DOWN) {
            face = AttachFace.CEILING;
            facing = context.getHorizontalDirection();
        } else {
            face = AttachFace.WALL;
            facing = clicked;
        }
        return this.defaultBlockState().setValue(FACE, face).setValue(FACING, facing);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        boolean ns = (facing == Direction.NORTH || facing == Direction.SOUTH);
        return switch (state.getValue(FACE)) {
            case FLOOR   -> ns ? SHAPE_FLOOR_NS : SHAPE_FLOOR_EW;
            case CEILING -> ns ? SHAPE_CEIL_NS  : SHAPE_CEIL_EW;
            case WALL    -> switch (facing) {
                case NORTH -> SHAPE_WALL_N;
                case SOUTH -> SHAPE_WALL_S;
                case EAST  -> SHAPE_WALL_E;
                default    -> SHAPE_WALL_W;
            };
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.LINKED_KEYBOARD.get().create(pos, state);
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

    // right-click (empty hand): open the mode selection screen, or jump to a favorited screen.
    // Shift+right-click always opens the main mode selection menu.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.CONSUME;
        if (!(level.getBlockEntity(pos) instanceof LinkedKeyboardBlockEntity be))
            return InteractionResult.CONSUME;

        // If the keyboard moved since it was last used, ask the player why before opening the menu
        if (be.needsPositionCheck()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                    new dev.bennethogan.universalkeyboard.network.ModPackets.ShowPositionMovePacket(pos));
            return InteractionResult.CONSUME;
        }

        // Shift+click always opens the main mode-selection menu
        if (!player.isShiftKeyDown() && be.getFavoriteScreen() != dev.bennethogan.universalkeyboard.livecontrol.FavoriteScreen.NONE) {
            ModPackets.openFavoriteForPlayer(sp, pos, be);
            return InteractionResult.CONSUME;
        }

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

        // Sequencer is always available regardless of what's linked
        int bits = KeyboardMode.availableBitfield(level, targetPos)
                | (1 << KeyboardMode.PERIPHERAL_SEQUENCER.ordinal());

        String typeName = level.getBlockState(targetPos).getBlock()
                .getName().getString();
        ModPackets.sendOpenModeSelection(sp, pos, typeName, bits);

        return InteractionResult.CONSUME;
    }

    // Right-click with a dye to change the keyboard's skin while preserving all data.
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem dyeItem))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        Block target = dyeTarget(dyeItem.getDyeColor());
        if (target == null || target == state.getBlock())
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        if (level.isClientSide) return ItemInteractionResult.SUCCESS;

        // Save block entity data before the block swap destroys it.
        CompoundTag saved = null;
        if (level.getBlockEntity(pos) instanceof LinkedKeyboardBlockEntity be) {
            saved = be.exportData(level.registryAccess());
        }

        level.setBlock(pos, target.defaultBlockState()
                .setValue(FACING, state.getValue(FACING))
                .setValue(FACE, state.getValue(FACE)),
                Block.UPDATE_ALL);

        // Restore into the freshly-created block entity.
        if (saved != null && level.getBlockEntity(pos) instanceof LinkedKeyboardBlockEntity newBe) {
            newBe.importData(saved, level.registryAccess());
            newBe.setChanged();
        }

        if (!player.isCreative()) stack.shrink(1);
        return ItemInteractionResult.SUCCESS;
    }

    @Nullable
    private static Block dyeTarget(DyeColor color) {
        return switch (color) {
            case WHITE, BLACK -> ModBlocks.LINKED_KEYBOARD.get();
            case PINK   -> ModBlocks.TRANS_KEYBOARD.get();
            case RED    -> ModBlocks.RAINBOW_KEYBOARD.get();
            case PURPLE -> ModBlocks.ACE_KEYBOARD.get();
            case BLUE   -> ModBlocks.BI_KEYBOARD.get();
            default     -> null;
        };
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

    // keyboards aren't lost to accidental creative breaks (copied Create typewriter).
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

    // Survival player breaks + non-player destruction
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
