package com.colin.beastmode.listeners;

import com.colin.beastmode.game.ArenaStatus;
import com.colin.beastmode.game.GameManager;
import com.colin.beastmode.game.GameModeType;
import com.colin.beastmode.model.ArenaDefinition;
import com.colin.beastmode.storage.ArenaStorage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;

public class SignListener implements Listener {

    private static final String SIGN_KEY_HUNT = ChatColor.BLUE + "[Beastmode]";
    private static final String SIGN_KEY_TRIAL = ChatColor.GREEN + "[Trial]";
    private static final long REFRESH_INTERVAL_TICKS = 60L;
    private final ArenaStorage arenaStorage;
    private final GameManager gameManager;
    private final String prefix;
    private final Map<SignKey, SignRegistration> trackedSigns = new ConcurrentHashMap<>();

    public SignListener(JavaPlugin plugin, ArenaStorage arenaStorage, GameManager gameManager, String prefix) {
        this.arenaStorage = arenaStorage;
        this.gameManager = gameManager;
        this.prefix = prefix;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshTrackedSigns,
                REFRESH_INTERVAL_TICKS, REFRESH_INTERVAL_TICKS);
    this.gameManager.registerStatusListener(this::handleArenaStatusChange);
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        String rawHeader = event.getLine(0);
        if (rawHeader == null) {
            return;
        }

        boolean huntHeader = rawHeader.equalsIgnoreCase("[beastmode]");
        boolean trialHeader = rawHeader.equalsIgnoreCase("[trial]") || rawHeader.equalsIgnoreCase("[timetrial]");
        if (!huntHeader && !trialHeader) {
            return;
        }

        GameModeType mode = determineMode(rawHeader, event.getLine(2), event.getLine(3));

        if (!player.hasPermission("beastmode.command")) {
            player.sendMessage(prefix + ChatColor.RED + "You do not have permission to create Beastmode signs.");
            return;
        }

        String arenaName = event.getLine(1) != null ? event.getLine(1).trim() : "";
        if (arenaName.isEmpty()) {
            player.sendMessage(prefix + ChatColor.RED + "Specify an arena name on the second line.");
            event.setLine(0, headerFor(mode));
            event.setLine(1, ChatColor.RED + "<arena>");
            return;
        }

        ArenaDefinition arena = arenaStorage.getArena(arenaName);
        if (arena == null) {
            player.sendMessage(prefix + ChatColor.RED + "Arena '" + arenaName + "' does not exist.");
            event.setLine(0, headerFor(mode));
            event.setLine(1, ChatColor.RED + "Unknown");
            event.setLine(2, ChatColor.GRAY + "Click to join");
            return;
        }

        ArenaStatus status = gameManager.getArenaStatus(arena.getName(), mode);
        applyLines(event, status, mode);
        registerSign(event.getBlock(), arena.getName(), mode);
        if (mode.isTimeTrial()) {
            player.sendMessage(prefix + ChatColor.GREEN + "Time trial sign created for arena '" + arena.getName() + "'.");
        } else {
            player.sendMessage(prefix + ChatColor.GREEN + "Join sign created for arena '" + arena.getName() + "'.");
        }
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
        if (front == null) {
            return;
        }

        String header = front.getLine(0);
        if (header == null) {
            return;
        }

        String strippedHeader = ChatColor.stripColor(header);
        if (!strippedHeader.equalsIgnoreCase("[beastmode]")
                && !strippedHeader.equalsIgnoreCase("[trial]")
                && !strippedHeader.equalsIgnoreCase("[timetrial]")) {
            return;
        }

        GameModeType mode = resolveMode(sign, front);

        event.setCancelled(true);
        Player player = event.getPlayer();
        String arenaName = ChatColor.stripColor(front.getLine(1));
        if (arenaName == null || arenaName.isBlank()) {
            player.sendMessage(prefix + ChatColor.RED + "This sign is missing an arena name.");
            return;
        }

        ArenaDefinition arena = arenaStorage.getArena(arenaName);
        if (arena == null) {
            applyMissing(front, mode);
            applyMissing(sign.getSide(Side.BACK), mode);
            registerSign(block, arenaName, mode);
            sign.update();
            player.sendMessage(prefix + ChatColor.RED + "Arena '" + arenaName + "' no longer exists.");
            return;
        }

        registerSign(block, arena.getName(), mode);
        updateSign(sign, arena.getName(), mode);
        if (mode.isTimeTrial()) {
            gameManager.joinTimeTrial(player, arena.getName());
        } else {
            gameManager.joinArena(player, arena.getName());
        }
        updateSign(sign, arena.getName(), mode);
    }

    private void applyLines(SignChangeEvent event, ArenaStatus status, GameModeType mode) {
        if (!status.isAvailable()) {
            event.setLine(0, headerFor(mode));
            event.setLine(1, ChatColor.RED + "Unknown");
            event.setLine(2, ChatColor.DARK_RED + "Arena missing");
            event.setLine(3, ChatColor.RED + "Status: Offline");
            return;
        }
        event.setLine(0, headerFor(mode));
        event.setLine(1, ChatColor.AQUA + status.getArenaName());
        event.setLine(2, formatLineTwo(status, mode));
        event.setLine(3, formatStatusLine(status, mode));
    }

    private void updateSign(Sign sign, String arenaName, GameModeType mode) {
        ArenaStatus status = gameManager.getArenaStatus(arenaName, mode);
        if (!status.isAvailable()) {
            applyMissing(sign.getSide(Side.FRONT), mode);
            applyMissing(sign.getSide(Side.BACK), mode);
        } else {
            applyLines(sign.getSide(Side.FRONT), status, mode);
            applyLines(sign.getSide(Side.BACK), status, mode);
        }
        sign.update();
    }

    private void applyLines(SignSide side, ArenaStatus status, GameModeType mode) {
        if (side == null) {
            return;
        }
        if (!status.isAvailable()) {
            applyMissing(side, mode);
            return;
        }
        side.setLine(0, headerFor(mode));
        side.setLine(1, ChatColor.AQUA + status.getArenaName());
        side.setLine(2, formatLineTwo(status, mode));
        side.setLine(3, formatStatusLine(status, mode));
    }

    private String formatLineTwo(ArenaStatus status, GameModeType mode) {
        if (mode.isTimeTrial()) {
            int count = Math.max(0, status.getPlayerCount());
            String label = count == 1 ? "Runner" : "Runners";
            return ChatColor.YELLOW + label + ": " + count;
        }
        int count = status.getPlayerCount();
        if (status.hasCapacityLimit() && status.getCapacity() > 0) {
            return ChatColor.YELLOW + "Players: " + count + "/" + status.getCapacity();
        }
        return ChatColor.YELLOW + "Players: " + count;
    }

    private String formatStatusLine(ArenaStatus status, GameModeType mode) {
        if (!status.isAvailable()) {
            return ChatColor.RED + "Status: Offline";
        }
        if (!status.isComplete()) {
            return ChatColor.RED + "Status: Setup";
        }
        if (mode.isTimeTrial()) {
            if (status.isMatchActive()) {
                return ChatColor.AQUA + "Status: Active";
            }
            if (status.isRunning() || status.isSelecting()) {
                return ChatColor.GOLD + "Status: Loading";
            }
            return ChatColor.GREEN + "Status: Ready";
        }
        if (status.isMatchActive()) {
            return mode.isTimeTrial()
                    ? ChatColor.RED + "Status: Running"
                    : ChatColor.RED + "Status: In-Game";
        }
        if (status.isSelecting()) {
            return mode.isTimeTrial()
                    ? ChatColor.GOLD + "Status: Loading"
                    : ChatColor.GOLD + "Status: Selecting";
        }
        if (status.isRunning()) {
            return mode.isTimeTrial()
                    ? ChatColor.YELLOW + "Status: Starting"
                    : ChatColor.YELLOW + "Status: Waiting";
        }
        return ChatColor.GREEN + "Status: Ready";
    }

    private void applyMissing(SignSide side, GameModeType mode) {
        if (side == null) {
            return;
        }
        side.setLine(0, headerFor(mode));
        side.setLine(1, ChatColor.RED + "Unknown");
        side.setLine(2, ChatColor.DARK_RED + "Arena missing");
        side.setLine(3, ChatColor.RED + "Status: Offline");
    }

    private void registerSign(Block block, String arenaName, GameModeType mode) {
        SignKey key = SignKey.fromBlock(block);
        if (key == null) {
            return;
        }
        if (arenaName == null) {
            return;
        }
        String trimmed = arenaName.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        trackedSigns.put(key, new SignRegistration(trimmed, mode));
    }

    private void refreshTrackedSigns() {
        Iterator<Map.Entry<SignKey, SignRegistration>> iterator = trackedSigns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SignKey, SignRegistration> entry = iterator.next();
            Block block = entry.getKey().block();
            if (block == null) {
                continue;
            }
            if (!(block.getState() instanceof Sign sign)) {
                iterator.remove();
                continue;
            }

            SignRegistration registration = entry.getValue();
            if (registration == null || registration.arenaName() == null || registration.arenaName().isBlank()) {
                iterator.remove();
                continue;
            }

            updateSign(sign, registration.arenaName(), registration.mode());
        }
    }

    private void handleArenaStatusChange(String arenaName) {
        if (arenaName == null) {
            return;
        }
        String target = arenaName.trim();
        if (target.isEmpty()) {
            return;
        }
        for (Map.Entry<SignKey, SignRegistration> entry : trackedSigns.entrySet()) {
            SignRegistration registration = entry.getValue();
            if (registration == null) {
                continue;
            }
            String trackedArena = registration.arenaName();
            if (trackedArena == null || !trackedArena.equalsIgnoreCase(target)) {
                continue;
            }
            Block block = entry.getKey().block();
            if (block == null) {
                continue;
            }
            if (!(block.getState() instanceof Sign sign)) {
                trackedSigns.remove(entry.getKey());
                continue;
            }
            updateSign(sign, trackedArena, registration.mode());
        }
    }

    private GameModeType resolveMode(Sign sign, SignSide front) {
        SignKey key = SignKey.fromBlock(sign.getBlock());
        if (key != null) {
            SignRegistration registration = trackedSigns.get(key);
            if (registration != null && registration.mode() != null) {
                return registration.mode();
            }
        }
        String header = front != null ? front.getLine(0) : null;
        String line2 = front != null ? front.getLine(2) : null;
        String line3 = front != null ? front.getLine(3) : null;
        return determineMode(header, line2, line3);
    }

    private GameModeType determineMode(String header, String line2, String line3) {
        String strippedHeader = strip(header);
        if (strippedHeader.equalsIgnoreCase("[trial]") || strippedHeader.equalsIgnoreCase("[timetrial]")) {
            return GameModeType.TIME_TRIAL;
        }
        if (containsTrial(line2) || containsTrial(line3)) {
            return GameModeType.TIME_TRIAL;
        }
        return GameModeType.HUNT;
    }

    private boolean containsTrial(String line) {
        String stripped = strip(line);
        if (stripped.isEmpty()) {
            return false;
        }
        String normalized = stripped.toLowerCase(Locale.ENGLISH);
        return normalized.equals("trial")
                || normalized.equals("time trial")
                || normalized.equals("time-trial")
                || normalized.equals("timetrial");
    }

    private String headerFor(GameModeType mode) {
        return mode.isTimeTrial() ? SIGN_KEY_TRIAL : SIGN_KEY_HUNT;
    }

    private String strip(String value) {
        if (value == null) {
            return "";
        }
        return ChatColor.stripColor(value).trim();
    }

    private record SignRegistration(String arenaName, GameModeType mode) {
    }

    private record SignKey(String world, int x, int y, int z) {
        private static SignKey fromBlock(Block block) {
            if (block == null) {
                return null;
            }
            Location location = block.getLocation();
            if (location.getWorld() == null) {
                return null;
            }
            return new SignKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        private Block block() {
            if (world == null) {
                return null;
            }
            var worldInstance = Bukkit.getWorld(world);
            if (worldInstance == null) {
                return null;
            }
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (!worldInstance.isChunkLoaded(chunkX, chunkZ)) {
                return null;
            }
            return worldInstance.getBlockAt(x, y, z);
        }
    }
}
