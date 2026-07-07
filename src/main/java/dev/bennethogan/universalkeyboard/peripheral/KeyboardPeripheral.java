package dev.bennethogan.universalkeyboard.peripheral;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.AttachedComputerSet;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.universalkeyboard.compat.PeripheralHelper;
import dev.bennethogan.universalkeyboard.compat.SableCompat;
import dev.bennethogan.universalkeyboard.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KeyboardPeripheral implements IPeripheral {

    private final LinkedKeyboardBlockEntity blockEntity;
    private final AttachedComputerSet computers = new AttachedComputerSet();

    public KeyboardPeripheral(LinkedKeyboardBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public String getType() { return "universal_keyboard"; }

    @Override
    public void attach(IComputerAccess computer) { computers.add(computer); }

    @Override
    public void detach(IComputerAccess computer) { computers.remove(computer); }

    @LuaFunction
    public final boolean isLinked() { return blockEntity.isLinkedAsComputer(); }

    @LuaFunction
    public final boolean isInRange() { return blockEntity.isComputerInRange(); }

    @LuaFunction
    public final double getRange() { return ModConfig.COMMON.keyboardRange.get(); }

    @LuaFunction
    public final void unlink() { blockEntity.unlink(); }

    // Getters --------------------------


    // Returns controllers Sable sublevel pose + motion as a table. Nil is not on a sublevel or sable isnt installed
    @LuaFunction(mainThread = true)
    public final Map<String, Object> getSablePose() {
        Level level = blockEntity.getLevel();
        BlockPos pos = blockEntity.getBlockPos();
        if (level == null || !SableCompat.isPresent() || !SableCompat.isOnSublevel(level, pos)) return null;
        double[] snap = SableCompat.getSnapshot(level, pos);
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < SableCompat.GETTER_NAMES.length && i < snap.length; i++)
            out.put(SableCompat.GETTER_NAMES[i], snap[i]);
        return out;
    }

    // Get sable value by name (yaw, posX, velZ, etc). Error on unknown name
    @LuaFunction(mainThread = true)
    public final double getSableValue(String name) throws LuaException {
        if (!SableCompat.isSableGetter(name))
            throw new LuaException("unknown sable value '" + name + "'");
        Level level = blockEntity.getLevel();
        if (level == null) return 0;
        return SableCompat.getValue(level, blockEntity.getBlockPos(), name);
    }

    // Get all variables as 1-based array
    @LuaFunction(mainThread = true)
    public final List<Double> getVariables() {
        double[] vars = blockEntity.getSequencerVars();
        if (vars == null) return List.of();
        List<Double> out = new ArrayList<>(vars.length);
        for (double v : vars) out.add(v);
        return out;
    }

    // Gettet for sequencer variable by its 1-based number (V1 = 1). Errors if out of range.
    @LuaFunction(mainThread = true)
    public final double getVariable(int index) throws LuaException {
        double[] vars = blockEntity.getSequencerVars();
        if (vars == null) vars = new double[0];
        if (index < 1 || index > vars.length)
            throw new LuaException("variable index out of range (1.." + vars.length + ")");
        return vars[index - 1];
    }

    // Set a sequencer variable by its 1-based number (V1 = 1). Requires the controller unlocked
    @LuaFunction(mainThread = true)
    public final void setVariable(int index, double value) throws LuaException {
        if (blockEntity.isLocked())
            throw new LuaException("controller is locked to " + blockEntity.getOwnerName());
        double[] vars = blockEntity.getSequencerVars();
        int len = vars == null ? 0 : vars.length;
        if (index < 1 || index > len)
            throw new LuaException("variable index out of range (1.." + len + ")");
        blockEntity.setSequencerVariable(index - 1, value);
    }

    // Current controller status, as a table
    @LuaFunction(mainThread = true)
    public final Map<String, Object> getState() {
        Level level = blockEntity.getLevel();
        BlockPos pos = blockEntity.getBlockPos();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("linked",           blockEntity.isLinked());
        out.put("channel",          blockEntity.getActiveChannel());
        out.put("sequencerRunning", blockEntity.isSequencerRunning());
        out.put("locked",           blockEntity.isLocked());
        out.put("onSublevel",       level != null && SableCompat.isOnSublevel(level, pos));
        return out;
    }

    // Remote peripheral passthroughs ------------------------
    // Controller is wirelessly linked to peripherals by channel, C1-C16.
    // This allows you to read/drive them wirelessly. Setters require unlocking to prevent issues


    // Which channels resolve to a linked CC peripheral, as a table
    @LuaFunction(mainThread = true)
    public final Map<Integer, String> getLinked() {
        Map<Integer, String> out = new LinkedHashMap<>();
        Level level = blockEntity.getLevel();
        if (level == null) return out;
        for (Map.Entry<Integer, List<BlockPos>> e : blockEntity.getAllChannelTargets().entrySet()) {
            for (BlockPos p : e.getValue()) {
                Object per = PeripheralHelper.getPeripheral(level, p);
                if (per != null) { out.put(e.getKey(), PeripheralHelper.getPeripheralType(per)); break; }
            }
        }
        return out;
    }

    // Method names on peripheral linked to (channel)
    @LuaFunction(mainThread = true)
    public final Map<String, String> getMethods(int channel) throws LuaException {
        Object per = resolveLinkedPeripheral(channel);
        Map<String, String> out = new LinkedHashMap<>();
        for (PeripheralHelper.MethodEntry m : PeripheralHelper.scanMethods(per))
            out.put(m.name(), m.argType().isEmpty() ? "get" : m.argType());
        return out;
    }

    // Numeric getter
    @LuaFunction(mainThread = true)
    public final double get(int channel, String method) throws LuaException {
        return PeripheralHelper.getDoubleGetter(resolveLinkedPeripheral(channel), method);
    }

    // Number setter for peripheral linked on (channel). Requires unlocked controller
    @LuaFunction(mainThread = true)
    public final void set(int channel, String method, double value) throws LuaException {
        if (blockEntity.isLocked())
            throw new LuaException("controller is locked to " + blockEntity.getOwnerName());
        Object per = resolveLinkedPeripheral(channel);
        String err = PeripheralHelper.callMethodWithDouble(per, method, value);
        if (err != null) throw new LuaException(err);
    }

    // Single-argument setter of any type on the peripheral linked to (channel), passes (value) as text,
    // parsed to the setters type. Requires unlocked controller
    @LuaFunction(mainThread = true)
    public final void setString(int channel, String method, String value) throws LuaException {
        if (blockEntity.isLocked())
            throw new LuaException("controller is locked to " + blockEntity.getOwnerName());
        Object per = resolveLinkedPeripheral(channel);
        String err = PeripheralHelper.callSetter(per, method, value);
        if (err != null) throw new LuaException(err);
    }

    // Invokes a zero-argument command on the peripheral linked to (channel), such as stop(). Requires unlocked controller
    @LuaFunction(mainThread = true)
    public final void call(int channel, String method) throws LuaException {
        if (blockEntity.isLocked())
            throw new LuaException("controller is locked to " + blockEntity.getOwnerName());
        Object per = resolveLinkedPeripheral(channel);
        String err = PeripheralHelper.callSetter(per, method, "");
        if (err != null) throw new LuaException(err);
    }

    // First linked CC peripheral on a channel, or a Lua error if none
    private Object resolveLinkedPeripheral(int channel) throws LuaException {
        Level level = blockEntity.getLevel();
        if (level != null)
            for (BlockPos p : blockEntity.getLinkedTargetPositions(channel)) {
                Object per = PeripheralHelper.getPeripheral(level, p);
                if (per != null) return per;
            }
        throw new LuaException("no peripheral linked on channel " + channel);
    }

    public void queueKeyEvent(int keyCode, boolean held) {
        computers.forEach(computer -> computer.queueEvent("key", keyCode, held));
    }

    public void queueCharEvent(char character) {
        computers.forEach(computer -> computer.queueEvent("char", String.valueOf(character)));
    }

    public void queueKeyUpEvent(int keyCode) {
        computers.forEach(computer -> computer.queueEvent("key_up", keyCode));
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof KeyboardPeripheral kp && kp.blockEntity == this.blockEntity;
    }

    @Override
    public @Nullable Object getTarget() { return blockEntity; }
}
