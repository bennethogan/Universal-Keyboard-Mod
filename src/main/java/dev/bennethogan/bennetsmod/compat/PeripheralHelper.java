package dev.bennethogan.bennetsmod.compat;

import dev.bennethogan.bennetsmod.UniversalKeyboardMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// All CC interaction goes through reflection so CC stays a soft dependency.
public class PeripheralHelper {

    public record MethodEntry(String name, String currentValue, String argType) {
        public boolean isGetter() { return argType.isEmpty(); }
    }

    public record ScanResult(String type, List<String[]> getters, List<String[]> setters,
                              @Nullable String callError) {}

    public record ThrusterState(
            String type,
            double targetVectorX, double targetVectorY,
            double currentVectorX, double currentVectorY,
            int thrust,
            int thrustConfig,
            double currentThrustPn,
            double displayedThrustPn,
            double airflowMs,
            int obstruction,
            int fuelAmountMb,
            int fuelCapacityMb
    ) {}

    private static final Set<String> THRUSTER_TYPES = Set.of(
            "thruster",                 // propulsion thruster (fuel/basic)
            "ion_thruster",             // ion thruster
            "vector_thruster",          // vector thruster
            "liquid_vector_thruster",   // liquid (fluid) vector thruster
            "creative_thruster",        // creative propulsion thruster
            "creative_vector_thruster", // creative vector thruster
            "propulsion_thruster"       // legacy — kept for old versions
    );

    public static boolean isThrusterType(String type) {
        return THRUSTER_TYPES.contains(type);
    }

    private static boolean initialized = false;
    private static boolean ccPresent   = false;

    private static Class<?>  iPeripheralClass;
    @SuppressWarnings("rawtypes")
    private static net.neoforged.neoforge.capabilities.BlockCapability peripheralCap;
    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> luaFunctionClass;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            iPeripheralClass   = Class.forName("dan200.computercraft.api.peripheral.IPeripheral");
            luaFunctionClass   = (Class<? extends Annotation>) Class.forName("dan200.computercraft.api.lua.LuaFunction");
            peripheralCap = net.neoforged.neoforge.capabilities.BlockCapability.createSided(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("computercraft", "peripheral"),
                    (Class) iPeripheralClass);
            ccPresent = true;
            UniversalKeyboardMod.LOGGER.info("CC:Tweaked detected — peripheral method support active.");
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            ccPresent = false;
        }
    }

    public static boolean isCCPresent() {
        init();
        return ccPresent;
    }

    /** Inverse of ensureThrusterPeripheralMode — restores NORMAL mode and zeros inputs. */
    public static void releaseThrusterControl(Level level, BlockPos pos) {
        init();
        if (!ccPresent) return;
        Object p = getPeripheral(level, pos);
        if (p == null || !isThrusterType(getPeripheralType(p))) return;
        try {
            Field beField = findField(p.getClass(), "blockEntity");
            if (beField == null) return;
            beField.setAccessible(true);
            Object be = beField.get(p);
            if (be == null) return;

            Object target = be;
            try {
                Method getCtrl = be.getClass().getMethod("getControllerBE");
                Object ctrl = getCtrl.invoke(be);
                if (ctrl != null) target = ctrl;
            } catch (Exception ignored) {}

            try { target.getClass().getMethod("setDigitalInput", float.class).invoke(target, 0.0f); }
            catch (Exception ignored) {}
            try { target.getClass().getMethod("setRedstonePower", int.class).invoke(target, 0); }
            catch (Exception ignored) {}

            Class<?> modeClass = findNestedEnum(target.getClass(), "ControlMode");
            if (modeClass != null) {
                for (Object c : modeClass.getEnumConstants()) {
                    if (((Enum<?>) c).name().equals("NORMAL")) {
                        target.getClass().getMethod("setControlMode", modeClass).invoke(target, c);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            UniversalKeyboardMod.LOGGER.warn("releaseThrusterControl failed: {}", e.getMessage());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static @Nullable Object getPeripheral(Level level, BlockPos pos) {
        init();
        if (!ccPresent) return null;
        for (Direction dir : Direction.values()) {
            Object p = level.getCapability(
                    (net.neoforged.neoforge.capabilities.BlockCapability) peripheralCap, pos, dir);
            if (p != null) return p;
        }
        return null;
    }

    public static boolean hasPeripheral(Level level, BlockPos pos) {
        return getPeripheral(level, pos) != null;
    }

    public static String getPeripheralType(Object peripheral) {
        try {
            return (String) peripheral.getClass().getMethod("getType").invoke(peripheral);
        } catch (Exception e) {
            return "unknown";
        }
    }

    public static List<MethodEntry> scanMethods(Object peripheral) {
        List<MethodEntry> entries = new ArrayList<>();
        if (luaFunctionClass == null) return entries;
        for (Method m : peripheral.getClass().getMethods()) {
            if (m.getAnnotation(luaFunctionClass) == null) continue;
            Parameter[] params = m.getParameters();
            if (params.length == 0) {
                entries.add(new MethodEntry(m.getName(), invokeGetter(peripheral, m), ""));
            } else if (params.length == 1) {
                String hint = argTypeHint(params[0].getType());
                if (!hint.isEmpty())
                    entries.add(new MethodEntry(m.getName(), "", hint));
            }
        }
        return entries;
    }

    private static String invokeGetter(Object peripheral, Method m) {
        try {
            Object r = m.invoke(peripheral);
            return r == null ? "null" : r.toString();
        } catch (Exception e) {
            return "?";
        }
    }

    private static String argTypeHint(Class<?> type) {
        if (type == int.class || type == long.class || type == Integer.class || type == Long.class)    return "int";
        if (type == double.class || type == float.class || type == Double.class || type == Float.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "true/false";
        if (type == String.class) return "string";
        return "";
    }

    public static @Nullable String callSetter(Object peripheral, String methodName, String argStr) {
        for (Method m : peripheral.getClass().getMethods()) {
            if (!m.getName().equals(methodName)) continue;
            if (m.getAnnotation(luaFunctionClass) == null) continue;
            Parameter[] params = m.getParameters();
            if (params.length != 1) continue;
            Object arg = parseArg(params[0].getType(), argStr);
            if (arg == null) return "invalid input for type " + argTypeHint(params[0].getType());
            try {
                m.invoke(peripheral, arg);
                return null;
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                UniversalKeyboardMod.LOGGER.warn("peripheral call {} failed: {}", methodName, cause.getMessage());
                return cause.getMessage();
            }
        }
        return "method not found";
    }

    private static @Nullable Object parseArg(Class<?> type, String s) {
        try {
            if (type == int.class     || type == Integer.class) return Integer.parseInt(s.trim());
            if (type == long.class    || type == Long.class)    return Long.parseLong(s.trim());
            if (type == double.class  || type == Double.class)  return Double.parseDouble(s.trim());
            if (type == float.class   || type == Float.class)   return Float.parseFloat(s.trim());
            if (type == boolean.class || type == Boolean.class) {
                String t = s.trim().toLowerCase();
                if (t.equals("true")  || t.equals("1")) return true;
                if (t.equals("false") || t.equals("0")) return false;
                return null;
            }
            if (type == String.class) return s;
        } catch (NumberFormatException ignored) {}
        return null;
    }

    public static double getDoubleGetter(Object peripheral, String methodName) {
        return getDoubleVal(peripheral, methodName);
    }

    /**
     * Reflectively sets the block entity inside a Propulsion peripheral to PERIPHERAL control mode
     * so that setDigitalInput() actually dirties thrust. Our mod never calls CC's attach(), so
     * without this the block entity stays in NORMAL mode and all power setters are no-ops.
     */
    private static void ensureThrusterPeripheralMode(Object peripheral) {
        try {
            Field beField = findField(peripheral.getClass(), "blockEntity");
            if (beField == null) return;
            beField.setAccessible(true);
            Object be = beField.get(peripheral);
            if (be == null) return;

            // For multiblock thrusters the peripheral wraps a member block entity,
            // but only the controller calculates thrust. Route to the controller.
            Object target = be;
            try {
                Method getCtrl = be.getClass().getMethod("getControllerBE");
                Object ctrl = getCtrl.invoke(be);
                if (ctrl != null) target = ctrl;
            } catch (Exception ignored) {}

            Class<?> modeClass = findNestedEnum(target.getClass(), "ControlMode");
            if (modeClass == null) return;

            Object peripheralMode = null;
            for (Object c : modeClass.getEnumConstants()) {
                if (((Enum<?>) c).name().equals("PERIPHERAL")) { peripheralMode = c; break; }
            }
            if (peripheralMode == null) return;

            Method setMode = target.getClass().getMethod("setControlMode", modeClass);
            setMode.invoke(target, peripheralMode);
        } catch (Exception e) {
            UniversalKeyboardMod.LOGGER.warn("ensureThrusterPeripheralMode failed: {}", e.getMessage());
        }
    }

    private static @Nullable Field findField(Class<?> cls, String name) {
        while (cls != null) {
            try { return cls.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static @Nullable Class<?> findNestedEnum(Class<?> cls, String simpleName) {
        while (cls != null) {
            for (Class<?> inner : cls.getDeclaredClasses()) {
                if (inner.isEnum() && inner.getSimpleName().equals(simpleName)) return inner;
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    // Names of peripheral methods that map to setDigitalInput on the block entity.
    private static final java.util.Set<String> THRUST_SETTER_METHODS = Set.of(
            "setPower", "setThrust", "setPowerNormalized", "setThrustNormalized");

    /**
     * For multiblock thrusters the peripheral wraps a non-controller member whose
     * setDigitalInput() doesn't forward to the controller in the compiled JAR (our
     * override is in the source reference only). After the normal peripheral call, also
     * directly invoke setDigitalInput on the resolved controller.
     */
    private static void forceControllerDigitalInput(Object peripheral, String methodName, double value) {
        try {
            Field beField = findField(peripheral.getClass(), "blockEntity");
            if (beField == null) return;
            beField.setAccessible(true);
            Object be = beField.get(peripheral);
            if (be == null) return;

            Object ctrl;
            try {
                ctrl = be.getClass().getMethod("getControllerBE").invoke(be);
            } catch (Exception e) { return; }
            // getControllerBE() returns `this` when already the controller — skip those
            if (ctrl == null || ctrl == be) return;

            float normalized = methodName.endsWith("Normalized")
                    ? (float) Math.max(0, Math.min(1.0, value))
                    : (float) Math.max(0, Math.min(15, Math.round(value))) / 15.0f;

            ctrl.getClass().getMethod("setDigitalInput", float.class).invoke(ctrl, normalized);
        } catch (Exception e) {
            UniversalKeyboardMod.LOGGER.warn("forceControllerDigitalInput failed: {}", e.getMessage());
        }
    }

    // Call a single-arg @LuaFunction method with a double value; auto-casts to int/float/long as needed.
    public static @Nullable String callMethodWithDouble(Object peripheral, String methodName, double value) {
        init();
        if (luaFunctionClass == null) return "CC not present";
        ensureThrusterPeripheralMode(peripheral);
        for (Method m : peripheral.getClass().getMethods()) {
            if (!m.getName().equals(methodName)) continue;
            if (m.getAnnotation(luaFunctionClass) == null) continue;
            Parameter[] params = m.getParameters();
            if (params.length != 1) continue;
            Class<?> type = params[0].getType();
            Object arg;
            if      (type == int.class    || type == Integer.class) arg = (int) Math.round(value);
            else if (type == long.class   || type == Long.class)    arg = Math.round(value);
            else if (type == double.class || type == Double.class)  arg = value;
            else if (type == float.class  || type == Float.class)   arg = (float) value;
            else continue;
            try {
                m.invoke(peripheral, arg);
                // Multiblock fix: the compiled Propulsion JAR may not forward
                // setDigitalInput from member to controller. Do it explicitly.
                if (THRUST_SETTER_METHODS.contains(methodName))
                    forceControllerDigitalInput(peripheral, methodName, value);
                return null;
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                UniversalKeyboardMod.LOGGER.warn("thruster call {} failed: {}", methodName, cause.getMessage());
                return cause.getMessage();
            }
        }
        return "method not found";
    }

    public static @Nullable ThrusterState scanThruster(Level level, BlockPos pos) {
        init();
        if (!ccPresent) return null;
        Object p = getPeripheral(level, pos);
        if (p == null) return null;
        String type = getPeripheralType(p);
        if (!isThrusterType(type)) return null;
        // Vector peripherals expose getThrust() → int 0-15.
        // Non-vector peripherals expose getPower() → double 0.0-1.0; scale to 0-15.
        boolean isVector = type.contains("vector");
        int thrust = isVector
                ? getIntVal(p, "getThrust")
                : (int) Math.round(getDoubleVal(p, "getPower") * 15);

        return new ThrusterState(
                type,
                getDoubleVal(p, "getTargetVectorX"),
                getDoubleVal(p, "getTargetVectorY"),
                getDoubleVal(p, "getVectorX"),
                getDoubleVal(p, "getVectorY"),
                thrust,
                getIntVal(p, "getThrustConfig"),
                getDoubleVal(p, "getCurrentThrustPN"),
                getDoubleVal(p, "getDisplayedThrustPN"),
                getDoubleVal(p, "getAirflowMs"),
                getIntVal(p, "getObstruction"),
                getIntVal(p, "getFuelAmountMb"),
                getIntVal(p, "getFuelCapacityMb")
        );
    }

    private static double getDoubleVal(Object p, String methodName) {
        try {
            Object r = p.getClass().getMethod(methodName).invoke(p);
            if (r instanceof Number n) return n.doubleValue();
        } catch (Exception ignored) {}
        return 0.0;
    }

    private static int getIntVal(Object p, String methodName) {
        try {
            Object r = p.getClass().getMethod(methodName).invoke(p);
            if (r instanceof Number n) return n.intValue();
        } catch (Exception ignored) {}
        return 0;
    }

    // Scan methods (and optionally call a setter first), returning structured data safe to send over the network.
    public static @Nullable ScanResult scanAndCall(Level level, BlockPos peripheralPos,
                                                    String methodName, String argStr) {
        init();
        if (!ccPresent) return null;
        Object peripheral = getPeripheral(level, peripheralPos);
        if (peripheral == null) return null;

        String callError = null;
        if (!methodName.isEmpty())
            callError = callSetter(peripheral, methodName, argStr);

        List<MethodEntry> entries = scanMethods(peripheral);
        List<String[]> getters = new ArrayList<>();
        List<String[]> setters = new ArrayList<>();
        for (MethodEntry e : entries) {
            if (e.isGetter()) getters.add(new String[]{ e.name(), e.currentValue() });
            else              setters.add(new String[]{ e.name(), e.argType() });
        }
        return new ScanResult(getPeripheralType(peripheral), getters, setters, callError);
    }
}
