package com.colin.beastmode.game;

import com.colin.beastmode.model.ArenaDefinition;
import com.colin.beastmode.storage.ArenaStorage;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Coordinates arena lifecycle commands and match preparation.
 */
final class MatchOrchestrationService {

    private static final String MSG_ARENA_NOT_RUNNING = "Arena %s is not currently running.";

    private final ActiveArenaDirectory arenaDirectory;
    private final ArenaStorage arenaStorage;
    private final ArenaLifecycleService arenaLifecycle;
    private final ArenaWaitingService waitingService;
    private final MatchSelectionService selectionService;
    private final ArenaDepartureService departureService;
    private final ArenaStatusService statusService;
    private final String prefix;

    MatchOrchestrationService(ActiveArenaDirectory arenaDirectory,
                              ArenaStorage arenaStorage,
                              ArenaLifecycleService arenaLifecycle,
                              ArenaWaitingService waitingService,
                              MatchSelectionService selectionService,
                              ArenaDepartureService departureService,
                              ArenaStatusService statusService,
                              String prefix) {
        this.arenaDirectory = arenaDirectory;
        this.arenaStorage = arenaStorage;
        this.arenaLifecycle = arenaLifecycle;
        this.waitingService = waitingService;
        this.selectionService = selectionService;
        this.departureService = departureService;
        this.statusService = statusService;
        this.prefix = prefix;
    }

    void startMatch(String key, ActiveArena activeArena) {
        if (activeArena == null) {
            return;
        }
        if (activeArena.isRunning() || activeArena.isSelecting() || activeArena.isMatchActive()) {
            return;
        }

        List<Player> participants = arenaLifecycle.collectParticipants(activeArena);
        if (participants.isEmpty()) {
            arenaLifecycle.cleanupArena(key, activeArena);
            return;
        }

        activeArena.setRunning(true);
        activeArena.clearMatchState();
        activeArena.enableDamageProtection();
        statusService.notifyArenaStatus(activeArena);
        if (activeArena.isRunnerWallOpened() || activeArena.isBeastWallOpened()) {
            arenaLifecycle.resetArenaState(activeArena);
        }

        ArenaDefinition arena = activeArena.getArena();
        if (!waitingService.sendPlayersToWaiting(arena, participants, activeArena.getMode())) {
            activeArena.setRunning(false);
            arenaLifecycle.cleanupArena(key, activeArena);
            return;
        }

        selectionService.maybeStartSelection(key, activeArena);
    }

    void maybeStartCountdown(String key, ActiveArena activeArena) {
        selectionService.maybeStartSelection(key, activeArena);
    }

    void cancelArena(Player player, String arenaName) {
        if (arenaName == null || arenaName.trim().isEmpty()) {
            send(player, ChatColor.RED + "Please specify an arena name.");
            return;
        }

        String trimmed = arenaName.trim();
        String key = trimmed.toLowerCase(Locale.ENGLISH);
        ActiveArena activeArena = arenaDirectory.get(key);
        if (activeArena == null) {
            send(player, ChatColor.YELLOW + MSG_ARENA_NOT_RUNNING.formatted(highlight(trimmed)));
            return;
        }

        List<Player> participants = arenaLifecycle.collectParticipants(activeArena);
        for (Player participant : participants) {
            send(participant, ChatColor.YELLOW + "The hunt was cancelled by " + player.getName() + ".");
        }

        arenaLifecycle.cleanupArena(key, activeArena);
        send(player, ChatColor.GREEN + "Cancelled hunt for arena " + highlight(activeArena.getArena().getName()) + ChatColor.GREEN + ".");
    }

    boolean hasActiveArena(String arenaName) {
        if (arenaName == null || arenaName.trim().isEmpty()) {
            return false;
        }
        String key = arenaName.trim().toLowerCase(Locale.ENGLISH);
        return arenaDirectory.contains(key);
    }

    void shutdown() {
        for (ActiveArena activeArena : arenaDirectory.values()) {
            activeArena.cancelTasks();
            arenaLifecycle.resetArenaState(activeArena);
            activeArena.clearPlayers();
            activeArena.setRunning(false);
        }
        arenaDirectory.clear();
        departureService.shutdown();
    }

    ArenaStatus getArenaStatus(String arenaName) {
        return getArenaStatus(arenaName, null);
    }

    ArenaStatus getArenaStatus(String arenaName, GameModeType desiredMode) {
        if (arenaName == null || arenaName.trim().isEmpty()) {
            return ArenaStatus.unavailable("");
        }

        ArenaDefinition arena = arenaStorage.getArena(arenaName);
        if (arena == null) {
            return ArenaStatus.unavailable(arenaName.trim());
        }

        String key = arena.getName().toLowerCase(Locale.ENGLISH);
        ActiveArena activeArena = arenaDirectory.get(key);

        int playerCount = 0;
        boolean running = false;
        boolean selecting = false;
        boolean matchActive = false;
        GameModeType activeMode = GameModeType.HUNT;

        if (activeArena != null) {
            playerCount = arenaLifecycle.countActivePlayers(activeArena);
            running = activeArena.isRunning();
            selecting = activeArena.isSelecting();
            matchActive = activeArena.isMatchActive();
            activeMode = activeArena.getMode();
        }

        GameModeType referenceMode = desiredMode != null ? desiredMode : activeMode;
        int capacity = waitingService.getQueueLimit(arena, referenceMode);
        if (capacity == Integer.MAX_VALUE) {
            capacity = -1;
        }

        return new ArenaStatus(arena.getName(), true, arena.isComplete(), playerCount, capacity,
                running, selecting, matchActive, activeMode);
    }

    void notifyArenaStatus(ActiveArena activeArena) {
        statusService.notifyArenaStatus(activeArena);
    }

    private String highlight(String arenaName) {
        return ChatColor.AQUA + arenaName + ChatColor.RESET;
    }

    private void send(Player player, String message) {
        if (player != null) {
            player.sendMessage(prefix + message);
        }
    }
}
