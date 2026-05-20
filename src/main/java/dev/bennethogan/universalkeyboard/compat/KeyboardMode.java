package dev.bennethogan.universalkeyboard.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public enum KeyboardMode {
    PERIPHERAL_SEQUENCER("Sequencer",        "Script peripheral calls with conditions and delays"),
    THRUSTER_CONTROL(    "Thruster Control",  "Control thruster direction and power"),
    VALUE_PANEL(         "Value Panel",       "Set values on a Create scroll-value block"),
    CC_PERIPHERAL(       "CC Peripheral",     "Call setter methods on a CC peripheral"),
    CC_COMPUTER(         "CC Computer",       "Type to a CC:Tweaked computer"),
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
            case CC_COMPUTER      -> isCCComputer(be);
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
            case CC_COMPUTER      -> "not a CC computer";
            case CC_PERIPHERAL    -> "no peripheral";
            case VALUE_PANEL      -> "no scroll value";
            case THRUSTER_CONTROL      -> "not a thruster peripheral";
            case PERIPHERAL_SEQUENCER  -> "no peripheral";
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
