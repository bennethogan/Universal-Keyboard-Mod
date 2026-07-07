package dev.bennethogan.universalkeyboard.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class VistaCompat {

    private static final Logger LOGGER = LogManager.getLogger("universalkeyboard/VistaCompat");

    private static final int SCAN_RADIUS  = 8;
    private static final int RESCAN_TICKS = 60;

    private static boolean initialized = false;
    static          boolean present    = false;

    private static Class<?> tvClass;
    private static Method   mIsScreenOn;
    private static Method   mGetVideoSource;
    private static Method   mGetScreenPixelSize;
    private static Method   mIsPaused;
    private static Method   mGetPlaybackTicks;
    private static Method   mShowsTime;
    private static Field    fFadeAnimation;
    private static Field    fEndermanAnimation;
    private static Method   mGetVideoFrameBuilder;
    private static Method   mVec2iX;
    private static Method   mVec2iY;

    private static Method   mCreateVideoSource;
    private static Object   emptyVideoSource;
    private static Object   noAnimState;
    private static java.lang.reflect.Constructor<?> vec2iCtor;

    // Linked viewfinder as a feed source
    private static Class<?> viewfinderClass;
    private static Method   mGetBroadcastVideo;   // ViewFinderBlockEntity -> IVideoSource

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            tvClass = Class.forName("net.mehvahdjukaar.vista.common.tv.TVBlockEntity");
            Class<?> videoSourceCls = Class.forName("net.mehvahdjukaar.vista.client.video_source.IVideoSource");
            Class<?> animCls        = Class.forName("net.mehvahdjukaar.vista.common.tv.IntAnimationState");
            Class<?> vec2iCls       = Class.forName("net.mehvahdjukaar.moonlight.api.util.math.Vec2i");

            mVec2iX = vec2iCls.getMethod("x");
            mVec2iY = vec2iCls.getMethod("y");

            mIsScreenOn         = tvClass.getMethod("isScreenOn", float.class);
            mGetVideoSource     = tvClass.getMethod("getVideoSource");
            mGetScreenPixelSize = tvClass.getMethod("getScreenPixelSize");
            mIsPaused           = tvClass.getMethod("isPaused");
            mGetPlaybackTicks   = tvClass.getMethod("getPlaybackTicks");
            mShowsTime          = tvClass.getMethod("showsTime");
            fFadeAnimation      = tvClass.getField("fadeAnimation");
            fEndermanAnimation  = tvClass.getField("endermanAnimation");

            mGetVideoFrameBuilder = videoSourceCls.getMethod("getVideoFrameBuilder",
                    float.class, MultiBufferSource.class, boolean.class, vec2iCls, vec2iCls,
                    int.class, boolean.class, animCls, animCls, boolean.class);

            mCreateVideoSource = videoSourceCls.getMethod("create", ItemStack.class);
            emptyVideoSource   = videoSourceCls.getField("EMPTY").get(null);
            noAnimState        = animCls.getField("NO_ANIM").get(null);
            vec2iCtor          = vec2iCls.getConstructor(int.class, int.class);

            viewfinderClass    = Class.forName("net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity");
            mGetBroadcastVideo = viewfinderClass.getMethod("getBroadcastVideo");

            present = true;
            LOGGER.info("Vista detected, compat enabled.");
        } catch (Throwable t) {
            LOGGER.info("Vista not present or renderer API changed, compat disabled. ({})", t.toString());
        }
    }

    public static boolean isPresent() {
        init();
        return present;
    }

    private static boolean descResolved = false;
    private static Field  fLinkedFeedComp;
    private static Method mSupplierGet, mStackGet, mBcastGetInstance, mGetFeedLocById,
                          mGetChunkSendPos, mGetTooltip, mGlobalPosPos;

    private static void descInit() {
        if (descResolved) return;
        descResolved = true;
        try {
            fLinkedFeedComp   = Class.forName("net.mehvahdjukaar.vista.VistaMod").getField("LINKED_FEED_COMPONENT");
            mSupplierGet      = java.util.function.Supplier.class.getMethod("get");
            mStackGet         = ItemStack.class.getMethod("get",
                    net.minecraft.core.component.DataComponentType.class);
            Class<?> bcastMgr = Class.forName("net.mehvahdjukaar.vista.common.broadcast.BroadcastManager");
            mBcastGetInstance = bcastMgr.getMethod("getInstance", net.minecraft.world.level.Level.class);
            mGetFeedLocById   = bcastMgr.getMethod("getFeedLocationById", java.util.UUID.class);
            Class<?> bcastLoc = Class.forName("net.mehvahdjukaar.vista.common.broadcast.IBroadcastLocation");
            mGetChunkSendPos  = bcastLoc.getMethod("getChunkSendPosition");
            mGetTooltip       = bcastLoc.getMethod("getTooltipComponent", net.minecraft.world.level.Level.class);
            mGlobalPosPos     = net.minecraft.core.GlobalPos.class.getMethod("pos");
        } catch (Throwable ignored) {}
    }

    public static String describeCassette(ItemStack cassette) {
        if (cassette == null || cassette.isEmpty()) return null;
        descInit();
        if (fLinkedFeedComp == null) return null;
        try {
            net.minecraft.world.level.Level level = Minecraft.getInstance().level;
            if (level == null) return null;
            Object dct  = mSupplierGet.invoke(fLinkedFeedComp.get(null));
            Object uuid = mStackGet.invoke(cassette, dct);
            if (uuid == null) return "Not linked to a camera";
            Object mgr = mBcastGetInstance.invoke(null, level);
            if (mgr == null) return "Linked camera unavailable";
            Object loc = mGetFeedLocById.invoke(mgr, uuid);
            if (loc == null) return "Linked camera (unknown)";
            Object gp = mGetChunkSendPos.invoke(loc);
            if (gp != null) {
                BlockPos p = (BlockPos) mGlobalPosPos.invoke(gp);
                return "Camera at " + p.getX() + ", " + p.getY() + ", " + p.getZ();
            }
            Object tip = mGetTooltip.invoke(loc, level);
            if (tip instanceof net.minecraft.network.chat.Component c) return c.getString();
            return "Linked camera";
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean tryDrawFeedFromItem(ItemStack cassette, PoseStack poseStack,
                                              MultiBufferSource buffer, float partialTick,
                                              float w, float h, int light) {
        if (!present || cassette == null || cassette.isEmpty()) return false;
        try {
            Object videoSource = mCreateVideoSource.invoke(null, cassette);
            return drawVideoSource(videoSource, poseStack, buffer, partialTick, w, h, light);
        } catch (Throwable t) {
            LOGGER.warn("VistaCompat.tryDrawFeedFromItem failed: {}", t.toString());
            return false;
        }
    }

    // Draw a linked viewfinder's live feed directly
    public static boolean tryDrawFeedFromViewfinder(BlockEntity viewfinderBe, PoseStack poseStack,
                                                    MultiBufferSource buffer, float partialTick,
                                                    float w, float h, int light) {
        if (!present || viewfinderClass == null || !viewfinderClass.isInstance(viewfinderBe)) return false;
        try {
            Object videoSource = mGetBroadcastVideo.invoke(viewfinderBe);
            return drawVideoSource(videoSource, poseStack, buffer, partialTick, w, h, light);
        } catch (Throwable t) {
            LOGGER.warn("VistaCompat.tryDrawFeedFromViewfinder failed: {}", t.toString());
            return false;
        }
    }

    // Request video source feed and draw quad
    private static boolean drawVideoSource(Object videoSource, PoseStack poseStack,
                                           MultiBufferSource buffer, float partialTick,
                                           float w, float h, int light) throws Exception {
        if (videoSource == null || videoSource == emptyVideoSource) return false;

        int base = 128;
        int sx = w >= h ? base : Math.max(1, Math.round(base * w / h));
        int sy = w >= h ? Math.max(1, Math.round(base * h / w)) : base;
        Object screenSize = vec2iCtor.newInstance(sx, sy);

        int ticks = Minecraft.getInstance().level != null
                ? (int) Minecraft.getInstance().level.getGameTime() : 0;

        Object vc = mGetVideoFrameBuilder.invoke(videoSource,
                partialTick, buffer, true, screenSize, screenSize,
                ticks, false, noAnimState, noAnimState, false);
        if (!(vc instanceof VertexConsumer consumer)) return false;

        double texAspect  = sy <= 0 ? 1.0 : (double) sx / sy;
        double quadAspect = h  <= 0 ? 1.0 : (double) w  / h;
        float uInset = 0f, vInset = 0f;
        if (quadAspect > texAspect) vInset = (float) ((1.0 - texAspect / quadAspect) / 2.0);
        else                        uInset = (float) ((1.0 - quadAspect / texAspect) / 2.0);

        emitFeedQuad(consumer, poseStack, w, h, light, uInset, vInset);
        return true;
    }

    private static final Map<BlockPos, BlockPos> cachedTvPos  = new HashMap<>();
    private static final Map<BlockPos, Long>     lastScanTick = new HashMap<>();

    private static Object findNearestTv(Level level, BlockPos wheelPos) {
        long now = level.getGameTime();
        Long lastScan = lastScanTick.get(wheelPos);
        BlockPos cached = cachedTvPos.get(wheelPos);

        if (lastScan != null && now - lastScan < RESCAN_TICKS) {
            if (cached != null) {
                BlockEntity be = level.getBlockEntity(cached);
                if (be != null && tvClass.isInstance(be)) return be;
            } else {
                return null;
            }
        }

        if (lastScanTick.size() > 64) { lastScanTick.clear(); cachedTvPos.clear(); }

        BlockPos bestPos = null;
        double   bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(
                wheelPos.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS),
                wheelPos.offset(SCAN_RADIUS,  SCAN_RADIUS,  SCAN_RADIUS))) {
            BlockEntity be = level.getBlockEntity(p);
            if (be != null && tvClass.isInstance(be)) {
                double d = p.distSqr(wheelPos);
                if (d < bestDist) { bestDist = d; bestPos = p.immutable(); }
            }
        }

        BlockPos key = wheelPos.immutable();
        lastScanTick.put(key, now);
        if (bestPos != null) {
            cachedTvPos.put(key, bestPos);
            return level.getBlockEntity(bestPos);
        }
        cachedTvPos.remove(key);
        return null;
    }

    public static boolean tryDrawFeed(Level level, BlockPos wheelPos,
                                      PoseStack poseStack, MultiBufferSource buffer,
                                      float partialTick, float w, float h, int light) {
        if (!present) return false;
        Object tvBe = findNearestTv(level, wheelPos);
        if (tvBe == null) return false;
        try {
            if (!(boolean) mIsScreenOn.invoke(tvBe, partialTick)) return false;

            Object videoSource = mGetVideoSource.invoke(tvBe);
            Object screenSize  = mGetScreenPixelSize.invoke(tvBe);
            boolean paused     = (boolean) mIsPaused.invoke(tvBe);
            int     videoTicks = (int) mGetPlaybackTicks.invoke(tvBe);
            boolean showsTime  = (boolean) mShowsTime.invoke(tvBe);
            Object fadeAnim    = fFadeAnimation.get(tvBe);
            Object staticAnim  = fEndermanAnimation.get(tvBe);

            Object vc = mGetVideoFrameBuilder.invoke(videoSource,
                    paused ? 1f : partialTick, buffer, !paused, screenSize, screenSize,
                    videoTicks, paused, fadeAnim, staticAnim, showsTime);
            if (!(vc instanceof VertexConsumer consumer)) return false;

            int texW = (int) mVec2iX.invoke(screenSize);
            int texH = (int) mVec2iY.invoke(screenSize);
            double texAspect  = texH <= 0 ? 1.0 : (double) texW / texH;
            double quadAspect = h    <= 0 ? 1.0 : (double) w    / h;
            float uInset = 0f, vInset = 0f;
            if (quadAspect > texAspect) vInset = (float) ((1.0 - texAspect / quadAspect) / 2.0);
            else                        uInset = (float) ((1.0 - quadAspect / texAspect) / 2.0);

            emitFeedQuad(consumer, poseStack, w, h, light, uInset, vInset);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("VistaCompat.tryDrawFeed failed: {}", t.toString());
            return false;
        }
    }

    private static void emitFeedQuad(VertexConsumer vc, PoseStack pose, float w, float h, int light,
                                     float uInset, float vInset) {
        int lu = light & 0xFFFF, lv = (light >> 16) & 0xFFFF;
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        Vector3f n = last.normal().transform(new Vector3f(0f, 0f, -1f));
        float umin = uInset, umax = 1f - uInset, vmin = vInset, vmax = 1f - vInset;
        feedVert(vc, m, -w,  h, umax, vmin, lu, lv, n);
        feedVert(vc, m,  w,  h, umin, vmin, lu, lv, n);
        feedVert(vc, m,  w, -h, umin, vmax, lu, lv, n);
        feedVert(vc, m, -w, -h, umax, vmax, lu, lv, n);
    }

    private static void feedVert(VertexConsumer vc, Matrix4f m, float x, float y, float u, float v,
                                 int lu, int lv, Vector3f n) {
        vc.addVertex(m, x, y, 0f);
        vc.setColor(1f, 1f, 1f, 1f);
        vc.setUv(u, v);
        vc.setOverlay(OverlayTexture.NO_OVERLAY);
        vc.setUv2(lu, lv);
        vc.setNormal(n.x, n.y, n.z);
    }
}
