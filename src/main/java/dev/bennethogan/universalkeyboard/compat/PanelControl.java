package dev.bennethogan.universalkeyboard.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

// Dashpanels compat relying on IExternalUpdatable modules or setState (CC)

public final class PanelControl {

    private PanelControl() {}

    private static boolean initialized = false;
    private static boolean present     = false;
    private static boolean driveReady  = false;

    private static Class<?> panelBeClass;
    private static Class<?> iInputClass;
    private static Class<?> iOutputClass;
    private static Class<?> iExternalUpdatableClass;
    private static Class<?> iModuleLuaObjectClass;
    private static Class<?> iModuleArgumentsClass;
    private static Class<?> returnMethodClass;
    private static Class<?> iMultiInputClass;
    private static Class<?> iMultiOutputClass;

    private static Method mGetSelectedModule; // PanelBlockEntity.getSelectedModules(Player) -> String
    private static Method mGetModule;      // PanelBlockEntity.getModule(String) -> Module
    private static Method mGetModules;     // PanelBlockEntity.getModules() -> ModuleMap (a Map)
    private static Method mGetOrCreate;    // ModulesNetworkMember.getOrCreate() -> ModulesNetwork
    private static Method mNetworkUpdate;  // ModulesNetworkMember.networkUpdate(ModulesNetwork)
    private static Method mSetNum;         // IExternalUpdatable.setNum(List)
    private static Method mSetAnalog;      // IOutput.setAnalog(int)
    private static Method mGetAnalog;      // IInput.getAnalog() -> int
    private static Method mGetMethods;     // IModuleLuaObject.getMethods(BiConsumer)
    private static Method mReturnMethodGet;// ReturnMethod.get(IModuleArguments)

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            panelBeClass = Class.forName("moth.boxxed.panels.content.panel.PanelBlockEntity");
            present = true;
        } catch (Throwable t) {
            return;   // Control Panels absent
        }
        try {
            // syncing which module the player is looking at, for linking raycast
            mGetSelectedModule = panelBeClass.getMethod("getSelectedModules",
                    net.minecraft.world.entity.player.Player.class);
        } catch (Throwable ignored) {
        }
        try {
            String api = "moth.boxxed.panels.api.module.";
            String cc  = "moth.boxxed.panels.compat.computercraft.";
            iInputClass             = Class.forName(api + "IInput");
            iOutputClass            = Class.forName(api + "IOutput");
            iExternalUpdatableClass = Class.forName(api + "IExternalUpdatable");
            iModuleLuaObjectClass   = Class.forName(cc  + "IModuleLuaObject");
            iModuleArgumentsClass   = Class.forName(cc  + "IModuleArguments");
            returnMethodClass       = Class.forName(cc  + "IModuleLuaObject$ReturnMethod");
            try { iMultiInputClass  = Class.forName(api + "IMultiInput"); }  catch (Throwable ignored) {}
            try { iMultiOutputClass = Class.forName(api + "IMultiOutput"); } catch (Throwable ignored) {}
            Class<?> networkClass   = Class.forName("moth.boxxed.panels.api.network.ModulesNetwork");

            mGetModule      = panelBeClass.getMethod("getModule", String.class);
            mGetModules     = panelBeClass.getMethod("getModules");
            mGetOrCreate    = panelBeClass.getMethod("getOrCreate");
            mNetworkUpdate  = panelBeClass.getMethod("networkUpdate", networkClass);
            mSetNum         = iExternalUpdatableClass.getMethod("setNum", List.class);
            mSetAnalog      = iOutputClass.getMethod("setAnalog", int.class);
            mGetAnalog      = iInputClass.getMethod("getAnalog");
            mGetMethods     = iModuleLuaObjectClass.getMethod("getMethods", BiConsumer.class);
            mReturnMethodGet = returnMethodClass.getMethod("get", iModuleArgumentsClass);
            driveReady = true;
        } catch (Throwable t) {
        }
    }

    public static boolean isPresent() { init(); return present; }

    public static boolean isPanel(BlockEntity be) {
        init();
        return present && panelBeClass != null && panelBeClass.isInstance(be);
    }

    public static boolean isPanel(Level level, BlockPos pos) {
        return level != null && isPanel(level.getBlockEntity(pos));
    }

    // list all names including non-drivable
    public static List<String> moduleNames(Level level, BlockPos pos) {
        if (!isPresent() || level == null) return List.of();
        BlockEntity be = level.getBlockEntity(pos);
        if (!isPanel(be) || mGetModules == null) return List.of();
        try {
            Object map = mGetModules.invoke(be);
            if (map instanceof Map<?, ?> m)
                return new ArrayList<>((Collection<String>) (Collection<?>) m.keySet());
        } catch (Throwable ignored) {}
        return List.of();
    }

   // levers, knobs, momentary/plain switches, bulbs, seven-segments. Multi-axis excluded for now
    public static List<String> drivableModuleNames(Level level, BlockPos pos) {
        if (!isPresent() || !driveReady || level == null) return List.of();
        BlockEntity be = level.getBlockEntity(pos);
        if (!isPanel(be) || mGetModules == null) return List.of();
        List<String> out = new ArrayList<>();
        try {
            Object map = mGetModules.invoke(be);
            if (map instanceof Map<?, ?> m)
                for (Map.Entry<?, ?> e : ((Map<String, Object>) m).entrySet())
                    if (e.getKey() instanceof String name && isDrivable(e.getValue())) out.add(name);
        } catch (Throwable ignored) {}
        return out;
    }

    public static boolean isDrivable(Object module) {
        if (module == null) return false;
        if (iMultiInputClass != null && iMultiInputClass.isInstance(module)) return false;
        if (iMultiOutputClass != null && iMultiOutputClass.isInstance(module)) return false;
        return (iExternalUpdatableClass != null && iExternalUpdatableClass.isInstance(module))
                || (iOutputClass != null && iOutputClass.isInstance(module))
                || (iModuleLuaObjectClass != null && iModuleLuaObjectClass.isInstance(module));
    }

    public static boolean isModuleDrivable(Level level, BlockPos pos, String moduleName) {
        if (!isPresent() || !driveReady || level == null || moduleName == null || moduleName.isEmpty())
            return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (!isPanel(be)) return false;
        try {
            return isDrivable(mGetModule.invoke(be, moduleName));
        } catch (Throwable ignored) {}
        return false;
    }


    // which module the player is currently pointing at on the panel, as tracked by dashpanels
    public static String getSelectedModule(Level level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
        if (!isPresent() || mGetSelectedModule == null || level == null || player == null) return "";
        BlockEntity be = level.getBlockEntity(pos);
        if (!isPanel(be)) return "";
        try {
            Object name = mGetSelectedModule.invoke(be, player);
            return name instanceof String s ? s : "";
        } catch (Throwable ignored) {}
        return "";
    }

    // get value 0-15, 0 if not found
    public static int getValue(Level level, BlockPos panelPos, String moduleName) {
        if (!isPresent() || !driveReady || level == null) return 0;
        BlockEntity be = level.getBlockEntity(panelPos);
        if (!isPanel(be)) return 0;
        try {
            Object module = mGetModule.invoke(be, moduleName);
            if (module != null && iInputClass.isInstance(module))
                return ((Number) mGetAnalog.invoke(module)).intValue();
        } catch (Throwable ignored) {}
        return 0;
    }

    // Drive a module to value 0-15, server-side
    public static void setValue(Level level, BlockPos panelPos, String moduleName, int value) {
        if (!isPresent() || !driveReady || level == null) return;
        BlockEntity be = level.getBlockEntity(panelPos);
        if (!isPanel(be)) return;
        try {
            Object module = mGetModule.invoke(be, moduleName);
            if (module == null) return;
            int v = Math.max(0, Math.min(15, value));
            boolean driven;
            if (iExternalUpdatableClass.isInstance(module)) {
                mSetNum.invoke(module, List.of(v));
                driven = true;
            } else if (iOutputClass.isInstance(module)) {
                mSetAnalog.invoke(module, v);
                driven = true;
            } else if (iModuleLuaObjectClass.isInstance(module)) {
                driven = invokeLuaMethod(module, "setState", v > 0);
            } else {
                driven = false;
            }
            if (driven) {
                Object net = mGetOrCreate.invoke(be);
                if (net != null) mNetworkUpdate.invoke(be, net);
            }
        } catch (Throwable ignored) {}
    }


    // invoke the modules CC method by name to build a stand-in IModuleArguments to carry the arg.
    // Used for setState when IExternalUpdateable isnt available
    private static boolean invokeLuaMethod(Object module, String name, Object... argVals) {
        try {
            Map<String, Object> methods = new HashMap<>();
            BiConsumer<String, Object> collector = methods::put;
            mGetMethods.invoke(module, collector);
            Object rm = methods.get(name);
            if (rm == null) return false;
            Object args = Proxy.newProxyInstance(
                    iModuleArgumentsClass.getClassLoader(),
                    new Class<?>[]{iModuleArgumentsClass},
                    (proxy, method, a) -> switch (method.getName()) {
                        case "count"   -> argVals.length;
                        case "get"     -> argVals[(int) a[0]];
                        case "getType" -> "boolean";
                        case "drop"    -> proxy;
                        default        -> method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                    });
            mReturnMethodGet.invoke(rm, args);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
