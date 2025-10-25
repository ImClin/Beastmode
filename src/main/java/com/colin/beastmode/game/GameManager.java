package com.colin.beastmode.game;

import com.colin.beastmode.Beastmode;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class GameManager {

    private final ArenaStorage arenaStorage;
    private final ActiveArenaDirectory arenaDirectory;
    private final String prefix;
    private final NamespacedKey exitTokenKey;
    private final NamespacedKey preferenceKey;
    private final ItemStack exitTokenTemplate;
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
    private final ArenaStatusService statusService;
    private final MatchOrchestrationService orchestration;
    private final PlayerPreferenceService preferenceService;
    private final ArenaDepartureService departureService;
    private final MatchCompletionService completionService;
    private final MatchEliminationService eliminationService;
    private final ArenaQueueService queueService;
    static final String MSG_ARENA_NOT_FOUND = "Arena %s does not exist.";
    static final String MSG_ARENA_INCOMPLETE = "Arena %s is not fully configured yet.";
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
        ConcurrentHashMap<String, ActiveArena> arenaBacking = new ConcurrentHashMap<>();
        this.arenaDirectory = new ActiveArenaDirectory(arenaBacking);
        this.statusService = new ArenaStatusService();
        this.playerSupport = new PlayerSupportService(plugin, prefix, LONG_EFFECT_DURATION_TICKS,
            exitTokenKey, preferenceKey, exitTokenTemplate);
        this.waitingService = new ArenaWaitingService(this.playerSupport, this.prefix);
        this.barrierService = new ArenaBarrierService();
        this.arenaLifecycle = new ArenaLifecycleService(this.arenaDirectory, this.barrierService, statusService::notifyArenaName);
        this.countdowns = new CountdownService(plugin);
        this.roleSelection = new RoleSelectionService(PERM_PREFERENCE_VIP, PERM_PREFERENCE_NJOG);
        this.matchSetup = new MatchSetupService(this.playerSupport, prefix);
        this.messaging = new ArenaMessagingService(prefix, DEFAULT_BEAST_NAME);
        this.playerTransitions = new PlayerTransitionService(plugin, this.playerSupport);
        this.matchOutcome = new MatchOutcomeService(prefix, DEFAULT_BEAST_NAME, this.playerSupport, playerTransitions);
        this.matchFlow = new MatchFlowService(plugin, countdowns, barrierService, playerSupport,
            messaging, prefix, LONG_EFFECT_DURATION_TICKS);
        this.selectionService = new MatchSelectionService(this.countdowns, this.roleSelection, this.matchSetup,
            this.messaging, this.matchFlow, this.waitingService, this.arenaLifecycle, statusService::notifyArenaStatus);
        this.preferenceService = new PlayerPreferenceService(this.arenaDirectory,
            this.playerSupport, this.roleSelection, this.prefix);
        this.departureService = new ArenaDepartureService(this.prefix, this.playerSupport, this.playerTransitions,
            this.waitingService, this.arenaLifecycle, this.matchOutcome, statusService::notifyArenaStatus);
        this.completionService = new MatchCompletionService(this.arenaDirectory, this.departureService);
        this.eliminationService = new MatchEliminationService(this.arenaDirectory,
            this.playerSupport, this.playerTransitions, this.departureService);
        this.orchestration = new MatchOrchestrationService(this.arenaDirectory, this.arenaStorage, this.arenaLifecycle,
            this.waitingService, this.selectionService, this.departureService, this.statusService, this.prefix);
        this.queueService = new ArenaQueueService(this.arenaStorage, this.arenaDirectory,
            this.playerSupport, this.roleSelection, this.waitingService, this.orchestration, this.statusService, this.prefix);
    }

    public void registerStatusListener(Consumer<String> listener) {
        statusService.register(listener);
    }

    public void unregisterStatusListener(Consumer<String> listener) {
        statusService.unregister(listener);
    }

    void notifyArenaStatus(ActiveArena activeArena) {
        orchestration.notifyArenaStatus(activeArena);
    }

    public void joinArena(Player player, String arenaName) {
        joinArena(player, arenaName, RolePreference.ANY);
    }

    public void joinArena(Player player, String arenaName, RolePreference preference) {
    queueService.join(player, arenaName, preference);
    }

    public void handlePlayerMove(Player player, Location from, Location to) {
        completionService.handlePlayerMove(player, from, to);
    }

    public void handlePlayerInteract(Player player, Block block) {
        completionService.handlePlayerInteract(player, block);
    }

    public void handlePlayerDeath(Player player) {
        eliminationService.handlePlayerDeath(player);
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

    ActiveArena activeArena = arenaDirectory.get(key);
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

    ActiveArena activeArena = arenaDirectory.get(key);
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

    ActiveArena activeArena = arenaDirectory.get(key);
        if (activeArena == null) {
            return false;
        }

        return activeArena.isDamageProtectionActive();
    }

    public void cancelArena(Player player, String arenaName) {
        orchestration.cancelArena(player, arenaName);
    }

    public boolean hasActiveArena(String arenaName) {
        return orchestration.hasActiveArena(arenaName);
    }

    public void shutdown() {
        orchestration.shutdown();
    }

    String findArenaByPlayer(UUID uuid) {
        return arenaDirectory.findArenaByPlayer(uuid);
    }

    public boolean isPlayerInArena(UUID uuid) {
        return uuid != null && findArenaByPlayer(uuid) != null;
    }

    public boolean canChoosePreference(Player player) {
        return roleSelection.canChoosePreference(player);
    }

    public ArenaStatus getArenaStatus(String arenaName) {
        return orchestration.getArenaStatus(arenaName);
    }

    void startMatch(String key, ActiveArena activeArena) {
        orchestration.startMatch(key, activeArena);
    }

    void maybeStartCountdown(String key, ActiveArena activeArena) {
        orchestration.maybeStartCountdown(key, activeArena);
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
        preferenceService.handlePreferenceItemUse(player, stack);
    }

}
