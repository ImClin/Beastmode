package com.colin.beastmode.game;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Provides shared access to active arena instances and lookup utilities.
 */
final class ActiveArenaDirectory {

    private final ConcurrentMap<String, ActiveArena> arenas;

    ActiveArenaDirectory(ConcurrentMap<String, ActiveArena> arenas) {
        this.arenas = arenas;
    }

    ActiveArena get(String key) {
        return key != null ? arenas.get(key) : null;
    }

    ActiveArena computeIfAbsent(String key, Supplier<ActiveArena> supplier) {
        if (key == null || supplier == null) {
            return null;
        }
        return arenas.computeIfAbsent(key, k -> supplier.get());
    }

    boolean contains(String key) {
        return key != null && arenas.containsKey(key);
    }

    void remove(String key) {
        if (key != null) {
            arenas.remove(key);
        }
    }

    void clear() {
        arenas.clear();
    }

    Collection<ActiveArena> values() {
        return arenas.values();
    }

    String findArenaByPlayer(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (Map.Entry<String, ActiveArena> entry : arenas.entrySet()) {
            ActiveArena arena = entry.getValue();
            if (arena != null && arena.contains(uuid)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
