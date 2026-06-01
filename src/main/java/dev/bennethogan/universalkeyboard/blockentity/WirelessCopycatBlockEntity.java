package dev.bennethogan.universalkeyboard.blockentity;

import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.Random;

public class WirelessCopycatBlockEntity extends CopycatBlockEntity {

    public static final ModelProperty<Integer> PREVIEW_FACE_PROPERTY = new ModelProperty<>();

    public static final int FACES      = 6;
    public static final int LOCATE_ALL = -2;
    private static final Random RAND = new Random();
    private static final String FREQ_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final String[] freqs = new String[FACES];
    private final boolean[] enabled = new boolean[FACES];
    public final int[] power = new int[FACES];

    private int previewFace = -1;
    private long previewExpiry = 0;

    public WirelessCopycatBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        for (int i = 0; i < FACES; i++) freqs[i] = generateFreq();
    }

    public static String generateFreq() {
        char[] result = new char[6];
        for (int i = 0; i < 6; i++) result[i] = FREQ_CHARS.charAt(RAND.nextInt(FREQ_CHARS.length()));
        return new String(result);
    }

    public String[] getFreqs()    { return freqs.clone(); }
    public boolean[] getEnabled() { return enabled.clone(); }
    public String  getFaceFreq(int face)    { return face >= 0 && face < FACES ? freqs[face] : null; }
    public boolean isFaceEnabled(int face)  { return face >= 0 && face < FACES && enabled[face]; }

    public int getPower(Direction dir) { return power[dir.ordinal()]; }

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
        if (previewFace < 0) return;
        if (sl.getGameTime() >= previewExpiry) {
            previewFace   = -1;
            previewExpiry = 0;
            notifyUpdate();
        }
    }

    public void setConfig(String[] newFreqs, boolean[] newEnabled) {
        if (level == null || level.isClientSide) return;
        WirelessRSNetwork.unregisterAll((Level) level, worldPosition);
        for (int i = 0; i < FACES; i++) {
            freqs[i]   = (newFreqs[i] != null && !newFreqs[i].isEmpty()) ? newFreqs[i] : generateFreq();
            enabled[i] = newEnabled[i];
            if (!enabled[i]) power[i] = 0;
        }
        for (int i = 0; i < FACES; i++)
            if (enabled[i]) WirelessRSNetwork.register((Level) level, freqs[i], worldPosition);
        setChanged();
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    public void setFreqPower(String freq, int pw) {
        boolean changed = false;
        for (int i = 0; i < FACES; i++) {
            if (enabled[i] && freqs[i].equals(freq) && power[i] != pw) {
                power[i] = pw;
                changed  = true;
            }
        }
        if (changed && level != null && !level.isClientSide)
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    @Override
    public ModelData getModelData() {
        if (previewFace == -1) return super.getModelData();
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
                if (enabled[i]) WirelessRSNetwork.register((Level) level, freqs[i], worldPosition);
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide) WirelessRSNetwork.unregisterAll((Level) level, worldPosition);
        super.remove();
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null && !level.isClientSide) WirelessRSNetwork.unregisterAll((Level) level, worldPosition);
        super.onChunkUnloaded();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        writeWireless(tag);
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

    private void writeWireless(CompoundTag tag) {
        ListTag freqList = new ListTag();
        for (String f : freqs) freqList.add(StringTag.valueOf(f != null ? f : ""));
        tag.put("freqs", freqList);
        byte mask = 0;
        for (int i = 0; i < FACES; i++) if (enabled[i]) mask |= (byte)(1 << i);
        tag.putByte("enabled", mask);
        if (previewFace >= 0) {
            tag.putByte("previewFace", (byte) previewFace);
            tag.putLong("previewExpiry", previewExpiry);
        }
    }

    private void readWireless(CompoundTag tag) {
        if (tag.contains("freqs", Tag.TAG_LIST)) {
            ListTag freqList = tag.getList("freqs", Tag.TAG_STRING);
            for (int i = 0; i < FACES && i < freqList.size(); i++) {
                freqs[i] = freqList.getString(i);
                if (freqs[i].isEmpty()) freqs[i] = generateFreq();
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
