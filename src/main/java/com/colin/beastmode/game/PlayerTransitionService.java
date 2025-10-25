package com.colin.beastmode.game;

import com.colin.beastmode.Beastmode;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Handles respawns and spectator transitions for arena participants.
 */
final class PlayerTransitionService {

    private final Beastmode plugin;
    private final PlayerSupportService playerSupport;

    PlayerTransitionService(Beastmode plugin, PlayerSupportService playerSupport) {
        this.plugin = plugin;
        this.playerSupport = playerSupport;
    }

    void sendPlayerToSpawn(ActiveArena activeArena, Player player) {
        if (player == null) {
            return;
        }

        if (activeArena != null) {
            activeArena.removeSpectatingRunner(player.getUniqueId());
        }

        playerSupport.removeExitToken(player);
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);

        forceRespawn(player, () -> {
            player.setGameMode(GameMode.ADVENTURE);
            playerSupport.restoreVitals(player);
            Location spawn = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
            if (spawn != null) {
                player.teleport(spawn.clone());
            }
        });
    }

    void sendToSpectator(ActiveArena activeArena, Player player, Location location) {
        if (player == null) {
            return;
        }

        if (activeArena != null) {
            activeArena.addSpectatingRunner(player.getUniqueId());
        }

        Location destination = location != null ? location.clone() : null;
        forceRespawn(player, () -> {
            if (destination != null) {
                player.teleport(destination);
            }
            playerSupport.removeExitToken(player);
            player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
            applySpectatorState(activeArena, player);
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> applySpectatorState(activeArena, player),
                    2L);
        });
    }

    private void applySpectatorState(ActiveArena activeArena, Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (activeArena != null && (!activeArena.isMatchActive()
                || !activeArena.isSpectatingRunner(player.getUniqueId()))) {
            return;
        }
        player.setGameMode(GameMode.SPECTATOR);
        playerSupport.restoreVitals(player);
    }

    private void forceRespawn(Player player, Runnable afterRespawn) {
        if (player == null) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            if (player.isDead()) {
                try {
                    player.spigot().respawn();
                } catch (Exception ignored) {
                    // Not every implementation supports force respawn.
                }
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (afterRespawn != null) {
                    afterRespawn.run();
                }
            }, 1L);
        }, 1L);
    }
}
