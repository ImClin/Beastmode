package com.colin.beastmode.game;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Coordinates player death handling for active matches.
 */
final class MatchEliminationService {

    private final ActiveArenaDirectory arenaDirectory;
    private final PlayerSupportService playerSupport;
    private final PlayerTransitionService transitions;
    private final ArenaDepartureService departures;

    MatchEliminationService(ActiveArenaDirectory arenaDirectory,
                            PlayerSupportService playerSupport,
                            PlayerTransitionService transitions,
                            ArenaDepartureService departures) {
        this.arenaDirectory = arenaDirectory;
        this.playerSupport = playerSupport;
        this.transitions = transitions;
        this.departures = departures;
    }

    void handlePlayerDeath(Player player) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        String key = arenaDirectory.findArenaByPlayer(uuid);
        if (key == null) {
            return;
        }

        ActiveArena activeArena = arenaDirectory.get(key);
        if (activeArena == null) {
            return;
        }

        if (!activeArena.isMatchActive()) {
            transitions.sendPlayerToSpawn(activeArena, player);
            return;
        }

        Location deathLocation = player.getLocation() != null ? player.getLocation().clone() : null;
        if (activeArena.isRunner(uuid)) {
            handleRunnerDeath(key, activeArena, player, deathLocation);
            return;
        }

        handleBeastDeath(key, activeArena, player, uuid);
    }

    private void handleRunnerDeath(String key,
                                   ActiveArena activeArena,
                                   Player player,
                                   Location deathLocation) {
        UUID uuid = player.getUniqueId();
        if (!activeArena.removeRunner(uuid)) {
            return;
        }

        playerSupport.resetLoadout(player);
        boolean matchFinished = departures.handleRunnerElimination(key, activeArena, player);
        if (!matchFinished) {
            transitions.sendToSpectator(activeArena, player, deathLocation);
        }
    }

    private void handleBeastDeath(String key,
                                  ActiveArena activeArena,
                                  Player beast,
                                  UUID uuid) {
        UUID beastId = activeArena.getBeastId();
        if (beastId == null || !beastId.equals(uuid)) {
            return;
        }

        playerSupport.resetLoadout(beast);
        if (!activeArena.isFinalPhase()) {
            activeArena.setRewardSuppressed(true);
        }
        departures.handleRunnerVictory(key, activeArena, null);
    }
}
