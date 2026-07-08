package dev.bennethogan.universalkeyboard.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;

public final class CannonControl {

    private CannonControl() {}

    private static boolean initialized = false;
    private static boolean present     = false;
    private static boolean aimReady    = false;

    private static Class<?> cannonClass;
    private static Method   mGetPowerLevel;
    private static Method   mSetFirePower;
    private static Method   mReadyToFire;
    private static Method   mIgnite;
    private static Method   mSyncToClients;
    private static Method   mGetGlobalPosition;
    private static Method   mGetBallisticData;
    private static Method   mSetRotationToMatchTraj;
    private static Method   mSnapToWantedRotation;
    private static Method   mGetWorldOrientation;
    private static Method   mSetWorldOrientation;
    private static Method   mIsFiring;
    private static Method   mGetCurrentUser;
    private static Method   mSetCurrentUser;
    private static Method   mCanBeUsedBy;

    private static Method   mComputeTrajectory;
    private static Method   mBallisticDrag;
    private static Method   m3dTrajectory;
    private static Method   m3dYaw;
    private static Method   mTrajGetX;
    private static Method   mTrajGetY;
    private static Method   mTrajFinalTime;

    private static Object   modeDown;
    private static Object   modeStraight;
    private static int      maxPower = 4;

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            cannonClass = Class.forName("net.mehvahdjukaar.supplementaries.common.block.tiles.CannonBlockTile");
            mGetPowerLevel = cannonClass.getMethod("getPowerLevel");
            mSetFirePower  = cannonClass.getMethod("setFirePower", byte.class);
            mReadyToFire   = cannonClass.getMethod("readyToFire");
            mIgnite        = cannonClass.getMethod("ignite", Entity.class);
            mSyncToClients = cannonClass.getMethod("syncToClients", boolean.class);

            // Ownership
            try { mGetCurrentUser = cannonClass.getMethod("getCurrentUser"); } catch (Throwable ignored) {}
            try { mSetCurrentUser = cannonClass.getMethod("setCurrentUser", java.util.UUID.class); } catch (Throwable ignored) {}
            for (Method m : cannonClass.getMethods())
                if (m.getName().equals("canBeUsedBy") && m.getParameterCount() == 2) { mCanBeUsedBy = m; break; }
            try { maxPower = cannonClass.getField("MAX_POWER_LEVEL").getInt(null); } catch (Throwable ignored) {}
            present = true;
        } catch (Throwable t) {
            return;
        }
        try {
            String cannonPkg = "net.mehvahdjukaar.supplementaries.common.block.cannon.";
            Class<?> utils   = Class.forName(cannonPkg + "CannonUtils");
            Class<?> mode    = Class.forName(cannonPkg + "ShootingMode");
            Class<?> traj3d  = Class.forName(cannonPkg + "BallisticTrajectory3D");
            Class<?> traj    = Class.forName(cannonPkg + "BallisticTrajectory");
            Class<?> ballist = Class.forName("net.mehvahdjukaar.supplementaries.common.block.fire_behaviors.BallisticData");

            mGetGlobalPosition      = cannonClass.getMethod("getGlobalPosition", float.class);
            mGetBallisticData       = cannonClass.getMethod("getBallisticData");
            mSetRotationToMatchTraj = cannonClass.getMethod("setRotationToMatchTrajectory", traj3d, float.class);
            mSnapToWantedRotation   = cannonClass.getMethod("snapToWantedRotationInstantly");
            mGetWorldOrientation    = cannonClass.getMethod("getWorldOrientation", float.class);
            mSetWorldOrientation    = cannonClass.getMethod("setWorldOrientation", org.joml.Quaternionf.class);
            mIsFiring               = cannonClass.getMethod("isFiring");

            mComputeTrajectory = utils.getMethod("computeTrajectory", cannonClass, Vec3.class, mode);
            mBallisticDrag     = ballist.getMethod("drag");
            m3dTrajectory      = traj3d.getMethod("trajectory");
            m3dYaw             = traj3d.getMethod("yaw");
            mTrajGetX          = traj.getMethod("getX", double.class);
            mTrajGetY          = traj.getMethod("getY", double.class);
            mTrajFinalTime     = traj.getMethod("finalTime");

            for (Object c : mode.getEnumConstants()) {
                String name = ((Enum<?>) c).name();
                if (name.equals("DOWN"))     modeDown = c;
                if (name.equals("STRAIGHT")) modeStraight = c;
            }
            aimReady = true;
        } catch (Throwable t) {
        }
    }

    public static boolean isPresent() { init(); return present; }
    public static int     maxPower()  { init(); return maxPower; }

    public static boolean isCannon(BlockEntity be) {
        init();
        return present && cannonClass != null && cannonClass.isInstance(be);
    }

    public static boolean isCannon(Level level, BlockPos pos) {
        return level != null && isCannon(level.getBlockEntity(pos));
    }

    public static int getPowerLevel(Level level, BlockPos pos) {
        if (!isPresent() || level == null) return 1;
        BlockEntity be = level.getBlockEntity(pos);
        if (!isCannon(be)) return 1;
        try { return ((Number) mGetPowerLevel.invoke(be)).intValue(); } catch (Throwable t) { return 1; }
    }

    private static Object shootingModeFor(Object cannonBe) {
        try {
            Object ballist = mGetBallisticData.invoke(cannonBe);
            float drag = ((Number) mBallisticDrag.invoke(ballist)).floatValue();
            return (drag != 0f) ? modeDown : modeStraight;
        } catch (Throwable t) {
            return modeDown != null ? modeDown : modeStraight;
        }
    }

    private static Object computeTrajectory3D(Object cannonBe, Vec3 target) {
        try { return mComputeTrajectory.invoke(null, cannonBe, target, shootingModeFor(cannonBe)); }
        catch (Throwable t) { return null; }
    }


    public static boolean aimAt(Level level, BlockPos pos, Vec3 target, int power) {
        if (!isPresent() || !aimReady || level == null || target == null) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (!isCannon(be)) return false;
        try {
            mSetFirePower.invoke(be, (byte) Math.max(1, Math.min(maxPower, power)));
            Object traj3d = computeTrajectory3D(be, target);
            if (traj3d == null) return false;
            mSetRotationToMatchTraj.invoke(be, traj3d, 1.0f);
            mSnapToWantedRotation.invoke(be);
            mSyncToClients.invoke(be, false);
            return true;
        } catch (Throwable t) { return false; }
    }


    public static void fire(Level level, BlockPos pos, Entity igniter) {
        if (!isPresent() || level == null) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!isCannon(be)) return;
        try {
            if ((boolean) mReadyToFire.invoke(be)) mIgnite.invoke(be, igniter);  // ignite() syncs itself
        } catch (Throwable ignored) {}
    }

    // manual mode to adjust pitch/yaw instead of ground targeting
    public static void nudgeAim(Level level, BlockPos pos, double dYawDeg, double dPitchDeg) {
        if (!isPresent() || !aimReady || level == null) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!isCannon(be)) return;
        try {
            org.joml.Quaternionf cur = (org.joml.Quaternionf) mGetWorldOrientation.invoke(be, 1.0f);
            org.joml.Quaternionf q = new org.joml.Quaternionf()
                    .rotateAxis((float) Math.toRadians(dYawDeg), 0f, 1f, 0f)
                    .mul(cur);
            org.joml.Vector3f right = q.transform(new org.joml.Vector3f(1f, 0f, 0f), new org.joml.Vector3f());
            q = new org.joml.Quaternionf()
                    .rotateAxis((float) Math.toRadians(dPitchDeg), right.x, right.y, right.z)
                    .mul(q);
            mSetWorldOrientation.invoke(be, q);          // clamp to the cannon's yaw/pitch restraints
            mSnapToWantedRotation.invoke(be);
            mSyncToClients.invoke(be, false);
        } catch (Throwable ignored) {}
    }


    public static void stepPower(Level level, BlockPos pos, int delta) {
        if (!isPresent() || level == null || delta == 0) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!isCannon(be)) return;
        try {
            int cur = ((Number) mGetPowerLevel.invoke(be)).intValue();
            int next = Math.max(1, Math.min(maxPower, cur + delta));
            if (next == cur) return;
            mSetFirePower.invoke(be, (byte) next);
            mSyncToClients.invoke(be, false);
        } catch (Throwable ignored) {}
    }

    public static boolean isFiring(Level level, BlockPos pos) {
        if (!isPresent() || !aimReady || level == null) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (!isCannon(be)) return false;
        try { return (boolean) mIsFiring.invoke(be); } catch (Throwable t) { return false; }
    }

    //ownership ----------------

    public static java.util.UUID getCurrentUser(Level level, BlockPos pos) {
        if (!isPresent() || level == null || mGetCurrentUser == null) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (!isCannon(be)) return null;
        try { return (java.util.UUID) mGetCurrentUser.invoke(be); } catch (Throwable t) { return null; }
    }

    public static void setCurrentUser(Level level, BlockPos pos, java.util.UUID uuid) {
        if (!isPresent() || level == null || mSetCurrentUser == null) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!isCannon(be)) return;
        try { mSetCurrentUser.invoke(be, uuid); } catch (Throwable ignored) {}
    }
    // here if I need it, but I think its going to cause promixity range check issues too
    public static boolean canBeUsedBy(Level level, BlockPos pos, Entity user) {
        if (!isPresent() || level == null || mCanBeUsedBy == null) return true;
        BlockEntity be = level.getBlockEntity(pos);
        if (!isCannon(be)) return true;
        try { return (boolean) mCanBeUsedBy.invoke(be, pos, user); } catch (Throwable t) { return true; }
    }
}
