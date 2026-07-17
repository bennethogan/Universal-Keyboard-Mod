package dev.bennethogan.universalkeyboard.wireless;

import java.util.Random;

// A helper for wireless link frequencies that is independent of Create, just in case
public final class WirelessFreqs {

    private WirelessFreqs() {}

    private static final Random RAND = new Random();
    private static final String FREQ_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public static String generate() {
        char[] result = new char[6];
        for (int i = 0; i < 6; i++) result[i] = FREQ_CHARS.charAt(RAND.nextInt(FREQ_CHARS.length()));
        return new String(result);
    }
}
