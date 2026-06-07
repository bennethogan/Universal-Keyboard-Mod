package dev.bennethogan.universalkeyboard.compat;

import dev.bennethogan.universalkeyboard.UniversalKeyboardMod;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;


/**
 * Compat detection for Create: Connected (create_connected)
 * The "Redstone Link Wildcard" option in the configuration file breaks our
 * redstone link feature if it is enabled. This detects whether the user has this
 * setting enabled, so I can force a dialog box only for the users who havent fixed it
 * For now, if Create connected is installed, but the config cant be read, it acts
 * like the option is still True
 */
public final class CreateConnectedCompat {

    private CreateConnectedCompat() {}

    public static final String MODID = "create_connected";

    private static final ResourceLocation WILDCARD_FEATURE =
            ResourceLocation.fromNamespaceAndPath(MODID, "redstone_link_wildcard");

    private static Boolean loaded;

    private static boolean reflectionInit = false;
    private static java.lang.reflect.Method commonGetter;   // CCConfigs.common()
    private static java.lang.reflect.Field  toggleField;    // CCommon.toggle (CFeatures)
    private static java.lang.reflect.Method isEnabledMethod; // CFeatures.isEnabled(ResourceLocation)

    public static boolean isLoaded() {
        if (loaded == null) loaded = ModList.get().isLoaded(MODID);
        return loaded;
    }


    public static boolean isRedstoneWildcardActive() {
        if (!isLoaded()) return false;
        initReflection();
        if (commonGetter == null) return true; // mod present but config unreadable -> assume its still on
        try {
            Object common = commonGetter.invoke(null);
            if (common == null) return true; // accessed before config load -> assume its still on
            Object features = toggleField.get(common);
            return (boolean) isEnabledMethod.invoke(features, WILDCARD_FEATURE);
        } catch (Throwable t) {
            UniversalKeyboardMod.LOGGER.debug("create_connected wildcard config read failed: {}", t.toString());
            return true;
        }
    }

    private static void initReflection() {
        if (reflectionInit) return;
        reflectionInit = true;
        if (!isLoaded()) return;
        try {
            Class<?> ccConfigs = Class.forName("com.hlysine.create_connected.config.CCConfigs");
            commonGetter = ccConfigs.getMethod("common");
            Class<?> ccommon = Class.forName("com.hlysine.create_connected.config.CCommon");
            toggleField = ccommon.getField("toggle");
            Class<?> cfeatures = Class.forName("com.hlysine.create_connected.config.CFeatures");
            isEnabledMethod = cfeatures.getMethod("isEnabled", ResourceLocation.class);
            UniversalKeyboardMod.LOGGER.info("Create: Connected detected — redstone link wildcard config probe active.");
        } catch (Throwable t) {
            // Fall back to presence-only detection if config cant be read
            commonGetter = null;
            UniversalKeyboardMod.LOGGER.info("Create: Connected detected, but config probe unavailable ({}). " +
                    "Warning will be shown based on presence only.", t.toString());
        }
    }
}
