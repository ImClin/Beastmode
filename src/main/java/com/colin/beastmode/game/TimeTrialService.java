package com.colin.beastmode.game;

import com.colin.beastmode.Beastmode;
import com.colin.beastmode.storage.TimeTrialStorage;
import com.colin.beastmode.storage.TimeTrialStorage.RecordUpdate;
import com.colin.beastmode.time.TimeTrialRecord;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages timing logic, countdowns, HUD updates, and leaderboard persistence for arena time trials.
 */
public final class TimeTrialService {

    private static final DecimalFormat MILLIS_FORMAT = new DecimalFormat("00.000");
    private static final int START_COUNTDOWN_SECONDS = 3;
    private static final int HUD_PERIOD_TICKS = 5;
    private static final long MIN_DELTA_DISPLAY_MILLIS = 50L;

    private final Beastmode plugin;
    private final TimeTrialStorage storage;
    private final PlayerSupportService playerSupport;
    private final ArenaMessagingService messaging;
    private final String prefix;
    private final Map<UUID, BukkitTask> countdownTasks = new ConcurrentHashMap<>();
    private final Map<ActiveArena, BukkitTask> hudTasks = new WeakHashMap<>();
    private final Map<UUID, FreezeHandle> freezeTasks = new ConcurrentHashMap<>();

     TimeTrialService(Beastmode plugin,
                            TimeTrialStorage storage,
                            PlayerSupportService playerSupport,
                     ArenaMessagingService messaging,
                     String prefix) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.playerSupport = Objects.requireNonNull(playerSupport, "playerSupport");
        this.messaging = Objects.requireNonNull(messaging, "messaging");
        this.prefix = Objects.requireNonNull(prefix, "prefix");
    }

    void startRun(ActiveArena activeArena, Collection<Player> participants) {
        if (activeArena == null || participants == null || !activeArena.isMatchActive()) {
            return;
        }
        activeArena.clearTimeTrialStarts();

        for (Player participant : participants) {
            if (participant == null || !participant.isOnline()) {
                continue;
            }
            UUID uuid = participant.getUniqueId();
            if (!activeArena.isRunner(uuid)) {
                continue;
            }
            participant.sendMessage(prefix + ChatColor.YELLOW + "Get ready... countdown begins!");
            beginCountdown(activeArena, participant, START_COUNTDOWN_SECONDS, false);
        }
    }

    boolean restartRun(ActiveArena activeArena, Player runner) {
        return restartRun(activeArena, runner, true);
    }

    boolean restartRun(ActiveArena activeArena, Player runner, boolean showRestartMessage) {
        if (activeArena == null || runner == null) {
            return false;
        }
        if (!activeArena.isTimeTrial() || !activeArena.isMatchActive()) {
            runner.sendMessage(prefix + ChatColor.RED + "The time trial is not active right now.");
            return false;
        }
        if (!runner.isOnline() || !activeArena.isRunner(runner.getUniqueId())) {
            runner.sendMessage(prefix + ChatColor.RED + "Only active runners can restart the trial.");
            return false;
        }
        if (activeArena.getArena() == null || activeArena.getArena().getRunnerSpawn() == null) {
            runner.sendMessage(prefix + ChatColor.RED + "Runner spawn is not configured; cannot restart.");
            return false;
        }

        UUID uuid = runner.getUniqueId();
        cancelCountdown(uuid);
        activeArena.removeTimeTrialStart(uuid);

        playerSupport.resetLoadout(runner);
        playerSupport.clearNegativeEffects(runner);
        playerSupport.restoreVitals(runner);
        runner.teleport(activeArena.getArena().getRunnerSpawn().clone());
        runner.setVelocity(new Vector(0, 0, 0));
        runner.setFallDistance(0f);
        playerSupport.giveTimeTrialRestartItem(runner);
        if (showRestartMessage) {
            runner.sendMessage(prefix + ChatColor.YELLOW + "Run restarted! Countdown begins.");
        } else {
            runner.sendMessage(prefix + ChatColor.YELLOW + "Countdown begins. Wait for GO before sprinting!");
        }

        beginCountdown(activeArena, runner, START_COUNTDOWN_SECONDS, showRestartMessage);
        return true;
    }

    TimeTrialResult completeRun(ActiveArena activeArena, Player finisher) {
        if (activeArena == null || finisher == null || activeArena.getArena() == null) {
            return new TimeTrialResult(-1L, false, -1, -1L);
        }

        UUID uuid = finisher.getUniqueId();
        cancelCountdown(uuid);
        playerSupport.clearNegativeEffects(finisher);

        Long start = activeArena.removeTimeTrialStart(uuid);
        if (start == null) {
            start = System.currentTimeMillis();
        }

        long elapsed = Math.max(0L, System.currentTimeMillis() - start);
        String arenaName = activeArena.getArena().getName();
        RecordUpdate update = storage.updateRecord(arenaName, uuid, finisher.getName(), elapsed);
        boolean personalBest = update.improved();
        int rank = update.rank();
        long bestTime = update.bestTimeMillis();

        String formatted = formatDuration(elapsed);
        if (personalBest) {
            String rankText = rank > 0 ? ChatColor.GOLD + " (#" + rank + ")" : "";
            finisher.sendMessage(prefix + ChatColor.GREEN + "New personal best: " + ChatColor.AQUA + formatted + rankText);
            finisher.playSound(finisher.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        } else {
            String bestFormatted = bestTime >= 0 ? formatDuration(bestTime) : formatted;
            finisher.sendMessage(prefix + ChatColor.YELLOW + "Finished in " + ChatColor.AQUA + formatted
                    + ChatColor.YELLOW + ". Personal best: " + ChatColor.AQUA + bestFormatted + ChatColor.YELLOW + ".");
            finisher.playSound(finisher.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }

        return new TimeTrialResult(elapsed, personalBest, rank, bestTime);
    }

    public List<TimeTrialRecord> getTopRecords(String arenaName, int limit) {
        return storage.getTopRecords(arenaName, limit);
    }

    public long getPersonalBest(String arenaName, java.util.UUID playerId) {
        if (arenaName == null || playerId == null) {
            return -1L;
        }
        return storage.getBestTime(arenaName, playerId);
    }

    public boolean deleteRecord(String arenaName, UUID playerId) {
        return storage.deleteRecord(arenaName, playerId);
    }

    public boolean deleteRecordByName(String arenaName, String playerName) {
        return storage.deleteRecordByName(arenaName, playerName);
    }

    public String formatDuration(long millis) {
        if (millis < 0) {
            return "-";
        }
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long remainder = millis % 1000;
        return minutes + ":" + MILLIS_FORMAT.format(seconds + remainder / 1000.0);
    }

    private void beginCountdown(ActiveArena activeArena, Player runner, int seconds, boolean restarting) {
        if (activeArena == null || runner == null || seconds <= 0) {
            return;
        }

        UUID uuid = runner.getUniqueId();
        cancelCountdown(uuid);

    playerSupport.giveTimeTrialRestartItem(runner);
    playerSupport.restoreVitals(runner);
    applyCountdownFreeze(activeArena, runner, seconds);

        BukkitTask[] holder = new BukkitTask[1];
        BukkitRunnable runnable = new BukkitRunnable() {
            private int remaining = Math.max(seconds, 1);

            @Override
            public void run() {
                if (!runner.isOnline() || !activeArena.isMatchActive() || !activeArena.isRunner(uuid)) {
                    cancel();
                    return;
                }

                if (remaining <= 0) {
                    cancel();
                    startTimer(activeArena, runner);
                    return;
                }

                sendCountdownActionBar(runner, remaining);
                remaining--;
            }

            @Override
            public synchronized void cancel() throws IllegalStateException {
                super.cancel();
                BukkitTask handle = holder[0];
                if (handle != null) {
                    activeArena.unregisterTask(handle);
                    countdownTasks.remove(uuid, handle);
                }
            }
        };

        runner.sendMessage(prefix + (restarting
                ? ChatColor.YELLOW + "Hold steady for the restart countdown."
                : ChatColor.YELLOW + "Wait for the countdown before moving."));

        holder[0] = runnable.runTaskTimer(plugin, 0L, 20L);
        activeArena.registerTask(holder[0]);
        countdownTasks.put(uuid, holder[0]);
    }

    private void cancelCountdown(UUID uuid) {
        if (uuid == null) {
            return;
        }
        BukkitTask task = countdownTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
            stopFreezeTask(uuid);
    }

    private void applyCountdownFreeze(ActiveArena activeArena, Player runner, int seconds) {
        if (runner == null) {
            return;
        }
        int durationTicks = Math.max(seconds * 20 + 20, 60);
        runner.setVelocity(new Vector(0, 0, 0));
        runner.setFreezeTicks(durationTicks);
        PotionEffect slow = new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, 255, false, false, true);
        runner.addPotionEffect(slow);
        startFreezeTask(activeArena, runner, durationTicks);
    }

    private void startTimer(ActiveArena activeArena, Player runner) {
        if (activeArena == null || runner == null || activeArena.getArena() == null) {
            return;
        }

    runner.removePotionEffect(PotionEffectType.SLOWNESS);
        runner.setFreezeTicks(0);

        UUID uuid = runner.getUniqueId();
            stopFreezeTask(uuid);
        activeArena.setTimeTrialStart(uuid, System.currentTimeMillis());

        messaging.broadcastGo(List.of(runner));
        runner.sendMessage(prefix + ChatColor.GREEN + "Go! Timer started.");

        ensureHudUpdaterRunning(activeArena);
    }

    private void ensureHudUpdaterRunning(ActiveArena activeArena) {
        if (activeArena == null || hudTasks.containsKey(activeArena)) {
            return;
        }

        BukkitTask[] holder = new BukkitTask[1];
        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeArena.isMatchActive()) {
                    cancel();
                    return;
                }

                Map<UUID, Long> starts = activeArena.getTimeTrialStartSnapshot();
                if (starts.isEmpty()) {
                    cancel();
                    return;
                }

                String arenaName = activeArena.getArena() != null ? activeArena.getArena().getName() : null;
                List<UUID> stale = new ArrayList<>();

                for (Map.Entry<UUID, Long> entry : starts.entrySet()) {
                    UUID uuid = entry.getKey();
                    Player runner = Bukkit.getPlayer(uuid);
                    if (runner == null || !runner.isOnline() || !activeArena.isRunner(uuid)) {
                        stale.add(uuid);
                        continue;
                    }

                    long start = entry.getValue();
                    long elapsed = Math.max(0L, System.currentTimeMillis() - start);
                    long best = arenaName != null ? storage.getBestTime(arenaName, uuid) : -1L;
                    sendHud(runner, elapsed, best);
                }

                for (UUID uuid : stale) {
                    activeArena.removeTimeTrialStart(uuid);
                }
            }

            @Override
            public synchronized void cancel() throws IllegalStateException {
                super.cancel();
                BukkitTask handle = holder[0];
                if (handle != null) {
                    activeArena.unregisterTask(handle);
                    hudTasks.remove(activeArena, handle);
                }
            }
        };

        holder[0] = runnable.runTaskTimer(plugin, 0L, HUD_PERIOD_TICKS);
        activeArena.registerTask(holder[0]);
        hudTasks.put(activeArena, holder[0]);
    }

    private void startFreezeTask(ActiveArena activeArena, Player runner, int durationTicks) {
        if (runner == null) {
            return;
        }
        UUID uuid = runner.getUniqueId();
        stopFreezeTask(uuid);

    Location anchor = runner.getLocation().clone();
    FreezeHandle handle = new FreezeHandle(activeArena, anchor);
        freezeTasks.put(uuid, handle);

        BukkitTask[] holder = new BukkitTask[1];
        BukkitRunnable runnable = new BukkitRunnable() {
            private int remaining = Math.max(durationTicks, 20);

            @Override
            public void run() {
                if (!runner.isOnline()) {
                    cancel();
                    return;
                }
                if (remaining-- <= 0) {
                    cancel();
                    return;
                }
                runner.setVelocity(new Vector(0, 0, 0));
                runner.setFallDistance(0f);
                if (handle.anchor != null && handle.anchor.getWorld() != null) {
                    runner.teleport(handle.anchor.clone(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                }
                if (runner.getFreezeTicks() < 5) {
                    runner.setFreezeTicks(5);
                }
            }

            @Override
            public synchronized void cancel() throws IllegalStateException {
                super.cancel();
                BukkitTask task = holder[0];
                if (task != null && handle.arena != null) {
                    handle.arena.unregisterTask(task);
                }
                freezeTasks.remove(uuid, handle);
            }
        };

        holder[0] = runnable.runTaskTimer(plugin, 0L, 1L);
        handle.task = holder[0];
        if (activeArena != null) {
            activeArena.registerTask(holder[0]);
        }
    }

    private void stopFreezeTask(UUID uuid) {
        if (uuid == null) {
            return;
        }
        FreezeHandle handle = freezeTasks.remove(uuid);
        if (handle != null && handle.task != null) {
            handle.task.cancel();
        }
    }

    private void sendHud(Player runner, long elapsed, long best) {
        StringBuilder builder = new StringBuilder();
        builder.append(ChatColor.GRAY).append("Time ")
                .append(ChatColor.AQUA).append(formatDuration(elapsed));

        if (best > 0) {
            builder.append(ChatColor.DARK_GRAY).append("  PB ")
                    .append(ChatColor.GOLD).append(formatDuration(best));
            long delta = elapsed - best;
            if (Math.abs(delta) >= MIN_DELTA_DISPLAY_MILLIS) {
                ChatColor color = delta < 0 ? ChatColor.GREEN : ChatColor.RED;
                String prefix = delta < 0 ? "-" : "+";
                builder.append(color)
                        .append(" (")
                        .append(prefix)
                        .append(formatDuration(Math.abs(delta)))
                        .append(")");
            }
        }

        runner.spigot().sendMessage(ChatMessageType.ACTION_BAR, toLegacyComponents(builder.toString()));
    }

    private void sendCountdownActionBar(Player runner, int remaining) {
        String message = ChatColor.GOLD + "Starting in " + ChatColor.AQUA + remaining + ChatColor.GOLD + "...";
        runner.spigot().sendMessage(ChatMessageType.ACTION_BAR, toLegacyComponents(message));
    }

    @SuppressWarnings("deprecation")
    private BaseComponent[] toLegacyComponents(String message) {
        return TextComponent.fromLegacyText(message);
    }

    private static final class FreezeHandle {
        final ActiveArena arena;
        final Location anchor;
        BukkitTask task;

        FreezeHandle(ActiveArena arena, Location anchor) {
            this.arena = arena;
            this.anchor = anchor;
        }
    }

    record TimeTrialResult(long elapsedMillis, boolean personalBest, int rank, long bestMillis) {
    }
}
