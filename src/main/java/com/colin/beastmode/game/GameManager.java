package com.colin.beastmode.game;

import com.colin.beastmode.Beastmode;
import com.colin.beastmode.model.ArenaDefinition;
import com.colin.beastmode.model.Cuboid;
import com.colin.beastmode.storage.ArenaStorage;
import org.bukkit.GameMode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

public class GameManager {

    private final Beastmode plugin;
    private final ArenaStorage arenaStorage;
    private final Map<String, ActiveArena> activeArenas = new ConcurrentHashMap<>();
    private final Set<UUID> pendingSpawnTeleports = ConcurrentHashMap.newKeySet();
    private final String prefix;
    private final NamespacedKey exitTokenKey;
    private final ItemStack exitTokenTemplate;
    private static final String MSG_ARENA_NOT_FOUND = "Arena %s does not exist.";
    private static final String MSG_ARENA_INCOMPLETE = "Arena %s is not fully configured yet.";
    private static final String MSG_ARENA_NOT_RUNNING = "Arena %s is not currently running.";
    private static final String DEFAULT_BEAST_NAME = "The Beast";
    private static final int EXIT_TOKEN_SLOT = 8;
    private static final int LONG_EFFECT_DURATION_TICKS = 20 * 600;

    public enum RolePreference {
        ANY,
        RUNNER,
        BEAST
    }

    public GameManager(Beastmode plugin, ArenaStorage arenaStorage) {
        this.plugin = plugin;
        this.arenaStorage = arenaStorage;
        this.prefix = plugin.getConfig().getString("messages.prefix", "[Beastmode] ");
        this.exitTokenKey = new NamespacedKey(plugin, "exit_token");
        this.exitTokenTemplate = createExitToken();
    }

    public void joinArena(Player player, String arenaName) {
        joinArena(player, arenaName, RolePreference.ANY);
    }

    public void joinArena(Player player, String arenaName, RolePreference preference) {
        if (arenaName == null || arenaName.trim().isEmpty()) {
            send(player, ChatColor.RED + "Please specify an arena name.");
            return;
        }

        arenaName = arenaName.trim();
        ArenaDefinition arena = arenaStorage.getArena(arenaName);
        if (arena == null) {
            send(player, ChatColor.RED + MSG_ARENA_NOT_FOUND.formatted(highlightArena(arenaName)));
            return;
        }
        if (!arena.isComplete()) {
            send(player, ChatColor.RED + MSG_ARENA_INCOMPLETE.formatted(highlightArena(arenaName)));
            return;
        }
        if (arena.getRunnerSpawn() == null || arena.getBeastSpawn() == null) {
            send(player, ChatColor.RED + "Arena spawns are missing. Reconfigure the arena before joining.");
            return;
        }

        if (findArenaByPlayer(player.getUniqueId()) != null) {
            send(player, ChatColor.RED + "You are already queued for an arena.");
            return;
        }

        String key = arena.getName().toLowerCase(Locale.ENGLISH);
        ActiveArena activeArena = activeArenas.computeIfAbsent(key, k -> new ActiveArena(arena));
        if (activeArena.isMatchActive()) {
            send(player, ChatColor.RED + "That arena is already in a hunt. Try again in a moment.");
            return;
        }

        int queueLimit = getQueueLimit(arena);
        if (queueLimit != Integer.MAX_VALUE) {
            int currentSize = activeArena.getPlayerIds().size();
            if (currentSize >= queueLimit) {
                int maxRunners = arena.getMaxRunners();
                String runnerText = formatRunnerCount(maxRunners);
                send(player, ChatColor.RED + "That arena already has the maximum of "
                        + ChatColor.AQUA + runnerText + ChatColor.RED + " (plus the Beast). Try again later.");
                return;
            }
        }

        boolean added = activeArena.addPlayer(player);
        activeArena.setPreference(player.getUniqueId(), preference);
        if (!added) {
            if (preference != RolePreference.ANY) {
                send(player, ChatColor.YELLOW + "Updated your preference to " + formatPreference(preference) + ".");
            } else {
                send(player, ChatColor.YELLOW + "You are already in the queue for this arena.");
            }
            return;
        }

        resetPlayerLoadout(player);
        if (!activeArena.isMatchActive()) {
            giveExitToken(player);
        }

        send(player, ChatColor.GREEN + "Joined arena " + ChatColor.AQUA + arena.getName() + ChatColor.GREEN + ".");
        if (preference != RolePreference.ANY) {
            send(player, ChatColor.GOLD + "Preference set to " + formatPreference(preference) + ".");
        }

        if (activeArena.isRunning()) {
            if (!teleportToWaiting(activeArena.getArena(), player)) {
                send(player, ChatColor.RED + "Waiting spawn is not configured correctly. Please notify an admin.");
            } else {
                send(player, ChatColor.YELLOW + "You slipped in before the gates drop. Hold tight!");
            }
            maybeStartCountdown(key, activeArena);
            return;
        }
        startMatch(key, activeArena);
    }

    private void resetPlayerLoadout(Player player) {
        if (player == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(null);
    }

    public void handlePlayerMove(Player player, Location from, Location to) {
        if (player == null || to == null) {
            return;
        }

        if (from != null
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        String key = findArenaByPlayer(player.getUniqueId());
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

        Cuboid finish = activeArena.getArena().getFinishRegion();
        if (finish == null) {
            return;
        }

        if (from != null && finish.contains(from)) {
            return;
        }

        if (!finish.contains(to)) {
            return;
        }

        if (!activeArena.isRunner(player.getUniqueId())) {
            return;
        }

        completeRunnerVictory(key, activeArena, player);
    }

    public void handlePlayerInteract(Player player, Block block) {
        if (player == null || block == null) {
            return;
        }

        String key = findArenaByPlayer(player.getUniqueId());
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

        completeRunnerVictory(key, activeArena, player);
    }

    private void completeRunnerVictory(String key, ActiveArena activeArena, Player finisher) {
        if (activeArena == null) {
            return;
        }

        List<Player> participants = collectParticipants(activeArena);
        if (participants.isEmpty()) {
            cleanupArena(key, activeArena);
            return;
        }

        UUID beastId = activeArena.getBeastId();
        if (finisher != null && finisher.isOnline() && !participants.contains(finisher)) {
            participants.add(finisher);
        }

        if (finisher != null) {
            if (activeArena.isFinalPhase()) {
                return;
            }

            activeArena.setFinalPhase(true);

            String finisherName = finisher.getName();
            String title = ChatColor.GOLD + "" + ChatColor.BOLD + "Parkour Complete!";
            String subtitle = ChatColor.AQUA + finisherName + ChatColor.YELLOW + " finished the parkour and is ready to slay the Beast!";

            List<UUID> toRemove = new ArrayList<>();
            for (Player participant : participants) {
                boolean isFinisher = participant.getUniqueId().equals(finisher.getUniqueId());
                boolean isBeast = beastId != null && beastId.equals(participant.getUniqueId());

                participant.sendTitle(title, subtitle, 10, 60, 10);
                participant.playSound(participant.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

                if (isFinisher) {
                    send(participant, ChatColor.GOLD + "" + ChatColor.BOLD + finisherName + ChatColor.RESET
                            + ChatColor.YELLOW + " finished the parkour and is ready to slay the Beast!");
                    applyFireResistance(participant);
                    scheduleRunnerReward(participant);
                    continue;
                }

                if (isBeast) {
                    send(participant, ChatColor.DARK_RED + "" + ChatColor.BOLD + finisherName
                            + ChatColor.RED + " raided the weapon cache! Stop them before they strike back.");
                    continue;
                }

                send(participant, ChatColor.YELLOW + "You have been removed from the arena while "
                        + ChatColor.AQUA + finisherName + ChatColor.YELLOW + " prepares to fight the Beast.");
        participant.removePotionEffect(PotionEffectType.SPEED);
        participant.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
                resetPlayerLoadout(participant);
                sendPlayerToSpawn(activeArena, participant);
                toRemove.add(participant.getUniqueId());
            }

            for (UUID uuid : toRemove) {
                activeArena.removePlayer(uuid);
            }
            return;
        }

        boolean finalPhase = activeArena.isFinalPhase();
        activeArena.setMatchActive(false);

        String title = ChatColor.GREEN + "" + ChatColor.BOLD + "Runner Victory!";
        String subtitle = finalPhase
                ? ChatColor.AQUA + "The Beast has been defeated."
                : ChatColor.AQUA + "The Beast never made it out.";

        for (Player participant : participants) {
            boolean isRunner = activeArena.isRunner(participant.getUniqueId());
            participant.sendTitle(title, subtitle, 10, 60, 10);
            participant.playSound(participant.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            if (isRunner && !finalPhase && !activeArena.isRewardSuppressed()) {
                scheduleRunnerReward(participant);
            }

            participant.removePotionEffect(PotionEffectType.SPEED);
            participant.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
            resetPlayerLoadout(participant);
            sendPlayerToSpawn(activeArena, participant);
        }

        cleanupArena(key, activeArena);
    }

    private void completeBeastVictory(String key, ActiveArena activeArena, Player beast) {
        activeArena.setMatchActive(false);
        List<Player> participants = collectParticipants(activeArena);
        if (beast != null && beast.isOnline() && !participants.contains(beast)) {
            participants.add(beast);
        }

        String beastName = beast != null ? beast.getName() : DEFAULT_BEAST_NAME;
        String title = ChatColor.DARK_RED + "" + ChatColor.BOLD + "Beast Victory!";
        String subtitle = ChatColor.RED + beastName + ChatColor.GRAY + " eliminated everyone.";

        for (Player participant : participants) {
            participant.sendTitle(title, subtitle, 10, 60, 10);
            participant.playSound(participant.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
            send(participant, ChatColor.DARK_RED + "" + ChatColor.BOLD + "The Beast prevailed! "
                    + ChatColor.RESET + ChatColor.RED + beastName + ChatColor.GRAY + " cleared the arena.");
            participant.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
            resetPlayerLoadout(participant);
            sendPlayerToSpawn(activeArena, participant);
        }

        cleanupArena(key, activeArena);
    }

    public void handlePlayerDeath(Player player) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        String key = findArenaByPlayer(uuid);
        if (key == null) {
            return;
        }

        ActiveArena activeArena = activeArenas.get(key);
        if (activeArena == null) {
            return;
        }

        if (!activeArena.isMatchActive()) {
            sendPlayerToSpawn(activeArena, player);
            return;
        }

        Location deathLocation = player.getLocation() != null ? player.getLocation().clone() : null;

        if (activeArena.isRunner(uuid)) {
            if (!activeArena.removeRunner(uuid)) {
                return;
            }
            resetPlayerLoadout(player);
            boolean matchEnded = handleRunnerElimination(key, activeArena, player);
            if (matchEnded) {
                return;
            }
            sendToSpectator(activeArena, player, deathLocation);
            return;
        }

        UUID beastId = activeArena.getBeastId();
        if (beastId != null && beastId.equals(uuid)) {
            resetPlayerLoadout(player);
            completeRunnerVictory(key, activeArena, null);
        }
    }

    public void handlePlayerQuit(Player player) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        String key = findArenaByPlayer(uuid);
        if (key == null) {
            return;
        }

        ActiveArena activeArena = activeArenas.get(key);
        if (activeArena == null) {
            return;
        }

    boolean wasRunner = activeArena.isRunner(uuid);
    boolean wasBeast = activeArena.getBeastId() != null && activeArena.getBeastId().equals(uuid);
    resetPlayerLoadout(player);
    player.setGameMode(GameMode.ADVENTURE);
    activeArena.removePlayer(uuid);
    pendingSpawnTeleports.add(uuid);
    removeExitToken(player);

        if (!activeArena.isMatchActive()) {
            List<Player> remaining = collectParticipants(activeArena);
            if (remaining.isEmpty()) {
                cleanupArena(key, activeArena);
                return;
            }
            int missing = getMissingParticipantsCount(remaining.size(), activeArena.getArena());
            if (missing > 0 && activeArena.isSelecting()) {
                activeArena.setSelecting(false);
                notifyWaitingForPlayers(activeArena, remaining);
            }
            return;
        }

        if (wasRunner) {
            handleRunnerElimination(key, activeArena, null);
            return;
        }

        if (wasBeast) {
            activeArena.setRewardSuppressed(true);
            completeRunnerVictory(key, activeArena, null);
        }
    }

    public void handlePlayerJoin(Player player) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (!pendingSpawnTeleports.remove(uuid)) {
            return;
        }

        sendPlayerToSpawn(null, player);
    }

    public boolean handleSpawnCommand(Player player) {
        if (player == null) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        String key = findArenaByPlayer(uuid);
        if (key == null) {
            return false;
        }

        ActiveArena activeArena = activeArenas.get(key);
        if (activeArena == null) {
            return false;
        }

        boolean wasRunner = activeArena.isRunner(uuid);
        boolean wasBeast = activeArena.getBeastId() != null && activeArena.getBeastId().equals(uuid);

        resetPlayerLoadout(player);
        player.setGameMode(GameMode.ADVENTURE);
        activeArena.removePlayer(uuid);
    removeExitToken(player);

        if (!activeArena.isMatchActive()) {
            List<Player> remaining = collectParticipants(activeArena);
            if (remaining.isEmpty()) {
                cleanupArena(key, activeArena);
            } else {
                int missing = getMissingParticipantsCount(remaining.size(), activeArena.getArena());
                if (missing > 0 && activeArena.isSelecting()) {
                    activeArena.setSelecting(false);
                    notifyWaitingForPlayers(activeArena, remaining);
                }
            }
            sendPlayerToSpawn(activeArena, player);
            send(player, ChatColor.YELLOW + "You left the hunt.");
            return true;
        }

        if (wasRunner) {
            handleRunnerElimination(key, activeArena, null);
        } else if (wasBeast) {
            activeArena.setRewardSuppressed(true);
            completeRunnerVictory(key, activeArena, null);
        }

        sendPlayerToSpawn(activeArena, player);
        send(player, ChatColor.YELLOW + "You left the hunt.");
        return true;
    }

    public boolean shouldCancelDamage(Player player) {
        if (player == null) {
            return false;
        }

        String key = findArenaByPlayer(player.getUniqueId());
        if (key == null) {
            return false;
        }

        ActiveArena activeArena = activeArenas.get(key);
        if (activeArena == null) {
            return false;
        }

        return activeArena.isDamageProtectionActive();
    }

    private boolean handleRunnerElimination(String key, ActiveArena activeArena, Player eliminated) {
        announceRunnerElimination(activeArena, eliminated);
        if (!activeArena.hasRunners()) {
            Player beast = getBeastPlayer(activeArena);
            completeBeastVictory(key, activeArena, beast);
            return true;
        }
        return false;
    }

    private void announceRunnerElimination(ActiveArena activeArena, Player eliminated) {
        if (activeArena == null) {
            return;
        }

        List<Player> participants = collectParticipants(activeArena);
        String eliminatedName = eliminated != null ? eliminated.getName() : "A runner";
        int remaining = activeArena.getRunnerCount();
        String remainingText;
        if (remaining <= 0) {
            remainingText = "No runners remain.";
        } else if (remaining == 1) {
            remainingText = "1 runner remains.";
        } else {
            remainingText = remaining + " runners remain.";
        }

        for (Player participant : participants) {
            send(participant, ChatColor.RED + eliminatedName + ChatColor.GRAY + " has been eliminated. "
                    + ChatColor.GOLD + remainingText);
        }
    }

    private Player getBeastPlayer(ActiveArena activeArena) {
        if (activeArena == null) {
            return null;
        }
        UUID beastId = activeArena.getBeastId();
        return beastId != null ? Bukkit.getPlayer(beastId) : null;
    }

    private void sendPlayerToSpawn(ActiveArena activeArena, Player player) {
        if (player == null) {
            return;
        }

        if (activeArena != null) {
            activeArena.removeSpectatingRunner(player.getUniqueId());
        }

        removeExitToken(player);
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);

        forceRespawn(player, () -> {
            player.setGameMode(GameMode.ADVENTURE);
            restorePlayerVitals(player);
            Location globalSpawn = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
            if (globalSpawn != null) {
                player.teleport(globalSpawn.clone());
            }
        });
    }

    private void sendToSpectator(ActiveArena activeArena, Player player, Location location) {
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
            removeExitToken(player);
            player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
            applySpectatorState(activeArena, player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> applySpectatorState(activeArena, player), 2L);
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
        restorePlayerVitals(player);
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
                    // Ignore - not all implementations support force respawn.
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

    public void cancelArena(Player player, String arenaName) {
        if (arenaName == null || arenaName.trim().isEmpty()) {
            send(player, ChatColor.RED + "Please specify an arena name.");
            return;
        }

        String trimmed = arenaName.trim();
        String key = trimmed.toLowerCase(Locale.ENGLISH);
        ActiveArena activeArena = activeArenas.get(key);
        if (activeArena == null) {
            send(player, ChatColor.YELLOW + MSG_ARENA_NOT_RUNNING.formatted(highlightArena(trimmed)));
            return;
        }

        List<Player> participants = collectParticipants(activeArena);
        for (Player participant : participants) {
            send(participant, ChatColor.YELLOW + "The hunt was cancelled by " + player.getName() + ".");
        }

        cleanupArena(key, activeArena);
        send(player, ChatColor.GREEN + "Cancelled hunt for arena " + ChatColor.AQUA + activeArena.getArena().getName() + ChatColor.GREEN + ".");
    }

    public boolean hasActiveArena(String arenaName) {
        if (arenaName == null || arenaName.trim().isEmpty()) {
            return false;
        }

        String key = arenaName.trim().toLowerCase(Locale.ENGLISH);
        return activeArenas.containsKey(key);
    }

    public void shutdown() {
        for (ActiveArena activeArena : activeArenas.values()) {
            activeArena.cancelTasks();
            resetArenaState(activeArena);
            activeArena.clearPlayers();
            activeArena.setRunning(false);
        }
        activeArenas.clear();
        pendingSpawnTeleports.clear();
    }

    private String findArenaByPlayer(UUID uuid) {
        for (Map.Entry<String, ActiveArena> entry : activeArenas.entrySet()) {
            if (entry.getValue().contains(uuid)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public boolean isPlayerInArena(UUID uuid) {
        return uuid != null && findArenaByPlayer(uuid) != null;
    }

    public ArenaStatus getArenaStatus(String arenaName) {
        if (arenaName == null || arenaName.trim().isEmpty()) {
            return ArenaStatus.unavailable("");
        }

        ArenaDefinition arena = arenaStorage.getArena(arenaName);
        if (arena == null) {
            return ArenaStatus.unavailable(arenaName.trim());
        }

        String key = arena.getName().toLowerCase(Locale.ENGLISH);
        ActiveArena activeArena = activeArenas.get(key);

        int playerCount = 0;
        boolean running = false;
        boolean selecting = false;
        boolean matchActive = false;

        if (activeArena != null) {
            playerCount = countActivePlayers(activeArena);
            running = activeArena.isRunning();
            selecting = activeArena.isSelecting();
            matchActive = activeArena.isMatchActive();
        }

        int capacity = getQueueLimit(arena);
        if (capacity == Integer.MAX_VALUE) {
            capacity = -1;
        }

        return new ArenaStatus(arena.getName(), arena.isComplete(), playerCount, capacity,
                running, selecting, matchActive);
    }

    private void startMatch(String key, ActiveArena activeArena) {
        if (activeArena.isRunning() || activeArena.isSelecting() || activeArena.isMatchActive()) {
            return;
        }

        List<Player> participants = collectParticipants(activeArena);
        if (participants.isEmpty()) {
            cleanupArena(key, activeArena);
            return;
        }

        activeArena.setRunning(true);
        activeArena.clearMatchState();
        activeArena.enableDamageProtection();
        if (activeArena.isRunnerWallOpened() || activeArena.isBeastWallOpened()) {
            resetArenaState(activeArena);
        }
        ArenaDefinition arena = activeArena.getArena();
        if (!sendPlayersToWaiting(arena, participants)) {
            activeArena.setRunning(false);
            cleanupArena(key, activeArena);
            return;
        }

        maybeStartCountdown(key, activeArena);
    }

    private boolean sendPlayersToWaiting(ArenaDefinition arena, List<Player> participants) {
        Location waitingPoint = arena.getWaitingSpawn();
        if (waitingPoint == null) {
            waitingPoint = arena.getRunnerSpawn();
        }
        Location beastSpawn = arena.getBeastSpawn();
        if (waitingPoint == null || beastSpawn == null) {
            for (Player player : participants) {
                send(player, ChatColor.RED + "Arena spawns are not configured.");
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

    private boolean teleportToWaiting(ArenaDefinition arena, Player participant) {
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
        restorePlayerVitals(participant);
        participant.sendTitle(ChatColor.GOLD + "Preparing...", "", 10, 40, 10);
        return true;
    }

    private void maybeStartCountdown(String key, ActiveArena activeArena) {
        List<Player> participants = collectParticipants(activeArena);
        if (participants.isEmpty()) {
            cleanupArena(key, activeArena);
            return;
        }

        ArenaDefinition arena = activeArena.getArena();
        int required = getRequiredParticipants(arena);
        if (participants.size() < required) {
            activeArena.setSelecting(false);
            notifyWaitingForPlayers(activeArena, participants);
            return;
        }

        if (activeArena.isSelecting()) {
            return;
        }

        activeArena.setSelecting(true);
        SelectionCountdown countdown = new SelectionCountdown(key, activeArena, 10, 5);
        countdown.start();
    }

    private void notifyWaitingForPlayers(ActiveArena activeArena, List<Player> participants) {
        if (participants.isEmpty() || activeArena == null) {
            return;
        }
        ArenaDefinition arena = activeArena.getArena();
        int missing = getMissingParticipantsCount(participants.size(), arena);
        if (missing <= 0) {
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

    private void scheduleTeleportCountdownStart(String key, ActiveArena activeArena, ArenaDefinition arena,
                                                Player beast) {
        final long delayTicks = 60L;
        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            activeArena.unregisterTask(holder[0]);
            beginTeleportCountdown(key, activeArena, arena, beast);
        }, delayTicks);
        activeArena.registerTask(holder[0]);
    }

    private void beginTeleportCountdown(String key, ActiveArena activeArena, ArenaDefinition arena,
                                        Player beast) {
        TeleportCountdown countdown = new TeleportCountdown(key, activeArena, arena, beast, 1);
        countdown.start();
    }

    private class SelectionCountdown extends BukkitRunnable {
        private final String key;
        private final ActiveArena activeArena;
        private final int wheelSeconds;
        private int remaining;
        private BukkitTask handle;

        private SelectionCountdown(String key, ActiveArena activeArena, int totalSeconds, int wheelSeconds) {
            this.key = key;
            this.activeArena = activeArena;
            this.wheelSeconds = wheelSeconds;
            this.remaining = totalSeconds;
        }

        private void start() {
            announceCountdown(remaining);
            handle = runTaskTimer(plugin, 20L, 20L);
            activeArena.registerTask(handle);
        }

        @Override
        public void run() {
            List<Player> current = collectParticipants(activeArena);
            if (current.size() < getRequiredParticipants(activeArena.getArena())) {
                cancel();
                activeArena.setSelecting(false);
                notifyWaitingForPlayers(activeArena, current);
                return;
            }

            remaining--;
            if (remaining > wheelSeconds) {
                announceCountdown(remaining);
                return;
            }

            cancel();
            WheelSelection wheel = new WheelSelection(key, activeArena, wheelSeconds);
            wheel.start();
        }

        private void announceCountdown(int seconds) {
            List<Player> players = collectParticipants(activeArena);
            for (Player player : players) {
                player.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + seconds,
                        ChatColor.AQUA + "seconds until game starts", 0, 20, 0);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
            }
        }

        @Override
        public synchronized void cancel() throws IllegalStateException {
            super.cancel();
            if (handle != null) {
                activeArena.unregisterTask(handle);
            }
        }
    }

    private class WheelSelection extends BukkitRunnable {
        private final String key;
        private final ActiveArena activeArena;
        private static final int PERIOD_TICKS = 4;
        private int ticksRemaining;
        private int index;
        private boolean finalized;
        private BukkitTask handle;

        private WheelSelection(String key, ActiveArena activeArena, int durationSeconds) {
            this.key = key;
            this.activeArena = activeArena;
            this.ticksRemaining = durationSeconds * 20;
            this.index = ThreadLocalRandom.current().nextInt(Math.max(activeArena.getPlayerIds().size(), 1));
        }

        private void start() {
            handle = runTaskTimer(plugin, 0L, PERIOD_TICKS);
            activeArena.registerTask(handle);
        }

        @Override
        public void run() {
            List<Player> current = collectParticipants(activeArena);
            if (current.size() < getRequiredParticipants(activeArena.getArena())) {
                cancel();
                activeArena.setSelecting(false);
                notifyWaitingForPlayers(activeArena, current);
                return;
            }

            if (ticksRemaining <= 20 && !finalized) {
                Player chosen = resolveBeast(activeArena, current, null);
                showFinalSelection(current, chosen);
                finalizeSelection(key, activeArena, current, chosen);
                finalized = true;
                cancel();
                return;
            }

            Player highlighted = selectCurrentPlayer(current);
            showWheelHighlight(current, highlighted);
            index++;
            ticksRemaining -= PERIOD_TICKS;
        }

        private Player selectCurrentPlayer(List<Player> players) {
            if (players.isEmpty()) {
                return null;
            }
            return players.get(index % players.size());
        }

        private void showWheelHighlight(List<Player> viewers, Player highlighted) {
            if (highlighted == null) {
                return;
            }
            String title = ChatColor.AQUA + "" + ChatColor.BOLD + highlighted.getName();
            String subtitle = ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Wheel of Fate";
            for (Player viewer : viewers) {
                viewer.sendTitle(title, subtitle, 0, 10, 0);
                viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.3f);
            }
        }

        private void showFinalSelection(List<Player> viewers, Player chosen) {
            if (chosen == null) {
                return;
            }
            String title = ChatColor.DARK_RED + "" + ChatColor.BOLD + chosen.getName();
            String subtitle = ChatColor.GOLD + "" + ChatColor.BOLD + "is the Beast!";
            for (Player viewer : viewers) {
                viewer.sendTitle(title, subtitle, 10, 80, 20);
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
            }
        }

        @Override
        public synchronized void cancel() throws IllegalStateException {
            super.cancel();
            if (handle != null) {
                activeArena.unregisterTask(handle);
            }
        }
    }

    private class TeleportCountdown extends BukkitRunnable {
        private final String key;
        private final ActiveArena activeArena;
        private final ArenaDefinition arena;
        private final Player beast;
        private int remaining;
        private BukkitTask handle;

        private TeleportCountdown(String key, ActiveArena activeArena, ArenaDefinition arena,
                                  Player beast, int seconds) {
            this.key = key;
            this.activeArena = activeArena;
            this.arena = arena;
            this.beast = beast;
            this.remaining = Math.max(seconds, 0);
        }

        private void start() {
            handle = runTaskTimer(plugin, 0L, 20L);
            activeArena.setMatchActive(true);
            activeArena.registerTask(handle);
        }

        @Override
        public void run() {
            List<Player> current = collectParticipants(activeArena);
            if (current.isEmpty()) {
                cancel();
                cleanupArena(key, activeArena);
                return;
            }

            if (remaining > 0) {
                displayTeleportCountdown(current, remaining);
                remaining--;
                return;
            }

            displayTeleportCountdown(current, 0);
            cancel();
            teleportParticipants(arena, current, beast);
            scheduleWallOpenings(key, activeArena, arena, beast, current);
        }

        private void displayTeleportCountdown(List<Player> players, int number) {
            String title;
            String subtitle;
            Sound sound;
            float pitch;

            if (number > 0) {
                title = ChatColor.GOLD + "" + ChatColor.BOLD + number;
                subtitle = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Teleporting soon...";
                sound = Sound.BLOCK_NOTE_BLOCK_HAT;
                pitch = 1.0f + (3 - Math.min(number, 3)) * 0.1f;
            } else {
                title = ChatColor.GREEN + "" + ChatColor.BOLD + "0";
                subtitle = ChatColor.AQUA + "" + ChatColor.BOLD + "Brace yourself!";
                sound = Sound.ENTITY_PLAYER_LEVELUP;
                pitch = 1.0f;
            }

            for (Player player : players) {
                player.sendTitle(title, subtitle, 0, 20, 0);
                player.playSound(player.getLocation(), sound, 1.0f, pitch);
            }
        }

        @Override
        public synchronized void cancel() throws IllegalStateException {
            super.cancel();
            if (handle != null) {
                activeArena.unregisterTask(handle);
            }
        }
    }

    private void finalizeSelection(String key, ActiveArena activeArena, List<Player> participants, Player selectedBeast) {
        List<Player> current = resolveParticipants(activeArena, participants);
        if (current.isEmpty()) {
            cleanupArena(key, activeArena);
            return;
        }

        ArenaDefinition arena = activeArena.getArena();
        if (!validateSpawns(current, arena)) {
            cleanupArena(key, activeArena);
            return;
        }

        Player beast = resolveBeast(activeArena, current, selectedBeast);
        activeArena.setSelecting(false);
        activeArena.setBeastId(beast != null ? beast.getUniqueId() : null);
        Set<UUID> runnerIds = new HashSet<>();
        for (Player player : current) {
            if (beast != null && player.equals(beast)) {
                continue;
            }
            runnerIds.add(player.getUniqueId());
        }
        activeArena.setRunners(runnerIds);
        removeExitTokens(current);
    announceBeast(current, beast);
    scheduleTeleportCountdownStart(key, activeArena, arena, beast);
    }

    private List<Player> resolveParticipants(ActiveArena activeArena, List<Player> participants) {
        if (participants != null && !participants.isEmpty()) {
            return new ArrayList<>(participants);
        }
        return collectParticipants(activeArena);
    }

    private boolean validateSpawns(List<Player> players, ArenaDefinition arena) {
        if (arena.getRunnerSpawn() != null && arena.getBeastSpawn() != null) {
            return true;
        }
        for (Player player : players) {
            send(player, ChatColor.RED + "Arena spawns are not configured.");
        }
        return false;
    }

    private Player resolveBeast(ActiveArena activeArena, List<Player> players, Player candidate) {
        if (candidate != null && players.contains(candidate)) {
            RolePreference preference = activeArena.getPreference(candidate.getUniqueId());
            if (preference != RolePreference.RUNNER) {
                return candidate;
            }
        }

        List<Player> preferredBeasts = new ArrayList<>();
        for (Player player : players) {
            if (activeArena.getPreference(player.getUniqueId()) == RolePreference.BEAST) {
                preferredBeasts.add(player);
            }
        }
        if (!preferredBeasts.isEmpty()) {
            return preferredBeasts.get(ThreadLocalRandom.current().nextInt(preferredBeasts.size()));
        }

        List<Player> eligible = new ArrayList<>();
        for (Player player : players) {
            if (activeArena.getPreference(player.getUniqueId()) != RolePreference.RUNNER) {
                eligible.add(player);
            }
        }
        if (!eligible.isEmpty()) {
            return eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        }

        if (players.size() == 1) {
            return null; // practice run
        }

        return players.get(ThreadLocalRandom.current().nextInt(players.size()));
    }

    private void announceBeast(List<Player> players, Player beast) {
        if (beast == null) {
            for (Player viewer : players) {
                String title = ChatColor.GREEN + "" + ChatColor.BOLD + "Practice Run";
                String subtitle = ChatColor.YELLOW + "" + ChatColor.BOLD + "No Beast this round.";
                viewer.sendTitle(title, subtitle, 10, 60, 20);
                viewer.sendMessage(prefix + ChatColor.YELLOW + "" + ChatColor.BOLD + "Practice run!" + ChatColor.RESET + ChatColor.YELLOW + " No Beast this time.");
                viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            }
            return;
        }

    for (Player viewer : players) {
        boolean isBeast = viewer.equals(beast);
        String title = isBeast
            ? ChatColor.DARK_RED + "" + ChatColor.BOLD + "You are the Beast!"
            : ChatColor.DARK_RED + "" + ChatColor.BOLD + beast.getName();
        String subtitle = isBeast
            ? ChatColor.GOLD + "" + ChatColor.BOLD + "Track them down!"
            : ChatColor.GOLD + "" + ChatColor.BOLD + "is the Beast!";
        int stay = isBeast ? 100 : 60;
        viewer.sendTitle(title, subtitle, 10, stay, 20);
        viewer.sendMessage(prefix + ChatColor.GOLD + "" + ChatColor.BOLD + beast.getName() + ChatColor.RED + "" + ChatColor.BOLD + " is the Beast!");
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.7f);
    }
    }

    private void scheduleWallOpenings(String key, ActiveArena activeArena, ArenaDefinition arena, Player beast, List<Player> participants) {
        int runnerDelay = Math.max(arena.getRunnerWallDelaySeconds(), 0);
        int beastDelay = Math.max(arena.getBeastReleaseDelaySeconds(), 0);

        Runnable runnerOpenAction = () -> {
            List<Player> current = collectParticipants(activeArena);
            if (current.isEmpty()) {
                cleanupArena(key, activeArena);
                return;
            }
            broadcastGo(current);
            openRunnerGate(activeArena, arena);
            if (beast == null) {
                sendPracticeReminder(current);
                cleanupArena(key, activeArena, false);
            } else {
                scheduleBeastRelease(key, activeArena, arena, beastDelay, beast, current);
            }
        };

        if (runnerDelay <= 0) {
            runnerOpenAction.run();
            return;
        }

    sendToPlayers(participants, ChatColor.AQUA + "" + ChatColor.BOLD + "Runner gate opens in "
        + ChatColor.WHITE + formatSeconds(runnerDelay) + ChatColor.AQUA + "." + ChatColor.RESET);
        broadcastReady(participants);
        startCountdown(key, activeArena, runnerDelay,
                    this::announceRunnerCountdown,
                runnerOpenAction);
    }

    private void scheduleBeastRelease(String key, ActiveArena activeArena, ArenaDefinition arena, int beastDelay,
                                      Player beast, List<Player> participantsAtRelease) {
        if (beast == null) {
            return;
        }

        Runnable beastOpenAction = () -> {
            List<Player> current = collectParticipants(activeArena);
            if (current.isEmpty()) {
                cleanupArena(key, activeArena);
                return;
            }
            broadcastBeastRelease(current, beast);
            openBeastGate(activeArena, arena, beast);
        };

        if (beastDelay <= 0) {
            applyBeastReleaseEffects(activeArena, beast);
            beastOpenAction.run();
            return;
        }

    sendToPlayers(participantsAtRelease, ChatColor.DARK_RED + "" + ChatColor.BOLD + "Beast gate opens in "
        + ChatColor.WHITE + formatSeconds(beastDelay) + ChatColor.DARK_RED + "." + ChatColor.RESET);

        startCountdown(key, activeArena, beastDelay,
                (players, seconds) -> {
                    announceBeastCountdown(players, seconds, beast);
                    if (seconds == 1) {
                        applyBeastReleaseEffects(activeArena, beast);
                    }
                },
                beastOpenAction);
    }

    private void applyBeastReleaseEffects(ActiveArena activeArena, Player beast) {
        if (beast == null || !beast.isOnline()) {
            return;
        }
        PotionEffect speed = new PotionEffect(PotionEffectType.SPEED, LONG_EFFECT_DURATION_TICKS, 0, false, false, true);
        beast.addPotionEffect(speed);
        applyFireResistance(beast);
        activeArena.setBeastId(beast.getUniqueId());
    }

    private void applyFireResistance(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PotionEffect fireResistance = new PotionEffect(PotionEffectType.FIRE_RESISTANCE, LONG_EFFECT_DURATION_TICKS, 0, false, false, true);
        player.addPotionEffect(fireResistance);
    }

    private void openRunnerGate(ActiveArena activeArena, ArenaDefinition arena) {
        if (!activeArena.isRunnerWallOpened()) {
            if (arena.getRunnerWall() != null) {
                if (activeArena.getRunnerWallSnapshot() == null) {
                    activeArena.setRunnerWallSnapshot(captureBlockStates(arena.getRunnerWall()));
                }
                setCuboidToAir(arena.getRunnerWall());
            }
            activeArena.setRunnerWallOpened(true);
            activeArena.releaseDamageProtectionAfter(1000L);
        }
    }

    private void openBeastGate(ActiveArena activeArena, ArenaDefinition arena, Player beast) {
        if (!activeArena.isBeastWallOpened()) {
            if (arena.getBeastWall() != null) {
                if (activeArena.getBeastWallSnapshot() == null) {
                    activeArena.setBeastWallSnapshot(captureBlockStates(arena.getBeastWall()));
                }
                setCuboidToAir(arena.getBeastWall());
            }
            activeArena.setBeastWallOpened(true);
        }
        if (beast != null && beast.isOnline()) {
            send(beast, ChatColor.DARK_RED + "You are free! Hunt them down!");
        }
    }

    private void startCountdown(String key, ActiveArena activeArena, int seconds,
                                BiConsumer<List<Player>, Integer> announcer, Runnable completion) {
        if (seconds <= 0) {
            completion.run();
            return;
        }

        CountdownRunnable runnable = new CountdownRunnable(key, activeArena, seconds, announcer, completion);
        runnable.start();
    }

    private class CountdownRunnable extends BukkitRunnable {
        private final String key;
        private final ActiveArena activeArena;
        private int remaining;
        private final BiConsumer<List<Player>, Integer> announcer;
        private final Runnable completion;
        private BukkitTask handle;

        private CountdownRunnable(String key, ActiveArena activeArena, int seconds,
                                  BiConsumer<List<Player>, Integer> announcer, Runnable completion) {
            this.key = key;
            this.activeArena = activeArena;
            this.remaining = seconds;
            this.announcer = announcer;
            this.completion = completion;
        }

        private void start() {
            handle = runTaskTimer(plugin, 0L, 20L);
            activeArena.registerTask(handle);
        }

        @Override
        public void run() {
            List<Player> current = collectParticipants(activeArena);
            if (current.isEmpty()) {
                cancel();
                cleanupArena(key, activeArena);
                return;
            }

            if (remaining <= 0) {
                cancel();
                completion.run();
                return;
            }

            announcer.accept(current, remaining);
            remaining--;
        }

        @Override
        public synchronized void cancel() throws IllegalStateException {
            super.cancel();
            if (handle != null) {
                activeArena.unregisterTask(handle);
            }
        }
    }

    private void announceRunnerCountdown(List<Player> players, int seconds) {
        if (seconds > 3) {
            return;
        }
        for (Player player : players) {
            String title = ChatColor.GOLD + "" + ChatColor.BOLD + seconds + "...";
            String subtitle = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Get ready!";
            player.sendTitle(title, subtitle, 0, 20, 0);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    private void announceBeastCountdown(List<Player> players, int seconds, Player beast) {
        if (seconds > 3) {
            return;
        }
    String name = beast != null ? beast.getName() : DEFAULT_BEAST_NAME;
        for (Player player : players) {
            String title = ChatColor.DARK_RED + "" + ChatColor.BOLD + seconds + "...";
            String subtitle = ChatColor.GOLD + "" + ChatColor.BOLD + name;
            player.sendTitle(title, subtitle, 0, 20, 0);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
        }
    }

    private void broadcastReady(List<Player> players) {
        for (Player player : players) {
            String title = ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "READY?";
            String subtitle = ChatColor.GRAY + "" + ChatColor.BOLD + "Hold the line.";
            player.sendTitle(title, subtitle, 0, 30, 0);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
        }
    }

    private void broadcastGo(List<Player> players) {
        for (Player player : players) {
            String title = ChatColor.GREEN + "" + ChatColor.BOLD + "GO!";
            String subtitle = ChatColor.WHITE + "" + ChatColor.BOLD + "Run for your life!";
            player.sendTitle(title, subtitle, 0, 20, 5);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }

    private void broadcastBeastRelease(List<Player> players, Player beast) {
    String name = beast != null ? beast.getName() : DEFAULT_BEAST_NAME;
        for (Player player : players) {
            String title = ChatColor.DARK_RED + "" + ChatColor.BOLD + name;
            String subtitle = ChatColor.GOLD + "" + ChatColor.BOLD + "is los!";
            player.sendTitle(title, subtitle, 0, 40, 10);
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
        }
    }

    private void sendPracticeReminder(List<Player> players) {
        for (Player player : players) {
            send(player, ChatColor.YELLOW + "" + ChatColor.BOLD + "Practice run active!" + ChatColor.RESET
                    + ChatColor.YELLOW + " Use /beastmode cancel <arena> when you are ready to reset the gates.");
        }
    }

    private void sendToPlayers(List<Player> players, String message) {
        for (Player player : players) {
            send(player, message);
        }
    }

    private int getQueueLimit(ArenaDefinition arena) {
        if (arena == null) {
            return Integer.MAX_VALUE;
        }
        int maxRunners = arena.getMaxRunners();
        if (maxRunners <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(maxRunners + 1, 1);
    }

    private int getRequiredParticipants(ArenaDefinition arena) {
        if (arena == null) {
            return 2;
        }
        int minRunners = Math.max(arena.getMinRunners(), 1);
        return Math.max(minRunners + 1, 2);
    }

    private int getMissingParticipantsCount(int participantCount, ArenaDefinition arena) {
        int required = getRequiredParticipants(arena);
        return Math.max(0, required - participantCount);
    }

    private String formatPlayerCount(int count) {
        return count + " more player" + (count == 1 ? "" : "s");
    }

    private String formatRunnerCount(int count) {
        return count + " runner" + (count == 1 ? "" : "s");
    }

    private String formatSeconds(int seconds) {
        if (seconds <= 0) {
            return "0 seconds";
        }
        return seconds + " " + (seconds == 1 ? "second" : "seconds");
    }

    private String formatPreference(RolePreference preference) {
        return switch (preference) {
            case RUNNER -> ChatColor.AQUA + "runner" + ChatColor.RESET;
            case BEAST -> ChatColor.DARK_RED + "beast" + ChatColor.RESET;
            default -> ChatColor.GRAY + "any" + ChatColor.RESET;
        };
    }

    private String highlightArena(String arenaName) {
        return ChatColor.AQUA + arenaName + ChatColor.RESET;
    }

    private void setCuboidToAir(Cuboid cuboid) {
        if (cuboid == null) {
            return;
        }
        World world = cuboid.getWorld();
        if (world == null) {
            return;
        }
        Location min = cuboid.getMin();
        Location max = cuboid.getMax();
        int minX = (int) Math.floor(min.getX());
        int minY = (int) Math.floor(min.getY());
        int minZ = (int) Math.floor(min.getZ());
        int maxX = (int) Math.floor(max.getX());
        int maxY = (int) Math.floor(max.getY());
        int maxZ = (int) Math.floor(max.getZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    block.setType(Material.AIR, false);
                }
            }
        }
    }

    private List<BlockState> captureBlockStates(Cuboid cuboid) {
        List<BlockState> states = new ArrayList<>();
        if (cuboid == null) {
            return states;
        }
        World world = cuboid.getWorld();
        if (world == null) {
            return states;
        }
        Location min = cuboid.getMin();
        Location max = cuboid.getMax();
        int minX = (int) Math.floor(min.getX());
        int minY = (int) Math.floor(min.getY());
        int minZ = (int) Math.floor(min.getZ());
        int maxX = (int) Math.floor(max.getX());
        int maxY = (int) Math.floor(max.getY());
        int maxZ = (int) Math.floor(max.getZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    states.add(block.getState());
                }
            }
        }
        return states;
    }

    private void restoreBlockStates(List<BlockState> states) {
        if (states == null) {
            return;
        }
        for (BlockState state : states) {
            if (state != null) {
                state.update(true, false);
            }
        }
    }

    private void teleportParticipants(ArenaDefinition arena, List<Player> players, Player beast) {
        Location beastLocation = arena.getBeastSpawn().clone();
        Location runnerLocation = arena.getRunnerSpawn().clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (beast != null && beast.isOnline()) {
                beast.teleport(beastLocation);
                beast.setGameMode(GameMode.ADVENTURE);
                restorePlayerVitals(beast);
            }
            for (Player runner : players) {
                if (beast != null && runner.equals(beast)) {
                    continue;
                }
                if (runner.isOnline()) {
                    runner.teleport(runnerLocation);
                    runner.setGameMode(GameMode.ADVENTURE);
                    restorePlayerVitals(runner);
                }
            }
            equipBeast(beast);
        });
    }

    private void equipBeast(Player beast) {
        if (beast == null || !beast.isOnline()) {
            return;
        }

        PlayerInventory inventory = beast.getInventory();
        inventory.clear();

        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        inventory.setItem(0, sword);
        beast.getInventory().setHeldItemSlot(0);

        ItemStack potion = createHealingPotion();
        inventory.setItem(1, potion.clone());
        inventory.setItem(2, potion.clone());
        inventory.setItem(3, potion.clone());

        inventory.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        inventory.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        inventory.setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));

        ItemStack boots = new ItemStack(Material.DIAMOND_BOOTS);
        var bootsMeta = boots.getItemMeta();
        if (bootsMeta != null) {
            Enchantment featherFalling = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("feather_falling"));
            if (featherFalling != null) {
                bootsMeta.addEnchant(featherFalling, 4, true);
            }
            bootsMeta.setUnbreakable(true);
            boots.setItemMeta(bootsMeta);
        }
        inventory.setBoots(boots);

        send(beast, ChatColor.DARK_RED + "" + ChatColor.BOLD + "You gear up in unbreakable armor.");
    }

    private ItemStack createExitToken() {
        ItemStack stack = new ItemStack(Material.NETHER_STAR);
        var meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Leave Hunt");
            meta.setLore(List.of(ChatColor.GRAY + "Right-click to leave the queue."));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(exitTokenKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void giveExitToken(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack current = inventory.getItem(EXIT_TOKEN_SLOT);
        if (isExitToken(current)) {
            return;
        }
        ItemStack token = exitTokenTemplate.clone();
        inventory.setItem(EXIT_TOKEN_SLOT, token);
    }

    private void removeExitToken(Player player) {
        if (player == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isExitToken(item)) {
                inventory.clear(slot);
            }
        }
    }

    private void removeExitTokens(Collection<Player> players) {
        if (players == null) {
            return;
        }
        for (Player player : players) {
            removeExitToken(player);
        }
    }

    public boolean isExitToken(ItemStack stack) {
        if (stack == null || stack.getType() != Material.NETHER_STAR) {
            return false;
        }
        var meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(exitTokenKey, PersistentDataType.BYTE);
    }

    private ItemStack createHealingPotion() {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        if (meta != null) {
            meta.setBasePotionType(PotionType.STRONG_HEALING);
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Burst Heal");
            potion.setItemMeta(meta);
        }
        return potion;
    }

    private ItemStack createEnchantedBow(int powerLevel) {
        ItemStack bow = new ItemStack(Material.BOW);
        var bowMeta = bow.getItemMeta();
        if (bowMeta != null) {
            Enchantment infinity = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("infinity"));
            Enchantment power = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("power"));
            if (infinity != null) {
                bowMeta.addEnchant(infinity, 1, true);
            }
            if (power != null) {
                bowMeta.addEnchant(power, Math.max(1, powerLevel), true);
            }
            bow.setItemMeta(bowMeta);
        }
        return bow;
    }

    private void scheduleRunnerReward(Player runner) {
        if (runner == null) {
            return;
        }

        UUID runnerId = runner.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player online = Bukkit.getPlayer(runnerId);
            if (online != null && online.isOnline()) {
                grantRunnerReward(online);
            }
        }, 20L);
    }

    private void grantRunnerReward(Player runner) {
        if (runner == null || !runner.isOnline()) {
            return;
        }

        resetPlayerLoadout(runner);
        restorePlayerVitals(runner);

        PlayerInventory inventory = runner.getInventory();

        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        var swordMeta = sword.getItemMeta();
        if (swordMeta != null) {
            Enchantment sharpness = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness"));
            if (sharpness != null) {
                swordMeta.addEnchant(sharpness, 3, true);
            }
            sword.setItemMeta(swordMeta);
        }
        inventory.setItem(0, sword);
        runner.getInventory().setHeldItemSlot(0);

    ItemStack bow = createEnchantedBow(1);
    inventory.setItem(1, bow);

        ItemStack potion = createHealingPotion();
        for (int slot = 2; slot <= 6; slot++) {
            inventory.setItem(slot, potion.clone());
        }

        ItemStack arrow = new ItemStack(Material.ARROW, 1);
        inventory.setItem(17, arrow);

        inventory.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        inventory.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        inventory.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        var bootsMeta = boots.getItemMeta();
        if (bootsMeta != null) {
            Enchantment featherFalling = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("feather_falling"));
            if (featherFalling != null) {
                bootsMeta.addEnchant(featherFalling, 4, true);
            }
            bootsMeta.setUnbreakable(true);
            boots.setItemMeta(bootsMeta);
        }
        inventory.setBoots(boots);

        PotionEffect speedBoost = new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false, true);
    runner.removePotionEffect(PotionEffectType.SPEED);
    runner.addPotionEffect(speedBoost);

        send(runner, ChatColor.GOLD + "" + ChatColor.BOLD + "Victory spoils! "
                + ChatColor.RESET + ChatColor.GOLD + "You bolted through the finish and are ready for the Beast.");
    }

    private void cleanupArena(String key, ActiveArena activeArena) {
        cleanupArena(key, activeArena, true);
    }

    private void cleanupArena(String key, ActiveArena activeArena, boolean restoreWalls) {
        activeArena.cancelTasks();
        clearBeastEffects(activeArena);
        if (restoreWalls) {
            resetArenaState(activeArena);
            activeArena.clearPlayers();
            activeArena.setRunning(false);
            activeArenas.remove(key);
            return;
        }

        activeArena.clearPlayers();
        activeArena.setRunning(false);
    }

    private void clearBeastEffects(ActiveArena activeArena) {
        if (activeArena == null) {
            return;
        }
        UUID beastId = activeArena.getBeastId();
        if (beastId == null) {
            return;
        }

        Player beast = Bukkit.getPlayer(beastId);
        if (beast != null) {
            beast.removePotionEffect(PotionEffectType.SPEED);
            beast.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        }
    }

    private void resetArenaState(ActiveArena activeArena) {
        if (activeArena.getRunnerWallSnapshot() != null) {
            restoreBlockStates(activeArena.getRunnerWallSnapshot());
            activeArena.setRunnerWallSnapshot(null);
        }
        if (activeArena.getBeastWallSnapshot() != null) {
            restoreBlockStates(activeArena.getBeastWallSnapshot());
            activeArena.setBeastWallSnapshot(null);
        }
        activeArena.setRunnerWallOpened(false);
        activeArena.setBeastWallOpened(false);
    }

    private List<Player> collectParticipants(ActiveArena activeArena) {
        List<Player> participants = new ArrayList<>();
        Iterator<UUID> iterator = activeArena.getPlayerIds().iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                iterator.remove();
                activeArena.removePreference(id);
                activeArena.removeRunner(id);
                continue;
            }
            participants.add(player);
        }
        return participants;
    }

    private int countActivePlayers(ActiveArena activeArena) {
        if (activeArena == null) {
            return 0;
        }
        int count = 0;
        for (UUID id : activeArena.getPlayerIds()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                count++;
            }
        }
        return count;
    }

    private void send(Player player, String message) {
        player.sendMessage(prefix + message);
    }

    private void restorePlayerVitals(Player player) {
        if (player == null) {
            return;
        }

        var attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHealth = attribute != null ? attribute.getValue() : player.getHealthScale();
        if (Double.isNaN(maxHealth) || maxHealth <= 0) {
            maxHealth = 20.0;
        }

        player.setHealth(Math.min(maxHealth, player.getHealth()));
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExhaustion(0.0f);
        if (player.getHealth() < maxHealth) {
            player.setHealth(maxHealth);
        }
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

    public static final class ArenaStatus {
        private final String arenaName;
        private final boolean complete;
        private final int playerCount;
        private final int capacity;
        private final boolean running;
        private final boolean selecting;
        private final boolean matchActive;

        private ArenaStatus(String arenaName, boolean complete, int playerCount, int capacity,
                             boolean running, boolean selecting, boolean matchActive) {
            this.arenaName = arenaName;
            this.complete = complete;
            this.playerCount = playerCount;
            this.capacity = capacity;
            this.running = running;
            this.selecting = selecting;
            this.matchActive = matchActive;
        }

        private static ArenaStatus unavailable(String arenaName) {
            return new ArenaStatus(arenaName, false, 0, -1, false, false, false);
        }

        public String getArenaName() {
            return arenaName;
        }

        public boolean isComplete() {
            return complete;
        }

        public int getPlayerCount() {
            return playerCount;
        }

        public int getCapacity() {
            return capacity;
        }

        public boolean hasCapacityLimit() {
            return capacity >= 0;
        }

        public boolean isRunning() {
            return running;
        }

        public boolean isSelecting() {
            return selecting;
        }

        public boolean isMatchActive() {
            return matchActive;
        }

        public boolean isBusy() {
            return running || selecting || matchActive;
        }
    }

    private static class ActiveArena {
        private final ArenaDefinition arena;
        private final Set<UUID> players = new LinkedHashSet<>();
        private final Set<UUID> runners = new HashSet<>();
        private final Set<BukkitTask> trackedTasks = new HashSet<>();
        private final Set<UUID> spectatingRunners = new HashSet<>();
        private final Map<UUID, RolePreference> preferences = new ConcurrentHashMap<>();
        private List<BlockState> runnerWallSnapshot;
        private List<BlockState> beastWallSnapshot;
        private boolean running;
        private boolean runnerWallOpened;
        private boolean beastWallOpened;
        private boolean selecting;
        private boolean matchActive;
        private boolean finalPhase;
    private boolean rewardSuppressed;
    private long invulnerabilityUntilMillis;
        private UUID beastId;

        private ActiveArena(ArenaDefinition arena) {
            this.arena = arena;
        }

        private ArenaDefinition getArena() {
            return arena;
        }

        private boolean addPlayer(Player player) {
            return players.add(player.getUniqueId());
        }

        private boolean contains(UUID uuid) {
            return players.contains(uuid);
        }

        private Set<UUID> getPlayerIds() {
            return players;
        }

        private void clearPlayers() {
            players.clear();
            preferences.clear();
            clearMatchState();
        }

        private void registerTask(BukkitTask task) {
            if (task != null) {
                trackedTasks.add(task);
            }
        }

        private void unregisterTask(BukkitTask task) {
            if (task != null) {
                trackedTasks.remove(task);
            }
        }

        private void cancelTasks() {
            for (BukkitTask task : new HashSet<>(trackedTasks)) {
                task.cancel();
            }
            trackedTasks.clear();
        }

        private boolean isRunning() {
            return running;
        }

        private void setRunning(boolean running) {
            this.running = running;
        }

        private boolean isSelecting() {
            return selecting;
        }

        private void setSelecting(boolean selecting) {
            this.selecting = selecting;
        }

        private boolean isMatchActive() {
            return matchActive;
        }

        private void setMatchActive(boolean matchActive) {
            this.matchActive = matchActive;
        }

        private boolean isFinalPhase() {
            return finalPhase;
        }

        private void setFinalPhase(boolean finalPhase) {
            this.finalPhase = finalPhase;
        }

        private boolean isRewardSuppressed() {
            return rewardSuppressed;
        }

        private void setRewardSuppressed(boolean rewardSuppressed) {
            this.rewardSuppressed = rewardSuppressed;
        }

        private void enableDamageProtection() {
            invulnerabilityUntilMillis = Long.MAX_VALUE;
        }

        private void releaseDamageProtectionAfter(long delayMillis) {
            invulnerabilityUntilMillis = System.currentTimeMillis() + Math.max(delayMillis, 0L);
        }

        private void clearDamageProtection() {
            invulnerabilityUntilMillis = 0L;
        }

        private boolean isDamageProtectionActive() {
            if (invulnerabilityUntilMillis == 0L) {
                return false;
            }
            if (invulnerabilityUntilMillis == Long.MAX_VALUE) {
                return true;
            }
            if (System.currentTimeMillis() <= invulnerabilityUntilMillis) {
                return true;
            }
            invulnerabilityUntilMillis = 0L;
            return false;
        }

        private UUID getBeastId() {
            return beastId;
        }

        private void setBeastId(UUID beastId) {
            this.beastId = beastId;
        }

        private void setRunners(Collection<UUID> runnerIds) {
            runners.clear();
            if (runnerIds != null) {
                runners.addAll(runnerIds);
            }
        }

        private boolean isRunner(UUID uuid) {
            return runners.contains(uuid);
        }

        private boolean removeRunner(UUID uuid) {
            return runners.remove(uuid);
        }

        private boolean hasRunners() {
            return !runners.isEmpty();
        }

        private int getRunnerCount() {
            return runners.size();
        }

        private boolean removePlayer(UUID uuid) {
            boolean removed = players.remove(uuid);
            preferences.remove(uuid);
            runners.remove(uuid);
            if (beastId != null && beastId.equals(uuid)) {
                beastId = null;
            }
            spectatingRunners.remove(uuid);
            return removed;
        }

        private void addSpectatingRunner(UUID uuid) {
            if (uuid != null) {
                spectatingRunners.add(uuid);
            }
        }

        private void removeSpectatingRunner(UUID uuid) {
            if (uuid != null) {
                spectatingRunners.remove(uuid);
            }
        }

        private boolean isSpectatingRunner(UUID uuid) {
            return uuid != null && spectatingRunners.contains(uuid);
        }

        private boolean isRunnerWallOpened() {
            return runnerWallOpened;
        }

        private void setRunnerWallOpened(boolean runnerWallOpened) {
            this.runnerWallOpened = runnerWallOpened;
        }

        private boolean isBeastWallOpened() {
            return beastWallOpened;
        }

        private void setBeastWallOpened(boolean beastWallOpened) {
            this.beastWallOpened = beastWallOpened;
        }

        private void setPreference(UUID uuid, RolePreference preference) {
            if (preference == null || preference == RolePreference.ANY) {
                preferences.remove(uuid);
            } else {
                preferences.put(uuid, preference);
            }
        }

        private RolePreference getPreference(UUID uuid) {
            return preferences.getOrDefault(uuid, RolePreference.ANY);
        }

        private void removePreference(UUID uuid) {
            preferences.remove(uuid);
        }

        private void clearMatchState() {
            selecting = false;
            matchActive = false;
            finalPhase = false;
            rewardSuppressed = false;
            clearDamageProtection();
            beastId = null;
            runners.clear();
            spectatingRunners.clear();
        }

        private List<BlockState> getRunnerWallSnapshot() {
            return runnerWallSnapshot;
        }

        private void setRunnerWallSnapshot(List<BlockState> runnerWallSnapshot) {
            this.runnerWallSnapshot = runnerWallSnapshot;
        }

        private List<BlockState> getBeastWallSnapshot() {
            return beastWallSnapshot;
        }

        private void setBeastWallSnapshot(List<BlockState> beastWallSnapshot) {
            this.beastWallSnapshot = beastWallSnapshot;
        }
    }
}
