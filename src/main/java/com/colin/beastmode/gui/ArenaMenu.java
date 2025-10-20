package com.colin.beastmode.gui;

import com.colin.beastmode.Beastmode;
import com.colin.beastmode.model.ArenaDefinition;
import com.colin.beastmode.storage.ArenaStorage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ArenaMenu {

    private static final int INVENTORY_SIZE = 54;
    private static final String TITLE = ChatColor.DARK_PURPLE + "Beastmode Arenas";
    private final ArenaStorage arenaStorage;
    private final Beastmode plugin;

    public ArenaMenu(Beastmode plugin, ArenaStorage arenaStorage) {
        this.plugin = plugin;
        this.arenaStorage = arenaStorage;
    }

    public void open(Player player) {
        player.openInventory(buildInventory());
    }

    public String getTitle() {
        return TITLE;
    }

    public void handleClick(Player player, ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR || !itemStack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }
        String arenaName = ChatColor.stripColor(meta.getDisplayName());
        player.sendMessage(plugin.getConfig().getString("messages.prefix", "[Beastmode] ")
                + ChatColor.GOLD + "Selected arena " + arenaName + ChatColor.GRAY + ". Detailed editing GUI coming soon.");
    }

    private Inventory buildInventory() {
    Inventory inventory = Bukkit.createInventory(null, INVENTORY_SIZE, TITLE);
        Collection<ArenaDefinition> arenas = arenaStorage.getArenas();
        if (arenas.isEmpty()) {
            inventory.setItem(22, createPlaceholderItem(ChatColor.YELLOW + "No arenas configured"));
            return inventory;
        }

        int slot = 0;
        for (ArenaDefinition arena : arenas) {
            if (slot >= INVENTORY_SIZE) {
                break;
            }
            inventory.setItem(slot++, createArenaItem(arena));
        }
        return inventory;
    }

    private ItemStack createArenaItem(ArenaDefinition arena) {
        ItemStack itemStack = new ItemStack(Material.PAPER);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + arena.getName());
            List<String> lore = new ArrayList<>();
            lore.add(statusLine("Runner wall", arena.getRunnerWall() != null));
            lore.add(statusLine("Beast wall", arena.getBeastWall() != null));
            lore.add(statusLine("Runner spawn", arena.getRunnerSpawn() != null));
            lore.add(statusLine("Beast spawn", arena.getBeastSpawn() != null));
            lore.add(statusLine("Runner wall delay", arena.getRunnerWallDelaySeconds() >= 0));
            lore.add(statusLine("Beast release delay", arena.getBeastReleaseDelaySeconds() >= 0));
            lore.add(statusLine("Finish button", arena.getFinishButton() != null));
            lore.add(ChatColor.GRAY + "Status: " + (arena.isComplete() ? ChatColor.GREEN + "Ready" : ChatColor.YELLOW + "Incomplete"));
            meta.setLore(lore);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private ItemStack createPlaceholderItem(String title) {
        ItemStack itemStack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(title);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private String statusLine(String label, boolean value) {
        return ChatColor.GRAY + label + ": " + (value ? ChatColor.GREEN + "set" : ChatColor.RED + "missing");
    }
}
