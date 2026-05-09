package dev.bennethogan.bennetsmod.item;

import dev.bennethogan.bennetsmod.UniversalKeyboardMod;
import dev.bennethogan.bennetsmod.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.bennetsmod.compat.KeyboardMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LinkedKeyboardItem extends BlockItem {

    public LinkedKeyboardItem(net.minecraft.world.level.block.Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level    level  = context.getLevel();
        BlockPos pos    = context.getClickedPos();
        Player   player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (player == null) return InteractionResult.PASS;

        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            // Don't try to link to another keyboard
            if (!(be instanceof LinkedKeyboardBlockEntity)) {
                List<KeyboardMode> modes = KeyboardMode.available(level, pos);
                if (!modes.isEmpty()) {
                    List<BlockPos> existing = getLinkedTargets(stack);

                    // Reject duplicate positions
                    if (existing.contains(pos)) {
                        player.displayClientMessage(Component.literal(
                                "§e[Universal Keyboard] §fThat block is already in the mesh."), true);
                        return InteractionResult.SUCCESS;
                    }

                    // Enforce same BlockEntityType for mesh members beyond the first
                    if (!existing.isEmpty() && be != null) {
                        BlockEntity firstBe = level.getBlockEntity(existing.get(0));
                        if (firstBe == null || !firstBe.getType().equals(be.getType())) {
                            player.displayClientMessage(Component.literal(
                                    "§c[Universal Keyboard] §fMesh can only link blocks of the same type."), true);
                            return InteractionResult.SUCCESS;
                        }
                    }

                    // Add to the item's mesh list
                    addLinkedTarget(stack, pos);
                    List<BlockPos> all = getLinkedTargets(stack);
                    String modeList = modes.stream().map(m -> m.displayName).collect(Collectors.joining(", "));

                    String msg;
                    if (all.size() == 1) {
                        msg = "§a[Universal Keyboard] §fMesh linked to "
                                + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                                + " §7(" + modeList + ")§f. Add more of the same type or place the keyboard!";
                    } else {
                        msg = "§a[Universal Keyboard] §fMesh now has §e" + all.size()
                                + " §fblocks linked §7(" + modeList + ")§f. Place when ready!";
                    }
                    player.displayClientMessage(Component.literal(msg), true);
                    return InteractionResult.SUCCESS;
                }
            }
        }

        return super.useOn(context);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player,
                                                  ItemStack stack, BlockState state) {
        boolean result = super.updateCustomBlockEntityTag(pos, level, player, stack, state);

        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof LinkedKeyboardBlockEntity keyboard) {
            List<BlockPos> targets = getLinkedTargets(stack);
            if (!targets.isEmpty()) {
                keyboard.setLinkedTargets(targets);
                clearLinkedTargets(stack);
                UniversalKeyboardMod.LOGGER.info("transferred {} mesh link(s) to placed keyboard at {}",
                        targets.size(), pos);
            }
        }
        return result;
    }

    // ----- NBT helpers -----

    private static void clearLinkedTargets(ItemStack stack) {
        CompoundTag tag = readTag(stack);
        if (tag == null) return;
        tag.remove("mesh_targets");
        tag.remove("target_x");
        tag.remove("target_y");
        tag.remove("target_z");
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            writeTag(stack, tag);
        }
    }

    private static void addLinkedTarget(ItemStack stack, BlockPos pos) {
        CompoundTag tag = getOrCreateTag(stack);
        ListTag list = tag.contains("mesh_targets", Tag.TAG_LIST)
                ? tag.getList("mesh_targets", Tag.TAG_COMPOUND)
                : new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putInt("x", pos.getX());
        entry.putInt("y", pos.getY());
        entry.putInt("z", pos.getZ());
        list.add(entry);
        tag.put("mesh_targets", list);
        writeTag(stack, tag);
    }

    public static List<BlockPos> getLinkedTargets(ItemStack stack) {
        CompoundTag tag = readTag(stack);
        if (tag == null) return List.of();
        List<BlockPos> result = new ArrayList<>();
        if (tag.contains("mesh_targets", Tag.TAG_LIST)) {
            ListTag list = tag.getList("mesh_targets", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                result.add(new BlockPos(e.getInt("x"), e.getInt("y"), e.getInt("z")));
            }
        } else if (tag.contains("target_x")) {
            // legacy single-target format
            result.add(new BlockPos(tag.getInt("target_x"), tag.getInt("target_y"), tag.getInt("target_z")));
        }
        return result;
    }

    private static CompoundTag getOrCreateTag(ItemStack stack) {
        var existing = stack.get(DataComponents.CUSTOM_DATA);
        return existing != null ? existing.copyTag() : new CompoundTag();
    }

    private static CompoundTag readTag(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : null;
    }

    private static void writeTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
