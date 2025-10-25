package com.colin.beastmode.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Maintains active arena rosters and handles cleanup utilities.
 */
final class ArenaLifecycleService {

    private final Map<String, ActiveArena> activeArenas;
    private final ArenaBarrierService barrierService;
    private final Consumer<String> statusNotifier;

    ArenaLifecycleService(Map<String, ActiveArena> activeArenas,
                          ArenaBarrierService barrierService,
                          Consumer<String> statusNotifier) {
        this.activeArenas = activeArenas;
        this.barrierService = barrierService;
        this.statusNotifier = statusNotifier;
    }

    List<Player> collectParticipants(ActiveArena activeArena) {
        List<Player> participants = new ArrayList<>();
        if (activeArena == null) {
            return participants;
        }

        Iterator<UUID> iterator = activeArena.getPlayerIds().iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                iterator.remove();
                activeArena.removePreference(id);
                activeArena.removeRunner(id);
                continue;
            }
            participants.add(player);
        }
        return participants;
    }

    int countActivePlayers(ActiveArena activeArena) {
        if (activeArena == null) {
            return 0;
        }
        int count = 0;
        for (UUID id : activeArena.getPlayerIds()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                count++;
            }
        }
        return count;
    }

    void cleanupArena(String key, ActiveArena activeArena) {
        cleanupArena(key, activeArena, true);
    }

    void cleanupArena(String key, ActiveArena activeArena, boolean restoreWalls) {
        String arenaName = null;
        if (activeArena != null && activeArena.getArena() != null) {
            arenaName = activeArena.getArena().getName();
        } else if (key != null) {
            arenaName = key;
        }

        if (activeArena == null) {
            notifyStatus(arenaName);
            if (key != null) {
                activeArenas.remove(key);
            }
            return;
        }

        activeArena.cancelTasks();
        clearBeastEffects(activeArena);
        if (restoreWalls) {
            resetArenaState(activeArena);
            activeArena.clearPlayers();
            activeArena.setRunning(false);
            activeArenas.remove(key);
            notifyStatus(arenaName);
            return;
        }

        activeArena.clearPlayers();
        activeArena.setRunning(false);
        notifyStatus(arenaName);
    }

    void resetArenaState(ActiveArena activeArena) {
        if (activeArena == null) {
            return;
        }
        if (activeArena.getRunnerWallSnapshot() != null) {
            barrierService.restore(activeArena.getRunnerWallSnapshot());
            activeArena.setRunnerWallSnapshot(null);
        }
        if (activeArena.getBeastWallSnapshot() != null) {
            barrierService.restore(activeArena.getBeastWallSnapshot());
            activeArena.setBeastWallSnapshot(null);
        }
        activeArena.setRunnerWallOpened(false);
        activeArena.setBeastWallOpened(false);
    }

    private void clearBeastEffects(ActiveArena activeArena) {
        if (activeArena == null) {
            return;
        }
        UUID beastId = activeArena.getBeastId();
        if (beastId == null) {
            return;
        }

        Player beast = Bukkit.getPlayer(beastId);
        if (beast != null) {
            beast.removePotionEffect(PotionEffectType.SPEED);
            beast.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        }
    }

    private void notifyStatus(String arenaName) {
        if (statusNotifier != null) {
            statusNotifier.accept(arenaName);
        }
    }
}
