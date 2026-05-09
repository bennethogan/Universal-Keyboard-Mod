package dev.bennethogan.bennetsmod.compat;

import dev.bennethogan.bennetsmod.UniversalKeyboardMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
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

    private static final Set<String> THRUSTER_TYPES =
            Set.of("propulsion_thruster", "vector_thruster", "creative_thruster");

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

    // Call a single-arg @LuaFunction method with a double value; auto-casts to int/float/long as needed.
    public static @Nullable String callMethodWithDouble(Object peripheral, String methodName, double value) {
        init();
        if (luaFunctionClass == null) return "CC not present";
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
        return new ThrusterState(
                type,
                getDoubleVal(p, "getTargetVectorX"),
                getDoubleVal(p, "getTargetVectorY"),
                getDoubleVal(p, "getVectorX"),
                getDoubleVal(p, "getVectorY"),
                getIntVal(p, "getThrust"),
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
