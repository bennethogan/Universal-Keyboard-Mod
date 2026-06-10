package dev.bennethogan.universalkeyboard.item;

import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.universalkeyboard.blockentity.WirelessCopycatBlockEntity;
import dev.bennethogan.universalkeyboard.compat.CreateValueHelper;
import dev.bennethogan.universalkeyboard.compat.KeyboardMode;
import dev.bennethogan.universalkeyboard.compat.PeripheralHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LinkedKeyboardItem extends BlockItem {

    public LinkedKeyboardItem(net.minecraft.world.level.block.Block block, Properties properties) {
        super(block, properties);
    }

    // ----- Enchantment glow when linked -----

    @Override
    public boolean isFoil(ItemStack stack) {
        return hasAnyLinkedTargets(stack);
    }

    // ----- Shift+right-click in air: toggle linking mode -----

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return super.use(level, player, hand);

        if (!level.isClientSide) {
            boolean nowLinking = !isLinkingMode(stack);
            setLinkingMode(stack, nowLinking);
            if (nowLinking) {
                int ch = getActiveLinkingChannel(stack);
                player.displayClientMessage(Component.literal(
                        "§a[Universal Keyboard] §fLinking mode ON — Channel §e" + ch +
                        "§f. Right-click targets to link/unlink. Scroll to change channel. Shift+right-click to finish."),
                        true);
            } else {
                int count = getAllChannelTargets(stack).values().stream().mapToInt(List::size).sum();
                player.displayClientMessage(Component.literal(
                        "§a[Universal Keyboard] §fLinking done. §e" + count +
                        "§f target(s) across channels. Place the keyboard!"),
                        true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // ----- Right-click on a block -----

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level     level  = context.getLevel();
        BlockPos  pos    = context.getClickedPos();
        Player    player = context.getPlayer();
        ItemStack stack  = context.getItemInHand();

        if (player == null) return InteractionResult.PASS;

        // Special: shift+right-click a WirelessCopycat → import its enabled face freqs onto the item
        if (player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof WirelessCopycatBlockEntity cb) {
            if (!level.isClientSide) importCopycatFreqs(stack, player, cb);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Shift+right-click: toggle linking mode, and try to add/remove clicked block on enter
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                boolean nowLinking = !isLinkingMode(stack);
                setLinkingMode(stack, nowLinking);
                if (nowLinking) {
                    int ch = getActiveLinkingChannel(stack);
                    tryToggleBlock(level, pos, player, stack, ch);
                } else {
                    int count = getAllChannelTargets(stack).values().stream().mapToInt(List::size).sum();
                    player.displayClientMessage(Component.literal(
                            "§a[Universal Keyboard] §fLinking done. §e" + count +
                            "§f target(s) across channels. Place the keyboard!"),
                            true);
                }
            }
            return InteractionResult.SUCCESS;
        }

        // Regular right-click: act only on linkable blocks when in linking mode
        // Non-linkable blocks fall through to super.useOn() for placement
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof LinkedKeyboardBlockEntity)) {
                List<KeyboardMode> modes = KeyboardMode.available(level, pos);
                if (!modes.isEmpty()) {
                    if (isLinkingMode(stack)) {
                        int ch = getActiveLinkingChannel(stack);
                        tryToggleBlock(level, pos, player, stack, ch);
                    } else {
                        player.displayClientMessage(Component.literal(
                                "§7[Universal Keyboard] §fShift+right-click to enter linking mode."),
                                true);
                    }
                    return InteractionResult.SUCCESS;
                }
            }
        }
        // Placement fallback (both sides): lets BlockItem handle placing the keyboard
        return super.useOn(context);
    }

    private void tryToggleBlock(Level level, BlockPos pos, Player player, ItemStack stack, int ch) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof LinkedKeyboardBlockEntity) return;

        List<KeyboardMode> modes = KeyboardMode.available(level, pos);
        if (modes.isEmpty()) {
            player.displayClientMessage(Component.literal(
                    "§7[Universal Keyboard] §fThat block has no compatible modes."), true);
            return;
        }

        List<BlockPos> channelList = getLinkedTargetsForChannel(stack, ch);

        // Already linked — right-click again to unlink
        if (channelList.contains(pos)) {
            removeLinkedTargetFromChannel(stack, ch, pos);
            int remaining = getLinkedTargetsForChannel(stack, ch).size();
            player.displayClientMessage(Component.literal(
                    "§e[Universal Keyboard] §fUnlinked from Channel §e" + ch +
                    "§f. §7(" + remaining + " remaining on this channel)"), true);
            return;
        }

        // Enforce same BlockEntityType and same connection type for all entries on the same channel
        if (!channelList.isEmpty() && be != null) {
            BlockEntity firstBe = level.getBlockEntity(channelList.get(0));
            if (firstBe == null || !firstBe.getType().equals(be.getType())) {
                player.displayClientMessage(Component.literal(
                        "§c[Universal Keyboard] §fChannel " + ch + " can only hold blocks of the same type."), true);
                return;
            }
            boolean newCc = PeripheralHelper.hasPeripheral(level, pos);
            boolean newVp = CreateValueHelper.hasScrollValue(be);
            boolean oldCc = PeripheralHelper.hasPeripheral(level, channelList.get(0));
            boolean oldVp = CreateValueHelper.hasScrollValue(firstBe);
            if (newCc != oldCc || newVp != oldVp) {
                player.displayClientMessage(Component.literal(
                        "§c[Universal Keyboard] §fChannel " + ch + " already has a different connection type (CC/Value Panel mismatch)."), true);
                return;
            }
        }

        addLinkedTargetToChannel(stack, ch, pos);
        int total = getLinkedTargetsForChannel(stack, ch).size();
        String msg = total == 1
                ? "§a[Keyboard] §fCh §e" + ch + "§f linked. Scroll to change channel."
                : "§a[Keyboard] §fCh §e" + ch + "§f — §e" + total + " §fblocks.";
        player.displayClientMessage(Component.literal(msg), true);
    }

    // ----- Transfer to block entity on placement -----

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player,
                                                  ItemStack stack, BlockState state) {
        boolean result = super.updateCustomBlockEntityTag(pos, level, player, stack, state);

        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof LinkedKeyboardBlockEntity keyboard) {
            Map<Integer, List<BlockPos>> allTargets = getAllChannelTargets(stack);
            if (!allTargets.isEmpty()) {
                keyboard.setAllChannelTargets(allTargets);
                clearAllLinkedTargets(stack);
                setLinkingMode(stack, false);
                int total = allTargets.values().stream().mapToInt(List::size).sum();
                UniversalKeyboardMod.LOGGER.info("transferred {} channel(s) with {} total target(s) to placed keyboard at {}",
                        allTargets.size(), total, pos);
            }

            // Transfer any wireless channel freqs collected from WirelessCopycat shift-clicks
            CompoundTag itemTag = readTag(stack);
            if (itemTag != null && itemTag.contains("wifi_freqs", Tag.TAG_LIST)) {
                ListTag wfl = itemTag.getList("wifi_freqs", Tag.TAG_STRING);
                if (!wfl.isEmpty()) {
                    String[] existing = keyboard.getLinkFreqs();
                    LinkedHashSet<String> merged = new LinkedHashSet<>();
                    for (String f : existing) if (f != null && !f.isEmpty()) merged.add(f);
                    for (int i = 0; i < wfl.size(); i++) {
                        String f = wfl.getString(i);
                        if (!f.isEmpty()) merged.add(f);
                    }
                    String[] out = new String[LinkedKeyboardBlockEntity.MAX_WIRELESS_FREQS];
                    int slot = 0;
                    for (String f : merged) { if (slot >= out.length) break; out[slot++] = f; }
                    keyboard.setLinkFreqs(out);
                }
                itemTag.remove("wifi_freqs");
                if (itemTag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
                else writeTag(stack, itemTag);
            }
        }
        return result;
    }

    // ----- Tooltip -----

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        Map<Integer, List<BlockPos>> all = getAllChannelTargets(stack);
        if (all.isEmpty()) {
            tooltip.add(Component.literal("§7Shift+right-click to enter linking mode."));
            return;
        }

        boolean linking = isLinkingMode(stack);
        int activeCh = getActiveLinkingChannel(stack);

        if (linking) {
            tooltip.add(Component.literal("§a⬛ Linking mode active — Channel §e" + activeCh));
        }

        for (int ch = 1; ch <= LinkedKeyboardBlockEntity.MAX_CHANNELS; ch++) {
            List<BlockPos> list = all.get(ch);
            if (list == null || list.isEmpty()) continue;
            String marker = (linking && ch == activeCh) ? "§e▶ " : "§7";
            tooltip.add(Component.literal(marker + "Channel " + ch + ": §f" + list.size() + " block(s)"));
        }
    }

    // ----- WirelessCopycat freq import -----

    private static void importCopycatFreqs(ItemStack stack, Player player, WirelessCopycatBlockEntity cb) {
        String[] enabledFreqs = cb.getEnabledFreqs();
        if (enabledFreqs.length == 0) {
            player.displayClientMessage(Component.literal(
                    "§7[Universal Keyboard] §fNo enabled faces on that copycat."), true);
            return;
        }
        CompoundTag tag = getOrCreateTag(stack);
        ListTag list = tag.contains("wifi_freqs", Tag.TAG_LIST)
                ? tag.getList("wifi_freqs", Tag.TAG_STRING)
                : new ListTag();
        Set<String> existing = new HashSet<>();
        for (int i = 0; i < list.size(); i++) existing.add(list.getString(i));
        int added = 0;
        for (String f : enabledFreqs) {
            if (!existing.contains(f) && list.size() < LinkedKeyboardBlockEntity.MAX_WIRELESS_FREQS) {
                list.add(StringTag.valueOf(f));
                existing.add(f);
                added++;
            }
        }
        tag.put("wifi_freqs", list);
        writeTag(stack, tag);
        player.displayClientMessage(Component.literal(
                "§a[Universal Keyboard] §f+" + added + " freq(s) added §8(" + list.size() + " total pending§8)"), true);
    }

    // ----- NBT helpers -----

    public static boolean isLinkingMode(ItemStack stack) {
        CompoundTag tag = readTag(stack);
        return tag != null && tag.getBoolean("linking_mode");
    }

    public static void setLinkingMode(ItemStack stack, boolean mode) {
        CompoundTag tag = getOrCreateTag(stack);
        if (mode) {
            // When entering linking mode, migrate saved channel data from BLOCK_ENTITY_DATA
            // into CUSTOM_DATA so existing links are preserved during relinking
            boolean hasChannels = false;
            for (int ch = 1; ch <= LinkedKeyboardBlockEntity.MAX_CHANNELS && !hasChannels; ch++)
                hasChannels = tag.contains("ch" + ch + "_targets");
            if (!hasChannels) {
                var beData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
                if (beData != null) {
                    CompoundTag beTag = beData.copyTag();
                    for (int ch = 1; ch <= LinkedKeyboardBlockEntity.MAX_CHANNELS; ch++) {
                        String key = "ch" + ch + "_targets";
                        if (beTag.contains(key)) tag.put(key, beTag.get(key).copy());
                    }
                    if (beTag.contains("active_channel") && !tag.contains("active_channel"))
                        tag.putInt("active_channel", beTag.getInt("active_channel"));
                }
            }
        }
        tag.putBoolean("linking_mode", mode);
        writeTag(stack, tag);
    }

    public static int getActiveLinkingChannel(ItemStack stack) {
        CompoundTag tag = readTag(stack);
        if (tag == null || !tag.contains("active_channel")) return 1;
        return Math.max(1, Math.min(LinkedKeyboardBlockEntity.MAX_CHANNELS, tag.getInt("active_channel")));
    }

    public static void setActiveLinkingChannel(ItemStack stack, int channel) {
        CompoundTag tag = getOrCreateTag(stack);
        tag.putInt("active_channel", Math.max(1, Math.min(LinkedKeyboardBlockEntity.MAX_CHANNELS, channel)));
        writeTag(stack, tag);
    }

    private static String channelKey(int ch) { return "ch" + ch + "_targets"; }

    private static void removeLinkedTargetFromChannel(ItemStack stack, int channel, BlockPos pos) {
        CompoundTag tag = readTag(stack);
        if (tag == null) return;
        String key = channelKey(channel);
        if (!tag.contains(key, Tag.TAG_LIST)) return;
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            if (e.getInt("x") == pos.getX() && e.getInt("y") == pos.getY() && e.getInt("z") == pos.getZ()) {
                list.remove(i);
                break;
            }
        }
        if (list.isEmpty()) tag.remove(key);
        else tag.put(key, list);
        if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else writeTag(stack, tag);
    }

    private static void addLinkedTargetToChannel(ItemStack stack, int channel, BlockPos pos) {
        CompoundTag tag = getOrCreateTag(stack);
        String key = channelKey(channel);
        ListTag list = tag.contains(key, Tag.TAG_LIST)
                ? tag.getList(key, Tag.TAG_COMPOUND)
                : new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putInt("x", pos.getX());
        entry.putInt("y", pos.getY());
        entry.putInt("z", pos.getZ());
        list.add(entry);
        tag.put(key, list);
        writeTag(stack, tag);
    }

    public static List<BlockPos> getLinkedTargetsForChannel(ItemStack stack, int channel) {
        CompoundTag tag = readTag(stack);
        if (tag == null) return new ArrayList<>();
        String key = channelKey(channel);
        List<BlockPos> result = new ArrayList<>();
        if (tag.contains(key, Tag.TAG_LIST)) {
            ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                result.add(new BlockPos(e.getInt("x"), e.getInt("y"), e.getInt("z")));
            }
        }
        return result;
    }

    /** Returns all non-empty channels as a map. Handles legacy single-mesh format. */
    public static Map<Integer, List<BlockPos>> getAllChannelTargets(ItemStack stack) {
        CompoundTag tag = readTag(stack);
        if (tag == null) return Map.of();
        Map<Integer, List<BlockPos>> result = new HashMap<>();
        for (int ch = 1; ch <= LinkedKeyboardBlockEntity.MAX_CHANNELS; ch++) {
            List<BlockPos> list = readChannel(tag, ch);
            if (!list.isEmpty()) result.put(ch, list);
        }
        // Legacy single mesh_targets → channel 1
        if (result.isEmpty() && tag.contains("mesh_targets", Tag.TAG_LIST)) {
            List<BlockPos> legacy = new ArrayList<>();
            ListTag list = tag.getList("mesh_targets", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                legacy.add(new BlockPos(e.getInt("x"), e.getInt("y"), e.getInt("z")));
            }
            if (!legacy.isEmpty()) result.put(1, legacy);
        }
        return result;
    }

    private static List<BlockPos> readChannel(CompoundTag tag, int ch) {
        String key = channelKey(ch);
        if (!tag.contains(key, Tag.TAG_LIST)) return List.of();
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        List<BlockPos> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            result.add(new BlockPos(e.getInt("x"), e.getInt("y"), e.getInt("z")));
        }
        return result;
    }

    public static boolean hasAnyLinkedTargets(ItemStack stack) {
        return !getAllChannelTargets(stack).isEmpty();
    }

    /** Legacy compat: returns channel 1 targets (or first non-empty channel). */
    public static List<BlockPos> getLinkedTargets(ItemStack stack) {
        Map<Integer, List<BlockPos>> all = getAllChannelTargets(stack);
        List<BlockPos> ch1 = all.get(1);
        if (ch1 != null && !ch1.isEmpty()) return ch1;
        return all.values().stream().findFirst().orElse(List.of());
    }

    private static void clearAllLinkedTargets(ItemStack stack) {
        CompoundTag tag = readTag(stack);
        if (tag == null) return;
        for (int ch = 1; ch <= LinkedKeyboardBlockEntity.MAX_CHANNELS; ch++)
            tag.remove(channelKey(ch));
        tag.remove("mesh_targets");
        tag.remove("target_x"); tag.remove("target_y"); tag.remove("target_z");
        tag.remove("active_channel");
        tag.remove("linking_mode");
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            writeTag(stack, tag);
        }
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
