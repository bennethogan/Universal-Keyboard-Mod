package dev.bennethogan.universalkeyboard.livecontrol;

public enum FavoriteScreen {
    NONE, LIVE_CONTROL, SEQUENCER, THRUSTER_CONTROL;

    public static FavoriteScreen fromByte(byte b) {
        FavoriteScreen[] vals = values();
        return (b >= 0 && b < vals.length) ? vals[b] : NONE;
    }
}
