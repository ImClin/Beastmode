package com.colin.beastmode.storage;

import com.colin.beastmode.Beastmode;
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
            int runnerDelay = section.getInt("runnerWallDelaySeconds", -1);
            int beastDelay = section.getInt("beastReleaseDelaySeconds", -1);

            ArenaDefinition arena = ArenaDefinition.builder(key)
                    .runnerWall(runnerWall)
                    .beastWall(beastWall)
            .finishRegion(legacyFinish)
            .finishButton(finishButton)
                    .runnerSpawn(runnerSpawn)
                    .beastSpawn(beastSpawn)
            .waitingSpawn(waitingSpawn)
                    .runnerWallDelaySeconds(runnerDelay)
                    .beastReleaseDelaySeconds(beastDelay)
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
        writeLocation(section.createSection("finishButton"), arena.getFinishButton());
        writeLocation(section.createSection("runnerSpawn"), arena.getRunnerSpawn());
        writeLocation(section.createSection("beastSpawn"), arena.getBeastSpawn());
    writeLocation(section.createSection("waitingSpawn"), arena.getWaitingSpawn());
        section.set("runnerWallDelaySeconds", arena.getRunnerWallDelaySeconds());
        section.set("beastReleaseDelaySeconds", arena.getBeastReleaseDelaySeconds());

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
