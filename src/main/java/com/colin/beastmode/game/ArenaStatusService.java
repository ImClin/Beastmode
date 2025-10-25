package com.colin.beastmode.game;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Broadcasts arena status changes to registered listeners.
 */
final class ArenaStatusService {

    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    void register(Consumer<String> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    void unregister(Consumer<String> listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    void notifyArenaStatus(ActiveArena activeArena) {
        if (activeArena == null || activeArena.getArena() == null) {
            return;
        }
        notifyArenaName(activeArena.getArena().getName());
    }

    void notifyArenaName(String arenaName) {
        String trimmed = trim(arenaName);
        if (trimmed.isEmpty()) {
            return;
        }
        for (Consumer<String> listener : listeners) {
            listener.accept(trimmed);
        }
    }

    private String trim(String arenaName) {
        if (arenaName == null) {
            return "";
        }
        return arenaName.trim();
    }
}
