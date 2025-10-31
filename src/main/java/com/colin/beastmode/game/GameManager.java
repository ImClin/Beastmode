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
import java.util.function.Consumer;

public class GameManager {

    private final ActiveArenaDirectory arenaDirectory;
    private final ArenaStatusService statusService;
    private final PlayerSupportService playerSupport;
    private final RoleSelectionService roleSelection;
    private final MatchOrchestrationService orchestration;
    private final PlayerPreferenceService preferenceService;
    private final ArenaDepartureService departureService;
    private final MatchCompletionService completionService;
    private final MatchEliminationService eliminationService;
    private final ArenaQueueService queueService;
    private final TimeTrialService timeTrials;
    static final String MSG_ARENA_NOT_FOUND = "Arena %s does not exist.";
    static final String MSG_ARENA_INCOMPLETE = "Arena %s is not fully configured yet.";
    private static final String PERM_PREFERENCE_VIP = "beastmode.preference.vip";
    private static final String PERM_PREFERENCE_NJOG = "beastmode.preference.njog";
    private static final String DEFAULT_BEAST_NAME = "The Beast";
    static final int EXIT_TOKEN_SLOT = 8;
    static final int PREFERENCE_BEAST_SLOT = 0;
    static final int PREFERENCE_RUNNER_SLOT = 1;
    static final int TIME_TRIAL_RESTART_SLOT = 0;
    private static final int LONG_EFFECT_DURATION_TICKS = 20 * 600;

    public enum RolePreference {
        ANY,
        RUNNER,
        BEAST
    }

    public GameManager(Beastmode plugin, ArenaStorage arenaStorage) {
        String prefix = plugin.getConfig().getString("messages.prefix", "[Beastmode] ");
        NamespacedKey exitTokenKey = new NamespacedKey(plugin, "exit_token");
        NamespacedKey preferenceKey = new NamespacedKey(plugin, "preference_selector");
        NamespacedKey restartKey = new NamespacedKey(plugin, "time_trial_restart");
        ItemStack exitTokenTemplate = createExitToken(exitTokenKey);
        ItemStack restartTokenTemplate = createRestartToken(restartKey);
        GameServices services = GameServices.create(plugin, arenaStorage, prefix, exitTokenKey,
                preferenceKey, restartKey, exitTokenTemplate, restartTokenTemplate, LONG_EFFECT_DURATION_TICKS,
                DEFAULT_BEAST_NAME, PERM_PREFERENCE_VIP, PERM_PREFERENCE_NJOG);

        this.arenaDirectory = services.arenaDirectory();
        this.statusService = services.statusService();
        this.playerSupport = services.playerSupport();
        this.roleSelection = services.roleSelection();
        this.preferenceService = services.preferenceService();
        this.departureService = services.departureService();
        this.completionService = services.completionService();
        this.eliminationService = services.eliminationService();
        this.orchestration = services.orchestration();
        this.queueService = services.queueService();
        this.timeTrials = services.timeTrials();
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
        queueService.join(player, arenaName, GameModeType.HUNT, preference);
    }

    public void joinTimeTrial(Player player, String arenaName) {
        queueService.joinTimeTrial(player, arenaName);
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

        ActiveArenaContext context = resolveActiveArena(player.getUniqueId());
        if (context == null) {
            return;
        }
        departureService.handlePlayerQuit(context.key(), context.arena(), player);
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

        ActiveArenaContext context = resolveActiveArena(player.getUniqueId());
        if (context == null) {
            return false;
        }
        return departureService.handleSpawnCommand(context.key(), context.arena(), player);
    }

    public boolean shouldCancelDamage(Player player) {
        if (player == null) {
            return false;
        }

        ActiveArenaContext context = resolveActiveArena(player.getUniqueId());
        if (context == null) {
            return false;
        }

        return context.arena().isDamageProtectionActive();
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

    private ActiveArenaContext resolveActiveArena(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        String key = findArenaByPlayer(uuid);
        if (key == null) {
            return null;
        }

        ActiveArena activeArena = arenaDirectory.get(key);
        if (activeArena == null) {
            return null;
        }

        return new ActiveArenaContext(key, activeArena);
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

    public TimeTrialService getTimeTrials() {
        return timeTrials;
    }

    public ArenaStatus getArenaStatus(String arenaName, GameModeType mode) {
        return orchestration.getArenaStatus(arenaName, mode);
    }

    void startMatch(String key, ActiveArena activeArena) {
        orchestration.startMatch(key, activeArena);
    }

    void maybeStartCountdown(String key, ActiveArena activeArena) {
        orchestration.maybeStartCountdown(key, activeArena);
    }

    private ItemStack createExitToken(NamespacedKey exitTokenKey) {
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

    private ItemStack createRestartToken(NamespacedKey restartKey) {
        ItemStack stack = new ItemStack(Material.CLOCK);
        var meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Restart Run");
            meta.setLore(List.of(
                    ChatColor.GRAY + "Right-click to start over.",
                    ChatColor.DARK_GRAY + "Only works in time trials."));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(restartKey, PersistentDataType.BYTE, (byte) 1);
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
        return playerSupport.isExitToken(stack)
                || playerSupport.isPreferenceSelector(stack)
                || playerSupport.isTimeTrialRestartItem(stack);
    }

    public void handlePreferenceItemUse(Player player, ItemStack stack) {
        preferenceService.handlePreferenceItemUse(player, stack);
    }

    public boolean isTimeTrialRestartItem(ItemStack stack) {
        return playerSupport.isTimeTrialRestartItem(stack);
    }

    public void handleTimeTrialRestart(Player player) {
        if (player == null) {
            return;
        }

        ActiveArenaContext context = resolveActiveArena(player.getUniqueId());
        if (context == null) {
            return;
        }

        timeTrials.restartRun(context.arena(), player);
    }

    private record ActiveArenaContext(String key, ActiveArena arena) {
    }
}
