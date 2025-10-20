package com.colin.beastmode.setup;

import com.colin.beastmode.Beastmode;
import com.colin.beastmode.model.ArenaDefinition;
import com.colin.beastmode.storage.ArenaStorage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SetupSessionManager {

    private final ArenaStorage arenaStorage;
    private final Map<UUID, SetupSession> sessions = new HashMap<>();
    private final NamespacedKey wandKey;
    private final String prefix;
    private static final String SAME_WORLD_MESSAGE = "Both corners must be in the same world.";

    public SetupSessionManager(Beastmode plugin, ArenaStorage arenaStorage) {
        this.arenaStorage = arenaStorage;
        this.wandKey = new NamespacedKey(plugin, "setup_wand");
        this.prefix = plugin.getConfig().getString("messages.prefix", "[Beastmode] ");
    }

    public boolean startSession(Player player, String arenaName) {
        if (!player.hasPermission("beastmode.command")) {
            send(player, ChatColor.RED + "You do not have permission to use this command.");
            return false;
        }
        if (arenaName == null || arenaName.trim().isEmpty()) {
            send(player, ChatColor.RED + "Please provide a valid arena name.");
            return false;
        }
        arenaName = arenaName.trim();
        if (!arenaName.matches("\\w{3,32}")) {
            send(player, ChatColor.RED + "Arena names must be 3-32 characters and contain only letters, numbers, and underscores.");
            return false;
        }
        if (sessions.containsKey(player.getUniqueId())) {
            send(player, ChatColor.RED + "You are already setting up an arena. Finish or cancel it first.");
            return false;
        }

        boolean replacing = arenaStorage.exists(arenaName);
        if (replacing) {
            send(player, ChatColor.YELLOW + "An arena with that name already exists. Completing this setup will overwrite it.");
        }

        SetupSession session = new SetupSession(player.getUniqueId(), arenaName);
        sessions.put(player.getUniqueId(), session);
        giveWand(player);
        send(player, ChatColor.GREEN + "Setup started for arena " + ChatColor.AQUA + arenaName + ChatColor.GREEN + ".");
        sendStageInstruction(player, session.getStage());
        return true;
    }

    public boolean handleBlockSelection(Player player, Location location) {
        SetupSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }

        SetupStage stage = session.getStage();
        if (!stage.expectsBlockSelection()) {
            send(player, ChatColor.RED + "You are not selecting blocks right now. Current stage: " + stage.name());
            return true;
        }

        Location blockLocation = blockLocation(location);
        switch (stage) {
            case RUNNER_WALL_POS1 -> {
                session.setRunnerWallPos1(blockLocation);
                send(player, ChatColor.GREEN + "Runner wall first corner saved.");
                advanceStage(session, SetupStage.RUNNER_WALL_POS2);
            }
            case RUNNER_WALL_POS2 -> {
                if (!sameWorld(session.getRunnerWallPos1(), blockLocation)) {
                    send(player, ChatColor.RED + SAME_WORLD_MESSAGE);
                    return true;
                }
                session.setRunnerWallPos2(blockLocation);
                send(player, ChatColor.GREEN + "Runner wall defined.");
                advanceStage(session, SetupStage.BEAST_WALL_POS1);
            }
            case BEAST_WALL_POS1 -> {
                session.setBeastWallPos1(blockLocation);
                send(player, ChatColor.GREEN + "Beast wall first corner saved.");
                advanceStage(session, SetupStage.BEAST_WALL_POS2);
            }
            case BEAST_WALL_POS2 -> {
                if (!sameWorld(session.getBeastWallPos1(), blockLocation)) {
                    send(player, ChatColor.RED + SAME_WORLD_MESSAGE);
                    return true;
                }
                session.setBeastWallPos2(blockLocation);
                send(player, ChatColor.GREEN + "Beast wall defined.");
                advanceStage(session, SetupStage.RUNNER_SPAWN);
            }
            case FINISH_BUTTON -> {
                if (!isButton(location.getBlock().getType())) {
                    send(player, ChatColor.RED + "Please click a button block to set the finish.");
                    return true;
                }
                session.setFinishButton(blockLocation);
                send(player, ChatColor.GREEN + "Finish button saved. Setup complete!");
                completeSession(player, session);
            }
            default -> send(player, ChatColor.RED + "Unhandled block selection stage: " + stage.name());
        }
        return true;
    }

    public boolean handleSpawnSelection(Player player, String arenaName, SetupSpawnType role) {
        SetupSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            send(player, ChatColor.RED + "You are not currently setting up an arena.");
            return false;
        }
        if (!session.getArenaName().equalsIgnoreCase(arenaName)) {
            send(player, ChatColor.RED + "You are setting up arena " + session.getArenaName() + ", not " + arenaName + ".");
            return false;
        }

        SetupStage stage = session.getStage();
        Location location = player.getLocation();
        switch (stage) {
            case RUNNER_SPAWN -> {
                if (role != SetupSpawnType.RUNNER) {
                    send(player, ChatColor.RED + "Set the runner spawn before the beast spawn.");
                    return false;
                }
                session.setRunnerSpawn(location.clone());
                send(player, ChatColor.GREEN + "Runner spawn saved.");
                advanceStage(session, SetupStage.BEAST_SPAWN);
                return true;
            }
            case BEAST_SPAWN -> {
                if (role != SetupSpawnType.BEAST) {
                    send(player, ChatColor.RED + "Set the beast spawn now.");
                    return false;
                }
                session.setBeastSpawn(location.clone());
                send(player, ChatColor.GREEN + "Beast spawn saved.");
                advanceStage(session, SetupStage.RUNNER_WALL_DELAY);
                return true;
            }
            default -> {
                send(player, ChatColor.RED + "Spawns are already configured for this stage.");
                return false;
            }
        }
    }

    public boolean handleNumericInput(Player player, int value) {
        SetupSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        SetupStage stage = session.getStage();
        if (!stage.expectsChatNumber()) {
            return false;
        }
        if (value < 0) {
            send(player, ChatColor.RED + "Please provide a positive number.");
            return true;
        }

        switch (stage) {
            case RUNNER_WALL_DELAY -> {
                session.setRunnerWallDelaySeconds(value);
                send(player, ChatColor.GREEN + "Runner wall delay set to " + value + " seconds.");
                advanceStage(session, SetupStage.BEAST_RELEASE_DELAY);
            }
            case BEAST_RELEASE_DELAY -> {
                session.setBeastReleaseDelaySeconds(value);
                send(player, ChatColor.GREEN + "Beast release delay set to " + value + " seconds.");
                advanceStage(session, SetupStage.FINISH_BUTTON);
            }
            default -> send(player, ChatColor.RED + "Unexpected numeric stage: " + stage.name());
        }
        return true;
    }

    public void cancelSession(Player player) {
        SetupSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            send(player, ChatColor.YELLOW + "Setup for arena " + session.getArenaName() + " has been cancelled.");
            removeWand(player);
        }
    }

    public void endAllSessions() {
        Collection<UUID> ids = sessions.keySet();
        for (UUID id : ids.toArray(new UUID[0])) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                cancelSession(player);
            } else {
                sessions.remove(id);
            }
        }
    }

    public SetupSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public void sendPrefixed(Player player, String message) {
        send(player, message);
    }

    public boolean isSetupWand(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.BLAZE_ROD) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        Byte marker = container.get(wandKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void advanceStage(SetupSession session, SetupStage nextStage) {
        session.setStage(nextStage);
        Player player = Bukkit.getPlayer(session.getPlayerId());
        if (player != null) {
            sendStageInstruction(player, nextStage);
        }
    }

    private void sendStageInstruction(Player player, SetupStage stage) {
        send(player, ChatColor.AQUA + stage.getFriendlyDescription());
    }

    private void send(Player player, String message) {
        player.sendMessage(prefix + message);
    }

    private boolean isButton(Material material) {
        return material != null && material.name().endsWith("_BUTTON");
    }

    private boolean sameWorld(Location first, Location second) {
        return first != null && second != null && first.getWorld() != null && first.getWorld().equals(second.getWorld());
    }

    private Location blockLocation(Location location) {
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private void giveWand(Player player) {
        removeWand(player);
        ItemStack wand = new ItemStack(Material.BLAZE_ROD, 1);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Beastmode Setup Wand");
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            wand.setItemMeta(meta);
        }
        PlayerInventory inventory = player.getInventory();
        inventory.addItem(wand);
    }

    private void removeWand(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getContents()) {
            if (isSetupWand(item)) {
                inventory.remove(item);
            }
        }
    }

    private void completeSession(Player player, SetupSession session) {
        try {
            ArenaDefinition arena = session.toArenaDefinition();
            arenaStorage.saveArena(arena);
            send(player, ChatColor.GREEN + "Arena " + ChatColor.AQUA + arena.getName() + ChatColor.GREEN + " saved successfully.");
            send(player, ChatColor.YELLOW + "Use /beastmode to review arenas or adjust settings.");
        } catch (IllegalArgumentException ex) {
            send(player, ChatColor.RED + "Could not save arena: " + ex.getMessage());
            return;
        }

        sessions.remove(player.getUniqueId());
        removeWand(player);
    }
}
