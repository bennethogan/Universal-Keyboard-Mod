package dev.bennethogan.bennetsmod.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects typewriter-type CC peripherals and reads their key-frequency bindings.
 *
 * The NBT format varies by mod. We try several common field-name variants in
 * order and log the actual NBT keys when none match, so users can report
 * the correct names for their typewriter mod.
 */
public class TypewriterHelper {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** One key-binding as read from the typewriter block entity. */
    public record Binding(int keyCode, ItemStack firstItem, ItemStack secondItem) {}

    /** True if the block at pos is a CC peripheral whose type name contains "typewriter". */
    public static boolean isTypewriter(Level level, BlockPos pos) {
        if (!PeripheralHelper.isCCPresent()) return false;
        Object p = PeripheralHelper.getPeripheral(level, pos);
        if (p == null) return false;
        return PeripheralHelper.getPeripheralType(p).toLowerCase().contains("typewriter");
    }

    /**
     * Reads all key-frequency bindings from the typewriter block entity at pos.
     * Returns an empty list if the format is unrecognised (check log for actual keys).
     */
    public static List<Binding> readBindings(Level level, BlockPos pos, HolderLookup.Provider registries) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return List.of();

        CompoundTag tag;
        try {
            tag = be.saveWithoutMetadata(registries);
        } catch (Exception e) {
            LOGGER.error("[BennetsMod] Cannot read typewriter NBT at {}: {}", pos, e.getMessage());
            return List.of();
        }

        // Try common list-field names used by various typewriter mods
        for (String key : new String[]{
                "Bindings", "bindings", "Keys", "keys",
                "KeyBindings", "key_bindings", "Controls", "controls"}) {
            if (tag.contains(key, Tag.TAG_LIST)) {
                return parseList(tag.getList(key, Tag.TAG_COMPOUND), registries, pos);
            }
        }

        LOGGER.warn("[BennetsMod] Typewriter at {} has unrecognised NBT format. " +
                "Top-level keys: {}. Please report this so support can be added.",
                pos, tag.getAllKeys());
        return List.of();
    }

    private static List<Binding> parseList(ListTag list, HolderLookup.Provider reg, BlockPos pos) {
        List<Binding> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);

            int keyCode = readInt(b, "GLFWKey", "Key", "key", "KeyCode", "key_code", "Code", "code");
            if (keyCode < 0) {
                LOGGER.warn("[BennetsMod] Typewriter binding {} at {} has no key code. Fields: {}",
                        i, pos, b.getAllKeys());
                continue;
            }

            ItemStack first  = readStack(b, reg,
                    "FirstItem", "First", "first", "Item1", "item1", "FrequencyFirst", "frequency_first");
            ItemStack second = readStack(b, reg,
                    "SecondItem", "Second", "second", "Item2", "item2", "FrequencySecond", "frequency_second");

            result.add(new Binding(keyCode, first, second));
        }
        return result;
    }

    private static int readInt(CompoundTag tag, String... keys) {
        for (String k : keys)
            if (tag.contains(k)) return tag.getInt(k); // works for any numeric tag
        return -1;
    }

    private static ItemStack readStack(CompoundTag tag, HolderLookup.Provider reg, String... keys) {
        for (String k : keys)
            if (tag.contains(k, Tag.TAG_COMPOUND))
                return ItemStack.parseOptional(reg, tag.getCompound(k));
        return ItemStack.EMPTY;
    }
}
