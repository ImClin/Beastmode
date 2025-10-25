package com.colin.beastmode.game;

import com.colin.beastmode.Beastmode;
import com.colin.beastmode.model.ArenaDefinition;
import com.colin.beastmode.model.Cuboid;
import com.colin.beastmode.storage.ArenaStorage;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class GameManager {

    private final ArenaStorage arenaStorage;
    private final Map<String, ActiveArena> activeArenas = new ConcurrentHashMap<>();
    private final String prefix;
    private final NamespacedKey exitTokenKey;
    private final NamespacedKey preferenceKey;
    private final ItemStack exitTokenTemplate;
    private final List<Consumer<String>> statusListeners = new CopyOnWriteArrayList<>();
    private final PlayerSupportService playerSupport;
    private final ArenaWaitingService waitingService;
    private final ArenaLifecycleService arenaLifecycle;
    private final ArenaBarrierService barrierService;
    private final CountdownService countdowns;
    private final RoleSelectionService roleSelection;
    private final MatchSetupService matchSetup;
    private final ArenaMessagingService messaging;
    private final PlayerTransitionService playerTransitions;
    private final MatchOutcomeService matchOutcome;
    private final MatchFlowService matchFlow;
    private final MatchSelectionService selectionService;
    private final ArenaDepartureService departureService;
    private final ArenaQueueService queueService;
    static final String MSG_ARENA_NOT_FOUND = "Arena %s does not exist.";
    static final String MSG_ARENA_INCOMPLETE = "Arena %s is not fully configured yet.";
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
        this.arenaStorage = arenaStorage;
        this.prefix = plugin.getConfig().getString("messages.prefix", "[Beastmode] ");
        this.exitTokenKey = new NamespacedKey(plugin, "exit_token");
        this.preferenceKey = new NamespacedKey(plugin, "preference_selector");
        this.exitTokenTemplate = createExitToken();
        this.playerSupport = new PlayerSupportService(plugin, prefix, LONG_EFFECT_DURATION_TICKS,
            exitTokenKey, preferenceKey, exitTokenTemplate);
        this.waitingService = new ArenaWaitingService(this.playerSupport, this.prefix);
        this.barrierService = new ArenaBarrierService();
        this.arenaLifecycle = new ArenaLifecycleService(this.activeArenas, this.barrierService, this::notifyStatusListeners);
        this.countdowns = new CountdownService(plugin);
        this.roleSelection = new RoleSelectionService(PERM_PREFERENCE_VIP, PERM_PREFERENCE_NJOG);
        this.matchSetup = new MatchSetupService(this.playerSupport, prefix);
        this.messaging = new ArenaMessagingService(prefix, DEFAULT_BEAST_NAME);
        this.playerTransitions = new PlayerTransitionService(plugin, this.playerSupport);
        this.matchOutcome = new MatchOutcomeService(prefix, DEFAULT_BEAST_NAME, this.playerSupport, playerTransitions);
        this.matchFlow = new MatchFlowService(plugin, countdowns, barrierService, playerSupport,
            messaging, prefix, LONG_EFFECT_DURATION_TICKS);
        this.selectionService = new MatchSelectionService(this.countdowns, this.roleSelection, this.matchSetup,
            this.messaging, this.matchFlow, this.waitingService, this.arenaLifecycle, this::notifyArenaStatus);
        this.departureService = new ArenaDepartureService(this.prefix, this.playerSupport, this.playerTransitions,
            this.waitingService, this.arenaLifecycle, this.matchOutcome, this::notifyArenaStatus);
        this.queueService = new ArenaQueueService(this.arenaStorage, this.activeArenas,
            this.playerSupport, this.roleSelection, this.waitingService, this.prefix);
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

    void notifyArenaStatus(ActiveArena activeArena) {
        if (activeArena == null || activeArena.getArena() == null) {
            return;
        }
        notifyStatusListeners(activeArena.getArena().getName());
    }

    public void joinArena(Player player, String arenaName) {
        joinArena(player, arenaName, RolePreference.ANY);
    }

    public void joinArena(Player player, String arenaName, RolePreference preference) {
        queueService.join(this, player, arenaName, preference);
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

    departureService.handleRunnerVictory(key, activeArena, player);
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

    departureService.handleRunnerVictory(key, activeArena, player);
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
            playerTransitions.sendPlayerToSpawn(activeArena, player);
            return;
        }

        Location deathLocation = player.getLocation() != null ? player.getLocation().clone() : null;
        boolean wasRunner = activeArena.isRunner(uuid);
        if (wasRunner) {
            if (!activeArena.removeRunner(uuid)) {
                return;
            }

            playerSupport.resetLoadout(player);
            boolean finished = departureService.handleRunnerElimination(key, activeArena, player);
            if (!finished) {
                playerTransitions.sendToSpectator(activeArena, player, deathLocation);
            }
            return;
        }

        UUID beastId = activeArena.getBeastId();
        if (beastId != null && beastId.equals(uuid)) {
            playerSupport.resetLoadout(player);
            if (!activeArena.isFinalPhase()) {
                activeArena.setRewardSuppressed(true);
            }
            departureService.handleRunnerVictory(key, activeArena, null);
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
        departureService.handlePlayerQuit(key, activeArena, player);
    }

    public void handlePlayerJoin(Player player) {
        if (player == null) {
            return;
        }

        departureService.handlePlayerJoin(player);
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
        return departureService.handleSpawnCommand(key, activeArena, player);
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

        List<Player> participants = arenaLifecycle.collectParticipants(activeArena);
        for (Player participant : participants) {
            send(participant, ChatColor.YELLOW + "The hunt was cancelled by " + player.getName() + ".");
        }

        arenaLifecycle.cleanupArena(key, activeArena);
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
            arenaLifecycle.resetArenaState(activeArena);
            activeArena.clearPlayers();
            activeArena.setRunning(false);
        }
        activeArenas.clear();
        departureService.shutdown();
    }

    String findArenaByPlayer(UUID uuid) {
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
            playerCount = arenaLifecycle.countActivePlayers(activeArena);
            running = activeArena.isRunning();
            selecting = activeArena.isSelecting();
            matchActive = activeArena.isMatchActive();
        }

        int capacity = waitingService.getQueueLimit(arena);
        if (capacity == Integer.MAX_VALUE) {
            capacity = -1;
        }

    return new ArenaStatus(arena.getName(), true, arena.isComplete(), playerCount, capacity,
        running, selecting, matchActive);
    }

    void startMatch(String key, ActiveArena activeArena) {
        if (activeArena.isRunning() || activeArena.isSelecting() || activeArena.isMatchActive()) {
            return;
        }

        List<Player> participants = arenaLifecycle.collectParticipants(activeArena);
        if (participants.isEmpty()) {
            arenaLifecycle.cleanupArena(key, activeArena);
            return;
        }

        activeArena.setRunning(true);
        activeArena.clearMatchState();
        activeArena.enableDamageProtection();
        notifyArenaStatus(activeArena);
        if (activeArena.isRunnerWallOpened() || activeArena.isBeastWallOpened()) {
            arenaLifecycle.resetArenaState(activeArena);
        }
        ArenaDefinition arena = activeArena.getArena();
        if (!waitingService.sendPlayersToWaiting(arena, participants)) {
            activeArena.setRunning(false);
            arenaLifecycle.cleanupArena(key, activeArena);
            return;
        }

        maybeStartCountdown(key, activeArena);
    }

    void maybeStartCountdown(String key, ActiveArena activeArena) {
        selectionService.maybeStartSelection(key, activeArena);
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
