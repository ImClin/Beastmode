package com.colin.beastmode.game;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Centralizes title/sound broadcasts for arena state transitions.
 */
final class ArenaMessagingService {

    private final String prefix;
    private final String defaultBeastName;

    ArenaMessagingService(String prefix, String defaultBeastName) {
        this.prefix = prefix;
        this.defaultBeastName = defaultBeastName;
    }

    void selectionCountdown(List<Player> players, int seconds) {
        if (!hasPlayers(players)) {
            return;
        }
        for (Player player : players) {
            player.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + seconds,
                    ChatColor.AQUA + "seconds until game starts", 0, 20, 0);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
        }
    }

    void wheelHighlight(List<Player> viewers, Player highlighted) {
        if (highlighted == null || !hasPlayers(viewers)) {
            return;
        }
        String title = ChatColor.AQUA + "" + ChatColor.BOLD + highlighted.getName();
        String subtitle = ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Wheel of Fate";
        for (Player viewer : viewers) {
            viewer.sendTitle(title, subtitle, 0, 10, 0);
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.3f);
        }
    }

    void wheelFinal(List<Player> viewers, Player chosen) {
        if (chosen == null || !hasPlayers(viewers)) {
            return;
        }
        String title = ChatColor.DARK_RED + "" + ChatColor.BOLD + chosen.getName();
        String subtitle = ChatColor.GOLD + "" + ChatColor.BOLD + "is the Beast!";
        for (Player viewer : viewers) {
            viewer.sendTitle(title, subtitle, 10, 80, 20);
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
        }
    }

    void teleportCountdown(List<Player> players, int number) {
        if (!hasPlayers(players)) {
            return;
        }

        String title;
        String subtitle;
        Sound sound;
        float pitch;

        if (number > 0) {
            title = ChatColor.GOLD + "" + ChatColor.BOLD + number;
            subtitle = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Teleporting soon...";
            sound = Sound.BLOCK_NOTE_BLOCK_HAT;
            pitch = 1.0f + (3 - Math.min(number, 3)) * 0.1f;
        } else {
            title = ChatColor.GREEN + "" + ChatColor.BOLD + "0";
            subtitle = ChatColor.AQUA + "" + ChatColor.BOLD + "Brace yourself!";
            sound = Sound.ENTITY_PLAYER_LEVELUP;
            pitch = 1.0f;
        }

        for (Player player : players) {
            player.sendTitle(title, subtitle, 0, 20, 0);
            player.playSound(player.getLocation(), sound, 1.0f, pitch);
        }
    }

    void runnerCountdown(List<Player> players, int seconds) {
        if (seconds > 3 || !hasPlayers(players)) {
            return;
        }
        for (Player player : players) {
            String title = ChatColor.GOLD + "" + ChatColor.BOLD + seconds + "...";
            String subtitle = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Get ready!";
            player.sendTitle(title, subtitle, 0, 20, 0);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    void beastCountdown(List<Player> players, int seconds, Player beast) {
        if (seconds > 3 || !hasPlayers(players)) {
            return;
        }
        String name = beast != null ? beast.getName() : defaultBeastName;
        for (Player player : players) {
            String title = ChatColor.DARK_RED + "" + ChatColor.BOLD + seconds + "...";
            String subtitle = ChatColor.GOLD + "" + ChatColor.BOLD + name;
            player.sendTitle(title, subtitle, 0, 20, 0);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
        }
    }

    void broadcastReady(List<Player> players) {
        if (!hasPlayers(players)) {
            return;
        }
        for (Player player : players) {
            String title = ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "READY?";
            String subtitle = ChatColor.GRAY + "" + ChatColor.BOLD + "Hold the line.";
            player.sendTitle(title, subtitle, 0, 30, 0);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
        }
    }

    void broadcastGo(List<Player> players) {
        if (!hasPlayers(players)) {
            return;
        }
        for (Player player : players) {
            String title = ChatColor.GREEN + "" + ChatColor.BOLD + "GO!";
            String subtitle = ChatColor.WHITE + "" + ChatColor.BOLD + "Run for your life!";
            player.sendTitle(title, subtitle, 0, 20, 5);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }

    void broadcastBeastRelease(List<Player> players, Player beast) {
        if (!hasPlayers(players)) {
            return;
        }
        String name = beast != null ? beast.getName() : defaultBeastName;
        for (Player player : players) {
            String title = ChatColor.DARK_RED + "" + ChatColor.BOLD + name;
            String subtitle = ChatColor.GOLD + "" + ChatColor.BOLD + "is los!";
            player.sendTitle(title, subtitle, 0, 40, 10);
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
        }
    }

    void practiceReminder(List<Player> players) {
        if (!hasPlayers(players)) {
            return;
        }
        for (Player player : players) {
            send(player, ChatColor.YELLOW + "" + ChatColor.BOLD + "Practice run active!" + ChatColor.RESET
                    + ChatColor.YELLOW + " Use /beastmode cancel <arena> when you are ready to reset the gates.");
        }
    }

    void announceBeast(List<Player> players, Player beast) {
        if (!hasPlayers(players)) {
            return;
        }

        if (beast == null) {
            for (Player viewer : players) {
                viewer.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "Practice Run",
                        ChatColor.YELLOW + "" + ChatColor.BOLD + "No Beast this round.", 10, 60, 20);
                send(viewer, ChatColor.YELLOW + "" + ChatColor.BOLD + "Practice run!" + ChatColor.RESET
                        + ChatColor.YELLOW + " No Beast this time.");
                viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            }
            return;
        }

        for (Player viewer : players) {
            boolean isBeast = viewer.equals(beast);
            String title = isBeast
                    ? ChatColor.DARK_RED + "" + ChatColor.BOLD + "You are the Beast!"
                    : ChatColor.DARK_RED + "" + ChatColor.BOLD + beast.getName();
            String subtitle = isBeast
                    ? ChatColor.GOLD + "" + ChatColor.BOLD + "Track them down!"
                    : ChatColor.GOLD + "" + ChatColor.BOLD + "is the Beast!";
            int stay = isBeast ? 100 : 60;
            viewer.sendTitle(title, subtitle, 10, stay, 20);
            send(viewer, ChatColor.GOLD + "" + ChatColor.BOLD + beast.getName() + ChatColor.RED + "" + ChatColor.BOLD + " is the Beast!");
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.7f);
        }
    }

    private boolean hasPlayers(List<Player> players) {
        return players != null && !players.isEmpty();
    }

    private void send(Player player, String message) {
        player.sendMessage(prefix + message);
    }
}
