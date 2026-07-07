package dev.bennethogan.universalkeyboard.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

// client-side remote contorlling of maneuver targeting on Supplementaries cannons
public final class GunManeuver {

    private GunManeuver() {}

    private static boolean initialized = false;
    private static boolean present     = false;

    private static Class<?> cannonClass;
    private static Method   mStart;      // CannonController.startControlling(CannonBlockTile)
    private static Method   mStop;       // CannonController.stopControlling()
    private static Method   mIsActive;   // CannonController.isActive()
    private static Field    fHit;        // CannonController.hit (HitResult)
    private static Field    fCannon;     // CannonController.cannon (CannonBlockTile)

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            cannonClass = Class.forName("net.mehvahdjukaar.supplementaries.common.block.tiles.CannonBlockTile");
            Class<?> ctrl = Class.forName("net.mehvahdjukaar.supplementaries.client.cannon.CannonController");
            mStart    = ctrl.getMethod("startControlling", cannonClass);
            mStop     = ctrl.getMethod("stopControlling");
            mIsActive = ctrl.getMethod("isActive");
            fHit      = ctrl.getDeclaredField("hit");    fHit.setAccessible(true);
            fCannon   = ctrl.getDeclaredField("cannon"); fCannon.setAccessible(true);
            present = true;
        } catch (Throwable t) {
        }
    }

    public static boolean isPresent() { init(); return present; }

    public static void start(Level level, BlockPos pos) {
        init();
        if (!present || level == null) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || !cannonClass.isInstance(be)) return;
        try { if (!isActive()) mStart.invoke(null, be); } catch (Throwable ignored) {}
    }

    public static void stop() {
        init();
        if (!present) return;
        try { if (isActive()) mStop.invoke(null); } catch (Throwable ignored) {}
    }

    public static boolean isActive() {
        init();
        if (!present) return false;
        try { return (boolean) mIsActive.invoke(null); } catch (Throwable t) { return false; }
    }

    public static Vec3 currentTarget() {
        init();
        if (!present) return null;
        try {
            Object hit = fHit.get(null);
            return (hit instanceof HitResult hr) ? hr.getLocation() : null;
        } catch (Throwable t) { return null; }
    }

    public static BlockPos controlledCannonPos() {
        init();
        if (!present) return null;
        try {
            Object c = fCannon.get(null);
            return (c instanceof BlockEntity be) ? be.getBlockPos() : null;
        } catch (Throwable t) { return null; }
    }
}
