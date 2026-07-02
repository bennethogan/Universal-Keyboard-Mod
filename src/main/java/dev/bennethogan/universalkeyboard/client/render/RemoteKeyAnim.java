package dev.bennethogan.universalkeyboard.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

//client receiving for other players' keypress animations cause its lame to not be able to see it synced
public final class RemoteKeyAnim {

    private RemoteKeyAnim() {}

    private static final long IDLE_EXPIRE_TICKS = 6000; // drop in case idle

    private static final class State {
        final Set<Integer> held = new HashSet<>();
        final KeyRipples ripples = new KeyRipples();
        long lastEvent;
    }

    private static final Map<BlockPos, State> BY_POS = new HashMap<>();

    public static void handle(BlockPos pos, int key, boolean press) {
        long now = gameTime();
        purge(now);
        State s = BY_POS.computeIfAbsent(pos.immutable(), p -> new State());
        s.lastEvent = now;
        if (press) {
            s.held.add(key);
            s.ripples.spawnAt(key);
        } else {
            s.held.remove(key);
        }
    }

    public static boolean[] texelGrid(BlockPos pos) {
        State s = BY_POS.get(pos);
        return s == null ? null : PressedKeys.texelGrid(s.held);
    }

    public static KeyRipples ripples(BlockPos pos) {
        State s = BY_POS.get(pos);
        return s == null ? null : s.ripples;
    }

    public static void clear() {
        BY_POS.clear();
    }

    private static void purge(long now) {
        for (Iterator<State> it = BY_POS.values().iterator(); it.hasNext(); ) {
            if (now - it.next().lastEvent > IDLE_EXPIRE_TICKS) it.remove();
        }
    }

    private static long gameTime() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }
}
