package dev.bennethogan.universalkeyboard.item;

import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.universalkeyboard.compat.KeyboardMode;
import dev.bennethogan.universalkeyboard.compat.PanelControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;


// Linking tool, just a stick for now. Right click a controller, shift+scroll to choose channels
// right click blocks to link/unlink them
// Uses a plain vanilla stick texture. Give a controller a stick to get a linking stick in return
public class LinkingStickItem extends Item {

    public LinkingStickItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                if (hasTarget(stack)) {
                    clearTarget(stack);
                    player.displayClientMessage(Component.literal(
                            "§e[Linking Stick] §fCleared target controller."), true);
                } else {
                    player.displayClientMessage(Component.literal(
                            "§7[Linking Stick] §fRight-click a controller to start linking."), true);
                }
            } else {
                BlockPos t = getTarget(stack);
                if (t == null) {
                    player.displayClientMessage(Component.literal(
                            "§7[Linking Stick] §fRight-click a controller to start linking."), true);
                } else {
                    player.displayClientMessage(Component.literal(
                            "§a[Linking Stick] §fController §e" + posStr(t) + "§f, Channel §e"
                            + getActiveChannel(stack) + "§f. Shift+right-click air to clear."), true);
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level     level  = context.getLevel();
        BlockPos  pos    = context.getClickedPos();
        Player    player = context.getPlayer();
        ItemStack stack  = context.getItemInHand();
        if (player == null) return InteractionResult.PASS;

        if (level.isClientSide) return InteractionResult.SUCCESS;

        String dim = level.dimension().location().toString();
        BlockEntity be = level.getBlockEntity(pos);

        // clicked a controller
        if (be instanceof LinkedKeyboardBlockEntity) {
            setTarget(stack, pos, dim);
            player.displayClientMessage(Component.literal(
                    "§a[Linking Stick] §fEditing controller at §e" + posStr(pos)
                    + "§f. Channel §e" + getActiveChannel(stack)
                    + "§f. Right-click targets to link; shift+scroll to change channel."), true);
            return InteractionResult.SUCCESS;
        }
        // clicked anything but
        BlockPos kbPos = getTarget(stack);
        if (kbPos == null) {
            player.displayClientMessage(Component.literal(
                    "§7[Linking Stick] §fRight-click a controller first"), true);
            return InteractionResult.SUCCESS;
        }
        if (!dim.equals(getTargetDim(stack))) {
            player.displayClientMessage(Component.literal(
                    "§c[Linking Stick] §fThat controller is in another dimension"), true);
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(kbPos) instanceof LinkedKeyboardBlockEntity kb)) {
            player.displayClientMessage(Component.literal(
                    "§c[Linking Stick] §fController not found (unloaded or removed)"), true);
            return InteractionResult.SUCCESS;
        }
        if (!kb.isUsableBy(player)) {
            player.displayClientMessage(Component.literal(
                    "§c[Linking Stick] §fThat controller is locked"), true);
            return InteractionResult.SUCCESS;
        }
        if (KeyboardMode.available(level, pos).isEmpty()) {
            player.displayClientMessage(Component.literal(
                    "§7[Linking Stick] §fThat block can't be linked"), true);
            return InteractionResult.SUCCESS;
        }

        toggleLink(level, kb, pos, player, be, getActiveChannel(stack));
        return InteractionResult.SUCCESS;
    }


    private void toggleLink(Level level, LinkedKeyboardBlockEntity kb, BlockPos pos, Player player,
                            BlockEntity targetBe, int ch) {
        // dashpanels per-module linking attempts
        if (PanelControl.isPanel(level, pos)) {
            String mod = PanelControl.getSelectedModule(level, pos, player);
            if (!mod.isEmpty()) {
                togglePanelModule(level, kb, pos, player, targetBe, ch, mod);
                return;
            }
        }

        List<BlockPos> chList = kb.getLinkedTargetPositions(ch);
        if (chList.contains(pos)) {
            kb.unlinkTarget(ch, pos);
            player.displayClientMessage(Component.literal(
                    "§e[Linking Stick] §fUnlinked from Channel §e" + ch), true);
            return;
        }
        if (!sameTypeOk(level, kb, ch, targetBe, player)) return;
        kb.addChannelTarget(ch, pos);
        player.displayClientMessage(Component.literal(
                "§a[Linking Stick] §fLinked to Channel §e" + ch
                + "§f §7(" + kb.getLinkedTargetPositions(ch).size() + " on channel)"), true);
    }

    private void togglePanelModule(Level level, LinkedKeyboardBlockEntity kb, BlockPos pos, Player player,
                                   BlockEntity targetBe, int ch, String moduleName) {
        boolean already = kb.getChannelModules(ch).contains(moduleName);
        if (!already && !PanelControl.isModuleDrivable(level, pos, moduleName)) {
            player.displayClientMessage(Component.literal(
                    "§c[Linking Stick] §fModule §b" + moduleName + "§f can't be operated by the keyboard"), true);
            return;
        }
        if (already) {
            kb.removeChannelModule(ch, moduleName);
            if (kb.getChannelModules(ch).isEmpty()) kb.unlinkTarget(ch, pos);
            player.displayClientMessage(Component.literal(
                    "§e[Linking Stick] §fUnlinked module §b" + moduleName + "§f from Channel §e" + ch), true);
            return;
        }
        if (!kb.getLinkedTargetPositions(ch).contains(pos)) {
            if (!sameTypeOk(level, kb, ch, targetBe, player)) return;
            kb.addChannelTarget(ch, pos);
        }
        kb.addChannelModule(ch, moduleName);
        player.displayClientMessage(Component.literal(
                "§a[Linking Stick] §fCh §e" + ch + "§f - module §b" + moduleName
                + "§f linked §7(" + kb.getChannelModules(ch).size() + " module(s))"), true);
    }


    // reject mismatched block-entities on the same channel
    private boolean sameTypeOk(Level level, LinkedKeyboardBlockEntity kb, int ch, BlockEntity targetBe, Player player) {
        List<BlockPos> chList = kb.getLinkedTargetPositions(ch);
        if (chList.isEmpty() || targetBe == null) return true;
        BlockEntity first = level.getBlockEntity(chList.get(0));
        if (first == null || !first.getType().equals(targetBe.getType())) {
            player.displayClientMessage(Component.literal(
                    "§c[Linking Stick] §fChannel " + ch + " can only hold blocks of the same type."), true);
            return false;
        }
        return true;
    }

    // ----- Tooltips ---------
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        BlockPos t = getTarget(stack);
        if (t == null) {
            tooltip.add(Component.literal("§7Add/remove peripheral links to your controller."));
        } else {
            tooltip.add(Component.literal("§7Controller: §f" + posStr(t)));
            tooltip.add(Component.literal("§7Channel: §e" + getActiveChannel(stack) + " §8(shift+scroll to change)"));
        }
    }

    // ---------- NBT helpers -------------
    public static boolean hasTarget(ItemStack stack) { return getTarget(stack) != null; }

    public static BlockPos getTarget(ItemStack stack) {
        CompoundTag tag = readTag(stack);
        if (tag == null || !tag.contains("tx")) return null;
        return new BlockPos(tag.getInt("tx"), tag.getInt("ty"), tag.getInt("tz"));
    }

    public static String getTargetDim(ItemStack stack) {
        CompoundTag tag = readTag(stack);
        return tag == null ? "" : tag.getString("tdim");
    }

    public static void setTarget(ItemStack stack, BlockPos pos, String dim) {
        CompoundTag tag = getOrCreateTag(stack);
        tag.putInt("tx", pos.getX());
        tag.putInt("ty", pos.getY());
        tag.putInt("tz", pos.getZ());
        tag.putString("tdim", dim);
        writeTag(stack, tag);
    }

    public static void clearTarget(ItemStack stack) {
        CompoundTag tag = readTag(stack);
        if (tag == null) return;
        tag.remove("tx"); tag.remove("ty"); tag.remove("tz"); tag.remove("tdim");
        if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else writeTag(stack, tag);
    }

    public static int getActiveChannel(ItemStack stack) {
        CompoundTag tag = readTag(stack);
        if (tag == null || !tag.contains("active_channel")) return 1;
        return Math.max(1, Math.min(LinkedKeyboardBlockEntity.MAX_CHANNELS, tag.getInt("active_channel")));
    }

    public static void setActiveChannel(ItemStack stack, int channel) {
        CompoundTag tag = getOrCreateTag(stack);
        tag.putInt("active_channel", Math.max(1, Math.min(LinkedKeyboardBlockEntity.MAX_CHANNELS, channel)));
        writeTag(stack, tag);
    }

    private static String posStr(BlockPos p) { return p.getX() + ", " + p.getY() + ", " + p.getZ(); }

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
