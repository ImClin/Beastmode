package com.colin.beastmode.game;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Manages player preference item interactions and messaging.
 */
final class PlayerPreferenceService {

    private final Map<String, ActiveArena> activeArenas;
    private final Function<UUID, String> arenaLookup;
    private final PlayerSupportService playerSupport;
    private final RoleSelectionService roleSelection;
    private final String prefix;

    PlayerPreferenceService(Map<String, ActiveArena> activeArenas,
                            Function<UUID, String> arenaLookup,
                            PlayerSupportService playerSupport,
                            RoleSelectionService roleSelection,
                            String prefix) {
        this.activeArenas = activeArenas;
        this.arenaLookup = arenaLookup;
        this.playerSupport = playerSupport;
        this.roleSelection = roleSelection;
        this.prefix = prefix;
    }

    void handlePreferenceItemUse(Player player, ItemStack stack) {
        if (player == null) {
            return;
        }

        GameManager.RolePreference desired = playerSupport.readPreferenceType(stack);
        if (desired == null) {
            return;
        }

        if (!roleSelection.canChoosePreference(player)) {
            send(player, ChatColor.RED + "You do not have permission to choose a role preference.");
            return;
        }

        String key = arenaLookup.apply(player.getUniqueId());
        if (key == null) {
            send(player, ChatColor.RED + "Join an arena queue before choosing a preference.");
            return;
        }

        ActiveArena activeArena = activeArenas.get(key);
        if (activeArena == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        GameManager.RolePreference current = activeArena.getPreference(playerId);
        GameManager.RolePreference next;
        if (current == desired) {
            next = GameManager.RolePreference.ANY;
            send(player, ChatColor.YELLOW + "Preference cleared. Odds returned to "
                + formatPreference(next) + ChatColor.YELLOW + ".");
        } else {
            next = desired;
            send(player, ChatColor.GOLD + "Preference set to " + formatPreference(next) + ChatColor.GOLD + ".");
        }

        activeArena.setPreference(playerId, next);
        playerSupport.givePreferenceSelectors(player, next);
    }

    private String formatPreference(GameManager.RolePreference preference) {
        return switch (preference) {
            case RUNNER -> ChatColor.AQUA + "runner" + ChatColor.RESET;
            case BEAST -> ChatColor.DARK_RED + "beast" + ChatColor.RESET;
            default -> ChatColor.GRAY + "any" + ChatColor.RESET;
        };
    }

    private void send(Player player, String message) {
        if (player != null) {
            player.sendMessage(prefix + message);
        }
    }
}
