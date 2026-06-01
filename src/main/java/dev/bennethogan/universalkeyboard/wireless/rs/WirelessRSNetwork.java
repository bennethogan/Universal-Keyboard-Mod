package dev.bennethogan.universalkeyboard.wireless.rs;

import dev.bennethogan.universalkeyboard.blockentity.WirelessCopycatBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

public class WirelessRSNetwork {

    private static final Map<Level, Map<String, Set<BlockPos>>> BY_FREQ = new IdentityHashMap<>();
    private static final Map<Level, Map<Long, Set<String>>> POS_TO_FREQS = new IdentityHashMap<>();
    private static final Map<Level, Map<String, Integer>> INPUT_POWER = new IdentityHashMap<>();

    public static void register(Level level, String freq, BlockPos pos) {
        if (freq == null || freq.isEmpty()) return;
        BY_FREQ.computeIfAbsent(level, k -> new HashMap<>())
               .computeIfAbsent(freq, k -> new HashSet<>())
               .add(pos.immutable());
        POS_TO_FREQS.computeIfAbsent(level, k -> new HashMap<>())
                    .computeIfAbsent(pos.asLong(), k -> new HashSet<>())
                    .add(freq);
    }

    public static void unregister(Level level, String freq, BlockPos pos) {
        if (freq == null || freq.isEmpty()) return;
        Map<String, Set<BlockPos>> bf = BY_FREQ.get(level);
        if (bf != null) {
            Set<BlockPos> set = bf.get(freq);
            if (set != null) { set.remove(pos); if (set.isEmpty()) bf.remove(freq); }
        }
        Map<Long, Set<String>> pf = POS_TO_FREQS.get(level);
        if (pf != null) {
            Set<String> freqs = pf.get(pos.asLong());
            if (freqs != null) { freqs.remove(freq); if (freqs.isEmpty()) pf.remove(pos.asLong()); }
        }
    }

    public static void unregisterAll(Level level, BlockPos pos) {
        Map<Long, Set<String>> pf = POS_TO_FREQS.get(level);
        if (pf == null) return;
        Set<String> freqs = pf.remove(pos.asLong());
        if (freqs == null) return;
        Map<String, Set<BlockPos>> bf = BY_FREQ.get(level);
        if (bf == null) return;
        for (String freq : freqs) {
            Set<BlockPos> set = bf.get(freq);
            if (set != null) { set.remove(pos); if (set.isEmpty()) bf.remove(freq); }
        }
    }

    public static void broadcast(Level level, String freq, int power) {
        if (freq == null || freq.isEmpty()) return;
        Map<String, Set<BlockPos>> bf = BY_FREQ.get(level);
        if (bf == null) return;
        Set<BlockPos> positions = bf.get(freq);
        if (positions == null || positions.isEmpty()) return;
        for (BlockPos pos : Set.copyOf(positions)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof WirelessCopycatBlockEntity cb) cb.setFreqPower(freq, power);
        }
    }

    public static Set<BlockPos> getPositions(Level level, String freq) {
        if (freq == null || freq.isEmpty()) return Set.of();
        Map<String, Set<BlockPos>> bf = BY_FREQ.get(level);
        if (bf == null) return Set.of();
        Set<BlockPos> positions = bf.get(freq);
        return positions == null ? Set.of() : Set.copyOf(positions);
    }

    public static void setInputPower(Level level, String freq, int power) {
        if (freq == null || freq.isEmpty()) return;
        INPUT_POWER.computeIfAbsent(level, k -> new HashMap<>()).put(freq, power);
    }

    public static int getInputPower(Level level, String freq) {
        if (freq == null || freq.isEmpty()) return 0;
        Map<String, Integer> pm = INPUT_POWER.get(level);
        return pm != null ? pm.getOrDefault(freq, 0) : 0;
    }
}
