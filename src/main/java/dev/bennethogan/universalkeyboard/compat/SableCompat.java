package dev.bennethogan.universalkeyboard.compat;

import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Optional Sable integration
 *
 * Sable.HELPER.getContaining(level, blockPos)-  non-null means on sublevel.
 *
 *
 *   sublevel.logicalPose()       --     Pose3dc
 *     pose.position()            --     Vector3dc  — methods x(), y(), z()
 *     pose.orientation()           --   Quaterniondc — methods x(), y(), z(), w()
 *   sublevel.latestLinearVelocity   --  org.joml.Vector3d — public fields x, y, z
 *   sublevel.latestAngularVelocity --   org.joml.Vector3d — public fields x, y, z
 */
public class SableCompat {

    private static final Logger LOGGER = LogManager.getLogger("universalkeyboard/SableCompat");
    private static final String DETECT_CLASS = "dev.ryanhcode.sable.companion.SableCompanion";

    public static final String[] GETTER_NAMES = {
        "posX",    "posY",    "posZ",
        "velX",    "velY",    "velZ",
        "angVelX", "angVelY", "angVelZ",
        "pitch",   "yaw",     "roll"
    };

    // ── Init state ────────────────────────────────────────────────────────────
    private static boolean initialized = false;
    static          boolean present    = false;

    private static Object sableHelper;   // Sable.HELPER
    private static Method getContainingVec3i;   // getContaining(Level, Vec3i) — BlockPos works
    private static Method getContainingChunk;   // getContaining(Level, ChunkPos)
    private static Method getContainingVec3;    // getContaining(Level, Vec3)

    // ── Sublevel handles (resolved lazily from first sublevel object) ─────────
    private static Method logicalPoseMethod; // ServerSubLevel.logicalPose() → Pose3dc
    private static Field  linVelField;       // Vector3d latestLinearVelocity
    private static Field  angVelField;       // Vector3d latestAngularVelocity
    // Public fields on concrete Vector3d (joml)
    private static Field  vecXF, vecYF, vecZF;
    private static volatile boolean sublevelResolved = false;

    // ── Pose3dc handles (resolved lazily from first pose object) ─────────────
    private static Method posePositionMethod;    // Pose3dc.position()  → Vector3dc
    private static Method poseOrientationMethod; // Pose3dc.orientation() → Quaterniondc
    private static Method poseTransformPosMethod; // Pose3dc.transformPosition(Vec3) → Vec3
    // Vector3dc interface methods: x(), y(), z()
    private static Method vdcX, vdcY, vdcZ;
    // Quaterniondc interface methods: w(), x(), y(), z()
    private static Method qdcW, qdcX, qdcY, qdcZ;
    private static volatile boolean poseResolved = false;

    // ── Init ──────────────────────────────────────────────────────────────────

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> companionClass = Class.forName(DETECT_CLASS);

            // SableCompanion.INSTANCE — the documented compatibility singleton
            Field instanceField = companionClass.getDeclaredField("INSTANCE");
            instanceField.setAccessible(true);
            sableHelper = instanceField.get(null);

            // Find all getContaining(LevelLike, X) overloads — Vec3i, ChunkPos, Vec3.
            // The first param must accept Level (or a Level subclass passed at runtime).
            if (sableHelper != null) {
                for (Method m : sableHelper.getClass().getMethods()) {
                    if (!"getContaining".equals(m.getName()) || m.getParameterCount() != 2) continue;
                    Class<?> p0 = m.getParameterTypes()[0];
                    Class<?> p1 = m.getParameterTypes()[1];
                    LOGGER.debug("SableCompat: found getContaining overload with [0]={} [1]={}",
                            p0.getName(), p1.getName());
                    // Skip overloads whose first param isn't compatible with Level.
                    if (!p0.isAssignableFrom(Level.class)
                            && !Level.class.isAssignableFrom(p0)) continue;
                    if (Vec3i.class.isAssignableFrom(p1))         getContainingVec3i = m;
                    else if (ChunkPos.class.isAssignableFrom(p1)) getContainingChunk = m;
                    else if (Vec3.class.isAssignableFrom(p1))     getContainingVec3  = m;
                }
            }

            if (getContainingVec3i == null && getContainingChunk == null && getContainingVec3 == null) {
                LOGGER.warn("SableCompat: no getContaining() overload found on SableCompanion ({}); Sable getters disabled.",
                        sableHelper != null ? sableHelper.getClass().getName() : "null");
                return;
            }

            present = true;
            LOGGER.info("Sable detected — sublevel getter support enabled.");
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            LOGGER.info("Sable not present — sublevel getters disabled.");
        } catch (Exception e) {
            LOGGER.warn("Sable reflection init failed: {}", e.getMessage());
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static boolean isPresent() { init(); return present; }

    /** Returns the ServerSubLevel object for the given position, or null if not on a sublevel. */
    public static Object getSublevel(Level level, BlockPos pos) {
        init();
        if (!present || level == null) return null;
        Object result = tryOverload(0, level, pos);
        if (result != null) return result;
        result = tryOverload(1, level, new ChunkPos(pos));
        if (result != null) return result;
        result = tryOverload(2, level, new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        return result;
    }

    /** Disable any overload that has failed once, prevents per-tick log spam. */
    private static Object tryOverload(int slot, Level level, Object arg) {
        Method m = switch (slot) {
            case 0 -> getContainingVec3i;
            case 1 -> getContainingChunk;
            default -> getContainingVec3;
        };
        if (m == null) return null;
        try {
            return m.invoke(sableHelper, level, arg);
        } catch (Throwable t) {
            Throwable cause = (t instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null)
                    ? ite.getCause() : t;
            LOGGER.warn("SableCompat: getContaining({}) failed: {} — disabling this overload.",
                    arg.getClass().getSimpleName(), cause.getMessage());
            switch (slot) {
                case 0 -> getContainingVec3i = null;
                case 1 -> getContainingChunk = null;
                default -> getContainingVec3  = null;
            }
            return null;
        }
    }

    public static boolean isOnSublevel(Level level, BlockPos pos) {
        return getSublevel(level, pos) != null;
    }

    public static boolean isSableGetter(String name) {
        for (String g : GETTER_NAMES) if (g.equals(name)) return true;
        return false;
    }

    public static double[] getSnapshot(Level level, BlockPos pos) {
        double[] out = new double[GETTER_NAMES.length];
        Object sublevel = getSublevel(level, pos);
        if (sublevel == null) return out;
        ensureSublevelHandles(sublevel);
        for (int i = 0; i < GETTER_NAMES.length; i++) {
            try { out[i] = getFromSublevel(sublevel, GETTER_NAMES[i], pos); }
            catch (Exception ignored) {}
        }
        return out;
    }

    public static double getValue(Level level, BlockPos pos, String name) {
        Object sublevel = getSublevel(level, pos);
        if (sublevel == null) return 0;
        ensureSublevelHandles(sublevel);
        try { return getFromSublevel(sublevel, name, pos); }
        catch (Exception e) { return 0; }
    }

    // ── Data access ───────────────────────────────────────────────────────────

    private static double getFromSublevel(Object sublevel, String name, BlockPos kbPos) throws Exception {
        return switch (name) {
            case "posX"    -> poseKeyboardPos(sublevel, kbPos, 0);
            case "posY"    -> poseKeyboardPos(sublevel, kbPos, 1);
            case "posZ"    -> poseKeyboardPos(sublevel, kbPos, 2);
            case "velX"    -> vecField(sublevel, linVelField, vecXF);
            case "velY"    -> vecField(sublevel, linVelField, vecYF);
            case "velZ"    -> vecField(sublevel, linVelField, vecZF);
            case "angVelX" -> vecField(sublevel, angVelField, vecXF);
            case "angVelY" -> vecField(sublevel, angVelField, vecYF);
            case "angVelZ" -> vecField(sublevel, angVelField, vecZF);
            case "pitch"   -> poseEuler(sublevel, 0);
            case "yaw"     -> poseEuler(sublevel, 1);
            case "roll"    -> poseEuler(sublevel, 2);
            default        -> 0;
        };
    }

    /** velocity / angVel: public fields x/y/z on concrete Vector3d */
    private static double vecField(Object sublevel, Field vecField, Field comp) throws Exception {
        if (vecField == null || comp == null) return 0;
        Object vec = vecField.get(sublevel);
        if (vec == null) return 0;
        Object v = comp.get(vec);
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    /** position: pose.position().x() / .y() / .z() via Vector3dc interface methods */
    private static double posePos(Object sublevel, int idx) throws Exception {
        Object pose = getPose(sublevel);
        if (pose == null || posePositionMethod == null) return 0;
        Object vec = posePositionMethod.invoke(pose);
        if (vec == null) return 0;
        Method m = switch (idx) { case 0 -> vdcX; case 1 -> vdcY; default -> vdcZ; };
        if (m == null) return 0;
        Object v = m.invoke(vec);
        return v instanceof Number n ? n.doubleValue() : 0;
    }


    private static double poseKeyboardPos(Object sublevel, BlockPos kbPos, int idx) throws Exception {
        Object pose = getPose(sublevel);
        if (pose == null) return 0;

        // Primary path: Pose3dc.transformPosition(Vec3) → Vec3 (no JOML instantiation needed)
        if (poseTransformPosMethod != null) {
            Vec3 local = Vec3.atCenterOf(kbPos);
            Object result = poseTransformPosMethod.invoke(pose, local);
            if (result instanceof Vec3 world) {
                return switch (idx) { case 0 -> world.x; case 1 -> world.y; default -> world.z; };
            }
        }

        // Fallback: manual quaternion rotation using already-resolved handles.
        // pose.position() is the world position of ship-local origin (0,0,0).
        // worldKeyboardPos = pose.position() + rotate(kbLocalPos, quaternion)
        if (posePositionMethod != null && poseOrientationMethod != null && qdcW != null && vdcX != null) {
            Object vec  = posePositionMethod.invoke(pose);
            Object quat = poseOrientationMethod.invoke(pose);
            if (vec != null && quat != null) {
                double px = invoke(vdcX, vec), py = invoke(vdcY, vec), pz = invoke(vdcZ, vec);
                double qw = invoke(qdcW, quat), qx = invoke(qdcX, quat),
                       qy = invoke(qdcY, quat), qz = invoke(qdcZ, quat);
                double lx = kbPos.getX() + 0.5, ly = kbPos.getY() + 0.5, lz = kbPos.getZ() + 0.5;
                // Rodrigues: t = 2 * cross(q.xyz, v);  v' = v + qw*t + cross(q.xyz, t)
                double tx = 2 * (qy * lz - qz * ly);
                double ty = 2 * (qz * lx - qx * lz);
                double tz = 2 * (qx * ly - qy * lx);
                double rx = lx + qw * tx + qy * tz - qz * ty;
                double ry = ly + qw * ty + qz * tx - qx * tz;
                double rz = lz + qw * tz + qx * ty - qy * tx;
                return switch (idx) { case 0 -> px + rx; case 1 -> py + ry; default -> pz + rz; };
            }
        }

        return posePos(sublevel, idx); // last resort: ship centre
    }

    /** rotation: pose.orientation().w()/x()/y()/z() → Euler degrees, YXZ convention */
    private static double poseEuler(Object sublevel, int idx) throws Exception {
        Object pose = getPose(sublevel);
        if (pose == null || poseOrientationMethod == null || qdcW == null) return 0;
        Object quat = poseOrientationMethod.invoke(pose);
        if (quat == null) return 0;
        double w = invoke(qdcW, quat), x = invoke(qdcX, quat),
               y = invoke(qdcY, quat), z = invoke(qdcZ, quat);
        return switch (idx) {
            case 0 -> Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, 2*(w*x - y*z)))));  // pitch
            case 1 -> Math.toDegrees(Math.atan2(2*(w*y + x*z), 1 - 2*(x*x + y*y)));         // yaw
            default -> Math.toDegrees(Math.atan2(2*(w*z + x*y), 1 - 2*(y*y + z*z)));         // roll
        };
    }

    private static Object getPose(Object sublevel) throws Exception {
        if (logicalPoseMethod == null) return null;
        Object pose = logicalPoseMethod.invoke(sublevel);
        if (pose != null && !poseResolved) resolvePoseHandles(pose);
        return pose;
    }

    // ── Lazy handle resolution ────────────────────────────────────────────────

    private static void ensureSublevelHandles(Object sublevel) {
        if (sublevelResolved) return;
        synchronized (SableCompat.class) {
            if (sublevelResolved) return;
            Class<?> cls = sublevel.getClass();
            LOGGER.info("SableCompat: resolving handles on sublevel type {}", cls.getName());

            // logicalPose() is on SubLevelAccess interface — find it on the concrete type
            try { logicalPoseMethod = cls.getMethod("logicalPose"); }
            catch (NoSuchMethodException e) {
                LOGGER.warn("SableCompat: logicalPose() not found on {}; pos/rot returns 0", cls.getName());
            }

            // latestLinearVelocity / latestAngularVelocity (Vector3d fields)
            linVelField = fieldAnywhere(cls, "latestLinearVelocity");
            angVelField = fieldAnywhere(cls, "latestAngularVelocity");

            if (linVelField != null) {
                // Concrete Vector3d — public fields x, y, z
                Class<?> vecCls = linVelField.getType();
                try { vecXF = vecCls.getField("x"); vecYF = vecCls.getField("y"); vecZF = vecCls.getField("z"); }
                catch (NoSuchFieldException e) {
                    LOGGER.warn("SableCompat: Vector3d x/y/z fields not found on {}", vecCls.getName());
                }
            }
            sublevelResolved = true;
        }
    }

    private static void resolvePoseHandles(Object pose) {
        synchronized (SableCompat.class) {
            if (poseResolved) return;
            Class<?> cls = pose.getClass();

            // Pose3dc.position() → Vector3dc, Pose3dc.orientation() → Quaterniondc
            try { posePositionMethod    = cls.getMethod("position");    } catch (NoSuchMethodException e) {
                LOGGER.warn("SableCompat: Pose3dc.position() not found; posX/Y/Z returns 0");
            }
            try { poseOrientationMethod = cls.getMethod("orientation"); } catch (NoSuchMethodException e) {
                LOGGER.warn("SableCompat: Pose3dc.orientation() not found; pitch/yaw/roll returns 0");
            }

            // Resolve Vector3dc interface methods from the return type of position()
            if (posePositionMethod != null) {
                Class<?> vecCls = posePositionMethod.getReturnType();
                try { vdcX = vecCls.getMethod("x"); vdcY = vecCls.getMethod("y"); vdcZ = vecCls.getMethod("z"); }
                catch (NoSuchMethodException e) {
                    LOGGER.warn("SableCompat: Vector3dc x()/y()/z() not found on {}", vecCls.getName());
                }
            }

            // Resolve Quaterniondc interface methods from the return type of orientation()
            if (poseOrientationMethod != null) {
                Class<?> quatCls = poseOrientationMethod.getReturnType();
                try {
                    qdcW = quatCls.getMethod("w"); qdcX = quatCls.getMethod("x");
                    qdcY = quatCls.getMethod("y"); qdcZ = quatCls.getMethod("z");
                } catch (NoSuchMethodException e) {
                    LOGGER.warn("SableCompat: Quaterniondc w()/x()/y()/z() not found on {}", quatCls.getName());
                }
            }

            // Pose3dc.transformPosition(Vec3) → Vec3 — preferred path for keyboard world pos
            try {
                poseTransformPosMethod = cls.getMethod("transformPosition", Vec3.class);
            } catch (NoSuchMethodException e) {
                LOGGER.debug("SableCompat: transformPosition(Vec3) not found; using quaternion fallback for posX/Y/Z");
            }

            poseResolved = true;
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static Field fieldAnywhere(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    private static double invoke(Method m, Object obj) {
        try { Object v = m.invoke(obj); return v instanceof Number n ? n.doubleValue() : 0; }
        catch (Exception e) { return 0; }
    }
}
