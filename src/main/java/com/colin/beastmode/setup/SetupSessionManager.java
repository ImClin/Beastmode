package com.colin.beastmode.setup;

import com.colin.beastmode.Beastmode;
import com.colin.beastmode.model.ArenaDefinition;
import com.colin.beastmode.model.Cuboid;
import com.colin.beastmode.storage.ArenaStorage;
import com.colin.beastmode.game.GameModeType;
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
    private static final int MIN_RUNNER_REQUIREMENT = 1;
    private static final int RUNNER_LIMIT_CAP = 100;

    public SetupSessionManager(Beastmode plugin, ArenaStorage arenaStorage) {
        this.arenaStorage = arenaStorage;
        this.wandKey = new NamespacedKey(plugin, "setup_wand");
        this.prefix = plugin.getConfig().getString("messages.prefix", "[Beastmode] ");
    }

    public boolean startSession(Player player, String arenaName) {
        return startSession(player, arenaName, GameModeType.HUNT);
    }

    public boolean startSession(Player player, String arenaName, GameModeType mode) {
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

        GameModeType sessionMode = mode != null ? mode : GameModeType.HUNT;
        SetupSession session = new SetupSession(player.getUniqueId(), arenaName, sessionMode);
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
        SetupMode mode = session.getMode();
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
                if (mode == SetupMode.EDIT_RUNNER_WALL) {
                    Cuboid runnerWall = Cuboid.fromCorners(session.getRunnerWallPos1(), session.getRunnerWallPos2());
                    arenaStorage.updateRunnerWall(session.getArenaName(), runnerWall);
                    finishEdit(player, session, ChatColor.GREEN + "Runner wall updated for arena "
                            + ChatColor.AQUA + session.getArenaName() + ChatColor.GREEN + ".");
                    return true;
                }
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
                if (mode == SetupMode.EDIT_BEAST_WALL) {
                    Cuboid beastWall = Cuboid.fromCorners(session.getBeastWallPos1(), session.getBeastWallPos2());
                    arenaStorage.updateBeastWall(session.getArenaName(), beastWall);
                    finishEdit(player, session, ChatColor.GREEN + "Beast wall updated for arena "
                            + ChatColor.AQUA + session.getArenaName() + ChatColor.GREEN + ".");
                    return true;
                }
                advanceStage(session, SetupStage.RUNNER_SPAWN);
            }
            case FINISH_BUTTON -> {
                if (!isButton(location.getBlock().getType())) {
                    send(player, ChatColor.RED + "Please click a button block to set the finish.");
                    return true;
                }
                session.setFinishButton(blockLocation);
                if (mode == SetupMode.EDIT_FINISH_BUTTON) {
                    arenaStorage.updateFinishButton(session.getArenaName(), blockLocation.clone());
                    finishEdit(player, session, ChatColor.GREEN + "Finish button updated for arena "
                            + ChatColor.AQUA + session.getArenaName() + ChatColor.GREEN + ".");
                    return true;
                }
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
                if (session.isTimeTrial()) {
                    advanceStage(session, SetupStage.FINISH_BUTTON);
                } else {
                    advanceStage(session, SetupStage.BEAST_SPAWN);
                }
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
        SetupMode mode = session.getMode();
        if (!stage.expectsChatNumber()) {
            return false;
        }
        if (value < 0) {
            send(player, ChatColor.RED + "Please provide a positive number.");
            return true;
        }

        ArenaDefinition arena = arenaStorage.getArena(session.getArenaName());

        switch (stage) {
            case RUNNER_WALL_DELAY -> {
                session.setRunnerWallDelaySeconds(value);
                send(player, ChatColor.GREEN + "Runner wall delay set to " + value + " seconds.");
                if (mode == SetupMode.EDIT_RUNNER_DELAY) {
                    arenaStorage.updateRunnerWallDelay(session.getArenaName(), value);
                    finishEdit(player, session, ChatColor.GREEN + "Runner wall delay updated for arena "
                            + ChatColor.AQUA + session.getArenaName() + ChatColor.GREEN + ".");
                    return true;
                }
                advanceStage(session, SetupStage.BEAST_RELEASE_DELAY);
            }
            case BEAST_RELEASE_DELAY -> {
                session.setBeastReleaseDelaySeconds(value);
                send(player, ChatColor.GREEN + "Beast release delay set to " + value + " seconds.");
                if (mode == SetupMode.EDIT_BEAST_DELAY) {
                    arenaStorage.updateBeastReleaseDelay(session.getArenaName(), value);
                    finishEdit(player, session, ChatColor.GREEN + "Beast release delay updated for arena "
                            + ChatColor.AQUA + session.getArenaName() + ChatColor.GREEN + ".");
                    return true;
                }
                advanceStage(session, SetupStage.BEAST_SPEED_LEVEL);
            }
            case BEAST_SPEED_LEVEL -> {
                session.setBeastSpeedLevel(value);
                send(player, ChatColor.GREEN + "Beast speed level set to " + value + ".");
                if (mode == SetupMode.EDIT_BEAST_SPEED) {
                    arenaStorage.updateBeastSpeedLevel(session.getArenaName(), value);
                    finishEdit(player, session, ChatColor.GREEN + "Beast speed updated for arena "
                            + ChatColor.AQUA + session.getArenaName() + ChatColor.GREEN + ".");
                    return true;
                }
                advanceStage(session, SetupStage.MIN_RUNNERS);
            }
            case MIN_RUNNERS -> {
                if (value < MIN_RUNNER_REQUIREMENT) {
                    send(player, ChatColor.RED + "Please choose at least " + MIN_RUNNER_REQUIREMENT + " runner." + ChatColor.GRAY + " (Minimum " + MIN_RUNNER_REQUIREMENT + ")");
                    return true;
                }
                if (value > RUNNER_LIMIT_CAP) {
                    send(player, ChatColor.RED + "That number is too high. Please choose a value up to " + RUNNER_LIMIT_CAP + ".");
                    return true;
                }
                if (mode == SetupMode.EDIT_MIN_RUNNERS && arena != null) {
                    int currentMax = arena.getMaxRunners();
                    if (currentMax != 0 && value > currentMax) {
                        send(player, ChatColor.RED + "Minimum runners cannot exceed the current maximum (" + currentMax + ").");
                        return true;
                    }
                }
                session.setMinRunners(value);
                send(player, ChatColor.GREEN + "Minimum runners set to " + value + ".");
                if (mode == SetupMode.EDIT_MIN_RUNNERS) {
                    arenaStorage.updateMinRunners(session.getArenaName(), value);
                    finishEdit(player, session, ChatColor.GREEN + "Minimum runners updated for arena "
                            + ChatColor.AQUA + session.getArenaName() + ChatColor.GREEN + ".");
                    return true;
                }
                advanceStage(session, SetupStage.MAX_RUNNERS);
            }
            case MAX_RUNNERS -> {
                if (value < 0) {
                    send(player, ChatColor.RED + "Maximum runners cannot be negative.");
                    return true;
                }
                if (value > RUNNER_LIMIT_CAP && value != 0) {
                    send(player, ChatColor.RED + "That number is too high. Please choose a value up to " + RUNNER_LIMIT_CAP + ", or 0 for unlimited.");
                    return true;
                }
                Integer minRunners = session.getMinRunners();
                if (minRunners == null) {
                    send(player, ChatColor.RED + "Set the minimum runners before the maximum.");
                    return true;
                }
                if (value != 0 && value < minRunners) {
                    send(player, ChatColor.RED + "Maximum runners must be at least the minimum (" + minRunners + ").");
                    return true;
                }
                session.setMaxRunners(value);
                if (value == 0) {
                    send(player, ChatColor.GREEN + "Maximum runners set to unlimited.");
                } else {
                    send(player, ChatColor.GREEN + "Maximum runners set to " + value + ".");
                }
                if (mode == SetupMode.EDIT_MAX_RUNNERS) {
                    arenaStorage.updateMaxRunners(session.getArenaName(), value);
                    finishEdit(player, session, ChatColor.GREEN + "Maximum runners updated for arena "
                            + ChatColor.AQUA + session.getArenaName() + ChatColor.GREEN + ".");
                    return true;
                }
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
        SetupSession session = sessions.get(player.getUniqueId());
        if (session != null && session.isTimeTrial()) {
            String message = switch (stage) {
                case RUNNER_SPAWN -> "Stand at the time-trial start point and run /beastmode setspawn <arena> runner.";
                case FINISH_BUTTON -> "Click the finish button for this trial with the setup wand.";
                default -> stage.getFriendlyDescription();
            };
            send(player, ChatColor.AQUA + message);
            return;
        }
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

    private void finishEdit(Player player, SetupSession session, String message) {
        send(player, message);
        sessions.remove(player.getUniqueId());
        removeWand(player);
    }

    public boolean beginRunnerWallEdit(Player player, String arenaName) {
        return beginSelectionEdit(player, arenaName, SetupMode.EDIT_RUNNER_WALL, SetupStage.RUNNER_WALL_POS1,
                ChatColor.YELLOW + "Select the first corner of the runner wall.");
    }

    public boolean beginBeastWallEdit(Player player, String arenaName) {
        return beginSelectionEdit(player, arenaName, SetupMode.EDIT_BEAST_WALL, SetupStage.BEAST_WALL_POS1,
                ChatColor.YELLOW + "Select the first corner of the beast wall.");
    }

    public boolean beginFinishButtonEdit(Player player, String arenaName) {
        return beginSelectionEdit(player, arenaName, SetupMode.EDIT_FINISH_BUTTON, SetupStage.FINISH_BUTTON,
                ChatColor.YELLOW + "Click the new finish button block.");
    }

    public boolean beginRunnerDelayEdit(Player player, String arenaName) {
        return beginNumericEdit(player, arenaName, SetupMode.EDIT_RUNNER_DELAY, SetupStage.RUNNER_WALL_DELAY,
                ChatColor.YELLOW + "Type the new runner wall delay in chat." + ChatColor.GRAY + " (or 'cancel')");
    }

    public boolean beginBeastDelayEdit(Player player, String arenaName) {
        return beginNumericEdit(player, arenaName, SetupMode.EDIT_BEAST_DELAY, SetupStage.BEAST_RELEASE_DELAY,
                ChatColor.YELLOW + "Type the new beast release delay in chat." + ChatColor.GRAY + " (or 'cancel')");
    }

    public boolean beginBeastSpeedEdit(Player player, String arenaName) {
        return beginNumericEdit(player, arenaName, SetupMode.EDIT_BEAST_SPEED, SetupStage.BEAST_SPEED_LEVEL,
                ChatColor.YELLOW + "Type the new beast speed level in chat." + ChatColor.GRAY + " (or 'cancel')");
    }

    public boolean beginMinRunnerEdit(Player player, String arenaName) {
        return beginNumericEdit(player, arenaName, SetupMode.EDIT_MIN_RUNNERS, SetupStage.MIN_RUNNERS,
                ChatColor.YELLOW + "Type the new minimum number of runners (at least 1)." + ChatColor.GRAY + " (or 'cancel')");
    }

    public boolean beginMaxRunnerEdit(Player player, String arenaName) {
        return beginNumericEdit(player, arenaName, SetupMode.EDIT_MAX_RUNNERS, SetupStage.MAX_RUNNERS,
                ChatColor.YELLOW + "Type the new maximum number of runners (0 for unlimited)." + ChatColor.GRAY + " (or 'cancel')");
    }

    private boolean beginSelectionEdit(Player player, String arenaName, SetupMode mode, SetupStage stage, String instruction) {
        ArenaDefinition arena = requireEditableArena(player, arenaName);
        if (arena == null) {
            return false;
        }
        if (sessions.containsKey(player.getUniqueId())) {
            send(player, ChatColor.RED + "You are already editing an arena. Finish or cancel first.");
            return false;
        }

    SetupSession session = new SetupSession(player.getUniqueId(), arena.getName(), arena.getGameModeType());
        session.setMode(mode);
        session.setStage(stage);
        sessions.put(player.getUniqueId(), session);
        giveWand(player);
        send(player, instruction);
        if (mode == SetupMode.EDIT_RUNNER_WALL) {
            send(player, ChatColor.GRAY + "Left-click to set the first corner, right-click to set the second.");
        } else if (mode == SetupMode.EDIT_BEAST_WALL) {
            send(player, ChatColor.GRAY + "Left-click to set the first corner, right-click to set the second.");
        }
        return true;
    }

    private boolean beginNumericEdit(Player player, String arenaName, SetupMode mode, SetupStage stage, String instruction) {
        ArenaDefinition arena = requireEditableArena(player, arenaName);
        if (arena == null) {
            return false;
        }
        if (sessions.containsKey(player.getUniqueId())) {
            send(player, ChatColor.RED + "You are already editing an arena. Finish or cancel first.");
            return false;
        }

    SetupSession session = new SetupSession(player.getUniqueId(), arena.getName(), arena.getGameModeType());
        session.setMode(mode);
        session.setStage(stage);
        if (stage == SetupStage.MIN_RUNNERS) {
            session.setMinRunners(arena.getMinRunners());
            session.setMaxRunners(arena.getMaxRunners());
        } else if (stage == SetupStage.MAX_RUNNERS) {
            session.setMinRunners(arena.getMinRunners());
            session.setMaxRunners(arena.getMaxRunners());
        } else if (stage == SetupStage.BEAST_SPEED_LEVEL) {
            session.setBeastSpeedLevel(arena.getBeastSpeedLevel());
        }
        sessions.put(player.getUniqueId(), session);
        int currentValue = switch (stage) {
            case RUNNER_WALL_DELAY -> arena.getRunnerWallDelaySeconds();
            case BEAST_RELEASE_DELAY -> arena.getBeastReleaseDelaySeconds();
            case BEAST_SPEED_LEVEL -> arena.getBeastSpeedLevel();
            case MIN_RUNNERS -> arena.getMinRunners();
            case MAX_RUNNERS -> arena.getMaxRunners();
            default -> -1;
        };
        if (currentValue >= 0 && stage != SetupStage.MAX_RUNNERS) {
            send(player, ChatColor.GRAY + "Current value: " + ChatColor.AQUA + currentValue);
        } else if (stage == SetupStage.MAX_RUNNERS) {
            if (currentValue == 0) {
                send(player, ChatColor.GRAY + "Current value: " + ChatColor.AQUA + "Unlimited");
            } else if (currentValue > 0) {
                send(player, ChatColor.GRAY + "Current value: " + ChatColor.AQUA + currentValue);
            }
        }
        send(player, instruction);
        return true;
    }

    private ArenaDefinition requireEditableArena(Player player, String arenaName) {
        if (arenaName == null || arenaName.trim().isEmpty()) {
            send(player, ChatColor.RED + "Provide a valid arena name.");
            return null;
        }
        if (!player.hasPermission("beastmode.command")) {
            send(player, ChatColor.RED + "You do not have permission to edit arenas.");
            return null;
        }
        ArenaDefinition arena = arenaStorage.getArena(arenaName);
        if (arena == null) {
            send(player, ChatColor.RED + "Arena '" + arenaName + "' does not exist.");
            return null;
        }
        return arena;
    }
}
