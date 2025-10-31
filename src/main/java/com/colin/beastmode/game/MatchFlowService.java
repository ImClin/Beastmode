package com.colin.beastmode.game;

import com.colin.beastmode.Beastmode;
import com.colin.beastmode.model.ArenaDefinition;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Coordinates teleport, gate, and release scheduling once a match is ready to begin.
 */
final class MatchFlowService {

    private static final long TELEPORT_DELAY_TICKS = 60L;

    private final Beastmode plugin;
    private final CountdownService countdowns;
    private final ArenaBarrierService barrierService;
    private final PlayerSupportService playerSupport;
    private final ArenaMessagingService messaging;
    private final String prefix;
    private final int longEffectDurationTicks;
    private final TimeTrialService timeTrials;

    MatchFlowService(Beastmode plugin,
                     CountdownService countdowns,
                     ArenaBarrierService barrierService,
                     PlayerSupportService playerSupport,
                     ArenaMessagingService messaging,
                     String prefix,
                     int longEffectDurationTicks,
                     TimeTrialService timeTrials) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.countdowns = Objects.requireNonNull(countdowns, "countdowns");
        this.barrierService = Objects.requireNonNull(barrierService, "barrierService");
        this.playerSupport = Objects.requireNonNull(playerSupport, "playerSupport");
        this.messaging = Objects.requireNonNull(messaging, "messaging");
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.longEffectDurationTicks = longEffectDurationTicks;
        this.timeTrials = Objects.requireNonNull(timeTrials, "timeTrials");
    }

    void scheduleMatchStart(ActiveArena activeArena,
                            ArenaDefinition arena,
                            Player beast,
                            Supplier<List<Player>> participantSupplier,
                            Consumer<Boolean> cleanupAction,
                            Runnable onMatchActivated) {
        if (activeArena == null || arena == null) {
            return;
        }
        Supplier<List<Player>> safeSupplier = participantSupplier != null
                ? participantSupplier
                : List::of;
        Consumer<Boolean> cleanup = cleanupAction != null
                ? cleanupAction
                : restoreWalls -> { };
        Runnable matchActivated = onMatchActivated != null
                ? onMatchActivated
                : () -> { };

        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            activeArena.unregisterTask(holder[0]);
            beginTeleportCountdown(activeArena, arena, beast, safeSupplier, cleanup, matchActivated);
        }, TELEPORT_DELAY_TICKS);
        activeArena.registerTask(holder[0]);
    }

    private void beginTeleportCountdown(ActiveArena activeArena,
                                        ArenaDefinition arena,
                                        Player beast,
                                        Supplier<List<Player>> participantSupplier,
                                        Consumer<Boolean> cleanupAction,
                                        Runnable onMatchActivated) {
        onMatchActivated.run();
        countdowns.startCountdown(activeArena, 1,
                participantSupplier,
                messaging::teleportCountdown,
                () -> cleanupAction.accept(true),
                () -> finishTeleportCountdown(activeArena, arena, beast, participantSupplier, cleanupAction));
    }

    private void finishTeleportCountdown(ActiveArena activeArena,
                                         ArenaDefinition arena,
                                         Player beast,
                                         Supplier<List<Player>> participantSupplier,
                                         Consumer<Boolean> cleanupAction) {
        List<Player> current = participantSupplier.get();
        if (current == null || current.isEmpty()) {
            cleanupAction.accept(true);
            return;
        }

        messaging.teleportCountdown(current, 0);
        teleportParticipants(activeArena, arena, current, beast);
        scheduleRunnerGate(arena, activeArena, beast, current, participantSupplier, cleanupAction);
    }

    private void scheduleRunnerGate(ArenaDefinition arena,
                                    ActiveArena activeArena,
                                    Player beast,
                                    List<Player> initialParticipants,
                                    Supplier<List<Player>> participantSupplier,
                                    Consumer<Boolean> cleanupAction) {
        boolean timeTrial = activeArena != null && activeArena.isTimeTrial();
        int runnerDelay = Math.max(arena.getRunnerWallDelaySeconds(), 0);
        int beastDelay = Math.max(arena.getBeastReleaseDelaySeconds(), 0);
        if (timeTrial) {
            runnerDelay = 0;
        }

        Runnable runnerOpenAction = () -> {
            List<Player> current = participantSupplier.get();
            if (current == null || current.isEmpty()) {
                cleanupAction.accept(true);
                return;
            }
            openRunnerGate(activeArena, arena);
            if (timeTrial) {
                timeTrials.startRun(activeArena, current);
                return;
            }

            messaging.broadcastGo(current);
            if (beast == null) {
                messaging.practiceReminder(current);
                cleanupAction.accept(false);
            } else {
                scheduleBeastRelease(arena, activeArena, beastDelay, beast, current, participantSupplier, cleanupAction);
            }
        };

        if (runnerDelay <= 0) {
            if (timeTrial) {
                BukkitTask[] holder = new BukkitTask[1];
                holder[0] = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (activeArena != null) {
                        activeArena.unregisterTask(holder[0]);
                    }
                    runnerOpenAction.run();
                }, 1L);
                if (activeArena != null) {
                    activeArena.registerTask(holder[0]);
                }
            } else {
                runnerOpenAction.run();
            }
            return;
        }

        notifyRunnerGateDelay(initialParticipants, runnerDelay);
        messaging.broadcastReady(initialParticipants);
        countdowns.startCountdown(activeArena, runnerDelay, participantSupplier,
                messaging::runnerCountdown,
                () -> cleanupAction.accept(true),
                runnerOpenAction);
    }

    private void scheduleBeastRelease(ArenaDefinition arena,
                                      ActiveArena activeArena,
                                      int beastDelay,
                                      Player beast,
                                      List<Player> initialParticipants,
                                      Supplier<List<Player>> participantSupplier,
                                      Consumer<Boolean> cleanupAction) {
        if (beast == null) {
            return;
        }

        Runnable beastOpenAction = () -> {
            List<Player> current = participantSupplier.get();
            if (current == null || current.isEmpty()) {
                cleanupAction.accept(true);
                return;
            }
            messaging.broadcastBeastRelease(current, beast);
            openBeastGate(activeArena, arena, beast);
        };

        if (beastDelay <= 0) {
            applyBeastReleaseEffects(activeArena, beast);
            beastOpenAction.run();
            return;
        }

        notifyBeastGateDelay(initialParticipants, beastDelay);
        countdowns.startCountdown(activeArena, beastDelay, participantSupplier,
                (players, seconds) -> {
                    messaging.beastCountdown(players, seconds, beast);
                    if (seconds == 1) {
                        applyBeastReleaseEffects(activeArena, beast);
                    }
                },
                () -> cleanupAction.accept(true),
                beastOpenAction);
    }

    private void teleportParticipants(ActiveArena activeArena, ArenaDefinition arena, List<Player> players, Player beast) {
        if (arena == null || arena.getRunnerSpawn() == null) {
            return;
        }
        boolean timeTrial = activeArena != null && activeArena.isTimeTrial();
        Location runnerLocation = arena.getRunnerSpawn().clone();
        Location beastLocation = !timeTrial && arena.getBeastSpawn() != null ? arena.getBeastSpawn().clone() : null;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!timeTrial && beastLocation != null && beast != null && beast.isOnline()) {
                playerSupport.clearPreferenceSelectors(beast);
                beast.teleport(beastLocation);
                beast.setGameMode(GameMode.ADVENTURE);
                playerSupport.restoreVitals(beast);
            }
            for (Player runner : players) {
                if (beast != null && runner.equals(beast)) {
                    continue;
                }
                if (runner.isOnline()) {
                    playerSupport.clearPreferenceSelectors(runner);
                    runner.teleport(runnerLocation);
                    runner.setGameMode(GameMode.ADVENTURE);
                    if (timeTrial) {
                        playerSupport.resetLoadout(runner);
                        playerSupport.restoreVitals(runner);
                        playerSupport.giveTimeTrialRestartItem(runner);
                    } else {
                        playerSupport.restoreVitals(runner);
                    }
                }
            }
            if (timeTrial) {
                playerSupport.hideTimeTrialParticipants(players);
            }
            if (!timeTrial && beast != null && playerSupport.applyBeastLoadout(beast)) {
                send(beast, ChatColor.DARK_RED + "" + ChatColor.BOLD + "You gear up in unbreakable armor.");
            }
        });
    }

    private void applyBeastReleaseEffects(ActiveArena activeArena, Player beast) {
        if (beast == null || !beast.isOnline()) {
            return;
        }
        int speedLevel = 1;
        if (activeArena != null && activeArena.getArena() != null) {
            speedLevel = Math.max(activeArena.getArena().getBeastSpeedLevel(), 0);
        }
        beast.removePotionEffect(PotionEffectType.SPEED);
        if (speedLevel > 0) {
            PotionEffect speed = new PotionEffect(PotionEffectType.SPEED,
                    longEffectDurationTicks,
                    Math.max(speedLevel - 1, 0),
                    false,
                    false,
                    true);
            beast.addPotionEffect(speed);
        }
        playerSupport.applyFireResistance(beast);
        if (activeArena != null) {
            activeArena.setBeastId(beast.getUniqueId());
        }
    }

    private void openRunnerGate(ActiveArena activeArena, ArenaDefinition arena) {
        if (!activeArena.isRunnerWallOpened()) {
            if (arena.getRunnerWall() != null) {
                if (activeArena.getRunnerWallSnapshot() == null) {
                    activeArena.setRunnerWallSnapshot(barrierService.capture(arena.getRunnerWall()));
                }
                barrierService.clear(arena.getRunnerWall());
            }
            activeArena.setRunnerWallOpened(true);
            activeArena.releaseDamageProtectionAfter(1000L);
        }
    }

    private void openBeastGate(ActiveArena activeArena, ArenaDefinition arena, Player beast) {
        if (!activeArena.isBeastWallOpened()) {
            if (arena.getBeastWall() != null) {
                if (activeArena.getBeastWallSnapshot() == null) {
                    activeArena.setBeastWallSnapshot(barrierService.capture(arena.getBeastWall()));
                }
                barrierService.clear(arena.getBeastWall());
            }
            activeArena.setBeastWallOpened(true);
        }
        if (beast != null && beast.isOnline()) {
            send(beast, ChatColor.DARK_RED + "You are free! Hunt them down!");
        }
    }

    private void notifyRunnerGateDelay(List<Player> players, int seconds) {
        if (players == null || players.isEmpty()) {
            return;
        }
        String message = ChatColor.AQUA + "" + ChatColor.BOLD + "Runner gate opens in "
                + ChatColor.WHITE + formatSeconds(seconds) + ChatColor.AQUA + "." + ChatColor.RESET;
        send(players, message);
    }

    private void notifyBeastGateDelay(List<Player> players, int seconds) {
        if (players == null || players.isEmpty()) {
            return;
        }
        String message = ChatColor.DARK_RED + "" + ChatColor.BOLD + "Beast gate opens in "
                + ChatColor.WHITE + formatSeconds(seconds) + ChatColor.DARK_RED + "." + ChatColor.RESET;
        send(players, message);
    }

    private void send(Player player, String message) {
        if (player == null) {
            return;
        }
        player.sendMessage(prefix + message);
    }

    private void send(List<Player> players, String message) {
        if (players == null || players.isEmpty()) {
            return;
        }
        for (Player player : players) {
            send(player, message);
        }
    }

    private String formatSeconds(int seconds) {
        if (seconds <= 0) {
            return "0 seconds";
        }
        return seconds + " " + (seconds == 1 ? "second" : "seconds");
    }
}
