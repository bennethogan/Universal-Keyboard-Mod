package dev.bennethogan.universalkeyboard.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class VistaCamera {

    private VistaCamera() {}

    private static boolean initialized = false;
    private static boolean present     = false;

    private static Class<?> viewfinderClass;
    private static Method   mGetWorldOrientation;
    private static Method   mSetWorldOrientation;
    private static Method   mGetZoomLevel;
    private static Method   mSetZoomLevel;
    private static Method   mSyncToClients;
    private static int      maxZoom = 44;

    // for resolving hollow tape's linked camera position
    private static Field  fLinkedFeedComp;
    private static Method mSupplierGet, mStackGet, mBcastGetInstance, mGetFeedLocById,
                          mGetChunkSendPos, mGlobalPosPos;

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            viewfinderClass = Class.forName("net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity");
            mGetWorldOrientation = viewfinderClass.getMethod("getWorldOrientation", float.class);
            mSetWorldOrientation = viewfinderClass.getMethod("setWorldOrientation", Quaternionf.class);
            mGetZoomLevel        = viewfinderClass.getMethod("getZoomLevel");
            mSetZoomLevel        = viewfinderClass.getMethod("setZoomLevel", int.class);
            mSyncToClients       = viewfinderClass.getMethod("syncToClients");
            try { maxZoom = viewfinderClass.getField("MAX_ZOOM").getInt(null); } catch (Throwable ignored) {}

            fLinkedFeedComp   = Class.forName("net.mehvahdjukaar.vista.VistaMod").getField("LINKED_FEED_COMPONENT");
            mSupplierGet      = java.util.function.Supplier.class.getMethod("get");
            mStackGet         = ItemStack.class.getMethod("get", net.minecraft.core.component.DataComponentType.class);
            Class<?> bcastMgr = Class.forName("net.mehvahdjukaar.vista.common.broadcast.BroadcastManager");
            mBcastGetInstance = bcastMgr.getMethod("getInstance", Level.class);
            mGetFeedLocById   = bcastMgr.getMethod("getFeedLocationById", java.util.UUID.class);
            Class<?> bcastLoc = Class.forName("net.mehvahdjukaar.vista.common.broadcast.IBroadcastLocation");
            mGetChunkSendPos  = bcastLoc.getMethod("getChunkSendPosition");
            mGlobalPosPos     = net.minecraft.core.GlobalPos.class.getMethod("pos");

            present = true;
        } catch (Throwable t) {
        }
    }

    public static BlockPos getCassetteCameraPos(Level level, ItemStack cassette) {
        init();
        if (!present || level == null || cassette == null || cassette.isEmpty() || fLinkedFeedComp == null)
            return null;
        try {
            Object dct  = mSupplierGet.invoke(fLinkedFeedComp.get(null));
            Object uuid = mStackGet.invoke(cassette, dct);
            if (uuid == null) return null;
            Object mgr = mBcastGetInstance.invoke(null, level);
            if (mgr == null) return null;
            Object loc = mGetFeedLocById.invoke(mgr, uuid);
            if (loc == null) return null;
            Object gp = mGetChunkSendPos.invoke(loc);
            return gp == null ? null : (BlockPos) mGlobalPosPos.invoke(gp);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isPresent() { init(); return present; }

    public static boolean isViewfinder(BlockEntity be) {
        init();
        return present && viewfinderClass != null && viewfinderClass.isInstance(be);
    }

    public static boolean isViewfinder(Level level, BlockPos pos) {
        return level != null && isViewfinder(level.getBlockEntity(pos));
    }

    public static void nudgeAim(Level level, BlockPos pos, double dYawDeg, double dPitchDeg) {
        if (!isPresent() || level == null) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!isViewfinder(be)) return;
        try {
            Quaternionf cur = (Quaternionf) mGetWorldOrientation.invoke(be, 1.0f);
            Quaternionf q = new Quaternionf()
                    .rotateAxis((float) Math.toRadians(dYawDeg), 0f, 1f, 0f)
                    .mul(cur);
            Vector3f right = q.transform(new Vector3f(1f, 0f, 0f), new Vector3f());
            q = new Quaternionf()
                    .rotateAxis((float) Math.toRadians(dPitchDeg), right.x, right.y, right.z)
                    .mul(q);
            mSetWorldOrientation.invoke(be, q);
            mSyncToClients.invoke(be);
        } catch (Throwable ignored) {}
    }


    public static void nudgeZoom(Level level, BlockPos pos, int delta) {
        if (!isPresent() || level == null || delta == 0) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!isViewfinder(be)) return;
        try {
            int cur = (int) mGetZoomLevel.invoke(be);
            int next = Math.max(1, Math.min(maxZoom, cur + delta));
            if (next == cur) return;
            mSetZoomLevel.invoke(be, next);
            mSyncToClients.invoke(be);
        } catch (Throwable ignored) {}
    }
}
