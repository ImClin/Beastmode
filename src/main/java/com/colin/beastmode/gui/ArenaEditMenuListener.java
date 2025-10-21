package com.colin.beastmode.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class ArenaEditMenuListener implements Listener {

    private final ArenaEditMenu editMenu;

    public ArenaEditMenuListener(ArenaEditMenu editMenu) {
        this.editMenu = editMenu;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        if (!editMenu.isEditMenu(title)) {
            return;
        }

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        editMenu.handleClick(player, title, clicked, event.getClick());
    }
}
