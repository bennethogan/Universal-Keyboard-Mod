package dev.bennethogan.universalkeyboard.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    private static Method   renderFeedMethod;

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            tvClass = Class.forName("net.mehvahdjukaar.vista.common.tv.TVBlockEntity");

            Class<?> rendCls = Class.forName("net.mehvahdjukaar.vista.client.renderer.TvBlockEntityRenderer");
            renderFeedMethod = rendCls.getMethod("renderFeed",
                    tvClass, float.class, PoseStack.class, MultiBufferSource.class,
                    float.class, float.class, int.class);

            present = true;
            LOGGER.info("Vista (cameramod) detected — TV feed compat enabled.");
        } catch (Throwable t) {
            LOGGER.info("Vista not present or renderFeed API missing — TV feed disabled. ({})", t.getMessage());
        }
    }

    public static boolean isPresent() {
        init();
        return present;
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
            return (boolean) renderFeedMethod.invoke(null, tvBe, partialTick, poseStack, buffer, w, h, light);
        } catch (Throwable t) {
            LOGGER.warn("VistaCompat.tryDrawFeed failed: {}", t.getMessage());
            return false;
        }
    }
}
