package dev.bennethogan.universalkeyboard.block;

import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import com.simibubi.create.content.decoration.copycat.CopycatPanelBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntityTicker;
import dev.bennethogan.universalkeyboard.blockentity.ModBlockEntities;
import dev.bennethogan.universalkeyboard.blockentity.WirelessCopycatBlockEntity;
import dev.bennethogan.universalkeyboard.network.ModPackets;
import dev.bennethogan.universalkeyboard.wireless.rs.WirelessRSNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class WirelessCopycatPanelBlock extends CopycatPanelBlock {

    public WirelessCopycatPanelBlock(Properties props) {
        super(props);
    }

    @Override
    public Class<CopycatBlockEntity> getBlockEntityClass() {
        return CopycatBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CopycatBlockEntity> getBlockEntityType() {
        return ModBlockEntities.WIRELESS_COPYCAT.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WirelessCopycatBlockEntity(ModBlockEntities.WIRELESS_COPYCAT.get(), pos, state);
    }

    // CopycatBlock returns a null ticker; this re-enables a server-side ticker so the
    // block entity can poll Sable ship mass in lazyTick
    @Override
    public <S extends BlockEntity> BlockEntityTicker<S> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<S> type) {
        return level.isClientSide ? null : new SmartBlockEntityTicker<>();
    }

    @Override
    public boolean isSignalSource(BlockState state) { return true; }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (level.getBlockEntity(pos) instanceof WirelessCopycatBlockEntity cb)
            return cb.getPower(direction.getOpposite());
        return 0;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                WirelessCopycatBlockEntity be = (WirelessCopycatBlockEntity) level.getBlockEntity(pos);
                if (be != null) ModPackets.sendOpenWirelessCopycatScreen(sp, pos, be.getFreqs(), be.getEnabled());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block fromBlock, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, fromBlock, fromPos, isMoving);
        if (level.isClientSide) return;
        if (!(level.getBlockEntity(pos) instanceof WirelessCopycatBlockEntity cb)) return;
        for (Direction dir : Direction.values()) {
            if (!fromPos.equals(pos.relative(dir))) continue;
            if (!cb.isFaceEnabled(dir.ordinal())) continue;
            String freq = cb.getFaceFreq(dir.ordinal());
            if (freq == null || freq.isEmpty()) continue;
            int power = level.getSignal(fromPos, dir.getOpposite());
            WirelessRSNetwork.setInputPower(level, freq, power);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof WirelessCopycatBlockEntity cb)
            cb.checkPreviewExpiry(level);
    }
}
