package com.colin.beastmode.listeners;

import com.colin.beastmode.game.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class GameListener implements Listener {

    private final GameManager gameManager;

    public GameListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        gameManager.handlePlayerMove(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        gameManager.handlePlayerDeath(event.getEntity());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        gameManager.handlePlayerQuit(event.getPlayer());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        gameManager.handlePlayerQuit(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        gameManager.handlePlayerJoin(event.getPlayer());
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        Action action = event.getAction();

        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if (hand == EquipmentSlot.HAND && gameManager.isPreferenceSelector(main)) {
                event.setCancelled(true);
                gameManager.handlePreferenceItemUse(player, main);
                return;
            }
            if (hand == EquipmentSlot.OFF_HAND && gameManager.isPreferenceSelector(off)) {
                event.setCancelled(true);
                gameManager.handlePreferenceItemUse(player, off);
                return;
            }
        }

        boolean usingExitToken = false;
        if (hand == EquipmentSlot.HAND) {
            usingExitToken = gameManager.isExitToken(main);
        } else if (hand == EquipmentSlot.OFF_HAND) {
            usingExitToken = gameManager.isExitToken(off);
        } else {
            usingExitToken = gameManager.isExitToken(main) || gameManager.isExitToken(off);
        }

        boolean inArena = gameManager.isPlayerInArena(player.getUniqueId());
        Block clickedBlock = event.getClickedBlock();

        if (usingExitToken && inArena) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && isJoinSign(clickedBlock)) {
                // Allow the join sign to process without triggering the exit token.
            } else {
                event.setCancelled(true);
                gameManager.handleSpawnCommand(player);
                return;
            }
        }

        if (hand == EquipmentSlot.OFF_HAND) {
            return;
        }

        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (clickedBlock == null) {
            return;
        }
        gameManager.handlePlayerInteract(player, clickedBlock);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || message.isEmpty()) {
            return;
        }

        String withoutSlash = message.charAt(0) == '/' ? message.substring(1) : message;
        if (withoutSlash.isEmpty()) {
            return;
        }

        String commandName = withoutSlash.split(" ", 2)[0];
        int namespaceIndex = commandName.lastIndexOf(':');
        if (namespaceIndex >= 0 && namespaceIndex + 1 < commandName.length()) {
            commandName = commandName.substring(namespaceIndex + 1);
        }

        if (!commandName.equalsIgnoreCase("spawn")) {
            return;
        }

        if (gameManager.handleSpawnCommand(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (gameManager.shouldCancelDamage(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        if (gameManager.isManagedItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (gameManager.isManagedItem(event.getCurrentItem()) || gameManager.isManagedItem(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        for (ItemStack stack : event.getNewItems().values()) {
            if (gameManager.isManagedItem(stack)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean isJoinSign(Block block) {
        if (block == null) {
            return false;
        }
        if (!(block.getState() instanceof Sign sign)) {
            return false;
        }
        SignSide front = sign.getSide(Side.FRONT);
        String header = front.getLine(0);
        if (header == null) {
            return false;
        }
        return ChatColor.stripColor(header).equalsIgnoreCase("[beastmode]");
    }
}
