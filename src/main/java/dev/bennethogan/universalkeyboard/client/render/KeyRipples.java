package dev.bennethogan.universalkeyboard.client.render;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

// cool freaking animation made from reading WaveRGB,
// an open source software for Logitech keyboard animation
public final class KeyRipples {

    private KeyRipples() {}

    //  tuning options that are non-configurable (for now)
    private static final float LIFE_TICKS = 8f;   // ring lives this many ticks
    private static final float MAX_RADIUS = 7f;   // texels traveled over a lifetime
    private static final float RING_WIDTH = 1.6f; // soft half-thickness of the ring
    private static final float PEAK_BLEND = 0.9f; // how white the ring gets at full strength
    private static final int   MAX_RIPPLES = 24;  // key-mash cap; oldest gets dropped
    private static final long  SECOND_RING_DELAY = 3; // ticks behind the first (Double explosion!!)

    private record Ripple(float cx, float cy, long spawnTick) {}

    private static final List<Ripple> ripples = new ArrayList<>();

    private static boolean doubleExplosion() {
        try { return dev.bennethogan.universalkeyboard.config.ModConfig.CLIENT.doubleExplosion.get(); }
        catch (Exception e) { return false; }
    }

    // explosion!
    public static void spawn(int glfwKey) {
        if (!KeyboardAnimations.animationsEnabled()) return;
        int[] t = KeyTexels.get(glfwKey);
        if (t == null) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (ripples.size() >= MAX_RIPPLES) ripples.remove(0);
        long now = level.getGameTime();
        // Center the ring on the key's full footprint for wide keys
        int w = t.length > 2 ? t[2] : 1;
        int h = t.length > 3 ? t[3] : 1;
        float cx = t[0] + w / 2f;
        float cy = t[1] + h / 2f;
        ripples.add(new Ripple(cx, cy, now));
        // Double explosion!! A second ring chases the first from the same key.
        if (doubleExplosion()) {
            if (ripples.size() >= MAX_RIPPLES) ripples.remove(0);
            ripples.add(new Ripple(cx, cy, now + SECOND_RING_DELAY));
        }
    }

    // for culling ripples
    public static boolean any(double now) {
        ripples.removeIf(r -> now - r.spawnTick > LIFE_TICKS);
        return !ripples.isEmpty();
    }


    // color blending with wave animation's RGB
    public static int apply(int tx, int ty, int argb, double now) {
        float strength = 0f;
        for (Ripple r : ripples) {
            float age01 = (float) ((now - r.spawnTick) / LIFE_TICKS);
            if (age01 < 0f || age01 > 1f) continue;
            float dx = tx + 0.5f - r.cx;
            float dy = ty + 0.5f - r.cy;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            float ring = age01 * MAX_RADIUS;
            float band = 1f - Math.abs(dist - ring) / RING_WIDTH;
            if (band <= 0f) continue;
            // fade in strength
            strength = Math.max(strength, band * (1f - age01));
        }
        if (strength <= 0f) return argb;

        float s = strength * PEAK_BLEND;
        int cr = (argb >> 16) & 0xFF, cg = (argb >> 8) & 0xFF, cb = argb & 0xFF;
        return 0xFF000000
                | ((int) (cr + (255 - cr) * s) << 16)
                | ((int) (cg + (255 - cg) * s) << 8)
                |  (int) (cb + (255 - cb) * s);
    }
}
