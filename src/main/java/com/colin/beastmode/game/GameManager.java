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
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class GameManager {

    private final Beastmode plugin;
    private final ArenaStorage arenaStorage;
    private final Map<String, ActiveArena> activeArenas = new ConcurrentHashMap<>();
    private final Set<UUID> pendingSpawnTeleports = ConcurrentHashMap.newKeySet();
    private final String prefix;
    private final NamespacedKey exitTokenKey;
    private final NamespacedKey preferenceKey;
    private final ItemStack exitTokenTemplate;
    private final List<Consumer<String>> statusListeners = new CopyOnWriteArrayList<>();
    private final PlayerSupportService playerSupport;
    private final ArenaBarrierService barrierService;
    private final CountdownService countdowns;
    private final RoleSelectionService roleSelection;
    private final MatchSetupService matchSetup;
    private final ArenaMessagingService messaging;
    private final MatchFlowService matchFlow;
    private static final String MSG_ARENA_NOT_FOUND = "Arena %s does not exist.";
    private static final String MSG_ARENA_INCOMPLETE = "Arena %s is not fully configured yet.";
    private static final String MSG_ARENA_NOT_RUNNING = "Arena %s is not currently running.";
    private static final String PERM_PREFERENCE_VIP = "beastmode.preference.vip";
    private static final String PERM_PREFERENCE_NJOG = "beastmode.preference.njog";
    private static final String DEFAULT_BEAST_NAME = "The Beast";
    static final int EXIT_TOKEN_SLOT = 8;
    static final int PREFERENCE_BEAST_SLOT = 0;
    static final int PREFERENCE_RUNNER_SLOT = 1;
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
        this.preferenceKey = new NamespacedKey(plugin, "preference_selector");
    this.exitTokenTemplate = createExitToken();
    this.playerSupport = new PlayerSupportService(plugin, prefix, LONG_EFFECT_DURATION_TICKS,
        exitTokenKey, preferenceKey, exitTokenTemplate);
    this.barrierService = new ArenaBarrierService();
    this.countdowns = new CountdownService(plugin);
    this.roleSelection = new RoleSelectionService(PERM_PREFERENCE_VIP, PERM_PREFERENCE_NJOG);
    this.matchSetup = new MatchSetupService(this.playerSupport, prefix);
    this.messaging = new ArenaMessagingService(prefix, DEFAULT_BEAST_NAME);
    this.matchFlow = new MatchFlowService(plugin, countdowns, barrierService, playerSupport,
        messaging, prefix, LONG_EFFECT_DURATION_TICKS);
    }

    public void registerStatusListener(Consumer<String> listener) {
        if (listener != null) {
            statusListeners.add(listener);
        }
    }

    public void unregisterStatusListener(Consumer<String> listener) {
        if (listener != null) {
            statusListeners.remove(listener);
        }
    }

    private void notifyStatusListeners(String arenaName) {
        if (arenaName == null) {
            return;
        }
        String trimmed = arenaName.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        for (Consumer<String> listener : statusListeners) {
            listener.accept(trimmed);
        }
    }

    private void notifyArenaStatus(ActiveArena activeArena) {
        if (activeArena == null || activeArena.getArena() == null) {
            return;
        }
        notifyStatusListeners(activeArena.getArena().getName());
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
        if (preference != null && preference != RolePreference.ANY && !canChoosePreference(player)) {
            preference = RolePreference.ANY;
        }

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
            if (canChoosePreference(player)) {
                playerSupport.givePreferenceSelectors(player, activeArena.getPreference(player.getUniqueId()));
            } else {
                playerSupport.clearPreferenceSelectors(player);
            }
            notifyArenaStatus(activeArena);
            return;
        }

    playerSupport.resetLoadout(player);
        if (!activeArena.isMatchActive()) {
            giveExitToken(player);
        }
        if (canChoosePreference(player)) {
            playerSupport.givePreferenceSelectors(player, activeArena.getPreference(player.getUniqueId()));
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
            notifyArenaStatus(activeArena);
            return;
        }
        startMatch(key, activeArena);
        notifyArenaStatus(activeArena);
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
        boolean wasRunner = activeArena.isRunner(uuid);
        if (wasRunner) {
            if (!activeArena.removeRunner(uuid)) {
                return;
            }

            playerSupport.resetLoadout(player);
            boolean finished = handleRunnerElimination(key, activeArena, player);
            if (!finished) {
                sendToSpectator(activeArena, player, deathLocation);
            }
            return;
        }

        UUID beastId = activeArena.getBeastId();
        if (beastId != null && beastId.equals(uuid)) {
            playerSupport.resetLoadout(player);
            if (!activeArena.isFinalPhase()) {
                activeArena.setRewardSuppressed(true);
            }
            completeRunnerVictory(key, activeArena, null);
        }
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
            if (!activeArena.isRunner(finisher.getUniqueId())) {
                return;
            }

            if (activeArena.isFinalPhase()) {
                rewardAdditionalFinisher(activeArena, finisher);
                return;
            }

            activeArena.setFinalPhase(true);

            String finisherName = finisher.getName();
            String title = ChatColor.GOLD + "" + ChatColor.BOLD + "Parkour Complete!";
            String subtitle = ChatColor.AQUA + finisherName + ChatColor.YELLOW + " finished the parkour and is ready to slay the Beast!";

            for (Player participant : participants) {
                boolean isFinisher = participant.getUniqueId().equals(finisher.getUniqueId());
                boolean isBeast = beastId != null && beastId.equals(participant.getUniqueId());

                participant.sendTitle(title, subtitle, 10, 60, 10);
                participant.playSound(participant.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

                if (isFinisher) {
                    send(participant, ChatColor.GOLD + "" + ChatColor.BOLD + finisherName + ChatColor.RESET
                            + ChatColor.YELLOW + " finished the parkour and is ready to slay the Beast!");
                    playerSupport.applyFireResistance(participant);
                    playerSupport.scheduleRunnerReward(participant);
                    continue;
                }

                if (isBeast) {
                    send(participant, ChatColor.DARK_RED + "" + ChatColor.BOLD + finisherName
                            + ChatColor.RED + " raided the weapon cache! Stop them before they strike back.");
                    continue;
                }

                send(participant, ChatColor.YELLOW + "Keep moving! "
                        + ChatColor.AQUA + finisherName + ChatColor.YELLOW + " found the armory and is gearing up.");
            }
            return;
        }

        boolean finalPhase = activeArena.isFinalPhase();
        activeArena.setMatchActive(false);
        notifyArenaStatus(activeArena);

        String title = ChatColor.GREEN + "" + ChatColor.BOLD + "Runner Victory!";
        String subtitle = finalPhase
                ? ChatColor.AQUA + "The Beast has been defeated."
                : ChatColor.AQUA + "The Beast never made it out.";

        for (Player participant : participants) {
            boolean isRunner = activeArena.isRunner(participant.getUniqueId());
            participant.sendTitle(title, subtitle, 10, 60, 10);
            participant.playSound(participant.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            if (isRunner && !finalPhase && !activeArena.isRewardSuppressed()) {
                playerSupport.scheduleRunnerReward(participant);
            }

            participant.removePotionEffect(PotionEffectType.SPEED);
            participant.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
            playerSupport.resetLoadout(participant);
            sendPlayerToSpawn(activeArena, participant);
        }

        cleanupArena(key, activeArena);
    }

    private void rewardAdditionalFinisher(ActiveArena activeArena, Player finisher) {
        if (activeArena == null || finisher == null || !finisher.isOnline()) {
            return;
        }

        List<Player> participants = collectParticipants(activeArena);
        if (participants.isEmpty()) {
            return;
        }

        UUID beastId = activeArena.getBeastId();
        String finisherName = finisher.getName();
        String title = ChatColor.GOLD + "" + ChatColor.BOLD + "Parkour Complete!";
        String subtitle = ChatColor.AQUA + finisherName + ChatColor.YELLOW + " stocked up for the fight!";

        for (Player participant : participants) {
            boolean isFinisher = participant.getUniqueId().equals(finisher.getUniqueId());
            boolean isBeast = beastId != null && beastId.equals(participant.getUniqueId());

            if (isFinisher) {
                participant.sendTitle(title, subtitle, 10, 60, 10);
                participant.playSound(participant.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                send(participant, ChatColor.GOLD + "" + ChatColor.BOLD + "Locked and loaded! "
                        + ChatColor.RESET + ChatColor.GOLD + "Help slay the Beast.");
                playerSupport.applyFireResistance(participant);
                playerSupport.scheduleRunnerReward(participant);
                continue;
            }

            if (isBeast) {
                send(participant, ChatColor.DARK_RED + "" + ChatColor.BOLD + finisherName
                        + ChatColor.RED + " armed up as well. Keep the pressure on!");
                continue;
            }

            send(participant, ChatColor.YELLOW + finisherName + " has stocked the armory. Reinforcements are coming!");
        }
    }

    private void completeBeastVictory(String key, ActiveArena activeArena, Player beast) {
        activeArena.setMatchActive(false);
        notifyArenaStatus(activeArena);
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
            playerSupport.resetLoadout(participant);
            sendPlayerToSpawn(activeArena, participant);
        }

        cleanupArena(key, activeArena);
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

        playerSupport.resetLoadout(player);
        playerSupport.restoreVitals(player);
        player.setGameMode(GameMode.ADVENTURE);

        activeArena.removePlayer(uuid);
        pendingSpawnTeleports.add(uuid);
        removeExitToken(player);
        notifyArenaStatus(activeArena);

        if (!activeArena.isMatchActive()) {
            List<Player> remaining = collectParticipants(activeArena);
            if (remaining.isEmpty()) {
                cleanupArena(key, activeArena);
                return;
            }
            int missing = getMissingParticipantsCount(remaining.size(), activeArena.getArena());
            if (missing > 0 && activeArena.isSelecting()) {
                activeArena.setSelecting(false);
                notifyArenaStatus(activeArena);
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

        playerSupport.resetLoadout(player);
        player.setGameMode(GameMode.ADVENTURE);
        activeArena.removePlayer(uuid);
        removeExitToken(player);
        notifyArenaStatus(activeArena);

        if (!activeArena.isMatchActive()) {
            List<Player> remaining = collectParticipants(activeArena);
            if (remaining.isEmpty()) {
                cleanupArena(key, activeArena);
            } else {
                int missing = getMissingParticipantsCount(remaining.size(), activeArena.getArena());
                if (missing > 0 && activeArena.isSelecting()) {
                    activeArena.setSelecting(false);
                    notifyArenaStatus(activeArena);
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
            playerSupport.restoreVitals(player);
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

    public boolean canChoosePreference(Player player) {
        return roleSelection.canChoosePreference(player);
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

    return new ArenaStatus(arena.getName(), true, arena.isComplete(), playerCount, capacity,
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
        notifyArenaStatus(activeArena);
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
        playerSupport.restoreVitals(participant);
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
            notifyArenaStatus(activeArena);
            notifyWaitingForPlayers(activeArena, participants);
            return;
        }

        if (activeArena.isSelecting()) {
            return;
        }

        activeArena.setSelecting(true);
        notifyArenaStatus(activeArena);
        countdowns.startSelectionCountdown(activeArena, 10, 5,
                () -> collectParticipants(activeArena),
                () -> getRequiredParticipants(activeArena.getArena()),
                players -> {
                    activeArena.setSelecting(false);
                    notifyArenaStatus(activeArena);
                    notifyWaitingForPlayers(activeArena, players);
                },
                messaging::selectionCountdown,
                () -> startWheelSelection(key, activeArena, 5),
                () -> cleanupArena(key, activeArena));
    }

    private void startWheelSelection(String key, ActiveArena activeArena, int durationSeconds) {
        countdowns.startWheelSelection(activeArena, durationSeconds,
                () -> collectParticipants(activeArena),
                () -> getRequiredParticipants(activeArena.getArena()),
                players -> {
                    activeArena.setSelecting(false);
                    notifyArenaStatus(activeArena);
                    notifyWaitingForPlayers(activeArena, players);
                },
                participants -> roleSelection.selectBeast(activeArena, participants, null),
                messaging::wheelHighlight,
                messaging::wheelFinal,
                (participants, chosen) -> finalizeSelection(key, activeArena, participants, chosen),
                () -> cleanupArena(key, activeArena));
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

    private void finalizeSelection(String key, ActiveArena activeArena, List<Player> participants, Player selectedBeast) {
        List<Player> current = matchSetup.resolveParticipants(activeArena, participants);
        if (current.isEmpty()) {
            cleanupArena(key, activeArena);
            return;
        }

        ArenaDefinition arena = activeArena.getArena();
        if (!matchSetup.validateSpawns(current, arena)) {
            cleanupArena(key, activeArena);
            return;
        }

        Player beast = roleSelection.selectBeast(activeArena, current, selectedBeast);
        matchSetup.assignRoles(activeArena, current, beast);
        notifyArenaStatus(activeArena);
        messaging.announceBeast(current, beast);
        matchFlow.scheduleMatchStart(activeArena, arena, beast,
                () -> collectParticipants(activeArena),
                restoreWalls -> cleanupArena(key, activeArena, restoreWalls),
                () -> {
                    activeArena.setMatchActive(true);
                    notifyArenaStatus(activeArena);
                });
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

    private void removeExitToken(Player player) {
        playerSupport.removeExitToken(player);
    }

    private void giveExitToken(Player player) {
        playerSupport.giveExitToken(player);
    }

    public boolean isExitToken(ItemStack stack) {
        return playerSupport.isExitToken(stack);
    }

    public boolean isPreferenceSelector(ItemStack stack) {
        return playerSupport.isPreferenceSelector(stack);
    }

    public boolean isManagedItem(ItemStack stack) {
        return playerSupport.isExitToken(stack) || playerSupport.isPreferenceSelector(stack);
    }

    public void handlePreferenceItemUse(Player player, ItemStack stack) {
        RolePreference desired = playerSupport.readPreferenceType(stack);
        if (player == null || desired == null) {
            return;
        }
        if (!canChoosePreference(player)) {
            send(player, ChatColor.RED + "You do not have permission to choose a role preference.");
            return;
        }

        String key = findArenaByPlayer(player.getUniqueId());
        if (key == null) {
            send(player, ChatColor.RED + "Join an arena queue before choosing a preference.");
            return;
        }

        ActiveArena activeArena = activeArenas.get(key);
        if (activeArena == null) {
            return;
        }

        RolePreference current = activeArena.getPreference(player.getUniqueId());
        RolePreference next;
        if (current == desired) {
            next = RolePreference.ANY;
            send(player, ChatColor.YELLOW + "Preference cleared. Odds returned to " + formatPreference(next) + ChatColor.YELLOW + ".");
        } else {
            next = desired;
            send(player, ChatColor.GOLD + "Preference set to " + formatPreference(next) + ChatColor.GOLD + ".");
        }

        activeArena.setPreference(player.getUniqueId(), next);
        playerSupport.givePreferenceSelectors(player, next);
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

    private void cleanupArena(String key, ActiveArena activeArena) {
        cleanupArena(key, activeArena, true);
    }

    private void cleanupArena(String key, ActiveArena activeArena, boolean restoreWalls) {
        String arenaName = null;
        if (activeArena != null && activeArena.getArena() != null) {
            arenaName = activeArena.getArena().getName();
        } else if (key != null) {
            arenaName = key;
        }
        if (activeArena == null) {
            if (arenaName != null && !arenaName.trim().isEmpty()) {
                notifyStatusListeners(arenaName);
            }
            if (key != null) {
                activeArenas.remove(key);
            }
            return;
        }

        activeArena.cancelTasks();
        clearBeastEffects(activeArena);
        if (restoreWalls) {
            resetArenaState(activeArena);
            activeArena.clearPlayers();
            activeArena.setRunning(false);
            activeArenas.remove(key);
            notifyStatusListeners(arenaName);
            return;
        }

        activeArena.clearPlayers();
        activeArena.setRunning(false);
        notifyStatusListeners(arenaName);
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
            barrierService.restore(activeArena.getRunnerWallSnapshot());
            activeArena.setRunnerWallSnapshot(null);
        }
        if (activeArena.getBeastWallSnapshot() != null) {
            barrierService.restore(activeArena.getBeastWallSnapshot());
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
