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

    GameManager.ArenaStatus status = gameManager.getArenaStatus(arena.getName());
    applyLines(event, status);
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
            applyMissing(front);
            applyMissing(sign.getSide(Side.BACK));
            sign.update();
            player.sendMessage(prefix + ChatColor.RED + "Arena '" + arenaName + "' no longer exists.");
            return;
        }

        updateSign(sign, arena.getName());
        gameManager.joinArena(player, arena.getName());
        updateSign(sign, arena.getName());
    }

    private void applyLines(SignChangeEvent event, GameManager.ArenaStatus status) {
        event.setLine(0, SIGN_KEY);
        event.setLine(1, ChatColor.AQUA + status.getArenaName());
        event.setLine(2, formatPlayerLine(status));
        event.setLine(3, formatStatusLine(status));
    }

    private void updateSign(Sign sign, String arenaName) {
        GameManager.ArenaStatus status = gameManager.getArenaStatus(arenaName);
        applyLines(sign.getSide(Side.FRONT), status);
        applyLines(sign.getSide(Side.BACK), status);
        sign.update();
    }

    private void applyLines(SignSide side, GameManager.ArenaStatus status) {
        if (side == null) {
            return;
        }
        side.setLine(0, SIGN_KEY);
        side.setLine(1, ChatColor.AQUA + status.getArenaName());
        side.setLine(2, formatPlayerLine(status));
        side.setLine(3, formatStatusLine(status));
    }

    private String formatPlayerLine(GameManager.ArenaStatus status) {
        int count = status.getPlayerCount();
        if (status.hasCapacityLimit()) {
            return ChatColor.YELLOW + "Players: " + count + "/" + status.getCapacity();
        }
        return ChatColor.YELLOW + "Players: " + count;
    }

    private String formatStatusLine(GameManager.ArenaStatus status) {
        if (!status.isComplete()) {
            return ChatColor.RED + "Status: Setup";
        }
        if (status.isBusy()) {
            return ChatColor.RED + "Status: In-Game";
        }
        return ChatColor.GREEN + "Status: Ready";
    }

    private void applyMissing(SignSide side) {
        if (side == null) {
            return;
        }
        side.setLine(0, SIGN_KEY);
        side.setLine(1, ChatColor.RED + "Unknown");
        side.setLine(2, ChatColor.DARK_RED + "Arena missing");
        side.setLine(3, ChatColor.RED + "Status: Offline");
    }
}
