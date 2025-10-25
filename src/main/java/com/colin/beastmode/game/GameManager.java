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
        String prefix = plugin.getConfig().getString("messages.prefix", "[Beastmode] ");
        NamespacedKey exitTokenKey = new NamespacedKey(plugin, "exit_token");
        NamespacedKey preferenceKey = new NamespacedKey(plugin, "preference_selector");
        ItemStack exitTokenTemplate = createExitToken(exitTokenKey);
        GameServices services = GameServices.create(plugin, arenaStorage, prefix, exitTokenKey,
            preferenceKey, exitTokenTemplate, LONG_EFFECT_DURATION_TICKS, DEFAULT_BEAST_NAME,
            PERM_PREFERENCE_VIP, PERM_PREFERENCE_NJOG);

        this.arenaDirectory = services.arenaDirectory;
        this.statusService = services.statusService;
        this.playerSupport = services.playerSupport;
        this.roleSelection = services.roleSelection;
        this.preferenceService = services.preferenceService;
        this.departureService = services.departureService;
        this.completionService = services.completionService;
        this.eliminationService = services.eliminationService;
        this.orchestration = services.orchestration;
        this.queueService = services.queueService;
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
