package dev.bennethogan.universalkeyboard.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

// server-side lease on Supplementaries cannons so when remote controlling is active, block is still
// considered "in-use"
public final class CannonLeases {

    private CannonLeases() {}

    private record Lease(UUID owner, long expireTick) {}

    private static final Map<GlobalPos, Lease> LEASES = new HashMap<>();
    private static final long TIMEOUT_TICKS = 60; // auto-release after 3s timeout

    // attempts to claim or renew ownership of a cannon at (pos) with (operater). True if safe to drive, false if not
    public static synchronized boolean claim(ServerLevel level, BlockPos pos, ServerPlayer operator) {
        if (level == null || operator == null || !CannonControl.isCannon(level, pos)) return false;
        UUID op = operator.getUUID();
        UUID cur = CannonControl.getCurrentUser(level, pos);
        if (cur != null && !cur.equals(op)) return false;
        CannonControl.setCurrentUser(level, pos, op);   // claim or renew
        LEASES.put(GlobalPos.of(level.dimension(), pos.immutable()),
                new Lease(op, level.getGameTime() + TIMEOUT_TICKS));
        return true;
    }

    // release one cannon held by (owner) at (pos)
    public static synchronized void release(ServerLevel level, BlockPos pos, UUID owner) {
        if (level == null) return;
        GlobalPos key = GlobalPos.of(level.dimension(), pos.immutable());
        Lease l = LEASES.remove(key);
        if (l == null || !l.owner().equals(owner)) { if (l != null) LEASES.put(key, l); return; }
        clearIfOurs(level, pos, owner);
    }

    public static synchronized void releaseAll(ServerLevel level, UUID owner, Iterable<BlockPos> cannons) {
        for (BlockPos p : cannons) release(level, p, owner);
    }

    // Lifecycle hooks

    public static void onServerTick(ServerTickEvent.Post event) { sweep(event.getServer()); }

    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && sp.getServer() != null)
            releaseOwner(sp.getServer(), sp.getUUID());
    }

    private static synchronized void sweep(MinecraftServer server) {
        if (LEASES.isEmpty() || server == null) return;
        Iterator<Map.Entry<GlobalPos, Lease>> it = LEASES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<GlobalPos, Lease> e = it.next();
            ServerLevel lvl = server.getLevel(e.getKey().dimension());
            if (lvl == null) { it.remove(); continue; }               // dimension gone
            if (lvl.getGameTime() > e.getValue().expireTick()) {
                clearIfOurs(lvl, e.getKey().pos(), e.getValue().owner());
                it.remove();
            }
        }
    }

    private static synchronized void releaseOwner(MinecraftServer server, UUID owner) {
        if (LEASES.isEmpty() || server == null) return;
        Iterator<Map.Entry<GlobalPos, Lease>> it = LEASES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<GlobalPos, Lease> e = it.next();
            if (!e.getValue().owner().equals(owner)) continue;
            ServerLevel lvl = server.getLevel(e.getKey().dimension());
            if (lvl != null) clearIfOurs(lvl, e.getKey().pos(), owner);
            it.remove();
        }
    }

    private static void clearIfOurs(ServerLevel level, BlockPos pos, UUID owner) {
        UUID cur = CannonControl.getCurrentUser(level, pos);
        if (cur == null || cur.equals(owner)) CannonControl.setCurrentUser(level, pos, null);
    }
}
