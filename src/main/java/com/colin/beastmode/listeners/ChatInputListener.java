package com.colin.beastmode.listeners;

import com.colin.beastmode.Beastmode;
import com.colin.beastmode.setup.SetupSession;
import com.colin.beastmode.setup.SetupSessionManager;
import com.colin.beastmode.setup.SetupStage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatInputListener implements Listener {

    private final Beastmode plugin;
    private final SetupSessionManager sessionManager;

    public ChatInputListener(Beastmode plugin, SetupSessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        SetupSession session = sessionManager.getSession(player);
        if (session == null) {
            return;
        }

        String message = event.getMessage().trim();
        if (message.equalsIgnoreCase("cancel")) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> sessionManager.cancelSession(player));
            return;
        }

        SetupStage stage = session.getStage();
        if (!stage.expectsChatNumber()) {
            return;
        }

        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                int value = Integer.parseInt(message);
                sessionManager.handleNumericInput(player, value);
            } catch (NumberFormatException ex) {
                sessionManager.sendPrefixed(player, ChatColor.RED + "Please enter a valid whole number.");
            }
        });
    }
}
