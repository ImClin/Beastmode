package com.colin.beastmode.game;

import com.colin.beastmode.model.Cuboid;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates operations on arena barrier regions so GameManager stays focused on flow control.
 */
final class ArenaBarrierService {

    List<BlockState> capture(Cuboid cuboid) {
        List<BlockState> states = new ArrayList<>();
        if (cuboid == null) {
            return states;
        }
        World world = cuboid.getWorld();
        if (world == null) {
            return states;
        }

        Location min = cuboid.getMin();
        Location max = cuboid.getMax();
        int minX = (int) Math.floor(min.getX());
        int minY = (int) Math.floor(min.getY());
        int minZ = (int) Math.floor(min.getZ());
        int maxX = (int) Math.floor(max.getX());
        int maxY = (int) Math.floor(max.getY());
        int maxZ = (int) Math.floor(max.getZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    states.add(block.getState());
                }
            }
        }
        return states;
    }

    void clear(Cuboid cuboid) {
        if (cuboid == null) {
            return;
        }
        World world = cuboid.getWorld();
        if (world == null) {
            return;
        }

        Location min = cuboid.getMin();
        Location max = cuboid.getMax();
        int minX = (int) Math.floor(min.getX());
        int minY = (int) Math.floor(min.getY());
        int minZ = (int) Math.floor(min.getZ());
        int maxX = (int) Math.floor(max.getX());
        int maxY = (int) Math.floor(max.getY());
        int maxZ = (int) Math.floor(max.getZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    block.setType(org.bukkit.Material.AIR, false);
                }
            }
        }
    }

    void restore(List<BlockState> states) {
        if (states == null) {
            return;
        }
        for (BlockState state : states) {
            if (state != null) {
                state.update(true, false);
            }
        }
    }
}
