package com.colin.beastmode.gui;

import com.colin.beastmode.model.ArenaDefinition;
import com.colin.beastmode.storage.ArenaStorage;
import com.colin.beastmode.setup.SetupSessionManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class ArenaEditMenu {

    private static final String TITLE_PREFIX = ChatColor.BLUE + "Edit Arena: ";
    private final JavaPlugin plugin;
    private final ArenaStorage arenaStorage;
    private final SetupSessionManager sessionManager;
    private final String prefix;

    public ArenaEditMenu(JavaPlugin plugin, ArenaStorage arenaStorage, SetupSessionManager sessionManager, String prefix) {
        this.plugin = plugin;
        this.arenaStorage = arenaStorage;
        this.sessionManager = sessionManager;
        this.prefix = prefix;
    }

    public void open(Player player, String arenaName) {
        if (arenaName == null || arenaName.isBlank()) {
            player.sendMessage(prefix + ChatColor.RED + "Provide a valid arena name.");
            return;
        }

        ArenaDefinition arena = arenaStorage.getArena(arenaName);
        if (arena == null) {
            player.sendMessage(prefix + ChatColor.RED + "Arena '" + arenaName + "' does not exist.");
            return;
        }

        Inventory inventory = Bukkit.createInventory(null, 36, TITLE_PREFIX + ChatColor.AQUA + arena.getName());

        inventory.setItem(10, runnerSpawnItem(arena));
        inventory.setItem(11, beastSpawnItem(arena));
        inventory.setItem(12, waitingSpawnItem(arena));
        inventory.setItem(13, runnerWallItem());
        inventory.setItem(14, beastWallItem());
        inventory.setItem(15, finishButtonItem(arena));

        inventory.setItem(19, runnerDelayItem(arena));
        inventory.setItem(20, beastDelayItem(arena));
        inventory.setItem(21, minRunnersItem(arena));
        inventory.setItem(22, maxRunnersItem(arena));

        inventory.setItem(31, reconfigureItem());
        inventory.setItem(35, closeItem());

        player.openInventory(inventory);
    }

    public boolean isEditMenu(String title) {
        if (title == null) {
            return false;
        }
        return ChatColor.stripColor(title).startsWith(ChatColor.stripColor(TITLE_PREFIX));
    }

    public void handleClick(Player player, String rawTitle, ItemStack clicked, ClickType clickType) {
        if (clicked == null || clicked.getType() == Material.AIR || !clicked.hasItemMeta()) {
            return;
        }

        String arenaName = extractArenaName(rawTitle);
        if (arenaName == null) {
            return;
        }

        ArenaDefinition arena = arenaStorage.getArena(arenaName);
        if (arena == null) {
            player.sendMessage(prefix + ChatColor.RED + "Arena '" + arenaName + "' no longer exists.");
            player.closeInventory();
            return;
        }

        Material type = clicked.getType();
        switch (type) {
            case LIME_BED -> handleRunnerSpawn(player, arena, clickType);
            case RED_BED -> handleBeastSpawn(player, arena, clickType);
            case CLOCK -> handleWaitingSpawn(player, arena, clickType);
            case OAK_FENCE -> handleRunnerWall(player, arena);
            case NETHER_BRICK_FENCE -> handleBeastWall(player, arena);
            case STONE_BUTTON, POLISHED_BLACKSTONE_BUTTON -> handleFinishButton(player, arena);
            case REPEATER -> handleRunnerDelay(player, arena);
            case COMPARATOR -> handleBeastDelay(player, arena);
            case SLIME_BALL -> handleMinRunners(player, arena);
            case MAGMA_CREAM -> handleMaxRunners(player, arena);
            case WRITABLE_BOOK -> handleReconfigure(player, arena);
            case BARRIER -> player.closeInventory();
            default -> {
            }
        }
    }

    private String extractArenaName(String rawTitle) {
        if (rawTitle == null) {
            return null;
        }
        String stripped = ChatColor.stripColor(rawTitle);
        String prefixStripped = ChatColor.stripColor(TITLE_PREFIX);
        if (!stripped.startsWith(prefixStripped)) {
            return null;
        }
        return stripped.substring(prefixStripped.length()).trim();
    }

    private ItemStack runnerSpawnItem(ArenaDefinition arena) {
        return spawnItem(Material.LIME_BED, ChatColor.GREEN + "Runner Spawn", arena.getRunnerSpawn());
    }

    private ItemStack beastSpawnItem(ArenaDefinition arena) {
        return spawnItem(Material.RED_BED, ChatColor.DARK_RED + "Beast Spawn", arena.getBeastSpawn());
    }

    private ItemStack waitingSpawnItem(ArenaDefinition arena) {
        return spawnItem(Material.CLOCK, ChatColor.GOLD + "Waiting Spawn", arena.getWaitingSpawn());
    }

    private ItemStack spawnItem(Material material, String name, org.bukkit.Location location) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
                List<String> lore = new ArrayList<>();
            if (location != null) {
                lore.add(ChatColor.GRAY + "Current: " + formatLocation(location));
                lore.add("");
                lore.add(ChatColor.YELLOW + "Left-click to set to your location.");
                lore.add(ChatColor.AQUA + "Right-click to teleport there.");
            } else {
                lore.add(ChatColor.RED + "Not set");
                lore.add("");
                lore.add(ChatColor.YELLOW + "Left-click to set to your location.");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack runnerWallItem() {
        return simpleItem(Material.OAK_FENCE, ChatColor.GREEN + "Runner Wall", ChatColor.YELLOW + "Click to reselect runner wall.");
    }

    private ItemStack beastWallItem() {
        return simpleItem(Material.NETHER_BRICK_FENCE, ChatColor.DARK_RED + "Beast Wall", ChatColor.YELLOW + "Click to reselect beast wall.");
    }

    private ItemStack finishButtonItem(ArenaDefinition arena) {
        ItemStack item = new ItemStack(Material.STONE_BUTTON);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Finish Button");
                List<String> lore = new ArrayList<>();
            if (arena.getFinishButton() != null) {
                lore.add(ChatColor.GRAY + "Current: " + formatLocation(arena.getFinishButton()));
            } else {
                lore.add(ChatColor.RED + "Not set");
            }
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click to select a new button block.");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack runnerDelayItem(ArenaDefinition arena) {
        String value = arena.getRunnerWallDelaySeconds() >= 0 ? String.valueOf(arena.getRunnerWallDelaySeconds()) : "Not set";
        return simpleItem(Material.REPEATER, ChatColor.GREEN + "Runner Wall Delay",
                ChatColor.GRAY + "Current: " + ChatColor.AQUA + value,
                ChatColor.YELLOW + "Click and enter a new value in chat.");
    }

    private ItemStack beastDelayItem(ArenaDefinition arena) {
        String value = arena.getBeastReleaseDelaySeconds() >= 0 ? String.valueOf(arena.getBeastReleaseDelaySeconds()) : "Not set";
        return simpleItem(Material.COMPARATOR, ChatColor.DARK_RED + "Beast Release Delay",
                ChatColor.GRAY + "Current: " + ChatColor.AQUA + value,
                ChatColor.YELLOW + "Click and enter a new value in chat.");
    }

    private ItemStack minRunnersItem(ArenaDefinition arena) {
        String value = String.valueOf(arena.getMinRunners());
        return simpleItem(Material.SLIME_BALL, ChatColor.GREEN + "Minimum Runners",
                ChatColor.GRAY + "Current: " + ChatColor.AQUA + value,
                ChatColor.YELLOW + "Click and enter a new value in chat." + ChatColor.GRAY + " (>= 1)");
    }

    private ItemStack maxRunnersItem(ArenaDefinition arena) {
        String value = arena.getMaxRunners() == 0 ? "Unlimited" : String.valueOf(arena.getMaxRunners());
        return simpleItem(Material.MAGMA_CREAM, ChatColor.GOLD + "Maximum Runners",
                ChatColor.GRAY + "Current: " + ChatColor.AQUA + value,
                ChatColor.YELLOW + "Click and enter a new value in chat." + ChatColor.GRAY + " (0 = unlimited)");
    }

    private ItemStack reconfigureItem() {
        return simpleItem(Material.WRITABLE_BOOK, ChatColor.GOLD + "Full Reconfigure",
                ChatColor.YELLOW + "Restart the full setup wizard.");
    }

    private ItemStack closeItem() {
        return simpleItem(Material.BARRIER, ChatColor.RED + "Close", ChatColor.GRAY + "Return to the arena list.");
    }

    private ItemStack simpleItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
                List<String> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(line);
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatLocation(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) {
            return "Unknown";
        }
        return ChatColor.WHITE + location.getWorld().getName() + ChatColor.GRAY + " | "
                + ChatColor.WHITE + location.getBlockX() + ChatColor.GRAY + ", "
                + ChatColor.WHITE + location.getBlockY() + ChatColor.GRAY + ", "
                + ChatColor.WHITE + location.getBlockZ();
    }

    private void handleRunnerSpawn(Player player, ArenaDefinition arena, ClickType clickType) {
        if (clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT) {
            teleport(player, arena.getRunnerSpawn(), ChatColor.GREEN + "Teleported to runner spawn.");
            return;
        }
        arenaStorage.updateRunnerSpawn(arena.getName(), player.getLocation().clone());
        player.sendMessage(prefix + ChatColor.GREEN + "Runner spawn updated.");
        reopen(player, arena.getName());
    }

    private void handleBeastSpawn(Player player, ArenaDefinition arena, ClickType clickType) {
        if (clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT) {
            teleport(player, arena.getBeastSpawn(), ChatColor.DARK_RED + "Teleported to beast spawn.");
            return;
        }
        arenaStorage.updateBeastSpawn(arena.getName(), player.getLocation().clone());
        player.sendMessage(prefix + ChatColor.GREEN + "Beast spawn updated.");
        reopen(player, arena.getName());
    }

    private void handleWaitingSpawn(Player player, ArenaDefinition arena, ClickType clickType) {
        if (clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT) {
            teleport(player, arena.getWaitingSpawn(), ChatColor.GOLD + "Teleported to waiting spawn.");
            return;
        }
        arenaStorage.updateWaitingSpawn(arena.getName(), player.getLocation().clone());
        player.sendMessage(prefix + ChatColor.GREEN + "Waiting spawn updated.");
        reopen(player, arena.getName());
    }

    private void handleRunnerWall(Player player, ArenaDefinition arena) {
        player.closeInventory();
        if (sessionManager.beginRunnerWallEdit(player, arena.getName())) {
            player.sendMessage(prefix + ChatColor.YELLOW + "Select the new runner wall corners with the setup wand.");
        }
    }

    private void handleBeastWall(Player player, ArenaDefinition arena) {
        player.closeInventory();
        if (sessionManager.beginBeastWallEdit(player, arena.getName())) {
            player.sendMessage(prefix + ChatColor.YELLOW + "Select the new beast wall corners with the setup wand.");
        }
    }

    private void handleFinishButton(Player player, ArenaDefinition arena) {
        player.closeInventory();
        if (sessionManager.beginFinishButtonEdit(player, arena.getName())) {
            player.sendMessage(prefix + ChatColor.YELLOW + "Click the new finish button block.");
        }
    }

    private void handleRunnerDelay(Player player, ArenaDefinition arena) {
        player.closeInventory();
        if (sessionManager.beginRunnerDelayEdit(player, arena.getName())) {
            player.sendMessage(prefix + ChatColor.YELLOW + "Enter the new runner wall delay in chat.");
        }
    }

    private void handleBeastDelay(Player player, ArenaDefinition arena) {
        player.closeInventory();
        if (sessionManager.beginBeastDelayEdit(player, arena.getName())) {
            player.sendMessage(prefix + ChatColor.YELLOW + "Enter the new beast release delay in chat.");
        }
    }

    private void handleMinRunners(Player player, ArenaDefinition arena) {
        player.closeInventory();
        if (sessionManager.beginMinRunnerEdit(player, arena.getName())) {
            player.sendMessage(prefix + ChatColor.YELLOW + "Enter the new minimum runner count in chat.");
        }
    }

    private void handleMaxRunners(Player player, ArenaDefinition arena) {
        player.closeInventory();
        if (sessionManager.beginMaxRunnerEdit(player, arena.getName())) {
            player.sendMessage(prefix + ChatColor.YELLOW + "Enter the new maximum runner count in chat (0 = unlimited).");
        }
    }

    private void handleReconfigure(Player player, ArenaDefinition arena) {
        player.closeInventory();
        if (sessionManager.startSession(player, arena.getName())) {
            player.sendMessage(prefix + ChatColor.YELLOW + "Reconfigure the arena using the setup wizard.");
        }
    }

    private void teleport(Player player, org.bukkit.Location location, String successMessage) {
        if (location == null || location.getWorld() == null) {
            player.sendMessage(prefix + ChatColor.RED + "That location is not set.");
            return;
        }
        player.teleport(location.clone());
        player.sendMessage(prefix + successMessage);
    }

    private void reopen(Player player, String arenaName) {
    Bukkit.getScheduler().runTaskLater(plugin, () -> open(player, arenaName), 1L);
    }
}
