package com.colin.beastmode.game;

import com.colin.beastmode.model.Cuboid;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Detects match completion triggers such as finish regions or buttons.
 */
final class MatchCompletionService {

    private final Map<String, ActiveArena> activeArenas;
    private final Function<UUID, String> arenaLookup;
    private final ArenaDepartureService departures;

    MatchCompletionService(Map<String, ActiveArena> activeArenas,
                           Function<UUID, String> arenaLookup,
                           ArenaDepartureService departures) {
        this.activeArenas = activeArenas;
        this.arenaLookup = arenaLookup;
        this.departures = departures;
    }

    void handlePlayerMove(Player player, Location from, Location to) {
        if (player == null || to == null) {
            return;
        }
        if (from != null
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        String key = arenaLookup.apply(player.getUniqueId());
        if (key == null) {
            return;
        }

        ActiveArena activeArena = activeArenas.get(key);
        if (activeArena == null || !activeArena.isMatchActive()) {
            return;
        }

        Location finishButton = activeArena.getArena().getFinishButton();
        if (finishButton != null) {
            return;
        }

        Cuboid finishRegion = activeArena.getArena().getFinishRegion();
        if (finishRegion == null) {
            return;
        }

        if (from != null && finishRegion.contains(from)) {
            return;
        }

        if (!finishRegion.contains(to)) {
            return;
        }

        if (!activeArena.isRunner(player.getUniqueId())) {
            return;
        }

        departures.handleRunnerVictory(key, activeArena, player);
    }

    void handlePlayerInteract(Player player, Block block) {
        if (player == null || block == null) {
            return;
        }

        String key = arenaLookup.apply(player.getUniqueId());
        if (key == null) {
            return;
        }

        ActiveArena activeArena = activeArenas.get(key);
        if (activeArena == null || !activeArena.isMatchActive()) {
            return;
        }

        Location finishButton = activeArena.getArena().getFinishButton();
        if (finishButton == null) {
            return;
        }

        if (!isSameBlock(block.getLocation(), finishButton)) {
            return;
        }

        if (!activeArena.isRunner(player.getUniqueId())) {
            return;
        }

        departures.handleRunnerVictory(key, activeArena, player);
    }

    private boolean isSameBlock(Location first, Location second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.getWorld() == null || second.getWorld() == null) {
            return false;
        }
        if (!first.getWorld().equals(second.getWorld())) {
            return false;
        }
        return first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }
}
