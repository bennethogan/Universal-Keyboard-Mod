package dev.bennethogan.universalkeyboard.compat;

import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
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
            double configMax,       // 100.0 for creative_thruster; maxPn for creative_vector_thruster
            double currentThrustPn,
            double displayedThrustPn,
            double airflowMs,
            int obstruction,
            int fuelAmountMb,
            int fuelCapacityMb
    ) {}

    // Special condition - RPM setters
    private static final Set<String> RPM_TYPE_NAMES = Set.of(
            "electric_motor",             // CreateAddition — setSpeed(number)
            "rotation_speed_controller"   // Create — setTargetSpeed(int)
    );

    public static boolean isRpmType(String type) {
        return RPM_TYPE_NAMES.contains(type);
    }


    public static boolean isRpmCapable(Object peripheral) {
        if (peripheral == null) return false;
        for (java.lang.reflect.Method m : peripheral.getClass().getMethods()) {
            String n = m.getName();
            if (!n.equals("setSpeed") && !n.equals("setTargetSpeed")) continue;
            java.lang.reflect.Parameter[] params = m.getParameters();
            if (params.length == 1) return true;
        }
        return false;
    }

    private static @Nullable String rpmSetterName(Object peripheral) {
        for (java.lang.reflect.Method m : peripheral.getClass().getMethods()) {
            if (!m.getName().equals("setSpeed") && !m.getName().equals("setTargetSpeed")) continue;
            if (m.getParameters().length == 1) return m.getName();
        }
        return null;
    }

    private static @Nullable String rpmGetterName(Object peripheral) {
        for (java.lang.reflect.Method m : peripheral.getClass().getMethods()) {
            if (!m.getName().equals("getSpeed") && !m.getName().equals("getTargetSpeed")) continue;
            if (m.getParameters().length == 0) return m.getName();
        }
        return null;
    }

    public static void callRpmSetter(Object peripheral, int rpm) {
        init();
        if (!ccPresent) return;
        String methodName = rpmSetterName(peripheral);
        if (methodName == null) return;
        callMethodWithDouble(peripheral, methodName, rpm);
    }

    public static int getRpmValue(Level level, BlockPos pos) {
        init();
        if (!ccPresent) return 0;
        Object p = getPeripheral(level, pos);
        if (p == null) return 0;
        String getter = rpmGetterName(p);
        if (getter == null) return 0;
        int val = getIntVal(p, getter);
        return Math.max(0, Math.min(256, Math.abs(val)));
    }


    // Special condition - thrusters

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
                if (m.getReturnType() == void.class) {
                    // void no-arg methods are commands (e.g. stop()) — don't call eagerly,
                    // expose as callable commands in the setter list.
                    entries.add(new MethodEntry(m.getName(), "", "call"));
                } else {
                    entries.add(new MethodEntry(m.getName(), invokeGetter(peripheral, m), ""));
                }
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
            if (params.length == 0) {
                // void no-arg command (e.g. stop())
                try {
                    m.invoke(peripheral);
                    return null;
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    UniversalKeyboardMod.LOGGER.warn("peripheral call {} failed: {}", methodName, cause.getMessage());
                    return cause.getMessage();
                }
            }
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

            // Read current power BEFORE switching mode. In NORMAL mode getPower() returns
            // redstone-based power; after switching to PERIPHERAL it returns digitalInput (0).
            // Preserve the level so the first peripheral call doesn't kill thrust — same as
            // what CC's own attach() does: setDigitalInput(clamp(getPower(), 0, 1)).
            float currentPower = 0f;
            try {
                Object r = target.getClass().getMethod("getPower").invoke(target);
                if (r instanceof Number n) currentPower = Math.max(0f, Math.min(1f, n.floatValue()));
            } catch (Exception ignored) {}

            setMode.invoke(target, peripheralMode);

            try {
                target.getClass().getMethod("setDigitalInput", float.class).invoke(target, currentPower);
            } catch (Exception ignored) {}
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

    // Call setVector(x, y) on a vector peripheral; falls back to setVectorX + setVectorY if absent.
    public static void callVectorSetter(Object peripheral, double x, double y) {
        init();
        if (!ccPresent) return;
        ensureThrusterPeripheralMode(peripheral);
        // Try the atomic two-arg setVector(double, double) first.
        for (Method m : peripheral.getClass().getMethods()) {
            if (!m.getName().equals("setVector")) continue;
            Parameter[] params = m.getParameters();
            if (params.length != 2) continue;
            Class<?> t0 = params[0].getType(), t1 = params[1].getType();
            boolean bothDouble = (t0 == double.class || t0 == Double.class || t0 == float.class || t0 == Float.class)
                    && (t1 == double.class || t1 == Double.class || t1 == float.class || t1 == Float.class);
            if (!bothDouble) continue;
            Object a0 = (t0 == float.class || t0 == Float.class) ? (float) x : x;
            Object a1 = (t1 == float.class || t1 == Float.class) ? (float) y : y;
            try {
                m.invoke(peripheral, a0, a1);
                return;
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                UniversalKeyboardMod.LOGGER.warn("setVector({},{}) failed: {}", x, y, cause.getMessage());
                return;
            }
        }
        // Fall back to separate single-axis setters.
        callMethodWithDouble(peripheral, "setVectorX", x);
        callMethodWithDouble(peripheral, "setVectorY", y);
    }

    // Call a single-arg method with a double value; auto-casts to int/float/long as needed.
    // No @LuaFunction annotation check — callers only pass names from the peripheral browser
    // or the hardcoded thruster-control method list, so annotation filtering is redundant here.
    public static @Nullable String callMethodWithDouble(Object peripheral, String methodName, double value) {
        init();
        if (!ccPresent) return "CC not present";
        ensureThrusterPeripheralMode(peripheral);
        for (Method m : peripheral.getClass().getMethods()) {
            if (!m.getName().equals(methodName)) continue;
            Parameter[] params = m.getParameters();
            if (params.length == 0) {
                // void no-arg command (e.g. the electric motor's stop()) — value is ignored.
                try {
                    m.invoke(peripheral);
                    return null;
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    UniversalKeyboardMod.LOGGER.warn("peripheral call {} failed: {}", methodName, cause.getMessage());
                    return cause.getMessage();
                }
            }
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

    /** Returns the current thruster power as a 0-15 signal, or 0 if not a thruster. */
    public static int getThrusterPower(Level level, BlockPos pos) {
        init();
        if (!ccPresent) return 0;
        Object p = getPeripheral(level, pos);
        if (p == null) return 0;
        String type = getPeripheralType(p);
        if (!isThrusterType(type)) return 0;
        if (type.contains("vector"))
            return getIntVal(p, "getThrust");
        return (int) Math.round(getDoubleVal(p, "getPower") * 15);
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
        boolean isVector          = type.contains("vector");
        boolean isCreativeVector  = type.equals("creative_vector_thruster");
        int thrust = isVector
                ? getIntVal(p, "getThrust")
                : (int) Math.round(getDoubleVal(p, "getPower") * 15);

        // creative_vector_thruster uses setThrustOutput(pN) instead of setThrustConfig(%).
        // configMax carries the max pN so the screen can scale the slider correctly.
        // thrustConfig is expressed as 0-100 (% of max) for both creative types.
        double configMax;
        int thrustConfig;
        if (isCreativeVector) {
            configMax = getDoubleVal(p, "getMaxThrustOutputPn");
            // peripheralThrustOutput field holds the exact override value — use it for accurate slider position.
            double override = getBeFieldDouble(p, "peripheralThrustOutput");
            thrustConfig = (override >= 0 && configMax > 0) ? (int) Math.round(override / configMax * 100) : 0;
        } else {
            configMax    = 100.0;
            thrustConfig = getIntVal(p, "getThrustConfig");
        }

        // creative_vector_thruster peripheral doesn't expose getCurrentThrustPN / getDisplayedThrustPN.
        // Fall back to the block entity methods so the phosphor display shows real thrust data.
        double currentThrustPn  = getDoubleVal(p, "getCurrentThrustPN");
        double displayedThrustPn = getDoubleVal(p, "getDisplayedThrustPN");
        if (isCreativeVector) {
            if (currentThrustPn  == 0) currentThrustPn  = getBeMethodDouble(p, "getCurrentThrust");
            if (displayedThrustPn == 0) displayedThrustPn = getBeMethodDouble(p, "getDisplayedThrustPnForTooltip");
        }

        return new ThrusterState(
                type,
                getDoubleVal(p, "getTargetVectorX"),
                getDoubleVal(p, "getTargetVectorY"),
                getDoubleVal(p, "getVectorX"),
                getDoubleVal(p, "getVectorY"),
                thrust,
                thrustConfig,
                configMax,
                currentThrustPn,
                displayedThrustPn,
                getDoubleVal(p, "getAirflowMs"),
                getIntVal(p, "getObstruction"),
                getIntVal(p, "getFuelAmountMb"),
                getIntVal(p, "getFuelCapacityMb")
        );
    }

    /** Read a double value from a method on the block entity embedded in the peripheral. */
    private static double getBeMethodDouble(Object peripheral, String methodName) {
        try {
            Field beField = findField(peripheral.getClass(), "blockEntity");
            if (beField == null) return 0;
            beField.setAccessible(true);
            Object be = beField.get(peripheral);
            if (be == null) return 0;
            Object r = be.getClass().getMethod(methodName).invoke(be);
            if (r instanceof Number n) return n.doubleValue();
        } catch (Exception ignored) {}
        return 0;
    }

    /** Read a double from a named field on the block entity embedded in the peripheral. */
    private static double getBeFieldDouble(Object peripheral, String fieldName) {
        try {
            Field beField = findField(peripheral.getClass(), "blockEntity");
            if (beField == null) return -1;
            beField.setAccessible(true);
            Object be = beField.get(peripheral);
            if (be == null) return -1;
            Field f = findField(be.getClass(), fieldName);
            if (f == null) return -1;
            f.setAccessible(true);
            Object v = f.get(be);
            if (v instanceof Number n) return n.doubleValue();
        } catch (Exception ignored) {}
        return -1;
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
