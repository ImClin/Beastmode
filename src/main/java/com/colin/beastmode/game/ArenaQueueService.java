package com.colin.beastmode.game;

import com.colin.beastmode.model.ArenaDefinition;
import com.colin.beastmode.storage.ArenaStorage;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

/**
 * Coordinates queue enrolment and waiting-room preparation for arenas.
 */
final class ArenaQueueService {

    private final ArenaStorage arenaStorage;
    private final Map<String, ActiveArena> activeArenas;
    private final PlayerSupportService playerSupport;
    private final RoleSelectionService roleSelection;
    private final ArenaWaitingService waitingService;
    private final String prefix;

    ArenaQueueService(ArenaStorage arenaStorage,
                      Map<String, ActiveArena> activeArenas,
                      PlayerSupportService playerSupport,
                      RoleSelectionService roleSelection,
                      ArenaWaitingService waitingService,
                      String prefix) {
        this.arenaStorage = arenaStorage;
        this.activeArenas = activeArenas;
        this.playerSupport = playerSupport;
        this.roleSelection = roleSelection;
        this.waitingService = waitingService;
        this.prefix = prefix;
    }

    void join(GameManager manager,
              Player player,
              String arenaName,
              GameManager.RolePreference desiredPreference) {
        if (player == null) {
            return;
        }

        if (arenaName == null || arenaName.trim().isEmpty()) {
            send(player, ChatColor.RED + "Please specify an arena name.");
            return;
        }

        ArenaDefinition arena = arenaStorage.getArena(arenaName.trim());
        if (arena == null) {
            send(player, ChatColor.RED + GameManager.MSG_ARENA_NOT_FOUND.formatted(highlight(arenaName)));
            return;
        }
        if (!arena.isComplete()) {
            send(player, ChatColor.RED + GameManager.MSG_ARENA_INCOMPLETE.formatted(highlight(arena.getName())));
            return;
        }
        if (arena.getRunnerSpawn() == null || arena.getBeastSpawn() == null) {
            send(player, ChatColor.RED + "Arena spawns are missing. Reconfigure the arena before joining.");
            return;
        }

        if (manager.findArenaByPlayer(player.getUniqueId()) != null) {
            send(player, ChatColor.RED + "You are already queued for an arena.");
            return;
        }

        GameManager.RolePreference preference = sanitizePreference(player, desiredPreference);

        String key = arena.getName().toLowerCase(Locale.ENGLISH);
        ActiveArena activeArena = activeArenas.computeIfAbsent(key, k -> new ActiveArena(arena));
        if (activeArena.isMatchActive()) {
            send(player, ChatColor.RED + "That arena is already in a hunt. Try again in a moment.");
            return;
        }

    if (isQueueFull(arena, activeArena)) {
            int maxRunners = arena.getMaxRunners();
            send(player, ChatColor.RED + "That arena already has the maximum of "
            + ChatColor.AQUA + waitingService.formatRunnerCount(maxRunners) + ChatColor.RED
                    + " (plus the Beast). Try again later.");
            return;
        }

        boolean added = activeArena.addPlayer(player);
        activeArena.setPreference(player.getUniqueId(), preference);
        if (!added) {
            handleExistingParticipant(player, activeArena, preference);
            manager.notifyArenaStatus(activeArena);
            return;
        }

        prepareWaitingLoadout(player, activeArena);

        send(player, ChatColor.GREEN + "Joined arena " + ChatColor.AQUA + arena.getName() + ChatColor.GREEN + ".");
        if (preference != GameManager.RolePreference.ANY) {
            send(player, ChatColor.GOLD + "Preference set to " + formatPreference(preference) + ChatColor.GOLD + ".");
        }

        if (activeArena.isRunning()) {
            if (!waitingService.teleportToWaiting(arena, player)) {
                send(player, ChatColor.RED + "Waiting spawn is not configured correctly. Please notify an admin.");
            } else {
                send(player, ChatColor.YELLOW + "You slipped in before the gates drop. Hold tight!");
            }
            manager.maybeStartCountdown(key, activeArena);
            manager.notifyArenaStatus(activeArena);
            return;
        }

        manager.startMatch(key, activeArena);
        manager.notifyArenaStatus(activeArena);
    }

    private GameManager.RolePreference sanitizePreference(Player player, GameManager.RolePreference preference) {
        if (preference == null || preference == GameManager.RolePreference.ANY) {
            return GameManager.RolePreference.ANY;
        }
        return roleSelection.canChoosePreference(player) ? preference : GameManager.RolePreference.ANY;
    }

    private boolean isQueueFull(ArenaDefinition arena, ActiveArena activeArena) {
        int limit = waitingService.getQueueLimit(arena);
        if (limit == Integer.MAX_VALUE) {
            return false;
        }
        return activeArena.getPlayerIds().size() >= limit;
    }

    private void handleExistingParticipant(Player player,
                                           ActiveArena activeArena,
                                           GameManager.RolePreference preference) {
        if (preference != GameManager.RolePreference.ANY) {
            send(player, ChatColor.YELLOW + "Updated your preference to " + formatPreference(preference) + ChatColor.YELLOW + ".");
        } else {
            send(player, ChatColor.YELLOW + "You are already in the queue for this arena.");
        }
        if (roleSelection.canChoosePreference(player)) {
            playerSupport.givePreferenceSelectors(player, activeArena.getPreference(player.getUniqueId()));
        } else {
            playerSupport.clearPreferenceSelectors(player);
        }
    }

    private void prepareWaitingLoadout(Player player, ActiveArena activeArena) {
        playerSupport.resetLoadout(player);
        if (!activeArena.isMatchActive()) {
            playerSupport.giveExitToken(player);
        }
        if (roleSelection.canChoosePreference(player)) {
            playerSupport.givePreferenceSelectors(player, activeArena.getPreference(player.getUniqueId()));
        } else {
            playerSupport.clearPreferenceSelectors(player);
        }
    }

    private String formatPreference(GameManager.RolePreference preference) {
        return switch (preference) {
            case RUNNER -> ChatColor.AQUA + "runner" + ChatColor.RESET;
            case BEAST -> ChatColor.DARK_RED + "beast" + ChatColor.RESET;
            default -> ChatColor.GRAY + "any" + ChatColor.RESET;
        };
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
