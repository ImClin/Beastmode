package com.colin.beastmode.storage;

import com.colin.beastmode.Beastmode;
import com.colin.beastmode.game.GameModeType;
import com.colin.beastmode.model.ArenaDefinition;
import com.colin.beastmode.model.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ArenaStorage {

    private final Beastmode plugin;
    private final Map<String, ArenaDefinition> arenas = new HashMap<>();
    private final Logger logger;
    private static final String WORLD_KEY = "world";

    public ArenaStorage(Beastmode plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        reload();
    }

    public void reload() {
        arenas.clear();
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection root = config.getConfigurationSection("arenas");
        if (root == null) {
            return;
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            Cuboid runnerWall = readCuboid(section.getConfigurationSection("runnerWall"));
            Cuboid beastWall = readCuboid(section.getConfigurationSection("beastWall"));
            Location finishButton = readLocation(section.getConfigurationSection("finishButton"));
            Cuboid legacyFinish = readCuboid(section.getConfigurationSection("finishRegion"));
            if (finishButton == null && legacyFinish != null) {
                World world = legacyFinish.getWorld();
                if (world != null) {
                    Location min = legacyFinish.getMin();
                    Location max = legacyFinish.getMax();
                    double centerX = (min.getX() + max.getX()) / 2.0;
                    double centerY = (min.getY() + max.getY()) / 2.0;
                    double centerZ = (min.getZ() + max.getZ()) / 2.0;
                    finishButton = new Location(world, Math.floor(centerX), Math.floor(centerY), Math.floor(centerZ));
                }
            }
        Location runnerSpawn = readLocation(section.getConfigurationSection("runnerSpawn"));
        Location beastSpawn = readLocation(section.getConfigurationSection("beastSpawn"));
        Location waitingSpawn = readLocation(section.getConfigurationSection("waitingSpawn"));
            String modeName = section.getString("gameMode", GameModeType.HUNT.name());
            GameModeType mode;
            try {
                mode = modeName != null ? GameModeType.valueOf(modeName.toUpperCase(Locale.ENGLISH)) : GameModeType.HUNT;
            } catch (IllegalArgumentException ex) {
                logger.log(Level.WARNING, "Unknown game mode '{0}' for arena {1}; defaulting to HUNT.", new Object[]{modeName, key});
                mode = GameModeType.HUNT;
            }

            int runnerDelay = section.getInt("runnerWallDelaySeconds", mode.isTimeTrial() ? 0 : -1);
            int beastDelay = section.getInt("beastReleaseDelaySeconds", -1);
            int beastSpeed = Math.max(section.getInt("beastSpeedLevel", 1), 0);
        int minRunners = Math.max(section.getInt("minRunners", 1), 1);
        int maxRunners = section.getInt("maxRunners", 0);
        if (maxRunners < 0) {
        maxRunners = 0;
        }
        if (maxRunners > 0 && maxRunners < minRunners) {
        logger.log(Level.WARNING, "Arena {0} has maxRunners {1} smaller than minRunners {2}; clamping to match minimum.",
            new Object[]{key, maxRunners, minRunners});
        maxRunners = minRunners;
        }

            ArenaDefinition arena = ArenaDefinition.builder(key)
                    .runnerWall(runnerWall)
                    .beastWall(beastWall)
            .gameMode(mode)
            .finishRegion(legacyFinish)
            .finishButton(finishButton)
                    .runnerSpawn(runnerSpawn)
                    .beastSpawn(beastSpawn)
            .waitingSpawn(waitingSpawn)
                    .runnerWallDelaySeconds(runnerDelay)
                    .beastReleaseDelaySeconds(beastDelay)
            .beastSpeedLevel(beastSpeed)
            .minRunners(minRunners)
            .maxRunners(maxRunners)
                    .build();
            arenas.put(key.toLowerCase(), arena);
        }
    }

    public void saveArena(ArenaDefinition arena) {
        FileConfiguration config = plugin.getConfig();
        String path = "arenas." + arena.getName();
        config.set(path, null);
        ConfigurationSection section = config.createSection(path);

        writeCuboid(section.createSection("runnerWall"), arena.getRunnerWall());
        writeCuboid(section.createSection("beastWall"), arena.getBeastWall());
        section.set("finishRegion", null);
        section.set("gameMode", arena.getGameModeType().name());
        writeLocation(section.createSection("finishButton"), arena.getFinishButton());
        writeLocation(section.createSection("runnerSpawn"), arena.getRunnerSpawn());
        writeLocation(section.createSection("beastSpawn"), arena.getBeastSpawn());
    writeLocation(section.createSection("waitingSpawn"), arena.getWaitingSpawn());
        section.set("runnerWallDelaySeconds", arena.getRunnerWallDelaySeconds());
        section.set("beastReleaseDelaySeconds", arena.getBeastReleaseDelaySeconds());
        section.set("beastSpeedLevel", arena.getBeastSpeedLevel());
    section.set("minRunners", arena.getMinRunners());
    section.set("maxRunners", arena.getMaxRunners());

        plugin.saveConfig();
        reload();
    }

    public ArenaDefinition getArena(String name) {
        if (name == null) {
            return null;
        }
        return arenas.get(name.toLowerCase());
    }

    public Collection<ArenaDefinition> getArenas() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    public boolean exists(String name) {
        return getArena(name) != null;
    }

    public void updateWaitingSpawn(String arenaName, Location waitingSpawn) {
        ArenaDefinition arena = getArena(arenaName);
        if (arena == null) {
            return;
        }
        ArenaDefinition updated = arena.withWaitingSpawn(waitingSpawn);
        saveArena(updated);
    }

    public void updateRunnerSpawn(String arenaName, Location runnerSpawn) {
        ArenaDefinition arena = getArena(arenaName);
        if (arena == null) {
            return;
        }
        ArenaDefinition updated = arena.withRunnerSpawn(runnerSpawn);
        saveArena(updated);
    }

    public void updateBeastSpawn(String arenaName, Location beastSpawn) {
        ArenaDefinition arena = getArena(arenaName);
        if (arena == null) {
            return;
        }
        ArenaDefinition updated = arena.withBeastSpawn(beastSpawn);
        saveArena(updated);
    }

    public void updateRunnerWallDelay(String arenaName, int seconds) {
        ArenaDefinition arena = getArena(arenaName);
        if (arena == null) {
            return;
        }
        ArenaDefinition updated = arena.withRunnerWallDelay(seconds);
        saveArena(updated);
    }

    public void updateBeastReleaseDelay(String arenaName, int seconds) {
        ArenaDefinition arena = getArena(arenaName);
        if (arena == null) {
            return;
        }
        ArenaDefinition updated = arena.withBeastReleaseDelay(seconds);
        saveArena(updated);
    }

    public void updateBeastSpeedLevel(String arenaName, int level) {
        ArenaDefinition arena = getArena(arenaName);
        if (arena == null) {
            return;
        }
        ArenaDefinition updated = arena.withBeastSpeedLevel(level);
        saveArena(updated);
    }

    public void updateMinRunners(String arenaName, int minRunners) {
        ArenaDefinition arena = getArena(arenaName);
        if (arena == null) {
            return;
        }
        ArenaDefinition updated = arena.withMinRunners(minRunners);
        saveArena(updated);
    }

    public void updateMaxRunners(String arenaName, int maxRunners) {
        ArenaDefinition arena = getArena(arenaName);
        if (arena == null) {
            return;
        }
        ArenaDefinition updated = arena.withMaxRunners(maxRunners);
        saveArena(updated);
    }

    public void updateRunnerWall(String arenaName, Cuboid cuboid) {
        ArenaDefinition arena = getArena(arenaName);
        if (arena == null) {
            return;
        }
        ArenaDefinition updated = arena.withRunnerWall(cuboid);
        saveArena(updated);
    }

    public void updateBeastWall(String arenaName, Cuboid cuboid) {
        ArenaDefinition arena = getArena(arenaName);
        if (arena == null) {
            return;
        }
        ArenaDefinition updated = arena.withBeastWall(cuboid);
        saveArena(updated);
    }

    public void updateFinishButton(String arenaName, Location finishButton) {
        ArenaDefinition arena = getArena(arenaName);
        if (arena == null) {
            return;
        }
        ArenaDefinition updated = arena.withFinishButton(finishButton);
        saveArena(updated);
    }

    public boolean deleteArena(String arenaName) {
        if (arenaName == null || arenaName.trim().isEmpty()) {
            return false;
        }

        ArenaDefinition arena = getArena(arenaName);
        if (arena == null) {
            return false;
        }

        FileConfiguration config = plugin.getConfig();
        config.set("arenas." + arena.getName(), null);
        plugin.saveConfig();
        reload();
        return true;
    }

    private Location readLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
    String worldName = section.getString(WORLD_KEY);
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw");
        float pitch = (float) section.getDouble("pitch");

        if (worldName == null) {
            logger.log(Level.WARNING, "Missing world name while loading location: {0}", section.getCurrentPath());
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            logger.log(Level.WARNING, "World ''{0}'' is not loaded while reading {1}", new Object[]{worldName, section.getCurrentPath()});
            return null;
        }

        return new Location(world, x, y, z, yaw, pitch);
    }

    private Cuboid readCuboid(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String worldName = section.getString(WORLD_KEY);
        ConfigurationSection pos1Sec = section.getConfigurationSection("pos1");
        ConfigurationSection pos2Sec = section.getConfigurationSection("pos2");
        if (worldName == null || pos1Sec == null || pos2Sec == null) {
            logger.log(Level.WARNING, "Incomplete cuboid definition at {0}", section.getCurrentPath());
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            logger.log(Level.WARNING, "World ''{0}'' is not loaded while reading {1}", new Object[]{worldName, section.getCurrentPath()});
            return null;
        }

        Location pos1 = new Location(world,
                pos1Sec.getDouble("x"),
                pos1Sec.getDouble("y"),
                pos1Sec.getDouble("z"));
        Location pos2 = new Location(world,
                pos2Sec.getDouble("x"),
                pos2Sec.getDouble("y"),
                pos2Sec.getDouble("z"));
        try {
            return Cuboid.fromCorners(pos1, pos2);
        } catch (IllegalArgumentException ex) {
            logger.warning("Failed to load cuboid at " + section.getCurrentPath() + ": " + ex.getMessage());
            return null;
        }
    }

    private void writeLocation(ConfigurationSection section, Location location) {
        if (section == null || location == null || location.getWorld() == null) {
            return;
        }
    section.set(WORLD_KEY, location.getWorld().getName());
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", location.getYaw());
        section.set("pitch", location.getPitch());
    }

    private void writeCuboid(ConfigurationSection section, Cuboid cuboid) {
        if (section == null || cuboid == null) {
            return;
        }
    section.set(WORLD_KEY, cuboid.getWorldName());
        writePoint(section.createSection("pos1"), cuboid.getMin());
        writePoint(section.createSection("pos2"), cuboid.getMax());
    }

    private void writePoint(ConfigurationSection section, Location location) {
        if (section == null || location == null) {
            return;
        }
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
    }
}
