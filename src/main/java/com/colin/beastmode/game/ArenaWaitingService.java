package com.colin.beastmode.game;

import com.colin.beastmode.model.ArenaDefinition;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Handles waiting-room teleports and messaging for arena queues.
 */
final class ArenaWaitingService {

    private final PlayerSupportService playerSupport;
    private final String prefix;

    ArenaWaitingService(PlayerSupportService playerSupport, String prefix) {
        this.playerSupport = playerSupport;
        this.prefix = prefix;
    }

    boolean sendPlayersToWaiting(ArenaDefinition arena, List<Player> participants, GameModeType mode) {
        Location waitingPoint = arena.getWaitingSpawn();
        if (waitingPoint == null) {
            waitingPoint = arena.getRunnerSpawn();
        }
        if (waitingPoint == null) {
            for (Player player : participants) {
                send(player, ChatColor.RED + "Arena spawns are not configured.");
            }
            return false;
        }

        if (!mode.isTimeTrial() && arena.getBeastSpawn() == null) {
            for (Player player : participants) {
                send(player, ChatColor.RED + "Beast spawn is not configured.");
            }
            return false;
        }

        boolean success = true;
        for (Player participant : participants) {
            if (!teleportToWaiting(arena, participant)) {
                success = false;
            }
        }
        return success;
    }

    boolean teleportToWaiting(ArenaDefinition arena, Player participant) {
        if (participant == null || !participant.isOnline()) {
            return false;
        }

        Location target = arena.getWaitingSpawn();
        if (target == null) {
            target = arena.getRunnerSpawn();
        }

        if (target == null) {
            return false;
        }

        participant.teleport(target.clone());
        participant.setGameMode(GameMode.ADVENTURE);
        playerSupport.restoreVitals(participant);
        participant.sendTitle(ChatColor.GOLD + "Preparing...", "", 10, 40, 10);
        return true;
    }

    void notifyWaitingForPlayers(ActiveArena activeArena, List<Player> participants) {
        if (participants.isEmpty() || activeArena == null) {
            return;
        }
        ArenaDefinition arena = activeArena.getArena();
        GameModeType mode = activeArena.getMode();
        int missing = getMissingParticipantsCount(participants.size(), arena, mode);
        if (missing <= 0) {
            return;
        }
        if (mode.isTimeTrial()) {
            for (Player player : participants) {
                send(player, ChatColor.YELLOW + "Waiting for one runner to begin the time trial.");
                player.sendTitle(ChatColor.YELLOW + "" + ChatColor.BOLD + "Waiting...",
                        ChatColor.GRAY + "Need a runner to start.", 10, 40, 10);
            }
            return;
        }

        int minRunners = Math.max(arena != null ? arena.getMinRunners() : 1, 1);
        String missingText = formatPlayerCount(missing);
        String runnerText = formatRunnerCount(minRunners);
        for (Player player : participants) {
            send(player, ChatColor.YELLOW + "Waiting for " + ChatColor.AQUA + missingText + ChatColor.YELLOW
                    + " (need at least " + ChatColor.AQUA + runnerText + ChatColor.YELLOW + " plus the Beast).");
            player.sendTitle(ChatColor.YELLOW + "" + ChatColor.BOLD + "Waiting...",
                    ChatColor.GRAY + "Need " + missingText + " to begin.", 10, 40, 10);
        }
    }

    int getQueueLimit(ArenaDefinition arena) {
        return getQueueLimit(arena, GameModeType.HUNT);
    }

    int getQueueLimit(ArenaDefinition arena, GameModeType mode) {
        if (mode == GameModeType.TIME_TRIAL) {
            if (arena == null) {
                return Integer.MAX_VALUE;
            }
            int maxRunners = arena.getMaxRunners();
            if (maxRunners <= 0) {
                return Integer.MAX_VALUE;
            }
            return Math.max(maxRunners, 1);
        }
        if (arena == null) {
            return Integer.MAX_VALUE;
        }
        int maxRunners = arena.getMaxRunners();
        if (maxRunners <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(maxRunners + 1, 1);
    }

    int getRequiredParticipants(ArenaDefinition arena) {
        return getRequiredParticipants(arena, GameModeType.HUNT);
    }

    int getRequiredParticipants(ArenaDefinition arena, GameModeType mode) {
        if (mode == GameModeType.TIME_TRIAL) {
            return 1;
        }
        if (arena == null) {
            return 2;
        }
        int minRunners = Math.max(arena.getMinRunners(), 1);
        return Math.max(minRunners + 1, 2);
    }

    int getMissingParticipantsCount(int participantCount, ArenaDefinition arena) {
        return getMissingParticipantsCount(participantCount, arena, GameModeType.HUNT);
    }

    int getMissingParticipantsCount(int participantCount, ArenaDefinition arena, GameModeType mode) {
        int required = getRequiredParticipants(arena, mode);
        return Math.max(0, required - participantCount);
    }

    String formatRunnerCount(int count) {
        return count + " runner" + (count == 1 ? "" : "s");
    }

    private String formatPlayerCount(int count) {
        return count + " more player" + (count == 1 ? "" : "s");
    }

    private void send(Player player, String message) {
        player.sendMessage(prefix + message);
    }
}
