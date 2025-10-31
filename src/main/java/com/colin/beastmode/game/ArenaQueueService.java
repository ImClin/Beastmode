package com.colin.beastmode.game;

import com.colin.beastmode.model.ArenaDefinition;
import com.colin.beastmode.storage.ArenaStorage;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Coordinates queue enrolment and waiting-room preparation for arenas.
 */
final class ArenaQueueService {

    private final ArenaStorage arenaStorage;
    private final ActiveArenaDirectory arenaDirectory;
    private final PlayerSupportService playerSupport;
    private final RoleSelectionService roleSelection;
    private final ArenaWaitingService waitingService;
    private final MatchOrchestrationService orchestration;
    private final ArenaStatusService statusService;
    private final TimeTrialService timeTrials;
    private final String prefix;

    ArenaQueueService(ArenaStorage arenaStorage,
                      ActiveArenaDirectory arenaDirectory,
                      PlayerSupportService playerSupport,
                      RoleSelectionService roleSelection,
                      ArenaWaitingService waitingService,
                      MatchOrchestrationService orchestration,
                      ArenaStatusService statusService,
                      TimeTrialService timeTrials,
                      String prefix) {
        this.arenaStorage = arenaStorage;
        this.arenaDirectory = arenaDirectory;
        this.playerSupport = playerSupport;
        this.roleSelection = roleSelection;
        this.waitingService = waitingService;
        this.orchestration = orchestration;
        this.statusService = statusService;
        this.timeTrials = timeTrials;
        this.prefix = prefix;
    }

    void join(Player player,
              String arenaName,
              GameManager.RolePreference desiredPreference) {
        join(player, arenaName, GameModeType.HUNT, desiredPreference);
    }

    void joinTimeTrial(Player player, String arenaName) {
        join(player, arenaName, GameModeType.TIME_TRIAL, GameManager.RolePreference.ANY);
    }

    void join(Player player,
              String arenaName,
              GameModeType mode,
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
        if (mode.isTimeTrial() && !arena.isTimeTrial()) {
            send(player, ChatColor.RED + "Arena " + highlight(arena.getName()) + ChatColor.RED + " is not configured for time trials.");
            return;
        }
        if (!mode.isTimeTrial() && arena.isTimeTrial()) {
            send(player, ChatColor.RED + "Arena " + highlight(arena.getName()) + " only supports time trials. Use /beastmode trial "
                    + highlight(arena.getName()) + ChatColor.RED + ".");
            return;
        }
        if (!arena.isComplete()) {
            send(player, ChatColor.RED + GameManager.MSG_ARENA_INCOMPLETE.formatted(highlight(arena.getName())));
            return;
        }
        if (arena.getRunnerSpawn() == null) {
            send(player, ChatColor.RED + "Arena spawns are missing. Reconfigure the arena before joining.");
            return;
        }
        if (!mode.isTimeTrial() && arena.getBeastSpawn() == null) {
            send(player, ChatColor.RED + "Arena spawns are missing. Reconfigure the arena before joining.");
            return;
        }

        if (arenaDirectory.findArenaByPlayer(player.getUniqueId()) != null) {
            send(player, ChatColor.RED + "You are already queued for an arena.");
            return;
        }

        GameManager.RolePreference preference = mode.isTimeTrial()
                ? GameManager.RolePreference.ANY
                : sanitizePreference(player, desiredPreference);

        String key = arena.getName().toLowerCase(Locale.ENGLISH);
        ActiveArena activeArena = arenaDirectory.computeIfAbsent(key, () -> new ActiveArena(arena));

        if (!ensureModeCompatible(player, activeArena, mode)) {
            return;
        }

        if (activeArena.isMatchActive() && !mode.isTimeTrial()) {
            send(player, ChatColor.RED + "That arena is already in a hunt. Try again in a moment.");
            return;
        }

        int queueLimit = waitingService.getQueueLimit(arena, mode);
        if (queueLimit != Integer.MAX_VALUE && activeArena.getPlayerIds().size() >= queueLimit) {
            if (mode.isTimeTrial()) {
                String runnerText = waitingService.formatRunnerCount(queueLimit);
                send(player, ChatColor.RED + "That arena already has the maximum of "
                        + ChatColor.AQUA + runnerText + ChatColor.RED + " warming up. Try again shortly.");
            } else {
                int maxRunners = arena.getMaxRunners();
                send(player, ChatColor.RED + "That arena already has the maximum of "
                        + ChatColor.AQUA + waitingService.formatRunnerCount(maxRunners) + ChatColor.RED
                        + " (plus the Beast). Try again later.");
            }
            return;
        }

        boolean added = activeArena.addPlayer(player);
        activeArena.setPreference(player.getUniqueId(), preference);
        if (!added) {
            handleExistingParticipant(player, activeArena, preference);
            statusService.notifyArenaStatus(activeArena);
            return;
        }

        activeArena.setMode(mode);

        prepareWaitingLoadout(player, activeArena, mode);

        if (mode.isTimeTrial()) {
            send(player, ChatColor.GREEN + "Joined time trial for " + ChatColor.AQUA + arena.getName() + ChatColor.GREEN + ".");
            send(player, ChatColor.YELLOW + "Countdown begins shortly. Good luck!");
        } else {
            send(player, ChatColor.GREEN + "Joined arena " + ChatColor.AQUA + arena.getName() + ChatColor.GREEN + ".");
        }
        if (!mode.isTimeTrial() && preference != GameManager.RolePreference.ANY) {
            send(player, ChatColor.GOLD + "Preference set to " + formatPreference(preference) + ChatColor.GOLD + ".");
        }

        if (activeArena.isRunning()) {
            if (mode.isTimeTrial() && activeArena.isMatchActive()) {
                activeArena.addRunner(player.getUniqueId());
                boolean restarted = timeTrials.restartRun(activeArena, player, false);
                if (!restarted) {
                    activeArena.removePlayer(player.getUniqueId());
                } else {
                    playerSupport.hideTimeTrialParticipant(player, activeArena);
                    send(player, ChatColor.YELLOW + "Countdown started. Wait for GO before sprinting!");
                }
                statusService.notifyArenaStatus(activeArena);
                return;
            }

            if (!waitingService.teleportToWaiting(arena, player)) {
                send(player, ChatColor.RED + "Waiting spawn is not configured correctly. Please notify an admin.");
            } else {
                send(player, ChatColor.YELLOW + "You slipped in before the gates drop. Hold tight!");
            }
            orchestration.maybeStartCountdown(key, activeArena);
            statusService.notifyArenaStatus(activeArena);
            return;
        }

        orchestration.startMatch(key, activeArena);
        statusService.notifyArenaStatus(activeArena);
    }

    private GameManager.RolePreference sanitizePreference(Player player, GameManager.RolePreference preference) {
        if (preference == null || preference == GameManager.RolePreference.ANY) {
            return GameManager.RolePreference.ANY;
        }
        return roleSelection.canChoosePreference(player) ? preference : GameManager.RolePreference.ANY;
    }

    private void handleExistingParticipant(Player player,
                                           ActiveArena activeArena,
                                           GameManager.RolePreference preference) {
        if (preference != GameManager.RolePreference.ANY) {
            send(player, ChatColor.YELLOW + "Updated your preference to " + formatPreference(preference) + ChatColor.YELLOW + ".");
        } else {
            send(player, ChatColor.YELLOW + "You are already in the queue for this arena.");
        }
        if (!activeArena.isTimeTrial() && roleSelection.canChoosePreference(player)) {
            playerSupport.givePreferenceSelectors(player, activeArena.getPreference(player.getUniqueId()));
        } else {
            playerSupport.clearPreferenceSelectors(player);
        }
    }

    private void prepareWaitingLoadout(Player player, ActiveArena activeArena, GameModeType mode) {
        playerSupport.resetLoadout(player);
        if (!activeArena.isMatchActive()) {
            playerSupport.giveExitToken(player);
        }
        if (!mode.isTimeTrial() && roleSelection.canChoosePreference(player)) {
            playerSupport.givePreferenceSelectors(player, activeArena.getPreference(player.getUniqueId()));
        } else {
            playerSupport.clearPreferenceSelectors(player);
        }
    }

    private boolean ensureModeCompatible(Player player, ActiveArena activeArena, GameModeType requested) {
        GameModeType current = activeArena.getMode();
        if (current == requested) {
            return true;
        }
        if (activeArena.isRunning() || activeArena.isSelecting() || activeArena.isMatchActive()
                || !activeArena.getPlayerIds().isEmpty()) {
            if (requested.isTimeTrial()) {
                send(player, ChatColor.RED + "That arena is preparing for a hunt. Try again when it is idle.");
            } else {
                send(player, ChatColor.RED + "That arena is currently set for time trials. Finish the practice run first.");
            }
            return false;
        }
        activeArena.setMode(requested);
        return true;
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
