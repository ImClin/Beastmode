package com.colin.beastmode.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class ArenaMenuListener implements Listener {

    private final ArenaMenu arenaMenu;

    public ArenaMenuListener(ArenaMenu arenaMenu) {
        this.arenaMenu = arenaMenu;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!event.getView().getTitle().equals(arenaMenu.getTitle())) {
            return;
        }

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        arenaMenu.handleClick(player, clicked);
    }
}
