package com.colin.beastmode.listeners;

import com.colin.beastmode.game.GameManager;
import com.colin.beastmode.model.ArenaDefinition;
import com.colin.beastmode.storage.ArenaStorage;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class SignListener implements Listener {

    private static final String SIGN_KEY = ChatColor.BLUE + "[Beastmode]";
    private final ArenaStorage arenaStorage;
    private final GameManager gameManager;
    private final String prefix;

    public SignListener(ArenaStorage arenaStorage, GameManager gameManager, String prefix) {
        this.arenaStorage = arenaStorage;
        this.gameManager = gameManager;
        this.prefix = prefix;
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        String rawHeader = event.getLine(0);
        if (rawHeader == null || !rawHeader.equalsIgnoreCase("[beastmode]")) {
            return;
        }

        if (!player.hasPermission("beastmode.command")) {
            player.sendMessage(prefix + ChatColor.RED + "You do not have permission to create Beastmode signs.");
            return;
        }

        String arenaName = event.getLine(1) != null ? event.getLine(1).trim() : "";
        if (arenaName.isEmpty()) {
            player.sendMessage(prefix + ChatColor.RED + "Specify an arena name on the second line.");
            event.setLine(0, SIGN_KEY);
            event.setLine(1, ChatColor.RED + "<arena>");
            return;
        }

        ArenaDefinition arena = arenaStorage.getArena(arenaName);
        if (arena == null) {
            player.sendMessage(prefix + ChatColor.RED + "Arena '" + arenaName + "' does not exist.");
            event.setLine(0, SIGN_KEY);
            event.setLine(1, ChatColor.RED + "Unknown");
            event.setLine(2, ChatColor.GRAY + "Click to join");
            return;
        }

        event.setLine(0, SIGN_KEY);
        event.setLine(1, ChatColor.AQUA + arena.getName());
        event.setLine(2, ChatColor.YELLOW + "Click to join");
        event.setLine(3, ChatColor.GREEN + "Status: "
                + (arena.isComplete() ? ChatColor.GREEN + "Ready" : ChatColor.RED + "Setup"));
        player.sendMessage(prefix + ChatColor.GREEN + "Join sign created for arena '" + arena.getName() + "'.");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        if (!(block.getState() instanceof Sign sign)) {
            return;
        }

        SignSide front = sign.getSide(Side.FRONT);
        String header = front.getLine(0);
        if (header == null || !ChatColor.stripColor(header).equalsIgnoreCase("[beastmode]")) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        String arenaName = ChatColor.stripColor(front.getLine(1));
        if (arenaName == null || arenaName.isBlank()) {
            player.sendMessage(prefix + ChatColor.RED + "This sign is missing an arena name.");
            return;
        }

        ArenaDefinition arena = arenaStorage.getArena(arenaName);
        if (arena == null) {
            player.sendMessage(prefix + ChatColor.RED + "Arena '" + arenaName + "' no longer exists.");
            return;
        }

        gameManager.joinArena(player, arena.getName());
    }
}
