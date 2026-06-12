package dev.bennethogan.universalkeyboard.compat;

import dev.bennethogan.universalkeyboard.compat.MonitorHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public enum KeyboardMode {
    PERIPHERAL_SEQUENCER("gui.universalkeyboard.mode.sequencer.name",        "gui.universalkeyboard.mode.sequencer.desc"),
    THRUSTER_CONTROL(    "gui.universalkeyboard.mode.thruster_control.name", "gui.universalkeyboard.mode.thruster_control.desc"),
    VALUE_PANEL(         "gui.universalkeyboard.mode.value_panel.name",      "gui.universalkeyboard.mode.value_panel.desc"),
    CC_PERIPHERAL(       "gui.universalkeyboard.mode.cc_peripheral.name",    "gui.universalkeyboard.mode.cc_peripheral.desc"),
    CC_COMPUTER(         "gui.universalkeyboard.mode.cc_computer.name",      "gui.universalkeyboard.mode.cc_computer.desc"),
    ;

    public final String displayName;
    public final String description;

    KeyboardMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public boolean isAvailableAt(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;
        return switch (this) {
            case CC_COMPUTER      -> isCCComputer(be) || MonitorHelper.isMonitor(be);
            case CC_PERIPHERAL    -> PeripheralHelper.hasPeripheral(level, pos);
            case VALUE_PANEL      -> CreateValueHelper.hasScrollValue(be);
            case THRUSTER_CONTROL -> {
                if (!PeripheralHelper.isCCPresent()) yield false;
                Object p = PeripheralHelper.getPeripheral(level, pos);
                yield p != null && PeripheralHelper.isThrusterType(PeripheralHelper.getPeripheralType(p));
            }
            case PERIPHERAL_SEQUENCER -> PeripheralHelper.hasPeripheral(level, pos) || isCCComputer(be);
        };
    }

    public boolean requiresCC() {
        return this == CC_COMPUTER || this == CC_PERIPHERAL || this == THRUSTER_CONTROL;
    }

    public String unavailableReason() {
        return switch (this) {
            case CC_COMPUTER          -> "gui.universalkeyboard.mode.cc_computer.unavailable";
            case CC_PERIPHERAL        -> "gui.universalkeyboard.mode.cc_peripheral.unavailable";
            case VALUE_PANEL          -> "gui.universalkeyboard.mode.value_panel.unavailable";
            case THRUSTER_CONTROL     -> "gui.universalkeyboard.mode.thruster_control.unavailable";
            case PERIPHERAL_SEQUENCER -> "gui.universalkeyboard.mode.sequencer.unavailable";
        };
    }

    public static List<KeyboardMode> available(Level level, BlockPos pos) {
        List<KeyboardMode> r = new ArrayList<>();
        for (KeyboardMode m : values()) if (m.isAvailableAt(level, pos)) r.add(m);
        return r;
    }

    public static int availableBitfield(Level level, BlockPos pos) {
        int bits = 0;
        for (KeyboardMode m : values())
            if (m.isAvailableAt(level, pos)) bits |= (1 << m.ordinal());
        return bits;
    }

    public static boolean isCCComputer(BlockEntity be) {
        String name = be.getClass().getName();
        return name.contains("dan200.computercraft") && name.contains("Computer");
    }
}
