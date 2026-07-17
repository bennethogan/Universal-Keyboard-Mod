package dev.bennethogan.universalkeyboard.blockentity;

import com.simibubi.create.api.schematic.nbt.PartialSafeNBT;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import dev.bennethogan.universalkeyboard.block.WirelessCopycatPanelBlock;
import dev.bennethogan.universalkeyboard.block.WirelessCopycatStepBlock;
import dev.bennethogan.universalkeyboard.compat.SableCompat;
import dev.bennethogan.universalkeyboard.config.ModConfig;
import dev.bennethogan.universalkeyboard.wireless.rs.WirelessRSNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.ArrayList;
import java.util.List;

public class WirelessCopycatBlockEntity extends CopycatBlockEntity implements PartialSafeNBT {

    public static final ModelProperty<Integer> PREVIEW_FACE_PROPERTY = new ModelProperty<>();

    public static final int FACES      = 6;
    public static final int LOCATE_ALL = -2;

    private final String[] freqs = new String[FACES];
    private final boolean[] enabled = new boolean[FACES];
    public final int[] power = new int[FACES];

    private int previewFace = -1;
    private long previewExpiry = 0;

    public WirelessCopycatBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        for (int i = 0; i < FACES; i++) freqs[i] = "";
        setLazyTickRate(20); // poll Sable ship mass ~once per second
    }

    public static String generateFreq() {
        // create-free utility in case that causes an issue when/if screens that arent create dependant generate frequencies
        return dev.bennethogan.universalkeyboard.wireless.WirelessFreqs.generate();
    }

    public String[] getFreqs()    { return freqs.clone(); }
    public boolean[] getEnabled() { return enabled.clone(); }
    public String  getFaceFreq(int face)    { return face >= 0 && face < FACES ? freqs[face] : null; }
    public boolean isFaceEnabled(int face)  { return face >= 0 && face < FACES && enabled[face]; }

    public int getPower(Direction dir) { return power[dir.ordinal()]; }

    public String[] getEnabledFreqs() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < FACES; i++)
            if (enabled[i] && freqs[i] != null && !freqs[i].isEmpty()) result.add(freqs[i]);
        return result.toArray(new String[0]);
    }

    public void startPreview(int faceIdx) {
        if (level == null || level.isClientSide || faceIdx < 0 || faceIdx >= FACES) return;
        int durationTicks = ModConfig.COMMON.copycatLocateDuration.get() * 20;
        previewFace   = faceIdx;
        previewExpiry = level.getGameTime() + durationTicks;
        notifyUpdate();
        ((ServerLevel) level).scheduleTick(worldPosition, getBlockState().getBlock(), durationTicks);
    }

    public void startLocate() {
        if (level == null || level.isClientSide) return;
        int durationTicks = ModConfig.COMMON.copycatLocateDuration.get() * 20;
        previewFace   = LOCATE_ALL;
        previewExpiry = level.getGameTime() + durationTicks;
        notifyUpdate();
        ((ServerLevel) level).scheduleTick(worldPosition, getBlockState().getBlock(), durationTicks);
    }

    public void checkPreviewExpiry(ServerLevel sl) {
        if (previewFace == -1) return;
        if (sl.getGameTime() >= previewExpiry) {
            clearPreview();
        }
    }

    private void clearPreview() {
        previewFace   = -1;
        previewExpiry = 0;
        if (level != null && !level.isClientSide) notifyUpdate();
    }

    // Render-time fall back to help test button not get stuck
    private boolean previewExpired() {
        return previewFace != -1 && level != null && level.getGameTime() >= previewExpiry;
    }

    public void setConfig(String[] newFreqs, boolean[] newEnabled) {
        if (level == null || level.isClientSide) return;
        WirelessRSNetwork.unregisterAll((Level) level, worldPosition);
        for (int i = 0; i < FACES; i++) {
            String f = newFreqs[i] != null ? newFreqs[i] : "";
            // generate random frequency only once enabled, it starts blank now
            if (newEnabled[i] && f.isEmpty()) f = generateFreq();
            freqs[i]   = f;
            enabled[i] = newEnabled[i];
            if (!enabled[i]) power[i] = 0;
        }
        for (int i = 0; i < FACES; i++)
            if (enabled[i] && !freqs[i].isEmpty()) WirelessRSNetwork.register((Level) level, freqs[i], worldPosition);
        setChanged();
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    public void setFreqPower(String freq, int pw) {
        boolean changed = false;
        for (int i = 0; i < FACES; i++) {
            if (enabled[i] && freq != null && freq.equals(freqs[i]) && power[i] != pw) {
                power[i] = pw;
                changed  = true;
            }
        }
        if (changed && level != null && !level.isClientSide)
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    @Override
    public ModelData getModelData() {
        if (previewFace == -1 || previewExpired()) return super.getModelData();
        return ModelData.builder()
                .with(com.simibubi.create.content.decoration.copycat.CopycatModel.MATERIAL_PROPERTY, getMaterial())
                .with(PREVIEW_FACE_PROPERTY, previewFace)
                .build();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide)
            for (int i = 0; i < FACES; i++)
                if (enabled[i] && !freqs[i].isEmpty()) WirelessRSNetwork.register((Level) level, freqs[i], worldPosition);
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level != null && !level.isClientSide) {
            if (level instanceof ServerLevel sl) checkPreviewExpiry(sl);
            updateShipMass(level, worldPosition, getBlockState());
        }
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide) {
            WirelessRSNetwork.unregisterAll((Level) level, worldPosition);
            reverseShipMass();
        }
        super.remove();
    }

    // ── Sable weight compat ──────────────────────────────────────────────────
    // The amount of downloads for the Copycats / Aero weight compat told me I needed to
    // make my Copycats have the same mass
    private static final double STEP_MASS  = 0.25;
    private static final double PANEL_MASS = 0.125;

    private java.lang.ref.WeakReference<Object> massSublevelRef = null;
    private double appliedMassDelta = 0.0;

    private double targetShipMass(BlockState state) {
        var block = state.getBlock();
        if (block instanceof WirelessCopycatStepBlock)  return STEP_MASS;
        if (block instanceof WirelessCopycatPanelBlock) return PANEL_MASS;
        return 1.0;
    }

    private void updateShipMass(Level level, BlockPos pos, BlockState state) {
        if (!SableCompat.isPresent()) return;
        Object sub  = SableCompat.getSublevel(level, pos);
        Object prev = massSublevelRef == null ? null : massSublevelRef.get();
        if (sub == prev) return;

        if (sub == null) {
            massSublevelRef  = null;
            appliedMassDelta = 0.0;
            return;
        }

        double def   = SableCompat.getDefaultBlockMass(state);
        double delta = targetShipMass(state) - def;
        if (Math.abs(delta) < 1.0e-9) {
            appliedMassDelta = 0.0;
        } else if (SableCompat.addBlockMass(level, pos, state, delta)) {
            appliedMassDelta = delta;
        } else {
            appliedMassDelta = 0.0;
        }
        massSublevelRef = new java.lang.ref.WeakReference<>(sub); // avoid retry spam
    }

    private void reverseShipMass() {
        if (appliedMassDelta == 0.0 || level == null || level.isClientSide) return;
        Object prev = massSublevelRef == null ? null : massSublevelRef.get();
        if (prev != null && SableCompat.getSublevel(level, worldPosition) == prev)
            SableCompat.addBlockMass(level, worldPosition, getBlockState(), -appliedMassDelta);
        appliedMassDelta = 0.0;
        massSublevelRef  = null;
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null && !level.isClientSide) WirelessRSNetwork.unregisterAll((Level) level, worldPosition);
        super.onChunkUnloaded();
    }

    // Logic to rotate cardinal direction faces during schematic printing and disassembly (ideally)
    @Override
    public void transform(BlockEntity be, StructureTransform transform) {
        super.transform(be, transform);

        String[] newFreqs   = new String[FACES];
        boolean[] newEnabled = new boolean[FACES];
        int[] newPower      = new int[FACES];
        for (int i = 0; i < FACES; i++) newFreqs[i] = "";

        for (Direction dir : Direction.values()) {
            Direction moved = transformDirection(transform, dir);
            int from = dir.ordinal();
            int to   = moved.ordinal();
            newFreqs[to]   = freqs[from] != null ? freqs[from] : "";
            newEnabled[to] = enabled[from];
            newPower[to]   = power[from];
        }

        System.arraycopy(newFreqs,   0, freqs,   0, FACES);
        System.arraycopy(newEnabled, 0, enabled, 0, FACES);
        System.arraycopy(newPower,   0, power,   0, FACES);

        notifyUpdate();
    }

    private static Direction transformDirection(StructureTransform transform, Direction dir) {
        Direction result = transform.mirrorFacing(dir); // no-op when mirror is null
        if (transform.rotation != null && transform.rotation != Rotation.NONE && transform.rotationAxis != null) {
            result = transform.rotateFacing(result);
        }
        return result;
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        try {
            super.writeSafe(tag, registries);
        } catch (Exception ignored) {
        }
        writeWireless(tag, false);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        writeWireless(tag, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        readWireless(tag);
        if (clientPacket) {
            requestModelDataUpdate();
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 16);
        }
    }

    private void writeWireless(CompoundTag tag, boolean clientPacket) {
        ListTag freqList = new ListTag();
        for (String f : freqs) freqList.add(StringTag.valueOf(f != null ? f : ""));
        tag.put("freqs", freqList);
        byte mask = 0;
        for (int i = 0; i < FACES; i++) if (enabled[i]) mask |= (byte)(1 << i);
        tag.putByte("enabled", mask);
        // Preview is a visual aid, only sync it to the client, dont persist to disk,
        // so a save/reload mid-test can't leave a face stuck gold
        if (clientPacket && previewFace >= 0) {
            tag.putByte("previewFace", (byte) previewFace);
            tag.putLong("previewExpiry", previewExpiry);
        }
    }

    private void readWireless(CompoundTag tag) {
        if (tag.contains("freqs", Tag.TAG_LIST)) {
            ListTag freqList = tag.getList("freqs", Tag.TAG_STRING);
            for (int i = 0; i < FACES && i < freqList.size(); i++) {
                freqs[i] = freqList.getString(i);
            }
        }
        if (tag.contains("enabled")) {
            byte mask = tag.getByte("enabled");
            for (int i = 0; i < FACES; i++) enabled[i] = (mask & (1 << i)) != 0;
        }
        previewFace   = tag.contains("previewFace")  ? tag.getByte("previewFace")  : -1;
        previewExpiry = tag.contains("previewExpiry") ? tag.getLong("previewExpiry") : 0;
    }
}
