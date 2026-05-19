package dev.bennethogan.universalkeyboard.peripheral;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.AttachedComputerSet;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.bennethogan.universalkeyboard.blockentity.LinkedKeyboardBlockEntity;
import dev.bennethogan.universalkeyboard.config.ModConfig;
import org.jetbrains.annotations.Nullable;

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
