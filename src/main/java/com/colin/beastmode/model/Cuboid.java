package com.colin.beastmode.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class Cuboid {

    private final String worldName;
    private final Location min;
    private final Location max;

    private Cuboid(String worldName, Location min, Location max) {
        this.worldName = worldName;
        this.min = min;
        this.max = max;
    }

    public static Cuboid fromCorners(Location first, Location second) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("Both locations must be non-null");
        }
        if (first.getWorld() == null || second.getWorld() == null) {
            throw new IllegalArgumentException("Locations must be bound to a world");
        }
        if (!first.getWorld().equals(second.getWorld())) {
            throw new IllegalArgumentException("Locations must be in the same world");
        }

        double minX = Math.min(first.getX(), second.getX());
        double minY = Math.min(first.getY(), second.getY());
        double minZ = Math.min(first.getZ(), second.getZ());
        double maxX = Math.max(first.getX(), second.getX());
        double maxY = Math.max(first.getY(), second.getY());
        double maxZ = Math.max(first.getZ(), second.getZ());

        Location min = new Location(first.getWorld(), minX, minY, minZ);
        Location max = new Location(first.getWorld(), maxX, maxY, maxZ);
        return new Cuboid(first.getWorld().getName(), min, max);
    }

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    public String getWorldName() {
        return worldName;
    }

    public Location getMin() {
        return min.clone();
    }

    public Location getMax() {
        return max.clone();
    }

    public boolean contains(Location location) {
        if (location == null) {
            return false;
        }
        if (location.getWorld() == null || !location.getWorld().getName().equalsIgnoreCase(worldName)) {
            return false;
        }

        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();

        double minX = Math.min(min.getX(), max.getX());
        double minY = Math.min(min.getY(), max.getY());
        double minZ = Math.min(min.getZ(), max.getZ());
        double maxX = Math.max(min.getX(), max.getX());
        double maxY = Math.max(min.getY(), max.getY());
        double maxZ = Math.max(min.getZ(), max.getZ());

        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }
}
