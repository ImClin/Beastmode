package com.colin.beastmode;

import com.colin.beastmode.command.BeastmodeCommand;
import com.colin.beastmode.game.GameManager;
import com.colin.beastmode.gui.ArenaEditMenu;
import com.colin.beastmode.gui.ArenaEditMenuListener;
import com.colin.beastmode.gui.ArenaMenu;
import com.colin.beastmode.gui.ArenaMenuListener;
import com.colin.beastmode.listeners.ChatInputListener;
import com.colin.beastmode.listeners.GameListener;
import com.colin.beastmode.listeners.SelectionListener;
import com.colin.beastmode.listeners.SignListener;
import com.colin.beastmode.setup.SetupSessionManager;
import com.colin.beastmode.storage.ArenaStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class Beastmode extends JavaPlugin {

    private ArenaStorage arenaStorage;
    private SetupSessionManager setupSessionManager;
    private ArenaEditMenu arenaEditMenu;
    private ArenaMenu arenaMenu;
    private GameManager gameManager;
    private String messagePrefix;

    @Override
    public void onEnable() {
        saveDefaultConfig();

    this.arenaStorage = new ArenaStorage(this);
    this.setupSessionManager = new SetupSessionManager(this, arenaStorage);
    this.gameManager = new GameManager(this, arenaStorage);
    this.messagePrefix = getConfig().getString("messages.prefix", "[Beastmode] ");
    this.arenaEditMenu = new ArenaEditMenu(this, arenaStorage, setupSessionManager, messagePrefix);
    this.arenaMenu = new ArenaMenu(this, arenaStorage, arenaEditMenu);

        registerCommands();
        registerListeners();
    }

    @Override
    public void onDisable() {
        if (setupSessionManager != null) {
            setupSessionManager.endAllSessions();
        }
        if (gameManager != null) {
            gameManager.shutdown();
        }
    }

    public static Beastmode getInstance() {
        return JavaPlugin.getPlugin(Beastmode.class);
    }

    public ArenaStorage getArenaStorage() {
        return arenaStorage;
    }

    public SetupSessionManager getSetupSessionManager() {
        return setupSessionManager;
    }

    public ArenaMenu getArenaMenu() {
        return arenaMenu;
    }

    public ArenaEditMenu getArenaEditMenu() {
        return arenaEditMenu;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    private void registerCommands() {
        PluginCommand command = getCommand("beastmode");
        if (command == null) {
            getLogger().severe("Failed to register /beastmode command");
            return;
        }

        BeastmodeCommand executor = new BeastmodeCommand(arenaStorage, setupSessionManager, arenaMenu, gameManager);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerListeners() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new SelectionListener(setupSessionManager), this);
        pluginManager.registerEvents(new ChatInputListener(this, setupSessionManager), this);
        pluginManager.registerEvents(new ArenaMenuListener(arenaMenu), this);
        pluginManager.registerEvents(new ArenaEditMenuListener(arenaEditMenu), this);
        pluginManager.registerEvents(new SignListener(arenaStorage, gameManager, messagePrefix), this);
        pluginManager.registerEvents(new GameListener(gameManager), this);
    }
}
