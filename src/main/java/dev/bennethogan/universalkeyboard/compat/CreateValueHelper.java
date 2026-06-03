package dev.bennethogan.universalkeyboard.compat;

import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import net.minecraft.world.level.block.entity.BlockEntity;


public class CreateValueHelper {

    // Synthetic setter name so its picked up for the sequencer
    public static final String VALUE_PANEL_SETTER = "Value Panel";

    private static boolean initialized = false;
    private static boolean createPresent = false;

    private static Class<?>  smartBlockEntityClass;
    private static Class<?>  scrollValueBehaviourClass;
    private static Class<?>  behaviourTypeClass;
    private static Object    scrollValueType;

    private static java.lang.reflect.Method getBehaviourMethod;
    private static java.lang.reflect.Method setValueMethod;
    private static java.lang.reflect.Method getValueMethod;
    private static java.lang.reflect.Field  minField;
    private static java.lang.reflect.Field  maxField;

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            smartBlockEntityClass = Class.forName(
                "com.simibubi.create.foundation.blockEntity.SmartBlockEntity");
            scrollValueBehaviourClass = Class.forName(
                "com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour");
            behaviourTypeClass = Class.forName(
                "com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType");

            scrollValueType    = scrollValueBehaviourClass.getField("TYPE").get(null);
            getBehaviourMethod = smartBlockEntityClass.getMethod("getBehaviour", behaviourTypeClass);
            setValueMethod     = scrollValueBehaviourClass.getMethod("setValue", int.class);
            getValueMethod     = scrollValueBehaviourClass.getMethod("getValue");

            minField = scrollValueBehaviourClass.getDeclaredField("min");
            maxField = scrollValueBehaviourClass.getDeclaredField("max");
            minField.setAccessible(true);
            maxField.setAccessible(true);

            createPresent = true;
            UniversalKeyboardMod.LOGGER.info("Create detected — scroll value support active.");
        } catch (ClassNotFoundException e) {
            UniversalKeyboardMod.LOGGER.info("Create not present — scroll value support disabled.");
        } catch (Exception e) {
            UniversalKeyboardMod.LOGGER.warn("Create reflection setup failed: {}", e.getMessage());
        }
    }

    public static boolean hasScrollValue(BlockEntity be) {
        init();
        if (!createPresent || be == null) return false;
        if (!smartBlockEntityClass.isInstance(be)) return false;
        return getScrollBehaviour(be) != null;
    }

    public static boolean setValue(BlockEntity be, int value) {
        init();
        if (!createPresent) return false;
        Object behaviour = getScrollBehaviour(be);
        if (behaviour == null) return false;
        try {
            setValueMethod.invoke(behaviour, value);
            return true;
        } catch (Exception e) {
            UniversalKeyboardMod.LOGGER.warn("setValue failed: {}", e.getMessage());
            return false;
        }
    }

    public static int getValue(BlockEntity be) {
        init();
        if (!createPresent) return Integer.MIN_VALUE;
        Object behaviour = getScrollBehaviour(be);
        if (behaviour == null) return Integer.MIN_VALUE;
        try { return (int) getValueMethod.invoke(behaviour); }
        catch (Exception e) { return Integer.MIN_VALUE; }
    }

    public static int getMin(BlockEntity be) {
        init();
        if (!createPresent) return 0;
        Object b = getScrollBehaviour(be);
        if (b == null) return 0;
        try { return (int) minField.get(b); } catch (Exception e) { return 0; }
    }

    public static int getMax(BlockEntity be) {
        init();
        if (!createPresent) return 256;
        Object b = getScrollBehaviour(be);
        if (b == null) return 256;
        try { return (int) maxField.get(b); } catch (Exception e) { return 256; }
    }

    public static boolean isCreatePresent() {
        init();
        return createPresent;
    }

    private static Object getScrollBehaviour(BlockEntity be) {
        try { return getBehaviourMethod.invoke(be, scrollValueType); }
        catch (Exception e) { return null; }
    }
}
