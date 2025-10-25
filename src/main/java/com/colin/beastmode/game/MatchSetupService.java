package com.colin.beastmode.game;

import org.bukkit.entity.Player;
import com.colin.beastmode.model.ArenaDefinition;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class MatchSetupService {

    private final PlayerSupportService playerSupport;
    private final String prefix;

    MatchSetupService(PlayerSupportService playerSupport, String prefix) {
        this.playerSupport = playerSupport;
        this.prefix = prefix;
    }

    List<Player> resolveParticipants(ActiveArena activeArena, List<Player> provided) {
        if (provided != null && !provided.isEmpty()) {
            return List.copyOf(provided);
        }
        if (activeArena == null) {
            return List.of();
        }
        return activeArena.getPlayerIds().stream()
                .map(Bukkit::getPlayer)
                .filter(player -> player != null && player.isOnline())
                .toList();
    }

    boolean validateSpawns(List<Player> players, ArenaDefinition arena) {
        if (arena != null && arena.getRunnerSpawn() != null && arena.getBeastSpawn() != null) {
            return true;
        }
        if (players != null) {
            for (Player player : players) {
                if (player != null) {
                    player.sendMessage(prefix + ChatColor.RED + "Arena spawns are not configured.");
                }
            }
        }
        return false;
    }

    void assignRoles(ActiveArena activeArena, List<Player> players, Player beast) {
        if (activeArena == null) {
            return;
        }

        activeArena.setSelecting(false);
        activeArena.setBeastId(beast != null ? beast.getUniqueId() : null);

        Set<UUID> runners = new HashSet<>();
        if (players != null) {
            for (Player player : players) {
                if (player == null) {
                    continue;
                }
                if (beast != null && beast.equals(player)) {
                    continue;
                }
                runners.add(player.getUniqueId());
            }
            playerSupport.removeExitTokens(players);
        }

        activeArena.setRunners(runners);
    }
}
