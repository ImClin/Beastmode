package com.colin.beastmode.game;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Handles players leaving arenas, whether by quitting, command, or reconnection.
 */
final class ArenaDepartureService {

    private final String prefix;
    private final PlayerSupportService playerSupport;
    private final PlayerTransitionService transitions;
    private final ArenaWaitingService waitingService;
    private final ArenaLifecycleService lifecycle;
    private final MatchOutcomeService matchOutcome;
    private final Consumer<ActiveArena> statusNotifier;
    private final Set<UUID> pendingSpawnTeleports = ConcurrentHashMap.newKeySet();

    ArenaDepartureService(String prefix,
                          PlayerSupportService playerSupport,
                          PlayerTransitionService transitions,
                          ArenaWaitingService waitingService,
                          ArenaLifecycleService lifecycle,
                          MatchOutcomeService matchOutcome,
                          Consumer<ActiveArena> statusNotifier) {
        this.prefix = prefix;
        this.playerSupport = playerSupport;
        this.transitions = transitions;
        this.waitingService = waitingService;
        this.lifecycle = lifecycle;
        this.matchOutcome = matchOutcome;
        this.statusNotifier = statusNotifier;
    }

    void handlePlayerQuit(String key, ActiveArena activeArena, Player player) {
        if (activeArena == null || player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        boolean wasRunner = activeArena.isRunner(uuid);
        boolean wasBeast = activeArena.getBeastId() != null && activeArena.getBeastId().equals(uuid);

        playerSupport.resetLoadout(player);
        playerSupport.restoreVitals(player);
        player.setGameMode(GameMode.ADVENTURE);

        if (activeArena.isTimeTrial()) {
            playerSupport.revealTimeTrialParticipant(player);
        }
        activeArena.removePlayer(uuid);
        pendingSpawnTeleports.add(uuid);
        playerSupport.removeExitToken(player);
        notifyStatus(activeArena);

        if (!activeArena.isMatchActive()) {
            handleIdleDeparture(key, activeArena);
            return;
        }

        if (wasRunner) {
            handleRunnerElimination(key, activeArena, null);
            return;
        }

        if (wasBeast) {
            activeArena.setRewardSuppressed(true);
            handleRunnerVictory(key, activeArena, null);
        }
    }

    boolean handleSpawnCommand(String key, ActiveArena activeArena, Player player) {
        if (activeArena == null || player == null) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        boolean wasRunner = activeArena.isRunner(uuid);
        boolean wasBeast = activeArena.getBeastId() != null && activeArena.getBeastId().equals(uuid);

        playerSupport.resetLoadout(player);
        player.setGameMode(GameMode.ADVENTURE);
        if (activeArena.isTimeTrial()) {
            playerSupport.revealTimeTrialParticipant(player);
        }
        activeArena.removePlayer(uuid);
        playerSupport.removeExitToken(player);
        notifyStatus(activeArena);

        if (!activeArena.isMatchActive()) {
            handleIdleDeparture(key, activeArena);
            transitions.sendPlayerToSpawn(activeArena, player);
            send(player, ChatColor.YELLOW + "You left the hunt.");
            return true;
        }

        if (wasRunner) {
            handleRunnerElimination(key, activeArena, null);
        } else if (wasBeast) {
            activeArena.setRewardSuppressed(true);
            handleRunnerVictory(key, activeArena, null);
        }

        transitions.sendPlayerToSpawn(activeArena, player);
        send(player, ChatColor.YELLOW + "You left the hunt.");
        return true;
    }

    void handlePlayerJoin(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!pendingSpawnTeleports.remove(uuid)) {
            return;
        }
        transitions.sendPlayerToSpawn(null, player);
    }

    void shutdown() {
        pendingSpawnTeleports.clear();
    }

    boolean handleRunnerElimination(String key, ActiveArena activeArena, Player eliminated) {
        return matchOutcome.handleRunnerElimination(activeArena, eliminated,
                () -> lifecycle.collectParticipants(activeArena),
                () -> notifyStatus(activeArena),
                () -> lifecycle.cleanupArena(key, activeArena));
    }

    void handleRunnerVictory(String key, ActiveArena activeArena, Player finisher) {
        matchOutcome.handleRunnerVictory(activeArena, finisher,
                () -> lifecycle.collectParticipants(activeArena),
                () -> notifyStatus(activeArena),
                () -> lifecycle.cleanupArena(key, activeArena));
    }

    private void handleIdleDeparture(String key, ActiveArena activeArena) {
        List<Player> remaining = lifecycle.collectParticipants(activeArena);
        if (remaining.isEmpty()) {
            lifecycle.cleanupArena(key, activeArena);
            return;
        }
        int missing = waitingService.getMissingParticipantsCount(remaining.size(), activeArena.getArena());
        if (missing > 0 && activeArena.isSelecting()) {
            activeArena.setSelecting(false);
            notifyStatus(activeArena);
            waitingService.notifyWaitingForPlayers(activeArena, remaining);
        }
    }

    private void notifyStatus(ActiveArena activeArena) {
        if (statusNotifier != null && activeArena != null) {
            statusNotifier.accept(activeArena);
        }
    }

    private void send(Player player, String message) {
        if (player != null) {
            player.sendMessage(prefix + message);
        }
    }
}
