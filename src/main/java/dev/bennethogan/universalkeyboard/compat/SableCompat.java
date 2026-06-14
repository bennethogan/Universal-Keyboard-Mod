package dev.bennethogan.universalkeyboard.compat;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.lang.reflect.Method;


public class SableCompat {

    private static final Logger LOGGER = LogManager.getLogger("universalkeyboard/SableCompat");

    public static final String[] GETTER_NAMES = {
        "posX",      "posY",      "posZ",
        "velX",      "velY",      "velZ",
        "angVelX",   "angVelY",   "angVelZ",
        "pitch",     "yaw",       "roll",
        "shipSizeX", "shipSizeY", "shipSizeZ"
    };


    private static boolean initialized = false;
    static          boolean present    = false;

    private static Object companionRef;

    // mass tracker handles
    private static Method getSelfMassTrackerMethod;
    private static Method addBlockMassMethod;
    private static Method getInertiaStaticMethod;
    private static Method sableGetPropertyMethod;
    private static Object massPropertyType;
    private static volatile boolean massHandlesResolved = false;
    private static boolean massSupported = false;

    //
    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            SableCompanion instance = SableCompanion.INSTANCE;
            companionRef = instance;
            present = true;
            LOGGER.info("Sable detected — companion API enabled.");
        } catch (Throwable t) {
            LOGGER.info("Sable not present — sublevel getters disabled. ({})", t.getMessage());
        }
    }

    // Public API

    public static boolean isPresent() { init(); return present; }

    public static Object getSublevel(Level level, BlockPos pos) {
        init();
        if (!present || level == null) return null;
        try {
            return companion().getContaining(level, (Vec3i) pos);
        } catch (Throwable t) {
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
        Object raw = getSublevel(level, pos);
        if (raw == null) return out;
        try {
            SubLevelAccess sla = (SubLevelAccess) raw;
            for (int i = 0; i < GETTER_NAMES.length; i++) {
                out[i] = getFromSublevel(sla, GETTER_NAMES[i], level, pos);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    public static double getValue(Level level, BlockPos pos, String name) {
        Object raw = getSublevel(level, pos);
        if (raw == null) return 0;
        try {
            return getFromSublevel((SubLevelAccess) raw, name, level, pos);
        } catch (Throwable t) {
            return 0;
        }
    }

    // Mass override

    public static double getDefaultBlockMass(BlockState state) {
        if (sableGetPropertyMethod == null || massPropertyType == null) return 1.0;
        try {
            Object v = sableGetPropertyMethod.invoke(state, massPropertyType);
            if (v instanceof Number n) return n.doubleValue();
        } catch (Throwable ignored) {}
        return 1.0;
    }

    public static boolean addBlockMass(Level level, BlockPos pos, BlockState state, double massDelta) {
        init();
        if (!present || level == null || level.isClientSide) return false;
        Object sublevel = getSublevel(level, pos);
        if (sublevel == null) return false;
        ensureMassHandles(sublevel, state);
        if (!massSupported) return false;
        try {
            Object tracker = getSelfMassTrackerMethod.invoke(sublevel);
            if (tracker == null) return false;
            Object inertia = getInertiaStaticMethod.invoke(null, level, pos, state);
            addBlockMassMethod.invoke(tracker, level, state, pos, massDelta, inertia);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("SableCompat: addBlockMass failed: {} — mass override disabled.", t.toString());
            massSupported = false;
            return false;
        }
    }

    // Internal data access

    private static double getFromSublevel(SubLevelAccess sla, String name, Level level, BlockPos kbPos) {
        return switch (name) {
            case "posX"      -> worldPos(sla, kbPos, 0);
            case "posY"      -> worldPos(sla, kbPos, 1);
            case "posZ"      -> worldPos(sla, kbPos, 2);
            case "velX"      -> pointVelocity(sla, level, kbPos, 0);
            case "velY"      -> pointVelocity(sla, level, kbPos, 1);
            case "velZ"      -> pointVelocity(sla, level, kbPos, 2);
            case "angVelX"   -> angularVelocity(sla, 0);
            case "angVelY"   -> angularVelocity(sla, 1);
            case "angVelZ"   -> angularVelocity(sla, 2);
            case "pitch"     -> euler(sla, 0);
            case "yaw"       -> euler(sla, 1);
            case "roll"      -> euler(sla, 2);
            case "shipSizeX" -> shipSize(sla, 0);
            case "shipSizeY" -> shipSize(sla, 1);
            case "shipSizeZ" -> shipSize(sla, 2);
            default          -> 0;
        };
    }

    private static double worldPos(SubLevelAccess sla, BlockPos kbPos, int idx) {
        Vector3d world = toWorldPos(sla.logicalPose(), kbPos);
        return switch (idx) { case 0 -> world.x; case 1 -> world.y; default -> world.z; };
    }

    private static double pointVelocity(SubLevelAccess sla, Level level, BlockPos kbPos, int idx) {
        Vector3d worldPos = toWorldPos(sla.logicalPose(), kbPos);
        Vector3d vel = new Vector3d();
        companion().getVelocity(level, sla, worldPos, vel);
        return switch (idx) { case 0 -> vel.x; case 1 -> vel.y; default -> vel.z; };
    }
    
    private static double angularVelocity(SubLevelAccess sla, int idx) {
        Quaterniondc qc = sla.logicalPose().orientation();
        Quaterniondc qp = sla.lastPose().orientation();

        // delta = q_cur * conj(q_prev)  (conj of unit quaternion = inverse)
        double dw =  qc.w()*qp.w() + qc.x()*qp.x() + qc.y()*qp.y() + qc.z()*qp.z();
        double dx = -qc.w()*qp.x() + qc.x()*qp.w() - qc.y()*qp.z() + qc.z()*qp.y();
        double dy = -qc.w()*qp.y() + qc.x()*qp.z() + qc.y()*qp.w() - qc.z()*qp.x();
        double dz = -qc.w()*qp.z() - qc.x()*qp.y() + qc.y()*qp.x() + qc.z()*qp.w();

        // Always take the shorter arc (dw >= 0 convention).
        if (dw < 0) { dx = -dx; dy = -dy; dz = -dz; }

        // ω ≈ 2 * delta.xyz * 20 ticks/s, then to degrees/s
        double scale = 2.0 * 20.0;
        return Math.toDegrees(switch (idx) { case 0 -> dx; case 1 -> dy; default -> dz; } * scale);
    }

    private static double euler(SubLevelAccess sla, int idx) {
        Quaterniondc q = sla.logicalPose().orientation();
        double w = q.w(), x = q.x(), y = q.y(), z = q.z();
        return switch (idx) {
            case 0 -> Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, 2*(w*x - y*z)))));  // pitch
            case 1 -> Math.toDegrees(Math.atan2(2*(w*y + x*z), 1 - 2*(x*x + y*y)));          // yaw
            default -> Math.toDegrees(Math.atan2(2*(w*z + x*y), 1 - 2*(y*y + z*z)));          // roll
        };
    }


    private static double shipSize(SubLevelAccess sla, int idx) {
        BoundingBox3dc bb = sla.boundingBox();
        return switch (idx) {
            case 0 -> bb.maxX() - bb.minX();
            case 1 -> bb.maxY() - bb.minY();
            default -> bb.maxZ() - bb.minZ();
        };
    }

    // Utilities
    private static Vector3d toWorldPos(Pose3dc pose, BlockPos local) {
        return pose.transformPosition(
                new Vector3d(local.getX() + 0.5, local.getY() + 0.5, local.getZ() + 0.5));
    }

    private static SableCompanion companion() {
        return (SableCompanion) companionRef;
    }

    // Mass handles
    private static void ensureMassHandles(Object sublevel, BlockState sampleState) {
        if (massHandlesResolved) return;
        synchronized (SableCompat.class) {
            if (massHandlesResolved) return;
            massHandlesResolved = true;
            try {
                try {
                    getSelfMassTrackerMethod = sublevel.getClass().getMethod("getSelfMassTracker");
                } catch (NoSuchMethodException e) {
                    LOGGER.info("SableCompat: getSelfMassTracker() not found on {}; mass override disabled.",
                            sublevel.getClass().getName());
                    return;
                }
                Object tracker = getSelfMassTrackerMethod.invoke(sublevel);
                if (tracker == null) {
                    LOGGER.info("SableCompat: mass tracker null; mass override disabled.");
                    return;
                }

                for (Method m : tracker.getClass().getMethods()) {
                    if (m.getName().equals("addBlockMass") && m.getParameterCount() == 5) {
                        addBlockMassMethod = m;
                        break;
                    }
                }
                if (addBlockMassMethod == null) {
                    LOGGER.info("SableCompat: addBlockMass(5-arg) not found; mass override disabled.");
                    return;
                }

                Class<?> helper = tryForName(
                        "dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper",
                        "dev.ryanhcode.sable.sable_rapier.physics.config.block_properties.PhysicsBlockPropertyHelper",
                        "dev.ryanhcode.sable_rapier.physics.config.block_properties.PhysicsBlockPropertyHelper");
                if (helper == null) {
                    LOGGER.info("SableCompat: PhysicsBlockPropertyHelper not found; mass override disabled.");
                    return;
                }
                for (Method m : helper.getMethods()) {
                    if (m.getName().equals("getInertia") && m.getParameterCount() == 3) {
                        getInertiaStaticMethod = m;
                        break;
                    }
                }
                if (getInertiaStaticMethod == null) {
                    LOGGER.info("SableCompat: getInertia(3-arg) not found; mass override disabled.");
                    return;
                }

                try {
                    Class<?> types = tryForName(
                            "dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyTypes",
                            "dev.ryanhcode.sable.sable_rapier.physics.config.block_properties.PhysicsBlockPropertyTypes",
                            "dev.ryanhcode.sable_rapier.physics.config.block_properties.PhysicsBlockPropertyTypes");
                    if (types == null) throw new ClassNotFoundException("PhysicsBlockPropertyTypes not found");
                    Object massSupplier = types.getField("MASS").get(null);
                    massPropertyType = massSupplier.getClass().getMethod("get").invoke(massSupplier);
                    for (Method m : sampleState.getClass().getMethods()) {
                        if (m.getName().equals("sable$getProperty") && m.getParameterCount() == 1) {
                            sableGetPropertyMethod = m;
                            break;
                        }
                    }
                } catch (Throwable t) {
                    LOGGER.debug("SableCompat: MASS property unavailable ({}); assuming default 1.0.", t.toString());
                }

                massSupported = true;
                LOGGER.info("SableCompat: block mass override enabled.");
            } catch (Throwable t) {
                LOGGER.warn("SableCompat: mass handle resolution failed: {}", t.toString());
            }
        }
    }

    private static Class<?> tryForName(String... candidates) {
        for (String name : candidates) {
            try { return Class.forName(name); }
            catch (ClassNotFoundException | NoClassDefFoundError ignored) {}
        }
        return null;
    }
}
